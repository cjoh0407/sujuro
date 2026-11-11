package com.ojj.sujuro.proposal.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.servlet.http.HttpSession;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.common.exception.BusinessException;

import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.proposal.dto.RqtDto;
import com.ojj.sujuro.proposal.dto.RqtExtractDto;
import com.ojj.sujuro.proposal.service.ProposalService;
import com.ojj.sujuro.proposal.service.RqtService;

import lombok.RequiredArgsConstructor;

/**
 * 요구사항 등록·수정·삭제·목차 연결 (FR-050~054). 제안 화면(/proposal) 제안서 탭에서 호출한다.
 * 제안팀원 중 주관팀 팀장 또는 목차 담당자만 할 수 있다 (FR-050, FR-016-1, 요구사항 v3.3).
 * 처리 후 같은 사업건의 /proposal 화면으로 돌아간다.
 */
@Controller
@RequiredArgsConstructor
public class RqtController {

    private final AccessChecker accessChecker;
    private final ProposalService proposalService;
    private final RqtService rqtService;

    /* ---------- 요구사항 (FR-050~054): 주관팀 팀장 또는 목차 담당자 ---------- */

    @PostMapping("/proposal/rqt/add")
    public String addRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid, RqtDto rqt, RedirectAttributes ra) {
        ra.addAttribute("view", ProposalController.VIEW_UNMAPPED); // 등록·수정은 "요구사항 추가 · 미매핑" 화면에서 한다
        return runReq(user, bid, ra, () -> {
            rqtService.register(bid, rqt, user.getEmpNo());
            return "요구사항을 등록했습니다. 위 미매핑 목록에서 세부 목차에 연결해 주세요.";
        });
    }

    @PostMapping("/proposal/rqt/edit")
    public String editRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam("rqtMttrSn") Long rqtMttrSn, RqtDto rqt, RedirectAttributes ra) {
        ra.addAttribute("view", ProposalController.VIEW_UNMAPPED);
        return runReq(user, bid, ra, () -> {
            rqtService.edit(bid, rqtMttrSn, rqt, user.getEmpNo());
            return "요구사항을 수정했습니다.";
        });
    }

    @PostMapping("/proposal/rqt/delete")
    public String deleteRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("rqtMttrSn") Long rqtMttrSn,
                            @RequestParam(value = "view", required = false) String view, RedirectAttributes ra) {
        if (ProposalController.VIEW_UNMAPPED.equals(view)) { // 미매핑 화면에서 지웠으면 그 화면으로
            ra.addAttribute("view", ProposalController.VIEW_UNMAPPED);
        }
        return runReq(user, bid, ra, () -> {
            rqtService.delete(bid, rqtMttrSn, user.getEmpNo());
            return "요구사항을 삭제했습니다.";
        });
    }

    /**
     * 요구사항-목차 연결 (FR-051). 최하위 목차 줄의 "+ 요구사항 추가"나 미매핑 줄의 목차 선택으로 연결하고,
     * ID 옆 ✕로 해제한다(tocSn 없음). rqtMttrSn이 비어 있으면(선택 안 함) 아무것도 하지 않는다.
     * 미매핑 화면에서 연결하면(view=unmapped) 처리 후 미매핑 화면으로 돌아간다.
     */
    @PostMapping("/proposal/rqt/map")
    public String mapRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "rqtMttrSn", required = false) Long rqtMttrSn,
                         @RequestParam(value = "tocSn", required = false) Long tocSn,
                         @RequestParam(value = "view", required = false) String view, RedirectAttributes ra) {
        if (ProposalController.VIEW_UNMAPPED.equals(view)) {
            ra.addAttribute("view", ProposalController.VIEW_UNMAPPED);
        }
        if (rqtMttrSn == null) {
            return FlashRedirect.to(ra, ProposalController.PATH, bid, null);
        }
        return runReq(user, bid, ra, () -> {
            rqtService.map(bid, rqtMttrSn, tocSn, user.getEmpNo());
            return tocSn == null ? "요구사항 연결을 해제했습니다. 미매핑으로 바뀌었습니다." : "요구사항을 목차에 연결했습니다.";
        });
    }

    /** 요구사항 전체 삭제 (다시 추출할 때). 주관팀 팀장만. 화면에서 확인창을 한 번 띄운다 */
    @PostMapping("/proposal/rqt/delete-all")
    public String deleteAllRqt(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid, RedirectAttributes ra) {
        ra.addAttribute("view", ProposalController.VIEW_UNMAPPED);
        return runReq(user, bid, ra, () -> {
            if (!accessChecker.isLeadTeamLeader(bid, user)) {
                throw new BusinessException("요구사항 전체 삭제는 주관팀 팀장만 할 수 있습니다.");
            }
            int n = rqtService.deleteAll(bid);
            return "요구사항 " + n + "건을 모두 삭제했습니다. 제안요청서 PDF를 다시 추출하거나 수기로 등록해 주세요.";
        });
    }

    /* ---------- 제안요청서 PDF 요구사항 추출 (FR-050-1): 추출 → 미리보기 → 확인 후 등록 ---------- */

    /** 세션에 미리보기 결과를 두는 이름. 사업건마다 하나만 둔다 */
    static final String EXTRACT_KEY = "rqtExtract";

    /** PDF에서 요구사항 ID·명칭을 뽑아 세션에 두고 미리보기 화면(?view=extract)으로 간다. 파일은 저장하지 않는다 */
    @PostMapping("/proposal/rqt/extract")
    public String extract(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          HttpSession session, RedirectAttributes ra) {
        ra.addAttribute("view", ProposalController.VIEW_EXTRACT);
        return runReq(user, bid, ra, () -> {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("제안요청서 PDF 파일을 골라 주세요.");
            }
            String nm = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (!nm.toLowerCase().endsWith(".pdf")) {
                throw new BusinessException("PDF 파일만 읽을 수 있습니다.");
            }
            RqtExtractDto r;
            try (InputStream in = file.getInputStream()) { // 다 읽으면 닫아야 윈도우에서 임시 업로드 파일이 지워진다
                r = rqtService.extract(bid, in, nm);
            } catch (IOException e) {
                throw new BusinessException("올린 파일을 읽지 못했습니다. 다시 올려 주세요.");
            }
            session.setAttribute(EXTRACT_KEY, r);
            return "요구사항 " + r.getFoundCount() + "개를 읽었습니다."
                    + (r.getWarnings().isEmpty() ? " 확인한 뒤 등록해 주세요." : " 확인이 필요한 내용이 있습니다. 아래 경고를 보고 원본과 비교해 주세요.");
        });
    }

    /** 미리보기에서 체크한 줄(pick = 줄 번호)만 등록한다. 표의 ID·명칭은 사람이 고친 값을 쓴다 */
    @PostMapping("/proposal/rqt/extract/register")
    public String registerExtracted(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                                    @RequestParam(value = "pick", required = false) List<Integer> picks,
                                    @RequestParam(value = "rqtMttrNo", required = false) List<String> nos,
                                    @RequestParam(value = "rqtMttrNm", required = false) List<String> names,
                                    HttpSession session, RedirectAttributes ra) {
        // 하나도 안 골랐으면 미리보기로 돌아가 다시 고르게 하고, 등록하면 미매핑 화면에서 바로 목차에 연결하게 한다
        ra.addAttribute("view", picks == null || picks.isEmpty() ? ProposalController.VIEW_EXTRACT : ProposalController.VIEW_UNMAPPED);
        return runReq(user, bid, ra, () -> {
            List<String> pickNos = new ArrayList<>();
            List<String> pickNms = new ArrayList<>();
            List<String> pickSes = new ArrayList<>();
            // 총괄표에서 읽은 구분명 (SFR → 기능 요구사항). ID를 고쳤어도 고친 ID의 접두어로 찾는다 (11/09)
            Object ex = session.getAttribute(EXTRACT_KEY);
            java.util.Map<String, String> seByPrefix = ex instanceof RqtExtractDto && ((RqtExtractDto) ex).getGroupNames() != null
                    ? ((RqtExtractDto) ex).getGroupNames() : java.util.Collections.<String, String>emptyMap();
            if (picks != null && nos != null && names != null) {
                for (Integer k : picks) {
                    if (k != null && k >= 0 && k < nos.size() && k < names.size()) {
                        pickNos.add(nos.get(k));
                        pickNms.add(names.get(k));
                        String no = nos.get(k) == null ? "" : nos.get(k).trim().toUpperCase();
                        int dash = no.indexOf('-');
                        pickSes.add(dash > 0 ? seByPrefix.get(no.substring(0, dash)) : null);
                    }
                }
            }
            int[] r = rqtService.registerAll(bid, pickNos, pickNms, pickSes, user.getEmpNo());
            session.removeAttribute(EXTRACT_KEY);
            return "요구사항 " + r[0] + "건을 등록했습니다." + (r[1] > 0 ? " (이미 있거나 비어 있는 " + r[1] + "건은 건너뜀)" : "")
                    + " 세부 목차에 연결해 주세요.";
        });
    }

    /** 미리보기 취소 */
    @PostMapping("/proposal/rqt/extract/cancel")
    public String cancelExtract(@RequestParam("bid") Long bid, HttpSession session, RedirectAttributes ra) {
        session.removeAttribute(EXTRACT_KEY);
        ra.addAttribute("view", ProposalController.VIEW_UNMAPPED); // 추출 카드가 있는 화면으로
        return FlashRedirect.to(ra, ProposalController.PATH, bid, null);
    }

    /** 세션의 미리보기 결과 중 이 사업건 것만 (다른 사업건 것이면 null) */
    static RqtExtractDto extractOf(HttpSession session, Long bid) {
        Object o = session.getAttribute(EXTRACT_KEY);
        return o instanceof RqtExtractDto && bid.equals(((RqtExtractDto) o).getPbancSn()) ? (RqtExtractDto) o : null;
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
