package com.ojj.sujuro.proposal;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.proposal.bid.ProposalBidDto;
import com.ojj.sujuro.proposal.bid.ProposalService;
import com.ojj.sujuro.proposal.rqt.RqtDto;
import com.ojj.sujuro.proposal.rqt.RqtService;
import com.ojj.sujuro.proposal.toc.TocDto;
import com.ojj.sujuro.proposal.toc.TocService;

/**
 * 제안 작성 화면: 목차(FR-046~048)와 요구사항(FR-050, 052~054). 접근은 SecurityConfig에서 팀장·팀원만 허용한다.
 * 편집(POST)은 팀장만 가능하고, 팀원·외부 인력은 조회만 한다. (FR-016-1)
 * 업무 규칙 위반은 화면 위쪽 안내 띠로 보여 주고 같은 사업건 화면으로 돌아온다.
 */
@Controller
public class ProposalController {

    private final ProposalService proposalService;
    private final TocService tocService;
    private final RqtService rqtService;

    public ProposalController(ProposalService proposalService, TocService tocService, RqtService rqtService) {
        this.proposalService = proposalService;
        this.tocService = tocService;
        this.rqtService = rqtService;
    }

    @GetMapping("/proposal")
    public String index(@AuthenticationPrincipal LoginUser user,
                        @RequestParam(value = "bid", required = false) Long bid,
                        @RequestParam(value = "filter", defaultValue = RqtService.FILTER_ALL) String filter,
                        @RequestParam(value = "edit", required = false) Long edit,
                        Model model) {
        List<ProposalBidDto> bids = proposalService.myBids(user.getEmpNo());
        model.addAttribute("bids", bids);
        model.addAttribute("canEdit", isLeader(user));
        if (bids.isEmpty()) {
            return "proposal/index";
        }
        ProposalBidDto current = null;
        if (bid != null) {
            current = proposalService.openBid(bid, user.getEmpNo());
        } else {
            current = bids.get(0);
        }
        Long pbancSn = current.getPbancSn();

        List<TocDto> tocs = tocService.list(pbancSn);
        List<RqtDto> all = rqtService.list(pbancSn);
        int total = tocService.totalPages(tocs);
        Integer limit = current.getLmtPageCnt();

        model.addAttribute("bid", current);
        model.addAttribute("tocs", tocs);
        model.addAttribute("groupPages", tocService.groupPages(tocs));
        model.addAttribute("totalPages", total);
        model.addAttribute("overPages", limit != null && total > limit ? total - limit : 0);
        model.addAttribute("rqts", rqtService.filter(all, filter, user.getEmpNo()));
        model.addAttribute("rqtTotal", all.size());
        model.addAttribute("unmapped", rqtService.unmappedCount(all));
        model.addAttribute("progress", rqtService.progress(all));
        model.addAttribute("filter", filter);
        if (edit != null && isLeader(user)) {
            model.addAttribute("editRqt", rqtService.findForEdit(pbancSn, edit));
        }
        return "proposal/index";
    }

    /* ---------- 목차 ---------- */

    @PostMapping("/proposal/toc/import")
    public String importToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid, RedirectAttributes ra) {
        return run(user, bid, ra, "공통 목차 양식을 불러왔습니다.", () -> tocService.importCommon(bid));
    }

    @PostMapping("/proposal/toc/add")
    public String addToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "upTocSn", required = false) Long upTocSn,
                         @RequestParam("tocNm") String tocNm,
                         @RequestParam(value = "pages", required = false) Integer pages, RedirectAttributes ra) {
        return run(user, bid, ra, "목차를 추가했습니다.", () -> tocService.add(bid, upTocSn, tocNm, pages));
    }

    @PostMapping("/proposal/toc/edit")
    public String editToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam("tocSn") Long tocSn, @RequestParam("tocNm") String tocNm,
                          @RequestParam(value = "pages", required = false) Integer pages, RedirectAttributes ra) {
        return run(user, bid, ra, "목차를 수정했습니다.", () -> tocService.edit(bid, tocSn, tocNm, pages));
    }

    @PostMapping("/proposal/toc/delete")
    public String deleteToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("tocSn") Long tocSn, RedirectAttributes ra) {
        return run(user, bid, ra, "목차를 삭제했습니다. 연결돼 있던 요구사항은 미매핑으로 바뀌었습니다.", () -> tocService.delete(bid, tocSn));
    }

    @PostMapping("/proposal/limit")
    public String limit(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                        @RequestParam(value = "limit", required = false) Integer limit, RedirectAttributes ra) {
        return run(user, bid, ra, "제한 쪽수를 저장했습니다.", () -> proposalService.changePageLimit(bid, limit));
    }

    /* ---------- 요구사항 ---------- */

    @PostMapping("/proposal/rqt/add")
    public String addRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid, RqtDto rqt, RedirectAttributes ra) {
        return run(user, bid, ra, "요구사항을 등록했습니다.", () -> rqtService.register(bid, rqt, user.getEmpNo()));
    }

    @PostMapping("/proposal/rqt/edit")
    public String editRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam("rqtMttrSn") Long rqtMttrSn, RqtDto rqt, RedirectAttributes ra) {
        return run(user, bid, ra, "요구사항을 수정했습니다.", () -> rqtService.edit(bid, rqtMttrSn, rqt));
    }

    @PostMapping("/proposal/rqt/delete")
    public String deleteRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("rqtMttrSn") Long rqtMttrSn, RedirectAttributes ra) {
        return run(user, bid, ra, "요구사항을 삭제했습니다.", () -> rqtService.delete(bid, rqtMttrSn));
    }

    /* ---------- 공통 처리 ---------- */

    private String run(LoginUser user, Long bid, RedirectAttributes ra, String okMessage, Runnable action) {
        try {
            proposalService.openBid(bid, user.getEmpNo());
            proposalService.requireLeader(isLeader(user));
            action.run();
            ra.addFlashAttribute("done", okMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("bid", bid);
        return "redirect:/proposal";
    }

    private boolean isLeader(LoginUser user) {
        return user.getRole() == Role.LEADER && !user.isExternal();
    }
}
