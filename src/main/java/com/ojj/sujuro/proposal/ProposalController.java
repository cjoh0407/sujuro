package com.ojj.sujuro.proposal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * 제안팀 · 목차·요구사항 매핑 화면 (FR-046~056, FR-063). 접근은 SecurityConfig에서 팀장·팀원만 허용한다. (화면 설계 v3.4)
 *   탭 prop: 제안서 목차 · 담당자 · 요구사항 연결 / 탭 sum: 제안요약서 목차 · 담당자
 * 목차 편집·양식·담당자 지정은 팀장만 한다. (FR-014)
 * 요구사항 등록·수정·삭제와 목차 연결은 팀장과 목차 담당자가 한다. 그 외 팀원은 조회만 한다. (FR-016-1, FR-051)
 * 업무 규칙 위반은 화면 위쪽 안내 띠로 보여 주고 같은 사업건·같은 탭 화면으로 돌아온다.
 */
@Controller
public class ProposalController {

    static final String TAB_PROPOSAL = "prop";
    static final String TAB_SUMMARY = "sum";

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
                        @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                        @RequestParam(value = "filter", defaultValue = RqtService.FILTER_ALL) String filter,
                        @RequestParam(value = "edit", required = false) Long edit,
                        Model model) {
        List<ProposalBidDto> bids = proposalService.myBids(user.getEmpNo());
        model.addAttribute("bids", bids);
        if (bids.isEmpty()) {
            return "proposal/index";
        }
        ProposalBidDto current = bid != null ? proposalService.openBid(bid, user.getEmpNo()) : bids.get(0);
        Long pbancSn = current.getPbancSn();
        boolean lead = isLeader(user);
        String tk = TAB_SUMMARY.equals(tab) ? TAB_SUMMARY : TAB_PROPOSAL;

        model.addAttribute("bid", current);
        model.addAttribute("tab", tk);
        model.addAttribute("canEdit", lead);
        boolean canReq = rqtService.canEdit(pbancSn, user.getEmpNo(), lead);
        model.addAttribute("canReq", canReq);
        if (lead) {
            model.addAttribute("members", proposalService.members(pbancSn));
            model.addAttribute("forms", tocService.forms(user.getEmpNo()));
        }

        if (TAB_SUMMARY.equals(tk)) {
            model.addAttribute("tocs", tocService.list(pbancSn, TocDto.SE_SUMMARY));
            return "proposal/index";
        }

        // ----- 제안서 목차 · 요구사항 -----
        List<TocDto> tocs = tocService.list(pbancSn, TocDto.SE_PROPOSAL);
        List<RqtDto> all = rqtService.list(pbancSn);
        int total = tocService.totalPages(tocs);
        Integer limit = current.getLmtPageCnt();
        int unassigned = tocService.unassignedCount(tocs);

        // 세부 목차별 연결된 요구사항(칩)과, 아직 어디에도 연결되지 않은 요구사항(+ 요구사항 추가 목록)
        Map<Long, List<RqtDto>> rqtsByToc = new HashMap<>();
        List<RqtDto> unmappedList = new ArrayList<>();
        for (RqtDto q : all) {
            if (q.isUnmapped()) {
                unmappedList.add(q);
            } else {
                rqtsByToc.computeIfAbsent(q.getTocSn(), k -> new ArrayList<>()).add(q);
            }
        }

        model.addAttribute("tocs", tocs);
        model.addAttribute("groupPages", tocService.groupPages(tocs));
        model.addAttribute("groupRqts", tocService.groupRequirements(tocs));
        model.addAttribute("totalPages", total);
        model.addAttribute("overPages", limit != null && total > limit ? total - limit : 0);
        model.addAttribute("unassigned", unassigned);
        model.addAttribute("assignedAll", !tocs.isEmpty() && unassigned == 0);
        model.addAttribute("rqtsByToc", rqtsByToc);
        model.addAttribute("unmappedList", unmappedList);
        model.addAttribute("rqts", rqtService.filter(all, filter, user.getEmpNo()));
        model.addAttribute("rqtTotal", all.size());
        model.addAttribute("unmapped", unmappedList.size());
        model.addAttribute("progress", rqtService.progress(all));
        model.addAttribute("filter", filter);
        if (edit != null && canReq) {
            model.addAttribute("editRqt", rqtService.findForEdit(pbancSn, edit));
        }
        return "proposal/index";
    }

    /* ---------- 목차 양식 (FR-046~048) ---------- */

    /** 양식 불러오기. 이미 목차가 있으면 다른 양식으로 바꾼다 (담당자·요구사항 연결은 초기화). */
    @PostMapping("/proposal/toc/import")
    public String importToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam(value = "tocFormSn", required = false) Long tocFormSn, RedirectAttributes ra) {
        return run(user, bid, tab, ra, "목차 양식을 불러왔습니다. 이번 사업에 맞게 달라진 부분만 수정하세요.",
                () -> tocService.importForm(bid, se(tab), tocFormSn, user.getEmpNo()));
    }

    /** 지금 목차를 커스텀 양식으로 저장 (본인만 다시 불러올 수 있다) */
    @PostMapping("/proposal/toc/form")
    public String saveForm(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                           @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                           @RequestParam(value = "tocFormNm", required = false) String tocFormNm, RedirectAttributes ra) {
        return run(user, bid, tab, ra, "커스텀 양식으로 저장했습니다. 다른 사업건에서도 불러올 수 있습니다.",
                () -> tocService.saveAsForm(bid, se(tab), tocFormNm, user.getEmpNo()));
    }

    /* ---------- 목차 편집 (FR-047, FR-056) ---------- */

    /** upTocSn이 없으면 큰 목차, 있으면 그 큰 목차 아래 세부 목차를 추가한다. */
    @PostMapping("/proposal/toc/add")
    public String addToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                         @RequestParam(value = "upTocSn", required = false) Long upTocSn,
                         @RequestParam(value = "tocNm", required = false) String tocNm,
                         @RequestParam(value = "pages", required = false) Integer pages, RedirectAttributes ra) {
        return run(user, bid, tab, ra, "목차를 추가했습니다.", () -> tocService.add(bid, se(tab), upTocSn, tocNm, pages));
    }

    @PostMapping("/proposal/toc/edit")
    public String editToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                          @RequestParam("tocSn") Long tocSn, @RequestParam(value = "tocNm", required = false) String tocNm,
                          @RequestParam(value = "pages", required = false) Integer pages, RedirectAttributes ra) {
        return run(user, bid, tab, ra, "목차를 수정했습니다.", () -> tocService.edit(bid, tocSn, tocNm, pages));
    }

    @PostMapping("/proposal/toc/delete")
    public String deleteToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam("tocSn") Long tocSn, RedirectAttributes ra) {
        return run(user, bid, tab, ra, "목차를 삭제했습니다. 연결돼 있던 요구사항은 미매핑으로 바뀌었습니다.",
                () -> tocService.delete(bid, tocSn));
    }

    /** 세부 목차 담당자 지정·해제 (FR-049). picId가 비어 있으면 미지정으로 돌린다. */
    @PostMapping("/proposal/toc/assign")
    public String assignToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam("tocSn") Long tocSn,
                            @RequestParam(value = "picId", required = false) String picId, RedirectAttributes ra) {
        String msg = picId == null || picId.trim().isEmpty() ? "담당자 지정을 해제했습니다." : "담당자를 지정했습니다.";
        return run(user, bid, tab, ra, msg, () -> tocService.assign(bid, tocSn, picId));
    }

    @PostMapping("/proposal/limit")
    public String limit(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                        @RequestParam(value = "limit", required = false) Integer limit, RedirectAttributes ra) {
        return run(user, bid, TAB_PROPOSAL, ra, "제한 쪽수를 저장했습니다.", () -> proposalService.changePageLimit(bid, limit));
    }

    /* ---------- 요구사항 (FR-050~054) ---------- */

    @PostMapping("/proposal/rqt/add")
    public String addRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid, RqtDto rqt, RedirectAttributes ra) {
        return runReq(user, bid, null, ra, "요구사항을 등록했습니다.", () -> rqtService.register(bid, rqt, user.getEmpNo()));
    }

    @PostMapping("/proposal/rqt/edit")
    public String editRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam("rqtMttrSn") Long rqtMttrSn, RqtDto rqt, RedirectAttributes ra) {
        return runReq(user, bid, null, ra, "요구사항을 수정했습니다.", () -> rqtService.edit(bid, rqtMttrSn, rqt));
    }

    @PostMapping("/proposal/rqt/delete")
    public String deleteRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("rqtMttrSn") Long rqtMttrSn, RedirectAttributes ra) {
        return runReq(user, bid, null, ra, "요구사항을 삭제했습니다.", () -> rqtService.delete(bid, rqtMttrSn));
    }

    /**
     * 요구사항-목차 연결 (FR-051). 세부 목차 줄의 "+ 요구사항 추가"로 연결하고, 칩의 ✕로 해제한다(tocSn 없음).
     * rqtMttrSn이 비어 있으면(선택 안 함) 아무것도 하지 않는다.
     */
    @PostMapping("/proposal/rqt/map")
    public String mapRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "rqtMttrSn", required = false) Long rqtMttrSn,
                         @RequestParam(value = "tocSn", required = false) Long tocSn,
                         @RequestParam(value = "filter", required = false) String filter, RedirectAttributes ra) {
        if (rqtMttrSn == null) {
            ra.addAttribute("bid", bid);
            return "redirect:/proposal";
        }
        String msg = tocSn == null ? "요구사항 연결을 해제했습니다. 미매핑으로 바뀌었습니다." : "요구사항을 목차에 연결했습니다.";
        return runReq(user, bid, filter, ra, msg, () -> rqtService.map(bid, rqtMttrSn, tocSn));
    }

    /* ---------- 공통 처리 ---------- */

    /** 목차·양식·담당자 처리: 팀장만 (FR-014, FR-016-1) */
    private String run(LoginUser user, Long bid, String tab, RedirectAttributes ra, String okMessage, Runnable action) {
        try {
            proposalService.openBid(bid, user.getEmpNo());
            proposalService.requireLeader(isLeader(user));
            action.run();
            ra.addFlashAttribute("done", okMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("bid", bid);
        if (TAB_SUMMARY.equals(tab)) {
            ra.addAttribute("tab", TAB_SUMMARY);
        }
        return "redirect:/proposal";
    }

    /** 요구사항 처리: 팀장 또는 목차 담당자만 (FR-016-1). 보던 필터 탭을 유지한다. */
    private String runReq(LoginUser user, Long bid, String filter, RedirectAttributes ra, String okMessage, Runnable action) {
        try {
            proposalService.openBid(bid, user.getEmpNo());
            rqtService.requireEditor(bid, user.getEmpNo(), isLeader(user));
            action.run();
            ra.addFlashAttribute("done", okMessage);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("bid", bid);
        if (filter != null && !filter.isEmpty()) {
            ra.addAttribute("filter", filter);
        }
        return "redirect:/proposal";
    }

    /** 탭 → 목차구분코드 */
    private String se(String tab) {
        return TAB_SUMMARY.equals(tab) ? TocDto.SE_SUMMARY : TocDto.SE_PROPOSAL;
    }

    private boolean isLeader(LoginUser user) {
        return user.getRole() == Role.LEADER;
    }
}
