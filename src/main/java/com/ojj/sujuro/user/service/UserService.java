package com.ojj.sujuro.user.service;

import com.ojj.sujuro.user.dto.CodeOption;
import com.ojj.sujuro.user.dto.UserDto;
import com.ojj.sujuro.user.dto.UserForm;
import com.ojj.sujuro.user.dto.UserProjectDto;
import com.ojj.sujuro.user.mapper.UserMapper;
import com.ojj.sujuro.user.support.PasswordPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;

/**
 * 직원 관리 (FR-003 ~ FR-008). 경영지원실만 쓴다. (URL 접근 규칙은 SecurityConfig의 /users/**)
 *
 * 입력 검사는 문제 목록을 돌려주고(화면에 모두 보여 주려고), 그 밖의 업무 규칙 위반은 BusinessException을 던진다.
 * 권한은 입력받지 않고 소속 부서와 직급으로 자동으로 정한다. (FR-007)
 *
 * 팀을 옮기거나 비활성으로 바꿀 때: 진행 중 사업(참여 확정~제출 완료)의 제안팀원·목차 담당자 자리가 있으면
 * 화면에서 [같이 빼기] / [그대로 두기]를 고르게 한다. 같이 빼면 명단에서 빼고 목차 담당자를 비운다.
 * 그 팀이 주관하는 진행 중 사업의 하나뿐인 팀장은 팀 이동·직급 변경·비활성을 막는다(맡을 사람이 없어지므로).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /** 공통코드 DEPTS: 경영지원실 / 영업부 (이 두 부서는 직급이 없다) */
    private static final String DEPT_MGMT = "DP010";
    private static final String DEPT_SALES = "DP020";
    /** 공통코드 JBGDS: 팀장 */
    private static final String RANK_LEADER = "JG020";

    private static final Pattern EMP_NO = Pattern.compile("^[0-9]{4}-[0-9]{4}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{9,11}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    // ---------------- 조회 (FR-003) ----------------

    public List<UserDto> search(String keyword, String status, Long teamSn) {
        return userMapper.search(escapeLike(keyword), blankToNull(status), teamSn);
    }

    public int countAll() {
        return userMapper.countAll();
    }

    public UserDto find(String userId) {
        return userMapper.findById(userId);
    }

    public List<CodeOption> teams() {
        return userMapper.findTeams();
    }

    public List<CodeOption> ranks() {
        return userMapper.findRanks();
    }

    public List<CodeOption> statuses() {
        return userMapper.findStatuses();
    }

    // ---------------- 추가 (FR-004) ----------------

    /** 입력을 검사해서 고쳐야 할 문제 목록을 돌려준다. 문제가 없으면 빈 목록. 전화번호·이메일은 정리한 값으로 바뀐다. */
    public List<String> validateNew(UserForm form) {
        normalize(form);
        List<String> errors = new ArrayList<>();
        String id = form.getUserId();
        if (id == null || id.isEmpty()) {
            errors.add("사번을 입력해 주세요.");
        } else if (!EMP_NO.matcher(id).matches()) {
            errors.add("사번은 숫자 4자리-숫자 4자리 형식이어야 합니다. (예: 2024-0001)");
        } else if (userMapper.countById(id) > 0) {
            errors.add("이미 등록된 사번입니다.");
        }
        validateCommon(form, null, errors);
        return errors;
    }

    /** 직원을 추가하고 초기 비밀번호를 돌려준다. 계정 상태는 '초기 비밀번호'이며 처음 로그인할 때 변경을 강제한다. (FR-002) */
    @Transactional
    public String create(UserForm form, String adminId) {
        String password = PasswordPolicy.generateInitial();
        try {
            userMapper.insert(form.getUserId(), form.getUserNm(), form.getTeamSn(), form.getJbgdCd(),
                    authorityCode(form.getTeamSn(), form.getJbgdCd()),
                    passwordEncoder.encode(password), LoginUser.STATUS_INITIAL,
                    form.getTelno(), form.getEmlAddr(), adminId);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("이미 등록된 사번입니다.");
        }
        return password;
    }

    // ---------------- 수정 (FR-005) ----------------

    public List<String> validateEdit(String userId, UserForm form, String adminId) {
        normalize(form);
        List<String> errors = new ArrayList<>();
        UserDto before = userMapper.findById(userId);
        if (before == null) {
            errors.add("직원 정보를 찾을 수 없습니다.");
            return errors;
        }
        validateCommon(form, userId, errors);
        // 경영지원실 담당자가 자기 권한을 스스로 없애면 이 화면에 다시 들어올 수 없다.
        if (errors.isEmpty() && userId.equals(adminId)
                && !authorityCode(form.getTeamSn(), form.getJbgdCd()).equals(trim(before.getAuthrtCd()))) {
            errors.add("본인의 권한이 바뀌는 수정은 다른 경영지원실 담당자가 해야 합니다.");
        }
        if (errors.isEmpty()) {
            String block = leaderBlock(before, form.getTeamSn(), authorityCode(form.getTeamSn(), form.getJbgdCd()), false);
            if (block != null) {
                errors.add(block);
            }
        }
        return errors;
    }

    /** 소속 팀이 바뀌는 수정인가 (바뀌면 진행 중 사업 정리를 물어본다) */
    public boolean teamChanged(String userId, UserForm form) {
        UserDto before = userMapper.findById(userId);
        return before != null && form.getTeamSn() != null && !form.getTeamSn().equals(before.getTeamSn());
    }

    /** 이 직원이 제안팀원인 진행 중 사업 (팀 이동·비활성 전에 화면에 보여 준다) */
    public List<UserProjectDto> activeProposals(String userId) {
        return userMapper.findActiveProposals(userId);
    }

    /**
     * 이름, 소속 팀, 직급, 연락처를 바꾸고 권한을 다시 정한다.
     * removeFromProposals 가 true 이고 팀이 바뀌면 진행 중 사업의 제안팀원·목차 담당자 자리에서 뺀다. 빠진 사업 수를 돌려준다.
     */
    @Transactional
    public int update(String userId, UserForm form, boolean removeFromProposals) {
        boolean moved = teamChanged(userId, form);
        userMapper.updateInfo(userId, form.getUserNm(), form.getTeamSn(), form.getJbgdCd(),
                authorityCode(form.getTeamSn(), form.getJbgdCd()), form.getTelno(), form.getEmlAddr());
        return moved && removeFromProposals ? detach(userId) : 0;
    }

    // ---------------- 비활성 (FR-006) ----------------

    /** 퇴사한 직원은 지우지 않고 비활성으로 바꾼다. 업무 이력이 그대로 남는다. */
    @Transactional
    public int deactivate(String userId, String adminId, boolean removeFromProposals) {
        UserDto user = checkDeactivatable(userId, adminId);
        userMapper.updateStatus(userId, LoginUser.STATUS_INACTIVE);
        return removeFromProposals ? detach(userId) : 0;
    }

    /** 비활성으로 바꿀 수 있는지 먼저 검사한다(확인 화면을 띄우기 전에도 쓴다). 안 되면 BusinessException */
    public UserDto checkDeactivatable(String userId, String adminId) {
        UserDto user = requireUser(userId);
        if (userId.equals(adminId)) {
            throw new BusinessException("로그인 중인 본인 계정은 비활성으로 바꿀 수 없습니다.");
        }
        if (user.isInactive()) {
            throw new BusinessException("이미 비활성인 계정입니다.");
        }
        String block = leaderBlock(user, user.getTeamSn(), trim(user.getAuthrtCd()), true);
        if (block != null) {
            throw new BusinessException(block);
        }
        return user;
    }

    // ---------------- 비밀번호 초기화 (FR-008) ----------------

    /**
     * 새 초기 비밀번호를 발급한다. 계정 상태는 '초기 비밀번호'가 되고 로그인 실패 횟수도 0이 되어 잠금이 풀린다.
     * 비활성 계정에 쓰면 다시 로그인할 수 있게 되므로 복구와 같다.
     */
    @Transactional
    public String resetPassword(String userId, String adminId) {
        requireUser(userId);
        if (userId.equals(adminId)) {
            throw new BusinessException("본인 비밀번호는 내 정보 화면에서 바꿔 주세요.");
        }
        String password = PasswordPolicy.generateInitial();
        userMapper.updatePassword(userId, passwordEncoder.encode(password), LoginUser.STATUS_INITIAL);
        return password;
    }

    // ---------------- 내부 ----------------

    /**
     * 진행 중 사업의 제안팀원 명단에서 빼고, 맡던 목차의 담당자를 비운다. (비운 목차는 목차 화면에서 '미지정'으로 보인다)
     * 올린 파일·Q&A·요구사항 같은 작업 기록은 그대로 둔다. 빠진 사업 수를 돌려준다.
     */
    private int detach(String userId) {
        List<UserProjectDto> projects = userMapper.findActiveProposals(userId);
        if (projects.isEmpty()) {
            return 0;
        }
        userMapper.clearActiveTocOwner(userId);
        userMapper.deleteActiveMemberships(userId);
        return projects.size();
    }

    /**
     * 그 팀이 주관하는 진행 중 사업이 있는데 이 직원이 그 팀의 하나뿐인 팀장이면, 팀장 자리가 비는 변경을 막는다.
     * 팀 이동, 팀장 → 다른 직급, 비활성이 해당된다. 막을 이유를 돌려주고, 괜찮으면 null.
     */
    private String leaderBlock(UserDto before, Long newTeamSn, String newAuthrtCd, boolean deactivating) {
        if (!Role.CD_LEADER.equals(trim(before.getAuthrtCd())) || before.getTeamSn() == null) {
            return null;
        }
        boolean losesLeader = deactivating || !before.getTeamSn().equals(newTeamSn) || !Role.CD_LEADER.equals(newAuthrtCd);
        if (!losesLeader) {
            return null;
        }
        int led = userMapper.countActiveLedProjects(before.getTeamSn());
        if (led == 0 || userMapper.countOtherLeaders(before.getTeamSn(), before.getUserId()) > 0) {
            return null;
        }
        return before.getTeamNm() + "이(가) 주관하는 진행 중 사업 " + led + "건의 하나뿐인 팀장입니다. "
                + "참여 결정·일정·통합본을 맡을 사람이 없어지므로, 먼저 다른 직원을 이 팀의 팀장으로 지정한 뒤 바꿔 주세요.";
    }

    /** 권한 자동 부여 (FR-007): 경영지원실 → 경영지원실, 영업부 → 영업부, 그 밖의 팀은 팀장 직급이면 팀장, 아니면 팀원 */
    String authorityCode(Long teamSn, String jbgdCd) {
        String dept = trim(userMapper.findDeptCd(teamSn));
        if (DEPT_MGMT.equals(dept)) {
            return Role.CD_MGMT;
        }
        if (DEPT_SALES.equals(dept)) {
            return Role.CD_SALES;
        }
        return RANK_LEADER.equals(trim(jbgdCd)) ? Role.CD_LEADER : Role.CD_MEMBER;
    }

    private void validateCommon(UserForm form, String exceptUserId, List<String> errors) {
        String name = form.getUserNm();
        if (name == null || name.isEmpty()) {
            errors.add("이름을 입력해 주세요.");
        } else if (name.length() > 100) {
            errors.add("이름은 100자 이하여야 합니다.");
        }

        String dept = null;
        if (form.getTeamSn() == null) {
            errors.add("소속 팀을 선택해 주세요.");
        } else {
            dept = trim(userMapper.findDeptCd(form.getTeamSn()));
            if (dept == null) {
                errors.add("사용할 수 없는 팀입니다.");
            }
        }

        if (DEPT_MGMT.equals(dept) || DEPT_SALES.equals(dept)) {
            // 경영지원실·영업부는 직급이 없다. 화면에서 값이 와도 비워서 저장한다. (v4.3)
            form.setJbgdCd(null);
        } else if (form.getJbgdCd() == null || form.getJbgdCd().isEmpty()) {
            errors.add("직급을 선택해 주세요.");
        } else if (!isRank(form.getJbgdCd())) {
            errors.add("선택할 수 없는 직급입니다.");
        }

        if (form.getTelno() != null && !PHONE.matcher(form.getTelno()).matches()) {
            errors.add("전화번호는 숫자 9~11자리로 입력해 주세요. (예: 010-1234-5678)");
        }
        // 이메일은 필수다. 마감·단계 전환 메일을 받을 주소가 없으면 알림이 가지 않기 때문 (v4.1)
        if (form.getEmlAddr() == null || form.getEmlAddr().isEmpty()) {
            errors.add("이메일을 입력해 주세요.");
        } else if (form.getEmlAddr().length() > 320 || !EMAIL.matcher(form.getEmlAddr()).matches()) {
            errors.add("이메일 형식이 올바르지 않습니다.");
        } else if (userMapper.countByEmail(form.getEmlAddr(), exceptUserId) > 0) {
            // 알림 메일이 엉뚱한 사람에게 몰리지 않게 한 사람에 한 주소만 쓴다
            errors.add("이미 다른 직원이 쓰는 이메일입니다.");
        }
    }

    private boolean isRank(String code) {
        for (CodeOption r : userMapper.findRanks()) {
            if (trim(r.getCode()).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private UserDto requireUser(String userId) {
        UserDto user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("직원 정보를 찾을 수 없습니다.");
        }
        return user;
    }

    /** 앞뒤 공백을 지우고, 비어 있으면 null. 전화번호는 숫자만 남긴다. */
    private void normalize(UserForm form) {
        form.setUserId(blankToNull(form.getUserId()));
        form.setUserNm(blankToNull(form.getUserNm()));
        form.setJbgdCd(blankToNull(form.getJbgdCd()));
        String tel = blankToNull(form.getTelno());
        form.setTelno(tel == null ? null : tel.replaceAll("[\\s-]", ""));
        form.setEmlAddr(blankToNull(form.getEmlAddr()));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** LIKE 검색에서 % _ 가 와일드카드로 먹히지 않게 \ 를 붙인다. */
    private static String escapeLike(String keyword) {
        String k = blankToNull(keyword);
        return k == null ? null : k.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
