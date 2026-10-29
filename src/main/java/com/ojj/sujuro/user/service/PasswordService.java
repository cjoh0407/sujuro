package com.ojj.sujuro.user.service;

import com.ojj.sujuro.user.mapper.UserMapper;
import com.ojj.sujuro.user.support.PasswordPolicy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.dto.LoginUser;

/**
 * 비밀번호 변경 (FR-002, FR-002-1, FR-002-2).
 * 두 메서드 모두 문제가 있으면 안내 문구 목록을 돌려주고, 성공하면 빈 목록을 돌려준다.
 */
@Service
public class PasswordService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 초기 비밀번호 강제 변경 (FR-002). 현재 비밀번호는 방금 로그인으로 확인했으므로 다시 묻지 않는다. 성공하면 계정 상태가 '활성'이 된다. */
    @Transactional
    public List<String> changeInitialPassword(String userId, String newPassword, String confirm) {
        return change(userId, null, newPassword, confirm);
    }

    /** 내 정보 화면에서 변경 (FR-002-2). 현재 비밀번호를 확인한 뒤 바꾼다. */
    @Transactional
    public List<String> changePassword(String userId, String currentPassword, String newPassword, String confirm) {
        return change(userId, currentPassword == null ? "" : currentPassword, newPassword, confirm);
    }

    /** currentPassword가 null이면 현재 비밀번호 확인을 건너뛴다. */
    private List<String> change(String userId, String currentPassword, String newPassword, String confirm) {
        String savedHash = userMapper.findPasswordHash(userId);
        List<String> errors = new ArrayList<>();

        if (savedHash == null) {
            errors.add("계정 정보를 찾을 수 없습니다.");
            return errors;
        }
        if (currentPassword != null && !passwordEncoder.matches(currentPassword, savedHash)) {
            errors.add("현재 비밀번호가 올바르지 않습니다.");
            return errors;
        }

        if (newPassword == null || newPassword.isEmpty()) {
            errors.add("새 비밀번호를 입력해 주세요.");
            return errors;
        }
        errors.addAll(PasswordPolicy.check(newPassword));
        if (!newPassword.equals(confirm)) {
            errors.add("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        // 초기 비밀번호는 평문이 없으므로, 지금 저장된 비밀번호와 같은지로 비교한다.
        if (passwordEncoder.matches(newPassword, savedHash)) {
            errors.add("현재(초기) 비밀번호와 같은 비밀번호는 사용할 수 없습니다.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword), LoginUser.STATUS_ACTIVE);
        return errors;
    }
}
