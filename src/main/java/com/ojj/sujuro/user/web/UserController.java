package com.ojj.sujuro.user.web;

import com.ojj.sujuro.user.dto.UserDto;
import com.ojj.sujuro.user.dto.UserForm;
import com.ojj.sujuro.user.service.UserService;

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
 * 쓰기 요청은 처리가 끝나면 목록으로 되돌아간다(redirect). 발급한 초기 비밀번호는 그때 한 번만 화면에 보여 주고,
 * 새로고침하면 사라진다. (FR-004)
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 직원 목록. edit에 사번을 주면 오른쪽 입력란이 수정 모드가 된다. */
    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser me,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String edit,
                       Model model) {
        UserForm form = new UserForm();
        UserDto editUser = edit == null ? null : userService.find(edit);
        if (editUser != null) {
            form = toForm(editUser);
        }
        fill(model, me, keyword, status, form, editUser, Collections.<String>emptyList());
        return "user/users";
    }

    /** 직원 추가 (FR-004) */
    @PostMapping
    public String create(@AuthenticationPrincipal LoginUser me,
                         @ModelAttribute UserForm form,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         Model model, RedirectAttributes redirect) {
        List<String> errors = userService.validateNew(form);
        if (!errors.isEmpty()) {
            fill(model, me, keyword, status, form, null, errors);
            return "user/users";
        }
        try {
            String password = userService.create(form, me.getEmpNo());
            redirect.addFlashAttribute("issued", issued(form.getUserNm() + " 님의 초기 비밀번호를 발급했습니다.", form.getUserId(), password));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    /** 이름·소속 팀·직급·연락처 수정 (FR-005). 권한은 자동으로 다시 정해진다. */
    @PostMapping("/{userId}/update")
    public String update(@AuthenticationPrincipal LoginUser me,
                         @PathVariable String userId,
                         @ModelAttribute UserForm form,
                         Model model, RedirectAttributes redirect) {
        List<String> errors = userService.validateEdit(userId, form, me.getEmpNo());
        if (!errors.isEmpty()) {
            form.setUserId(userId);
            fill(model, me, null, null, form, userService.find(userId), errors);
            return "user/users";
        }
        userService.update(userId, form);
        redirect.addFlashAttribute("message", form.getUserNm() + " 님의 정보를 수정했습니다. 권한이 소속과 직급에 맞게 다시 정해졌습니다.");
        return "redirect:/users";
    }

    /** 비활성 처리 (FR-006) */
    @PostMapping("/{userId}/deactivate")
    public String deactivate(@AuthenticationPrincipal LoginUser me,
                             @PathVariable String userId, RedirectAttributes redirect) {
        try {
            userService.deactivate(userId, me.getEmpNo());
            redirect.addFlashAttribute("message", "비활성으로 바꿨습니다. 업무 이력은 그대로 남아 있습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    /** 비밀번호 초기화 (FR-008) */
    @PostMapping("/{userId}/reset")
    public String reset(@AuthenticationPrincipal LoginUser me,
                        @PathVariable String userId, RedirectAttributes redirect) {
        try {
            String password = userService.resetPassword(userId, me.getEmpNo());
            UserDto user = userService.find(userId);
            redirect.addFlashAttribute("issued", issued(user.getUserNm() + " 님의 비밀번호를 초기화했습니다.", userId, password));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    // ---------------- 내부 ----------------

    private void fill(Model model, LoginUser me, String keyword, String status,
                      UserForm form, UserDto editUser, List<String> errors) {
        model.addAttribute("users", userService.search(keyword, status));
        model.addAttribute("teams", userService.teams());
        model.addAttribute("ranks", userService.ranks());
        model.addAttribute("statuses", userService.statuses());
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("form", form);
        model.addAttribute("editUser", editUser);
        model.addAttribute("errors", errors);
        model.addAttribute("myId", me.getEmpNo());
    }

    private static UserForm toForm(UserDto u) {
        UserForm f = new UserForm();
        f.setUserId(u.getUserId());
        f.setUserNm(u.getUserNm());
        f.setTeamSn(u.getTeamSn());
        f.setJbgdCd(u.getJbgdCd() == null ? null : u.getJbgdCd().trim());
        f.setTelno(u.getTelno());
        f.setEmlAddr(u.getEmlAddr());
        return f;
    }

    private static Map<String, String> issued(String title, String empNo, String password) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("empNo", empNo);
        m.put("password", password);
        return m;
    }
}
