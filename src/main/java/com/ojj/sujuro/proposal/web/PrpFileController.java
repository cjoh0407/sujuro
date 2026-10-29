package com.ojj.sujuro.proposal.web;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.PrpFileDto;
import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.service.ProposalService;
import com.ojj.sujuro.proposal.service.PrpFileService;
import com.ojj.sujuro.proposal.service.TocService;

/**
 * 제안서 파일 관리 화면 (FR-057~066, 화면 설계 v3.2). 사업건 단위, 그 사업건 제안팀만 연다.
 *   탭 full: 전체 제안서(통합본) / part: 제안서 파트별 파일(목차별) / sum: 제안요약서(목차별)
 * 권한 (AccessChecker, 없으면 403):
 *   보기·내려받기                 — requireProposalMember (제안팀원)
 *   목차별 파일 올리기            — 제안팀원 중 주관팀 팀장 또는 그 목차 담당자 (서비스에서 담당자 확인)
 *   통합본 올리기·최종본 확정·검토 완료 표시 — requireLeadTeamLeader (주관팀 팀장)
 *   파일 삭제(숨김)               — 제안팀원 중 올린 사람 또는 주관팀 팀장. 사유 필수, 원본·이력은 남긴다 (NFR-009)
 */
@Controller
public class PrpFileController {

    static final String TAB_FULL = "full";
    static final String TAB_PART = "part";
    static final String TAB_SUMMARY = "sum";
    private static final String PATH = "/files";

    private final AccessChecker accessChecker;
    private final ProposalService proposalService;
    private final TocService tocService;
    private final PrpFileService fileService;
    private final FileStorage storage;

    public PrpFileController(AccessChecker accessChecker, ProposalService proposalService, TocService tocService,
                             PrpFileService fileService, FileStorage storage) {
        this.accessChecker = accessChecker;
        this.proposalService = proposalService;
        this.tocService = tocService;
        this.fileService = fileService;
        this.storage = storage;
    }

    @GetMapping("/files")
    public String index(@AuthenticationPrincipal LoginUser user,
                        @RequestParam(value = "bid", required = false) Long bid,
                        @RequestParam(value = "tab", defaultValue = TAB_PART) String tab,
                        @RequestParam(value = "deleted", defaultValue = "false") boolean showDeleted,
                        Model model) {
        String me = user.getEmpNo();
        List<ProposalBidDto> bids = proposalService.myBids(me);
        model.addAttribute("bids", bids);
        if (bids.isEmpty()) {
            return "proposal/files";
        }
        Long pbancSn = bid != null ? bid : bids.get(0).getPbancSn();
        accessChecker.requireProposalMember(pbancSn, user);
        String tk = TAB_FULL.equals(tab) || TAB_SUMMARY.equals(tab) ? tab : TAB_PART;

        boolean lead = accessChecker.isLeadTeamLeader(pbancSn, user);
        boolean submitted = fileService.isSubmitted(pbancSn);
        model.addAttribute("me", me);
        model.addAttribute("bid", proposalService.openBid(pbancSn, me));
        model.addAttribute("tab", tk);
        model.addAttribute("canAssign", lead);
        model.addAttribute("submitted", submitted);

        // 삭제 버튼을 보여 줄 파일 (올린 사람·주관팀 팀장, 최종본·검토 완료·제출 후 제외)
        Set<Long> deletable = new HashSet<>();
        String se;
        if (TAB_FULL.equals(tk)) {
            se = PrpFileDto.SE_FULL;
            List<PrpFileDto> full = fileService.fullFiles(pbancSn);
            for (PrpFileDto f : full) {
                if (fileService.canDelete(f, null, submitted, me, lead)) {
                    deletable.add(f.getFileSn());
                }
            }
            model.addAttribute("fullFiles", full);
        } else {
            boolean sum = TAB_SUMMARY.equals(tk);
            se = sum ? PrpFileDto.SE_SUMMARY : PrpFileDto.SE_PART;
            List<TocDto> leaves = tocService.leaves(tocService.list(pbancSn, sum ? TocDto.SE_SUMMARY : TocDto.SE_PROPOSAL));
            Map<Long, String> progress = new HashMap<>();
            for (TocDto t : leaves) {
                progress.put(t.getTocSn(), t.getPrgrsSttsCd());
            }
            Map<Long, List<PrpFileDto>> byToc = fileService.tocFiles(pbancSn, se);
            for (List<PrpFileDto> arr : byToc.values()) {
                for (PrpFileDto f : arr) {
                    if (fileService.canDelete(f, progress.get(f.getTocSn()), submitted, me, lead)) {
                        deletable.add(f.getFileSn());
                    }
                }
            }
            model.addAttribute("leaves", leaves);
            model.addAttribute("filesByToc", byToc);
        }
        model.addAttribute("deletable", deletable);
        List<PrpFileDto> deletedFiles = fileService.deletedFiles(pbancSn, se);
        model.addAttribute("deletedCnt", deletedFiles.size());
        model.addAttribute("showDeleted", showDeleted);
        if (showDeleted) {
            model.addAttribute("deletedFiles", deletedFiles);
        }
        return "proposal/files";
    }

