package com.ojj.sujuro.user;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ojj.sujuro.auth.LoginUser;

/**
 * 내 정보 화면 (FR-002-2). 내 기본 정보를 보여 주고, 현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾼다.
 * 로그인한 사용자 모두(외부 인력 포함)가 쓴다.
 */
@Controller
public class ProfileController {

    private final PasswordService passwordService;

    public ProfileController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @GetMapping("/profile")
    public String profile() {
        return "user/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@AuthenticationPrincipal LoginUser user,
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
        return "user/profile";
    }
}
