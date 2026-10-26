package com.ojj.sujuro.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ojj.sujuro.auth.LoginUser;

/**
 * 대시보드 (FR-089, 090). 역할과 소속 팀에 따라 보이는 내용이 다르다. 구성은 {@link DashboardService}.
 */
@Controller
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("view", service.build(user));
        return "dashboard/index";
    }
}