    /** 파일 올리기. 탭 full이면 통합본(주관팀 팀장), 아니면 tocSn 목차에 올린다(주관팀 팀장 또는 담당자). */
    @PostMapping("/files/upload")
    public String upload(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "tab", defaultValue = TAB_PART) String tab,
                         @RequestParam(value = "tocSn", required = false) Long tocSn,
                         @RequestParam(value = "memo", required = false) String memo,
                         @RequestParam(value = "file", required = false) MultipartFile file, RedirectAttributes ra) {
        if (TAB_FULL.equals(tab)) {
            accessChecker.requireLeadTeamLeader(bid, user);
        } else {
            accessChecker.requireProposalMember(bid, user);
        }
        return FlashRedirect.run(ra, PATH, bid, tabParam(tab), () -> {
            if (TAB_FULL.equals(tab)) {
                int cycl = fileService.uploadFull(bid, file, memo, user.getEmpNo());
                return cycl + "차 통합본을 올렸습니다.";
            }
            String se = TAB_SUMMARY.equals(tab) ? PrpFileDto.SE_SUMMARY : PrpFileDto.SE_PART;
            String ver = fileService.uploadToc(bid, se, tocSn, file, memo, user.getEmpNo(),
                    accessChecker.isLeadTeamLeader(bid, user));
            return "파일을 올렸습니다. (v" + ver + ")";
        });
    }

    /** 목차 검토 완료 표시·해제 (FR-066) */
    @PostMapping("/files/review")
    public String review(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "tab", defaultValue = TAB_PART) String tab,
                         @RequestParam("tocSn") Long tocSn,
                         @RequestParam(value = "done", defaultValue = "false") boolean done, RedirectAttributes ra) {
        accessChecker.requireLeadTeamLeader(bid, user);
        return FlashRedirect.run(ra, PATH, bid, tabParam(tab), () -> {
            fileService.markReviewed(bid, tocSn, done);
            return done ? "검토 완료로 표시했습니다." : "검토 완료 표시를 풀었습니다.";
        });
    }

    /** 파일 삭제: 목록에서만 숨기고 원본·삭제자·일시·사유를 남긴다 (NFR-009) */
    @PostMapping("/files/delete")
    public String delete(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "tab", defaultValue = TAB_PART) String tab,
                         @RequestParam("fileSn") Long fileSn,
                         @RequestParam(value = "reason", required = false) String reason, RedirectAttributes ra) {
        accessChecker.requireProposalMember(bid, user);
        return FlashRedirect.run(ra, PATH, bid, tabParam(tab), () -> {
            fileService.deleteFile(bid, fileSn, reason, user.getEmpNo(), accessChecker.isLeadTeamLeader(bid, user));
            return "파일을 삭제했습니다. 원본과 기록은 \"삭제된 파일 보기\"에서 확인할 수 있습니다.";
        });
    }

    /** 통합본 중 최종본 확정 (FR-064) */
    @PostMapping("/files/final")
    public String confirmFinal(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                               @RequestParam("fileSn") Long fileSn, RedirectAttributes ra) {
        accessChecker.requireLeadTeamLeader(bid, user);
        return FlashRedirect.run(ra, PATH, bid, tabParam(TAB_FULL), () -> {
            fileService.confirmFinal(bid, fileSn, user.getEmpNo());
            return "최종본으로 확정했습니다.";
        });
    }

    /** 내려받기: 모든 버전을 내려받을 수 있다 (FR-062) */
    @GetMapping("/files/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser user,
                                             @RequestParam("bid") Long bid, @RequestParam("fileSn") Long fileSn) {
        accessChecker.requireProposalMember(bid, user);
        PrpFileDto f = fileService.findForDownload(bid, fileSn);
        Resource body = storage.load(f.getFilePathNm(), f.getStrgFileNm());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getOrgnlFileNm(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /** redirect용 탭 파라미터: full·sum 외에는 part */
    private static String tabParam(String tab) {
        return TAB_FULL.equals(tab) || TAB_SUMMARY.equals(tab) ? tab : TAB_PART;
    }
}
