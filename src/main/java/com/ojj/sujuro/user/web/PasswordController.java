package com.ojj.sujuro.user.web;

import com.ojj.sujuro.user.service.PasswordService;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ojj.sujuro.auth.dto.LoginUser;
import lombok.RequiredArgsConstructor;

/**
 * 초기 비밀번호 변경 화면 (FR-002).
 * 계정 상태가 '초기 비밀번호'인 사용자만 쓴다. 변경이 끝나면 로그아웃시키고 새 비밀번호로 다시 로그인하게 한다.
 * (로그인 정보 객체에 옛 계정 상태가 남지 않게 하기 위해서이다.)
 *
 * 비밀번호 변경 화면 (FR-002-2, 11/09): 헤더 내 정보 팝업의 [비밀번호 변경]에서 들어온다.
 * 현재 비밀번호를 확인한 뒤 바꾸고, 로그인은 그대로 유지한다. 따로 내 정보 화면은 없다.
 */
@Controller
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @GetMapping("/password/initial")
    public String form(@AuthenticationPrincipal LoginUser user) {
        if (!user.isInitialPassword()) {
            return "redirect:/dashboard";
        }
        return "user/password-initial";
    }

    @PostMapping("/password/initial")
    public String change(@AuthenticationPrincipal LoginUser user,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         HttpServletRequest request, HttpServletResponse response,
                         Authentication authentication, Model model) {
        if (!user.isInitialPassword()) {
            return "redirect:/dashboard";
        }
        List<String> errors = passwordService.changeInitialPassword(user.getEmpNo(), newPassword, confirmPassword);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return "user/password-initial";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?pwchanged";
    }

    @GetMapping("/password/change")
    public String changeForm() {
        return "user/password-change";
    }

    @PostMapping("/password/change")
    public String changeByUser(@AuthenticationPrincipal LoginUser user,
                               @RequestParam String currentPassword,
                               @RequestParam String newPassword,
                               @RequestParam String confirmPassword,
                               Model model) {
        List<String> errors = passwordService.changePassword(user.getEmpNo(), currentPassword, newPassword, confirmPassword);
        if (errors.isEmpty()) {
            model.addAttribute("done", true);
        } else {
            model.addAttribute("errors", errors);
        }
        return "user/password-change";
    }
}
