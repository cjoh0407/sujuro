package com.ojj.sujuro.bid.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.bid.dto.BidNoticeSearchDTO;
import com.ojj.sujuro.bid.dto.CollectResultDTO;
import com.ojj.sujuro.bid.dto.NoticeDetailDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterResultDTO;
import com.ojj.sujuro.bid.dto.StatusCountDTO;
import com.ojj.sujuro.bid.service.BidNoticeService;
import com.ojj.sujuro.bid.service.NoticeCollectService;
import com.ojj.sujuro.bid.service.NoticeDetailService;
import com.ojj.sujuro.bid.service.NoticeRegisterService;
import com.ojj.sujuro.bid.service.NoticeTeamService;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.history.HistoryService;

/**
 * 공고 관리 (FR-017 ~ 022, 026, 035). 목록을 탭·검색·정렬·페이지로 보여 주고, 공고번호 하나로 등록하고,
 * 공고명을 누르면 상세를 보여 준다.
 *
 * 볼 수 있는 사람은 영업부·팀장·경영지원실이고 팀원은 들어올 수 없다.
 * 이 규칙은 SecurityConfig 의 /notices/** URL 규칙에서 막으므로 여기서 다시 검사하지 않는다.
 *
 * 상세에서 영업부는 담당 팀을 배정하고, 배정된 팀의 팀장은 참여 여부를 결정하거나 중도 철회한다
 * (FR-037~042, NoticeTeamService).
 */
@Controller
@RequestMapping("/notices")
public class NoticeController {

    private final BidNoticeService bidNoticeService;
    private final NoticeRegisterService noticeRegisterService;
    private final NoticeDetailService noticeDetailService;
    private final NoticeCollectService noticeCollectService;
    private final NoticeTeamService noticeTeamService;
    private final HistoryService historyService;

    public NoticeController(BidNoticeService bidNoticeService,
                            NoticeRegisterService noticeRegisterService,
                            NoticeDetailService noticeDetailService,
                            NoticeCollectService noticeCollectService,
                            NoticeTeamService noticeTeamService,
                            HistoryService historyService) {
        this.bidNoticeService = bidNoticeService;
        this.noticeRegisterService = noticeRegisterService;
        this.noticeDetailService = noticeDetailService;
        this.noticeCollectService = noticeCollectService;
        this.noticeTeamService = noticeTeamService;
        this.historyService = historyService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") BidNoticeSearchDTO search, Model model) {
        List<StatusCountDTO> tabs = bidNoticeService.findStatusCounts();

        model.addAttribute("result", bidNoticeService.findPage(search));
        model.addAttribute("lastRun", bidNoticeService.findLastRun());
        model.addAttribute("tabs", tabs);
        // 전체 탭의 숫자. 상태별 건수를 더한 값이라 따로 세지 않는다.
        model.addAttribute("totalAll", tabs.stream().mapToInt(tab -> tab.getCnt()).sum());
        return "bid/notices";
    }

    /**
     * [지금 수집] (FR-025). 오후 5시 자동 수집과 같은 일을 지금 한다. 영업부만 누른다.
     * 방금 누른 경우와 다른 수집이 도는 중이면 수집하지 않고 그 이유를 목록 위에 보여 준다.
     * 나라장터를 여러 번 부르므로 끝날 때까지 수십 초 걸릴 수 있다.
     */
    @PostMapping("/collect")
    @PreAuthorize("hasRole('SALES')")
    public String collect(@AuthenticationPrincipal LoginUser user, RedirectAttributes redirect) {
        try {
            CollectResultDTO result = noticeCollectService.collectNow(user.getEmpNo());
            redirect.addFlashAttribute("collectMessage", "지금 수집을 마쳤습니다: " + result.getSummary());
            if (!result.getFailures().isEmpty()) {
                redirect.addFlashAttribute("collectError",
                        "일부는 나라장터가 응답하지 않아 확인하지 못했습니다. " + String.join(" / ", result.getFailures()));
            }
        } catch (BusinessException e) {
            redirect.addFlashAttribute("collectError", e.getMessage());
        }
        return "redirect:/notices";
    }

