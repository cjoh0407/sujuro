package com.ojj.sujuro.proposal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.proposal.bid.ProposalBidDto;
import com.ojj.sujuro.proposal.bid.ProposalPerm;
import com.ojj.sujuro.proposal.bid.ProposalService;
import com.ojj.sujuro.proposal.rqt.RqtDto;
import com.ojj.sujuro.proposal.rqt.RqtService;
import com.ojj.sujuro.proposal.toc.TocDto;
import com.ojj.sujuro.proposal.toc.TocService;

/**
 * 제안팀 · 목차·요구사항 매핑 화면 (FR-046~056, FR-063, 요구사항 v3.2 / 화면 설계 v3.2).
 *   탭 prop: 제안서 목차 · 담당자 · 요구사항 연결 / 탭 sum: 제안요약서 목차 · 담당자
 * 권한 (ProposalPerm):
 *   목차 양식 불러오기·저장 — 이 사업건 주관팀 소속 제안팀원
 *   목차 추가·수정·삭제·쪽수 — 제안팀원 전체(주관팀·참여팀 인원)
 *   최하위 목차 담당자 지정·페이지 제한 — 주관팀 팀장
 *   요구사항 등록·수정·삭제·연결 — 주관팀 팀장 또는 목차 담당자 (FR-016-1)
 * 업무 규칙 위반은 화면 위쪽 안내 띠로 보여 주고 같은 사업건·같은 탭 화면으로 돌아온다.
 */
@Controller
public class ProposalController {

    static final String TAB_PROPOSAL = "prop";
    static final String TAB_SUMMARY = "sum";

