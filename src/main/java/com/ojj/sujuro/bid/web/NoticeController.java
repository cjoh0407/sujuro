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
import com.ojj.sujuro.bid.dto.BidNoticeSearchDTO;
import com.ojj.sujuro.bid.dto.NoticeDetailDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterResultDTO;
import com.ojj.sujuro.bid.dto.StatusCountDTO;
import com.ojj.sujuro.bid.service.BidNoticeService;
import com.ojj.sujuro.bid.service.NoticeDetailService;
import com.ojj.sujuro.bid.service.NoticeRegisterService;
import com.ojj.sujuro.history.HistoryService;

/**
 * 공고 관리 (FR-017 ~ 022, 026, 035). 목록을 탭·검색·정렬·페이지로 보여 주고, 공고번호 하나로 등록하고,
 * 공고명을 누르면 상세를 보여 준다.
 *
 * 볼 수 있는 사람은 영업부·팀장·경영지원실이고 팀원은 들어올 수 없다.
 * 이 규칙은 SecurityConfig 의 /notices/** URL 규칙에서 막으므로 여기서 다시 검사하지 않는다.
 *
 * 아직 붙이지 않은 것
 * - [지금 불러오기] (입찰공고 연결·정정 확인)
 * - 상세의 담당 팀 배정 · 참여 결정 · 중도 철회 입력
 */
@Controller
@RequestMapping("/notices")
public class NoticeController {

    private final BidNoticeService bidNoticeService;
    private final NoticeRegisterService noticeRegisterService;
    private final NoticeDetailService noticeDetailService;
    private final HistoryService historyService;

    public NoticeController(BidNoticeService bidNoticeService,
                            NoticeRegisterService noticeRegisterService,
                            NoticeDetailService noticeDetailService,
                            HistoryService historyService) {
        this.bidNoticeService = bidNoticeService;
        this.noticeRegisterService = noticeRegisterService;
        this.noticeDetailService = noticeDetailService;
        this.historyService = historyService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") BidNoticeSearchDTO search, Model model) {
        List<StatusCountDTO> tabs = bidNoticeService.findStatusCounts();

        model.addAttribute("result", bidNoticeService.findPage(search));
        model.addAttribute("tabs", tabs);
        // 전체 탭의 숫자. 상태별 건수를 더한 값이라 따로 세지 않는다.
        model.addAttribute("totalAll", tabs.stream().mapToInt(tab -> tab.getCnt()).sum());
        return "bid/notices";
    }

    /**
     * 공고 상세 (FR-026, 035, 035-1, 036, 041). 목록에서 공고명을 누르면 온다.
     * 배정·결정 이력은 오창준 님 공통 이력(HistoryService)에서 읽는다. 등록 이력도 같은 표에 있다.
     */
    @GetMapping("/{pbancSn}")
    public String detail(@PathVariable Long pbancSn, Model model) {
        NoticeDetailDTO notice = noticeDetailService.findDetail(pbancSn);
        if (notice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다.");
        }
        model.addAttribute("notice", notice);
        model.addAttribute("histories", historyService.findByPbanc(pbancSn));
        return "bid/notice-detail";
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
