package com.ojj.sujuro.proposal.web;

import java.util.function.Supplier;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.proposal.dto.RqtDto;
import com.ojj.sujuro.proposal.service.ProposalService;
import com.ojj.sujuro.proposal.service.RqtService;

/**
 * 요구사항 등록·수정·삭제·목차 연결 (FR-050~054). 제안 화면(/proposal) 제안서 탭에서 호출한다.
 * 제안팀원 중 주관팀 팀장 또는 목차 담당자만 할 수 있다 (FR-050, FR-016-1, 요구사항 v3.3).
 * 처리 후 같은 사업건의 /proposal 화면으로 돌아간다.
 */
@Controller
public class RqtController {

    private final AccessChecker accessChecker;
    private final ProposalService proposalService;
    private final RqtService rqtService;

    public RqtController(AccessChecker accessChecker, ProposalService proposalService, RqtService rqtService) {
        this.accessChecker = accessChecker;
        this.proposalService = proposalService;
        this.rqtService = rqtService;
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
            rqtService.edit(bid, rqtMttrSn, rqt, user.getEmpNo());
            return "요구사항을 수정했습니다.";
        });
    }

    @PostMapping("/proposal/rqt/delete")
    public String deleteRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("rqtMttrSn") Long rqtMttrSn, RedirectAttributes ra) {
        return runReq(user, bid, ra, () -> {
            rqtService.delete(bid, rqtMttrSn, user.getEmpNo());
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
            return FlashRedirect.to(ra, ProposalController.PATH, bid, null);
        }
        return runReq(user, bid, ra, () -> {
            rqtService.map(bid, rqtMttrSn, tocSn, user.getEmpNo());
            return tocSn == null ? "요구사항 연결을 해제했습니다. 미매핑으로 바뀌었습니다." : "요구사항을 목차에 연결했습니다.";
        });
    }

    /**
     * 요구사항 처리: 제안팀원(requireProposalMember) 중 주관팀 팀장 또는 목차 담당자만 (FR-050, FR-016-1).
     * 참여팀 팀장도 자기가 담당한 목차가 있으면 다룰 수 있다.
     */
    private String runReq(LoginUser user, Long bid, RedirectAttributes ra, Supplier<String> action) {
        accessChecker.requireProposalMember(bid, user);
        return FlashRedirect.run(ra, ProposalController.PATH, bid, null, () -> {
            proposalService.openBid(bid, user.getEmpNo());
            rqtService.requireEditor(bid, user.getEmpNo(), accessChecker.isLeadTeamLeader(bid, user));
            return action.get();
        });
    }
}
