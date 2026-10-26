package com.ojj.sujuro.notification;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ojj.sujuro.auth.AccessChecker;
import com.ojj.sujuro.auth.LoginUser;

/**
 * 알림 화면 (FR-032, FR-033). 로그인한 사용자 누구나 자기 알림만 본다.
 *
 *   GET  /alerts            목록 (?filter=unread 로 안 읽은 알림만)
 *   POST /alerts/{id}/open  읽음 처리 후 관련 화면으로 이동
 *   POST /alerts/read-all   모두 읽음
 */
@Controller
public class NotificationController {

    private final NotificationInboxMapper mapper;
    private final AccessChecker accessChecker;

    public NotificationController(NotificationInboxMapper mapper, AccessChecker accessChecker) {
        this.mapper = mapper;
        this.accessChecker = accessChecker;
    }

    @GetMapping("/alerts")
    public String list(@AuthenticationPrincipal LoginUser user,
                       @RequestParam(value = "filter", defaultValue = "all") String filter,
                       Model model) {
        boolean unreadOnly = "unread".equals(filter);
        List<NotificationDto> items = mapper.findMine(user.getEmpNo(), unreadOnly);
        for (NotificationDto n : items) {
            n.setLink(linkOf(n, user));
        }
        model.addAttribute("items", items);
        model.addAttribute("filter", unreadOnly ? "unread" : "all");
        return "notification/alerts";
    }

    @Transactional
    @PostMapping("/alerts/{notiSn}/open")
    public String open(@AuthenticationPrincipal LoginUser user, @PathVariable("notiSn") Long notiSn) {
        NotificationDto n = mapper.findMineOne(user.getEmpNo(), notiSn);
        if (n == null) {
            return "redirect:/alerts";
        }
        mapper.markRead(user.getEmpNo(), notiSn);
        String link = linkOf(n, user);
        return link != null ? "redirect:" + link : "redirect:/alerts";
    }

    @Transactional
    @PostMapping("/alerts/read-all")
    public String readAll(@AuthenticationPrincipal LoginUser user) {
        mapper.markAllRead(user.getEmpNo());
        return "redirect:/alerts";
    }

    /**
     * 알림을 눌렀을 때 갈 곳. 제안팀원인 사업건만 제안 화면으로 보낸다. (NFR-004)
     * 공고 관리 화면이 만들어지면 영업부·경영지원실용 이동 주소를 여기에 추가한다.
     */
    private String linkOf(NotificationDto n, LoginUser user) {
        if (n.getPbancSn() != null && accessChecker.isProposalMember(n.getPbancSn(), user)) {
            return "/proposal?bid=" + n.getPbancSn();
        }
        return null;
    }
}
