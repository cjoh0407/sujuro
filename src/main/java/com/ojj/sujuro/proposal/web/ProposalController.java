package com.ojj.sujuro.proposal.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.RqtDto;
import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.service.ProposalService;
import com.ojj.sujuro.proposal.service.RqtService;
import com.ojj.sujuro.proposal.service.TocService;

/**
 * 제안팀 · 목차·요구사항 매핑 화면 (FR-046~056, FR-063, 요구사항 v3.3 / 화면 설계 v3.2).
 *   탭 prop: 제안서 목차 · 담당자 · 요구사항 연결 / 탭 sum: 제안요약서 목차 · 담당자
 * 권한은 AccessChecker로 판정한다. 권한이 없으면 AccessDeniedException(403)이 난다.
 *   목차 양식 불러오기·저장            — requireLeadTeamMember (주관팀 인원)
 *   목차 추가·이름·쪽수·삭제            — requireProposalMember (제안팀 = 주관+참여 전원)
 *   최하위 목차 담당자 지정·페이지 제한 — requireLeadTeamLeader (주관팀 팀장. 참여팀 팀장은 제외)
 * 같은 화면에서 쓰는 다른 처리는 컨트롤러를 나눴다.
 *   제안팀 인원 추가·제외, 참여팀 지정  — ProposalTeamController (/proposal/team/**)
 *   요구사항 등록·수정·삭제·연결        — RqtController (/proposal/rqt/**)
 * 업무 규칙 위반(BusinessException)은 화면 위쪽 안내 띠로 보여 주고 같은 사업건·같은 탭 화면으로 돌아온다.
 */
@Controller
public class ProposalController {

    static final String TAB_PROPOSAL = "prop";
    static final String TAB_SUMMARY = "sum";
    static final String PATH = "/proposal";

    private final AccessChecker accessChecker;
    private final ProposalService proposalService;
    private final TocService tocService;
    private final RqtService rqtService;

    public ProposalController(AccessChecker accessChecker, ProposalService proposalService,
                              TocService tocService, RqtService rqtService) {
        this.accessChecker = accessChecker;
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
        Long pbancSn = bid != null ? bid : bids.get(0).getPbancSn();
        accessChecker.requireProposalMember(pbancSn, user);
        ProposalBidDto current = proposalService.openBid(pbancSn, me);
        String tk = TAB_SUMMARY.equals(tab) ? TAB_SUMMARY : TAB_PROPOSAL;
        String se = se(tk);

        // 화면 버튼 노출 (서버에서도 같은 기준으로 다시 막는다)
        boolean canLoad = accessChecker.isLeadTeamMember(pbancSn, user);
        boolean canAssign = accessChecker.isLeadTeamLeader(pbancSn, user);
        boolean canReq = rqtService.canEdit(pbancSn, me, canAssign);

        model.addAttribute("me", me);
        model.addAttribute("bid", current);
        model.addAttribute("tab", tk);
        model.addAttribute("canLoad", canLoad);
        model.addAttribute("canEdit", true); // requireProposalMember를 통과했으니 제안팀원
        model.addAttribute("canAssign", canAssign);
        model.addAttribute("canReq", canReq);
        model.addAttribute("members", proposalService.members(pbancSn));
        if (canAssign) { // 제안팀 구성 카드의 추가·참여팀 선택
            model.addAttribute("addableUsers", proposalService.addableUsers(pbancSn));
            model.addAttribute("teamOptions", proposalService.teamOptions(pbancSn));
        }
        if (canLoad) {
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
        return run(user, bid, tab, ra, accessChecker::requireLeadTeamMember, b -> () -> {
            tocService.importForm(bid, se(tab), tocFormSn, b.getSprvsnTeamSn());
            return "목차 양식을 불러왔습니다. 이번 사업에 맞게 달라진 부분만 수정하세요. (목차 단계 제한 없음)";
        });
    }

    /** 지금 목차를 주관팀 양식으로 저장 (같은 팀이 다른 사업건에서 불러올 수 있다) */
    @PostMapping("/proposal/toc/form")
    public String saveForm(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                           @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                           @RequestParam(value = "tocFormNm", required = false) String tocFormNm, RedirectAttributes ra) {
        return run(user, bid, tab, ra, accessChecker::requireLeadTeamMember, b -> () -> {
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
        return run(user, bid, tab, ra, accessChecker::requireProposalMember, b -> () -> {
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
        return run(user, bid, tab, ra, accessChecker::requireProposalMember, b -> () -> {
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
        return run(user, bid, tab, ra, accessChecker::requireProposalMember, b -> () -> {
            tocService.rename(bid, tocSn, label);
            return "목차 이름을 수정했습니다.";
        });
    }

    /** 최하위 목차 예상 쪽수 수정 */
    @PostMapping("/proposal/toc/pages")
    public String pagesToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                           @RequestParam("tocSn") Long tocSn, @RequestParam(value = "pages", required = false) Integer pages,
                           RedirectAttributes ra) {
        return run(user, bid, TAB_PROPOSAL, ra, accessChecker::requireProposalMember, b -> () -> {
            tocService.changePages(bid, tocSn, pages);
            return "예상 쪽수를 저장했습니다.";
        });
    }

    /** 목차 삭제 (하위 목차 포함) */
    @PostMapping("/proposal/toc/delete")
    public String deleteToc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam("tocSn") Long tocSn, RedirectAttributes ra) {
        return run(user, bid, tab, ra, accessChecker::requireProposalMember, b -> () -> {
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
        return run(user, bid, tab, ra, accessChecker::requireLeadTeamLeader, b -> () -> {
            tocService.assign(bid, tocSn, picId);
            return picId == null || picId.trim().isEmpty() ? "담당자 지정을 해제했습니다." : "담당자를 지정했습니다.";
        });
    }

    @PostMapping("/proposal/limit")
    public String limit(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                        @RequestParam(value = "limit", required = false) Integer limit, RedirectAttributes ra) {
        return run(user, bid, TAB_PROPOSAL, ra, accessChecker::requireLeadTeamLeader, b -> () -> {
            proposalService.changePageLimit(bid, limit);
            return "제한 쪽수를 저장했습니다.";
        });
    }

    /* ---------- 공통 처리 ---------- */

    /**
     * 권한 확인(AccessChecker, 없으면 403) 후 사업건을 열고 처리한다. 처리는 완료 안내 문구를 돌려준다.
     * 안내 띠·redirect는 FlashRedirect가 맡는다.
     */
    private String run(LoginUser user, Long bid, String tab, RedirectAttributes ra,
                       BiConsumer<Long, LoginUser> requireAccess,
                       Function<ProposalBidDto, Supplier<String>> action) {
        requireAccess.accept(bid, user);
        return FlashRedirect.run(ra, PATH, bid, tabParam(tab),
                () -> action.apply(proposalService.openBid(bid, user.getEmpNo())).get());
    }

    /** redirect용 탭 파라미터: 요약서 탭만 붙인다 (기본은 제안서 탭) */
    static String tabParam(String tab) {
        return TAB_SUMMARY.equals(tab) ? TAB_SUMMARY : null;
    }

    /** 탭 → 목차구분코드 */
    private static String se(String tab) {
        return TAB_SUMMARY.equals(tab) ? TocDto.SE_SUMMARY : TocDto.SE_PROPOSAL;
    }
}