    /**
     * 공고 상세 (FR-026, 035, 035-1, 036, 041). 목록에서 공고명을 누르면 온다.
     * 배정·결정 이력은 오창준 님 공통 이력(HistoryService)에서 읽는다. 등록 이력도 같은 표에 있다.
     */
    @GetMapping("/{pbancSn}")
    public String detail(@PathVariable Long pbancSn, @AuthenticationPrincipal LoginUser user, Model model) {
        NoticeDetailDTO notice = noticeDetailService.findDetail(pbancSn);
        if (notice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다.");
        }
        model.addAttribute("notice", notice);
        model.addAttribute("histories", historyService.findByPbanc(pbancSn));
        // 배정 칸은 영업부에게만 보인다 (FR-037)
        if (user.getRole() == Role.SALES && notice.isAssignable()) {
            model.addAttribute("teamOptions", noticeTeamService.findTeamOptions());
            model.addAttribute("ptcpShpOptions", noticeTeamService.findPtcpShpOptions());
        }
        // 참여 결정 카드는 팀 단위 화면이다. 배정된 팀의 팀장에게만 보이고,
        // 영업부·경영지원실·다른 팀 팀장은 머리의 상태 표시와 담당 팀 카드로 진행을 본다 (FR-039)
        boolean assignedLeader = noticeTeamService.isAssignedLeader(pbancSn, user);
        model.addAttribute("assignedLeader", assignedLeader);
        model.addAttribute("canDecide", notice.isAwaitingDecision() && assignedLeader);
        model.addAttribute("canWithdraw", notice.isWithdrawable() && assignedLeader);
        return "bid/notice-detail";
    }

    /** 담당 팀 배정·재배정 (FR-037, 024, 040). 영업부만 한다. */
    @PostMapping("/{pbancSn}/assign")
    @PreAuthorize("hasRole('SALES')")
    public String assign(@PathVariable Long pbancSn,
                         @RequestParam(required = false) Long teamSn,
                         @RequestParam(required = false) String ptcpShpCd,
                         @AuthenticationPrincipal LoginUser user,
                         RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("teamMessage", noticeTeamService.assign(pbancSn, teamSn, ptcpShpCd, user.getEmpNo()));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("teamError", e.getMessage());
        }
        return "redirect:/notices/" + pbancSn;
    }

    /** 다른 팀에도 맡기지 않고 불참으로 종결한다. 영업부만, 신규 공고만, 사유 필수 */
    @PostMapping("/{pbancSn}/close")
    @PreAuthorize("hasRole('SALES')")
    public String close(@PathVariable Long pbancSn,
                        @RequestParam(required = false) String reason,
                        @AuthenticationPrincipal LoginUser user,
                        RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("teamMessage", noticeTeamService.close(pbancSn, reason, user.getEmpNo()));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("teamError", e.getMessage());
            redirect.addFlashAttribute("closeReason", reason); // 입력한 사유를 다시 채워 준다
        }
        return "redirect:/notices/" + pbancSn;
    }

    /**
     * 팀장의 참여·불참 결정 (FR-038~040). result 는 join(참여) 또는 decline(불참)이다.
     * 배정된 팀의 팀장이 아니면 서비스가 403 으로 막는다.
     */
    @PostMapping("/{pbancSn}/decision")
    @PreAuthorize("hasRole('LEADER')")
    public String decide(@PathVariable Long pbancSn,
                         @RequestParam(required = false) String result,
                         @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal LoginUser user,
                         RedirectAttributes redirect) {
        try {
            if (!"join".equals(result) && !"decline".equals(result)) {
                throw new BusinessException("참여 또는 불참을 눌러 주세요.");
            }
            redirect.addFlashAttribute("teamMessage", noticeTeamService.decide(pbancSn, "join".equals(result), reason, user));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("teamError", e.getMessage());
            redirect.addFlashAttribute("decideReason", reason);
        }
        return "redirect:/notices/" + pbancSn;
    }

    /** 중도 철회 (FR-042). 참여 확정 뒤 배정된 팀의 팀장이 사유를 넣어 신청하면 공고가 영업부로 넘어간다. */
    @PostMapping("/{pbancSn}/withdraw")
    @PreAuthorize("hasRole('LEADER')")
    public String withdraw(@PathVariable Long pbancSn,
                           @RequestParam(required = false) String reason,
                           @AuthenticationPrincipal LoginUser user,
                           RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("teamMessage", noticeTeamService.withdraw(pbancSn, reason, user));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("teamError", e.getMessage());
            redirect.addFlashAttribute("withdrawReason", reason);
        }
        return "redirect:/notices/" + pbancSn;
    }

    /**
     * 공고번호 하나로 등록한다 (FR-021, 022). 번호 형식을 보고 발주계획·사전규격·입찰공고 중 단계를 정한다.
     * 등록은 영업부만 한다. 메뉴 접근은 SecurityConfig 가 영업부·팀장·경영지원실까지 열어 두므로
     * 여기서 한 번 더 좁힌다.
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('SALES')")
    public String register(@RequestParam String pbancNo,
                           @AuthenticationPrincipal LoginUser user,
                           RedirectAttributes redirect) {
        NoticeRegisterResultDTO result = noticeRegisterService.register(pbancNo, user.getEmpNo());

        StringBuilder msg = new StringBuilder();
        msg.append(result.getStepNm()).append(" 단계로 등록했습니다: ").append(result.getPbancNm());
        if (result.isAlreadyHasBidNotice()) {
            msg.append(" · 입찰공고(").append(result.getLinkedBidNo()).append(")가 이미 나 있습니다.");
        }
        redirect.addFlashAttribute("registMessage", msg.toString());
        return "redirect:/notices";
    }
}
