package com.ojj.sujuro.auth.web;

import com.ojj.sujuro.auth.dto.LoginUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 로그인 화면. 실제 인증 처리는 Spring Security(SecurityConfig)가 한다. (FR-001) */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal LoginUser user) {
        if (user != null) {
            return "redirect:/dashboard"; // 이미 로그인한 사용자는 로그인 화면을 건너뛴다.
        }
        return "login";
    }
}
