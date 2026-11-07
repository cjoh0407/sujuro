package com.ojj.sujuro.dashboard.web;

import com.ojj.sujuro.dashboard.service.DashboardService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ojj.sujuro.auth.dto.LoginUser;
import lombok.RequiredArgsConstructor;

/**
 * 대시보드 (FR-089, 090). 역할과 소속 팀에 따라 보이는 내용이 다르다. 구성은 {@link DashboardService}.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal LoginUser user,
                            @RequestParam(value = "ym", required = false) String ym,
                            Model model) {
        // ym: 미니 캘린더로 볼 달. 화살표는 이 주소를 비동기로 불러 캘린더만 바꾼다 (dashboard.js)
        model.addAttribute("view", service.build(user, ym));
        return "dashboard/index";
    }
}
