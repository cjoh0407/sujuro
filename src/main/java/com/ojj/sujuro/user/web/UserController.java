package com.ojj.sujuro.user.web;

import com.ojj.sujuro.user.dto.UserDto;
import com.ojj.sujuro.user.dto.UserForm;
import com.ojj.sujuro.user.dto.UserProjectDto;
import com.ojj.sujuro.user.service.UserService;
import com.ojj.sujuro.user.support.InitialPasswordMailer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 관리 화면 (FR-003 ~ FR-008). 경영지원실만 들어올 수 있다. (SecurityConfig의 /users/** 규칙)
 *
 * 직원 등록·수정은 모달에서 한다. 입력 오류가 나면 화면을 다시 그리면서 모달을 연 채로 오류를 보여 준다 (openModal).
 * 쓰기 요청은 처리가 끝나면 목록으로 되돌아가고(redirect), 보던 검색어·상태·소속 필터를 유지한다.
 *
 * 초기 비밀번호(등록·초기화)는 직원 이메일로 보낸다. 보냈으면 화면에 비밀번호를 보여 주지 않고,
 * 보내지 못했을 때만 화면에 한 번 보여 줘서 직접 전달하게 한다. (FR-004, FR-008)
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final InitialPasswordMailer passwordMailer;

    /** 직원 목록 */
    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser me,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long team,
                       @RequestParam(value = "page", defaultValue = "1") int page,
                       Model model) {
        fill(model, me, keyword, status, team, new UserForm(), null, Collections.<String>emptyList(), page);
        return "user/users";
    }

    /** 직원 추가 (FR-004). 초기 비밀번호는 이메일로 보낸다. */
    @PostMapping
    public String create(@AuthenticationPrincipal LoginUser me,
                         @ModelAttribute UserForm form,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Long team,
                         Model model, RedirectAttributes redirect) {
        List<String> errors = userService.validateNew(form);
        if (!errors.isEmpty()) {
            fill(model, me, keyword, status, team, form, null, errors);
            model.addAttribute("openModal", "create");
            return "user/users";
        }
        try {
            String password = userService.create(form, me.getEmpNo());
            boolean sent = passwordMailer.send(form.getUserNm(), form.getUserId(), form.getEmlAddr(), password, true);
            notifyPassword(redirect, sent, form.getUserNm() + " 님을 등록했습니다.", form.getUserId(), form.getEmlAddr(), password);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        keep(redirect, keyword, status, team);
        return "redirect:/users";
    }

    /**
     * 이름·소속 팀·직급·연락처 수정 (FR-005). 권한은 자동으로 다시 정해진다.
     * 팀이 바뀌는데 진행 중 사업의 제안팀원이면 먼저 [같이 빼기]/[그대로 두기]를 묻는다 (involve = remove | keep).
     */
    @PostMapping("/{userId}/update")
    public String update(@AuthenticationPrincipal LoginUser me,
                         @PathVariable String userId,
                         @ModelAttribute UserForm form,
                         @RequestParam(required = false) String involve,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Long team,
                         Model model, RedirectAttributes redirect) {
        List<String> errors = userService.validateEdit(userId, form, me.getEmpNo());
        if (!errors.isEmpty()) {
            form.setUserId(userId);
            fill(model, me, keyword, status, team, form, userService.find(userId), errors);
            model.addAttribute("openModal", "edit");
            return "user/users";
        }
        if (involve == null && userService.teamChanged(userId, form)) {
            List<UserProjectDto> projects = userService.activeProposals(userId);
            if (!projects.isEmpty()) {
                form.setUserId(userId);
                UserDto user = userService.find(userId);
                fill(model, me, keyword, status, team, form, user, Collections.<String>emptyList());
                model.addAttribute("pendingKind", "move");
                model.addAttribute("pendingUser", user);
                model.addAttribute("pendingProjects", projects);
                return "user/users";
            }
        }
        int removed = userService.update(userId, form, "remove".equals(involve));
        redirect.addFlashAttribute("message", form.getUserNm() + " 님의 정보를 수정했습니다. 권한이 소속과 직급에 맞게 다시 정해졌습니다."
                + removedText(removed));
        keep(redirect, keyword, status, team);
        return "redirect:/users";
    }

    /**
     * 비활성 처리 (FR-006). 진행 중 사업의 제안팀원이면 먼저 [같이 빼기](기본)/[그대로 두기]를 묻는다.
     * 그 팀이 주관하는 진행 중 사업의 하나뿐인 팀장이면 막는다.
     */
    @PostMapping("/{userId}/deactivate")
    public String deactivate(@AuthenticationPrincipal LoginUser me,
                             @PathVariable String userId,
                             @RequestParam(required = false) String involve,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Long team,
                             Model model, RedirectAttributes redirect) {
        try {
            UserDto user = userService.checkDeactivatable(userId, me.getEmpNo());
            if (involve == null) {
                List<UserProjectDto> projects = userService.activeProposals(userId);
                if (!projects.isEmpty()) {
                    fill(model, me, keyword, status, team, new UserForm(), null, Collections.<String>emptyList());
                    model.addAttribute("pendingKind", "deactivate");
                    model.addAttribute("pendingUser", user);
                    model.addAttribute("pendingProjects", projects);
                    return "user/users";
                }
            }
            int removed = userService.deactivate(userId, me.getEmpNo(), "remove".equals(involve));
            redirect.addFlashAttribute("message", user.getUserNm() + " 님을 비활성으로 바꿨습니다. 업무 이력은 그대로 남아 있습니다."
                    + removedText(removed));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        keep(redirect, keyword, status, team);
        return "redirect:/users";
    }

    /** 비밀번호 초기화 (FR-008). 비활성 계정이면 복구와 같다. 새 초기 비밀번호는 이메일로 보낸다. */
    @PostMapping("/{userId}/reset")
    public String reset(@AuthenticationPrincipal LoginUser me,
                        @PathVariable String userId,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) Long team,
                        RedirectAttributes redirect) {
        try {
            String password = userService.resetPassword(userId, me.getEmpNo());
            UserDto user = userService.find(userId);
            boolean sent = passwordMailer.send(user.getUserNm(), userId, user.getEmlAddr(), password, false);
            notifyPassword(redirect, sent, user.getUserNm() + " 님의 비밀번호를 초기화했습니다.", userId, user.getEmlAddr(), password);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        keep(redirect, keyword, status, team);
        return "redirect:/users";
    }

    // ---------------- 내부 ----------------

    /** 메일을 보냈으면 안내 문구만, 못 보냈으면 비밀번호를 한 번 보여 줄 정보를 넘긴다 */
    static void notifyPassword(RedirectAttributes redirect, boolean sent, String title,
                               String empNo, String email, String password) {
        if (sent) {
            redirect.addFlashAttribute("message", title + " 초기 비밀번호를 " + InitialPasswordMailer.mask(email)
                    + " 로 보냈습니다. 직원은 처음 로그인할 때 비밀번호를 바꿔야 합니다.");
            return;
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("empNo", empNo);
        m.put("password", password);
        redirect.addFlashAttribute("issued", m);
    }

    private static String removedText(int removed) {
        return removed > 0 ? " 진행 중 사업 " + removed + "건의 제안팀에서 빼고, 맡던 목차의 담당자를 비웠습니다." : "";
    }

    /** 처리 후 목록으로 돌아갈 때 보던 검색어·상태·소속 필터를 유지한다 */
    private static void keep(RedirectAttributes redirect, String keyword, String status, Long team) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            redirect.addAttribute("keyword", keyword);
        }
        if (status != null && !status.trim().isEmpty()) {
            redirect.addAttribute("status", status);
        }
        if (team != null) {
            redirect.addAttribute("team", team);
        }
    }

    /** 직원 목록 한 쪽에 보여 줄 인원 (11/09: 표 안 스크롤 대신 10명씩 페이지) */
    private static final int PAGE_SIZE = 10;

    private void fill(Model model, LoginUser me, String keyword, String status, Long team,
                      UserForm form, UserDto editUser, List<String> errors) {
        fill(model, me, keyword, status, team, form, editUser, errors, 1);
    }

    private void fill(Model model, LoginUser me, String keyword, String status, Long team,
                      UserForm form, UserDto editUser, List<String> errors, int page) {
        List<UserDto> all = userService.search(keyword, status, team);
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int pageNo = Math.min(Math.max(1, page), totalPages); // 잘못된 번호는 가까운 쪽으로
        int from = (pageNo - 1) * PAGE_SIZE;
        model.addAttribute("users", new java.util.ArrayList<>(all.subList(from, Math.min(all.size(), from + PAGE_SIZE))));
        model.addAttribute("filteredCount", all.size());
        model.addAttribute("page", pageNo);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", userService.countAll());
        model.addAttribute("teams", userService.teams());
        model.addAttribute("ranks", userService.ranks());
        model.addAttribute("statuses", userService.statuses());
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("team", team);
        model.addAttribute("form", form);
        model.addAttribute("editUser", editUser);
        model.addAttribute("errors", errors);
        model.addAttribute("myId", me.getEmpNo());
    }
}
