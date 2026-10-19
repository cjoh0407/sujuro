package com.ojj.sujuro.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 대시보드 (FR-089, 090). 지금은 공통 레이아웃을 확인하기 위한 임시 화면이다.
 * 10/24에 역할별 할 일과 현황 카드를 채운다.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard/index";
    }
}
