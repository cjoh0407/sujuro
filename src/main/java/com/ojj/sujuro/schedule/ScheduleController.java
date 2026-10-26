package com.ojj.sujuro.schedule;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 일정 (FR-091 ~ FR-094). 접근 규칙은 {@link ScheduleService} 에 있다.
 * 주소: /schedule (SecurityConfig 에서 로그인한 모든 사용자가 들어올 수 있다)
 */
@Controller
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping("/schedule")
    public String view(@AuthenticationPrincipal LoginUser user,
                       @RequestParam(value = "ym", required = false) String ym,
                       @RequestParam(value = "team", required = false) Long team,
                       @RequestParam(value = "edit", required = false) Long edit,
                       Model model) {
        model.addAttribute("view", service.month(user, ym, team, edit));
        return "schedule/index";
    }

    @PostMapping("/schedule/add")
    public String add(@AuthenticationPrincipal LoginUser user,
                      @RequestParam("ym") String ym,
                      @RequestParam("kindCd") String kindCd,
                      @RequestParam("title") String title,
                      @RequestParam("date") String date,
                      RedirectAttributes ra) {
        return run(ym, ra, "내부 일정을 추가했습니다.", () -> service.add(user, kindCd, title, date));
    }

    @PostMapping("/schedule/edit")
    public String edit(@AuthenticationPrincipal LoginUser user,
                       @RequestParam("ym") String ym,
                       @RequestParam("id") Long id,
                       @RequestParam("kindCd") String kindCd,
                       @RequestParam("title") String title,
                       @RequestParam("date") String date,
                       RedirectAttributes ra) {
        return run(ym, ra, "내부 일정을 수정했습니다.", () -> service.edit(user, id, kindCd, title, date));
    }

    @PostMapping("/schedule/delete")
    public String delete(@AuthenticationPrincipal LoginUser user,
                         @RequestParam("ym") String ym,
                         @RequestParam("id") Long id,
                         RedirectAttributes ra) {
        return run(ym, ra, "내부 일정을 삭제했습니다.", () -> service.delete(user, id));
    }

    /** 처리하고 보던 달로 돌아간다. 업무 안내(BusinessException)는 화면 위에 알리고, 권한 오류(403)는 그대로 던진다. */
    private String run(String ym, RedirectAttributes ra, String okMessage, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("done", okMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("ym", ym);
        return "redirect:/schedule";
    }
}
