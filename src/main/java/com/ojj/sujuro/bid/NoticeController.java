package com.ojj.sujuro.bid;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.LoginUser;

/**
 * 공고 관리 (FR-017 ~ 020). 목록을 탭·검색·정렬·페이지로 보여 준다.
 *
 * 볼 수 있는 사람은 영업부·팀장·경영지원실이고 팀원과 외부 인력은 들어올 수 없다.
 * 이 규칙은 SecurityConfig 의 /notices/** URL 규칙에서 막으므로 여기서 다시 검사하지 않는다.
 *
 * 아직 붙이지 않은 것
 * - 본공고(입찰공고번호)로 등록               : 다음 단계
 * - [지금 불러오기] (본공고 연결·정정 확인)   : 10/22
 * - 공고 상세                                 : 10/23
 */
@Controller
@RequestMapping("/notices")
public class NoticeController {

    private final BidNoticeService bidNoticeService;
    private final NoticeRegisterService noticeRegisterService;

    public NoticeController(BidNoticeService bidNoticeService,
                            NoticeRegisterService noticeRegisterService) {
        this.bidNoticeService = bidNoticeService;
        this.noticeRegisterService = noticeRegisterService;
    }

    @GetMapping
    public String list(@ModelAttribute("search") BidNoticeSearch search, Model model) {
        List<StatusCount> tabs = bidNoticeService.findStatusCounts();

        model.addAttribute("result", bidNoticeService.findPage(search));
        model.addAttribute("tabs", tabs);
        // 전체 탭의 숫자. 상태별 건수를 더한 값이라 따로 세지 않는다.
        model.addAttribute("totalAll", tabs.stream().mapToInt(tab -> tab.getCnt()).sum());
        return "bid/notices";
    }

    /**
     * 사전규격등록번호로 관심 공고를 등록한다 (FR-021). 등록은 영업부만 한다.
     * 메뉴 접근은 SecurityConfig 가 영업부·팀장·경영지원실까지 열어 두므로 여기서 한 번 더 좁힌다.
     */
    @PostMapping("/pre-spec")
    @PreAuthorize("hasRole('SALES')")
    public String registerPreSpec(@RequestParam String bfSpecRgstNo,
                                  @AuthenticationPrincipal LoginUser user,
                                  RedirectAttributes redirect) {
        NoticeRegisterService.RegistResult result =
                noticeRegisterService.registerByPreSpecNo(bfSpecRgstNo, user.getEmpNo());

        StringBuilder msg = new StringBuilder();
        msg.append("등록했습니다: ").append(result.getPbancNm());
        if (result.isAlreadyHasBidNotice()) {
            msg.append(" · 본공고(").append(result.getLinkedBidNo())
               .append(")가 이미 올라와 있어 다음 수집에서 연결됩니다.");
        } else {
            msg.append(" · 본공고가 올라오면 알려 드립니다.");
        }
        redirect.addFlashAttribute("registMessage", msg.toString());
        return "redirect:/notices";
    }
}