    private static final String NO_LOAD = "목차 양식 불러오기·저장은 이 사업건 주관팀 인원만 할 수 있습니다.";
    private static final String NO_EDIT = "목차는 이 사업건 제안팀(주관팀·참여팀 인원)만 수정할 수 있습니다.";
    private static final String NO_ASSIGN = "담당자 지정과 페이지 제한은 주관팀 팀장만 할 수 있습니다.";

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
                        @RequestParam(value = "edit", required = false) Long edit,
                        Model model) {
        String me = user.getEmpNo();
        List<ProposalBidDto> bids = proposalService.myBids(me);
        model.addAttribute("bids", bids);
        if (bids.isEmpty()) {
            return "proposal/proposal";
        }
        ProposalBidDto current = proposalService.openBid(bid != null ? bid : bids.get(0).getPbancSn(), me);
        Long pbancSn = current.getPbancSn();
        ProposalPerm perm = ProposalPerm.of(current, user);
        String tk = TAB_SUMMARY.equals(tab) ? TAB_SUMMARY : TAB_PROPOSAL;
        String se = se(tk);
        boolean canReq = perm.isEdit() && rqtService.canEdit(pbancSn, me, perm.isAssign());

        model.addAttribute("me", me);
        model.addAttribute("bid", current);
        model.addAttribute("tab", tk);
        model.addAttribute("perm", perm);
        model.addAttribute("canReq", canReq);
        model.addAttribute("members", proposalService.members(pbancSn));
        if (perm.isLoad()) {
            model.addAttribute("forms", tocService.forms(current.getSprvsnTeamSn(), se));
        }

        List<TocDto> tocs = tocService.list(pbancSn, se);
        model.addAttribute("tocs", tocs);
        if (TAB_SUMMARY.equals(tk)) {
            return "proposal/proposal";
        }

        // ----- 제안서 목차 · 요구사항 -----
        List<RqtDto> all = rqtService.list(pbancSn);
        List<TocDto> leaves = tocService.leaves(tocs);
        int total = tocService.totalPages(tocs);
        Integer limit = current.getLmtPageCnt();
        int unassigned = tocService.unassignedCount(tocs);

        // 최하위 목차별 연결된 요구사항과, 아직 어디에도 연결되지 않은 요구사항
        Map<Long, List<RqtDto>> rqtsByToc = new HashMap<>();
        for (TocDto t : leaves) {
            rqtsByToc.put(t.getTocSn(), new ArrayList<>());
        }
        List<RqtDto> unmappedList = new ArrayList<>();
        for (RqtDto q : all) {
            if (q.isUnmapped()) {
                unmappedList.add(q);
            } else {
                rqtsByToc.computeIfAbsent(q.getTocSn(), k -> new ArrayList<>()).add(q);
            }
        }
        // 미매핑 요구사항을 연결할 수 있는 목차: 담당자를 지정한 최하위 목차
        List<TocDto> mapTargets = new ArrayList<>();
        for (TocDto t : leaves) {
            if (t.getPicId() != null) {
                mapTargets.add(t);
            }
        }

        model.addAttribute("totalPages", total);
        model.addAttribute("overPages", limit != null && total > limit ? total - limit : 0);
        model.addAttribute("unassigned", unassigned);
        model.addAttribute("assignedAll", !leaves.isEmpty() && unassigned == 0);
        model.addAttribute("rqtsByToc", rqtsByToc);
        model.addAttribute("unmappedList", unmappedList);
        model.addAttribute("mapTargets", mapTargets);
        model.addAttribute("rqtTotal", all.size());
        model.addAttribute("unmapped", unmappedList.size());
        model.addAttribute("progress", rqtService.progress(all));
        if (edit != null && canReq) {
            model.addAttribute("editRqt", rqtService.findForEdit(pbancSn, edit));
        }
        return "proposal/proposal";
    }

    /* ---------- 목차 양식 (FR-046~048): 주관팀 인원 ---------- */

    /** 양식 불러오기. 이미 목차가 있으면 다른 양식으로 바꾼다 (담당자·요구사항 연결은 초기화). */
    @PostMapping("/proposal/toc/import")
    public String importToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam(value = "tocFormSn", required = false) Long tocFormSn, RedirectAttributes ra) {
        return run(user, bid, tab, ra, ProposalPerm::isLoad, NO_LOAD, b -> () -> {
            tocService.importForm(bid, se(tab), tocFormSn, b.getSprvsnTeamSn());
            return "목차 양식을 불러왔습니다. 이번 사업에 맞게 달라진 부분만 수정하세요. (목차 단계 제한 없음)";
        });
    }

    /** 지금 목차를 주관팀 양식으로 저장 (같은 팀이 다른 사업건에서 불러올 수 있다) */
    @PostMapping("/proposal/toc/form")
    public String saveForm(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                           @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                           @RequestParam(value = "tocFormNm", required = false) String tocFormNm, RedirectAttributes ra) {
        return run(user, bid, tab, ra, ProposalPerm::isLoad, NO_LOAD, b -> () -> {
            tocService.saveAsForm(bid, se(tab), tocFormNm, b.getSprvsnTeamSn(), user.getEmpNo());
            return "'" + tocFormNm.trim() + "'을 " + b.getSprvsnTeamNm() + " 양식으로 저장했습니다. 같은 팀이 다른 사업건에서도 불러올 수 있습니다.";
        });
    }

    /* ---------- 목차 편집 (FR-047, FR-056): 제안팀원 전체 ---------- */

    /** 1단계 목차 추가 (목차가 없을 때 "직접 목차 만들기"도 여기로 온다). label은 번호 포함 이름 */
    @PostMapping("/proposal/toc/add")
    public String addToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                         @RequestParam(value = "label", required = false) String label, RedirectAttributes ra) {
        return run(user, bid, tab, ra, ProposalPerm::isEdit, NO_EDIT, b -> () -> {
            tocService.addTop(bid, se(tab), label);
            return "목차를 추가했습니다.";
        });
    }

    /** 기준 목차의 "+ 하위"(mode=d) / "+ 같은 단계"(mode=s) 추가 */
    @PostMapping("/proposal/toc/add-near")
    public String addNear(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                          @RequestParam("tocSn") Long tocSn, @RequestParam(value = "mode", defaultValue = TocService.MODE_CHILD) String mode,
                          @RequestParam(value = "label", required = false) String label, RedirectAttributes ra) {
        return run(user, bid, tab, ra, ProposalPerm::isEdit, NO_EDIT, b -> () -> {
            int unmapped = tocService.addNear(bid, tocSn, mode, label);
            return unmapped > 0 ? "목차를 추가했습니다. 상위가 된 목차에 연결돼 있던 요구사항 " + unmapped + "건은 미매핑으로 바뀌었습니다."
                                : "목차를 추가했습니다.";
        });
    }

    /** 번호 포함 이름 수정 */
    @PostMapping("/proposal/toc/label")
    public String renameToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam("tocSn") Long tocSn, @RequestParam(value = "label", required = false) String label,
                            RedirectAttributes ra) {
        return run(user, bid, tab, ra, ProposalPerm::isEdit, NO_EDIT, b -> () -> {
            tocService.rename(bid, tocSn, label);
            return "목차 이름을 수정했습니다.";
        });
    }

    /** 최하위 목차 예상 쪽수 수정 */
    @PostMapping("/proposal/toc/pages")
    public String pagesToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                           @RequestParam("tocSn") Long tocSn, @RequestParam(value = "pages", required = false) Integer pages,
                           RedirectAttributes ra) {
        return run(user, bid, TAB_PROPOSAL, ra, ProposalPerm::isEdit, NO_EDIT, b -> () -> {
            tocService.changePages(bid, tocSn, pages);
            return "예상 쪽수를 저장했습니다.";
        });
    }

    /** 목차 삭제 (하위 목차 포함) */
    @PostMapping("/proposal/toc/delete")
    public String deleteToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam("tocSn") Long tocSn, RedirectAttributes ra) {
        return run(user, bid, tab, ra, ProposalPerm::isEdit, NO_EDIT, b -> () -> {
            int unmapped = tocService.delete(bid, tocSn);
            return unmapped > 0 ? "목차를 삭제했습니다. 연결돼 있던 요구사항 " + unmapped + "건은 미매핑으로 바뀌었습니다."
                                : "목차를 삭제했습니다.";
        });
    }

    /* ---------- 담당자·페이지 제한: 주관팀 팀장 ---------- */

    /** 최하위 목차 담당자 지정·해제 (FR-049). picId가 비어 있으면 미지정으로 돌린다. */
    @PostMapping("/proposal/toc/assign")
    public String assignToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam("tocSn") Long tocSn,
                            @RequestParam(value = "picId", required = false) String picId, RedirectAttributes ra) {
        return run(user, bid, tab, ra, ProposalPerm::isAssign, NO_ASSIGN, b -> () -> {
            tocService.assign(bid, tocSn, picId);
            return picId == null || picId.trim().isEmpty() ? "담당자 지정을 해제했습니다." : "담당자를 지정했습니다.";
        });
    }

    @PostMapping("/proposal/limit")
    public String limit(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                        @RequestParam(value = "limit", required = false) Integer limit, RedirectAttributes ra) {
        return run(user, bid, TAB_PROPOSAL, ra, ProposalPerm::isAssign, NO_ASSIGN, b -> () -> {
            proposalService.changePageLimit(bid, limit);
            return "제한 쪽수를 저장했습니다.";
        });
    }

    /* ---------- 요구사항 (FR-050~054): 주관팀 팀장 또는 목차 담당자 ---------- */

    @PostMapping("/proposal/rqt/add")
    public String addRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid, RqtDto rqt, RedirectAttributes ra) {
        return runReq(user, bid, ra, () -> {
            rqtService.register(bid, rqt, user.getEmpNo());
            return "요구사항을 등록했습니다. 아래 \"미매핑 요구사항\"에서 최하위 목차에 연결하세요.";
        });
    }

    @PostMapping("/proposal/rqt/edit")
    public String editRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam("rqtMttrSn") Long rqtMttrSn, RqtDto rqt, RedirectAttributes ra) {
        return runReq(user, bid, ra, () -> {
            rqtService.edit(bid, rqtMttrSn, rqt);
            return "요구사항을 수정했습니다.";
        });
    }

    @PostMapping("/proposal/rqt/delete")
    public String deleteRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("rqtMttrSn") Long rqtMttrSn, RedirectAttributes ra) {
        return runReq(user, bid, ra, () -> {
            rqtService.delete(bid, rqtMttrSn);
            return "요구사항을 삭제했습니다.";
        });
    }

    /**
     * 요구사항-목차 연결 (FR-051). 최하위 목차 줄의 "+ 요구사항 추가"나 미매핑 줄의 목차 선택으로 연결하고,
     * ID 옆 ✕로 해제한다(tocSn 없음). rqtMttrSn이 비어 있으면(선택 안 함) 아무것도 하지 않는다.
     */
    @PostMapping("/proposal/rqt/map")
    public String mapRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "rqtMttrSn", required = false) Long rqtMttrSn,
                         @RequestParam(value = "tocSn", required = false) Long tocSn, RedirectAttributes ra) {
        if (rqtMttrSn == null) {
            ra.addAttribute("bid", bid);
            return "redirect:/proposal";
        }
        return runReq(user, bid, ra, () -> {
            rqtService.map(bid, rqtMttrSn, tocSn);
            return tocSn == null ? "요구사항 연결을 해제했습니다. 미매핑으로 바뀌었습니다." : "요구사항을 목차에 연결했습니다.";
        });
    }

    /* ---------- 공통 처리 ---------- */

    /** 사업건을 열고(제안팀원 확인) 권한을 확인한 뒤 처리한다. 처리는 완료 안내 문구를 돌려준다. */
    private String run(LoginUser user, Long bid, String tab, RedirectAttributes ra,
                       Predicate<ProposalPerm> allowed, String denyMessage,
                       Function<ProposalBidDto, Supplier<String>> action) {
        try {
            ProposalBidDto b = proposalService.openBid(bid, user.getEmpNo());
            proposalService.require(allowed.test(ProposalPerm.of(b, user)), denyMessage);
            ra.addFlashAttribute("done", action.apply(b).get());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("bid", bid);
        if (TAB_SUMMARY.equals(tab)) {
            ra.addAttribute("tab", TAB_SUMMARY);
        }
        return "redirect:/proposal";
    }

    /** 요구사항 처리: 제안팀원 중 주관팀 팀장 또는 목차 담당자만 (FR-016-1) */
    private String runReq(LoginUser user, Long bid, RedirectAttributes ra, Supplier<String> action) {
        try {
            ProposalBidDto b = proposalService.openBid(bid, user.getEmpNo());
            ProposalPerm perm = ProposalPerm.of(b, user);
            proposalService.require(perm.isEdit(), NO_EDIT);
            rqtService.requireEditor(bid, user.getEmpNo(), perm.isAssign());
            ra.addFlashAttribute("done", action.get());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("bid", bid);
        return "redirect:/proposal";
    }

    /** 탭 → 목차구분코드 */
    private static String se(String tab) {
        return TAB_SUMMARY.equals(tab) ? TocDto.SE_SUMMARY : TocDto.SE_PROPOSAL;
    }
}
