package com.ojj.sujuro.schedule.web;

import com.ojj.sujuro.schedule.service.ScheduleService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;

/**
 * 일정 (FR-091 ~ FR-094). 접근 규칙은 {@link ScheduleService} 에 있다.
 * 주소: /schedule (SecurityConfig 에서 로그인한 모든 사용자가 들어올 수 있다)
 */
@Controller
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService service;

    @GetMapping("/schedule")
    public String view(@AuthenticationPrincipal LoginUser user,
                       @RequestParam(value = "ym", required = false) String ym,
                       @RequestParam(value = "team", required = false) Long team,
                       @RequestParam(value = "pbanc", required = false) Long pbanc,
                       @RequestParam(value = "edit", required = false) Long edit,
                       Model model) {
        model.addAttribute("view", service.month(user, ym, team, pbanc, edit));
        return "schedule/index";
    }

    /* team, pbanc: 보던 팀 탭·사업 탭. 처리한 뒤 같은 화면으로 돌아가는 데만 쓴다 (권한 판단에는 쓰지 않는다) */

    @PostMapping("/schedule/add")
    public String add(@AuthenticationPrincipal LoginUser user,
                      @RequestParam("ym") String ym,
                      @RequestParam(value = "team", required = false) Long team,
                      @RequestParam(value = "pbanc", required = false) Long pbanc,
                      @RequestParam(value = "pbancSn", required = false) Long pbancSn,
                      @RequestParam("title") String title,
                      @RequestParam("date") String date,
                      @RequestParam(value = "time", required = false) String time,
                      RedirectAttributes ra) {
        return run(ym, team, pbanc, ra, "일정을 추가했습니다. 제안팀원에게 알렸습니다.",
                () -> service.add(user, pbancSn, title, date, time));
    }

    /** 이름·날짜·시간만 고친다. 붙은 사업건은 바꾸지 않는다 */
    @PostMapping("/schedule/edit")
    public String edit(@AuthenticationPrincipal LoginUser user,
                       @RequestParam("ym") String ym,
                       @RequestParam(value = "team", required = false) Long team,
                       @RequestParam(value = "pbanc", required = false) Long pbanc,
                       @RequestParam("id") Long id,
                       @RequestParam("title") String title,
                       @RequestParam("date") String date,
                       @RequestParam(value = "time", required = false) String time,
                       RedirectAttributes ra) {
        return run(ym, team, pbanc, ra, "일정을 수정했습니다. 제안팀원에게 알렸습니다.",
                () -> service.edit(user, id, title, date, time));
    }

    @PostMapping("/schedule/delete")
    public String delete(@AuthenticationPrincipal LoginUser user,
                         @RequestParam("ym") String ym,
                         @RequestParam(value = "team", required = false) Long team,
                         @RequestParam(value = "pbanc", required = false) Long pbanc,
                         @RequestParam("id") Long id,
                         RedirectAttributes ra) {
        return run(ym, team, pbanc, ra, "일정을 삭제했습니다. 제안팀원에게 알렸습니다.", () -> service.delete(user, id));
    }

    /** 처리하고 보던 달·탭으로 돌아간다. 업무 안내(BusinessException)는 화면 위에 알리고, 권한 오류(403)는 그대로 던진다. */
    private String run(String ym, Long team, Long pbanc, RedirectAttributes ra, String okMessage, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("done", okMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("ym", ym);
        if (team != null) {
            ra.addAttribute("team", team);
        }
        if (pbanc != null) {
            ra.addAttribute("pbanc", pbanc);
        }
        return "redirect:/schedule";
    }
}
