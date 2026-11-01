package com.ojj.sujuro.proposal.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.proposal.dto.DocDto;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.PrpFileDto;
import com.ojj.sujuro.proposal.dto.SubmissionDto;
import com.ojj.sujuro.proposal.service.DocService;
import com.ojj.sujuro.proposal.service.ProposalService;

import lombok.RequiredArgsConstructor;

/**
 * 제출서류 · 조견표 화면 (FR-015, FR-067~073, 화면 설계 v3.2). 경영지원실과 제안팀이 함께 준비한다.
 * 누가 여는가:
 *   경영지원실 — 참여 확정 이후(참여 확정·제안 작성 중·제출 완료·결과 등록) 사업건 전체
 *   팀장·팀원 — 그 사업건 제안팀원만 (AccessChecker.requireProposalMember)
 * 할 수 있는 일:
 *   경영지원실 — 체크리스트 항목 추가·삭제·기한, 모든 서류 상태, 나라장터 등록 완료
 *   제안팀     — 제안팀 담당 서류 상태
 *   함께       — 서류 파일·조견표 파일 올리기·내려받기 (조견표는 승인·반려 없이 함께 확인하고 공유, FR-072)
 *   파일 삭제(숨김) — 올린 사람, 주관팀 팀장, 경영지원실. 사유 필수, 원본·이력은 남긴다 (NFR-009)
 * 나라장터 등록 뒤에는 모두 읽기 전용이다. 권한이 없으면 403, 업무 규칙 위반은 안내 띠로 보여 준다.
 */
@Controller
@RequiredArgsConstructor
public class DocController {

    private static final String PATH = "/docs";

    private final AccessChecker accessChecker;
    private final ProposalService proposalService;
    private final DocService docService;
    private final FileStorage storage;

    @GetMapping("/docs")
    public String index(@AuthenticationPrincipal LoginUser user,
                        @RequestParam(value = "bid", required = false) Long bid,
                        @RequestParam(value = "deleted", defaultValue = "false") boolean showDeleted, Model model) {
        boolean admin = isMgmt(user);
        List<ProposalBidDto> bids = admin ? docService.bidsForMgmt() : proposalService.myBids(user.getEmpNo());
        model.addAttribute("bids", bids);
        if (bids.isEmpty()) {
            return "proposal/docs";
        }
        Long pbancSn = bid != null ? bid : bids.get(0).getPbancSn();
        requireViewer(pbancSn, user);

        SubmissionDto sub = docService.submission(pbancSn);
        List<DocDto> docs = docService.docs(pbancSn);
        int ready = 0;
        List<String> missing = new ArrayList<>();
        for (DocDto d : docs) {
            if (d.isReady()) {
                ready++;
            } else if (d.isEssential()) {
                missing.add(d.getDcmntNm());
            }
        }

        model.addAttribute("bid", docService.bid(pbancSn));
        model.addAttribute("admin", admin);
        model.addAttribute("team", !admin);
        model.addAttribute("sub", sub);
        model.addAttribute("open", !sub.isSubmitted());
        model.addAttribute("docs", docs);
        model.addAttribute("readyCnt", ready);
        model.addAttribute("missingCnt", missing.size());
        model.addAttribute("missing", String.join(", ", missing));
        model.addAttribute("canRegister", admin && sub.isSubmittable() && !docs.isEmpty() && missing.isEmpty());
        List<PrpFileDto> cheatFiles = docService.cheatFiles(pbancSn);
        model.addAttribute("cheatFiles", cheatFiles);

        // 삭제 버튼을 보여 줄 파일 (올린 사람·주관팀 팀장·경영지원실, 나라장터 등록 전)
        String me = user.getEmpNo();
        boolean lead = !admin && accessChecker.isLeadTeamLeader(pbancSn, user);
        Set<Long> deletable = new HashSet<>();
        for (DocDto d : docs) {
            if (d.getFileSn() != null && docService.canDeleteFile(d.getFileRgtrId(), sub.isSubmitted(), me, admin, lead)) {
                deletable.add(d.getFileSn());
            }
        }
        for (PrpFileDto f : cheatFiles) {
            if (docService.canDeleteFile(f.getRgtrId(), sub.isSubmitted(), me, admin, lead)) {
                deletable.add(f.getFileSn());
            }
        }
        model.addAttribute("deletable", deletable);
        List<PrpFileDto> deletedFiles = docService.deletedFiles(pbancSn);
        model.addAttribute("deletedCnt", deletedFiles.size());
        model.addAttribute("showDeleted", showDeleted);
        if (showDeleted) {
            model.addAttribute("deletedFiles", deletedFiles);
        }
        return "proposal/docs";
    }

    /* ---------- 체크리스트 (경영지원실) ---------- */

    @PostMapping("/docs/add")
    public String addDoc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "dcmntNm", required = false) String name,
                         @RequestParam(value = "esntlYn", defaultValue = "Y") String esntlYn,
                         @RequestParam(value = "tkcgSeCd", required = false) String tkcgSeCd,
                         @RequestParam(value = "due", required = false) String due, RedirectAttributes ra) {
        requireMgmt(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "서류 항목을 추가했습니다.", () -> docService.addDoc(bid, name, "Y".equals(esntlYn), tkcgSeCd, due));
    }

    @PostMapping("/docs/delete")
    public String deleteDoc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("dcmntSn") Long dcmntSn, RedirectAttributes ra) {
        requireMgmt(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "서류 항목을 삭제했습니다.", () -> docService.deleteDoc(bid, dcmntSn));
    }

    @PostMapping("/docs/due")
    public String changeDue(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("dcmntSn") Long dcmntSn,
                            @RequestParam(value = "due", required = false) String due, RedirectAttributes ra) {
        requireMgmt(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "제출 기한을 바꿨습니다.", () -> docService.changeDue(bid, dcmntSn, due));
    }

    /* ---------- 서류 상태·파일 (경영지원실·제안팀) ---------- */

    @PostMapping("/docs/status")
    public String changeStatus(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                               @RequestParam("dcmntSn") Long dcmntSn,
                               @RequestParam("prgrsSttsCd") String status, RedirectAttributes ra) {
        requireViewer(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "준비 상태를 바꿨습니다.", () -> docService.changeStatus(bid, dcmntSn, status, isMgmt(user)));
    }

    @PostMapping("/docs/upload")
    public String uploadDoc(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam("dcmntSn") Long dcmntSn,
                            @RequestParam(value = "file", required = false) MultipartFile file, RedirectAttributes ra) {
        requireViewer(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "서류 파일을 올렸습니다.", () -> docService.uploadDoc(bid, dcmntSn, file, user.getEmpNo()));
    }

    /** 서류·조견표 파일 삭제: 목록에서만 숨기고 원본·삭제자·일시·사유를 남긴다 (NFR-009) */
    @PostMapping("/docs/file-delete")
    public String deleteFile(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                             @RequestParam("fileSn") Long fileSn,
                             @RequestParam(value = "reason", required = false) String reason, RedirectAttributes ra) {
        requireViewer(bid, user);
        boolean admin = isMgmt(user);
        boolean lead = !admin && accessChecker.isLeadTeamLeader(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "파일을 삭제했습니다. 원본과 기록은 \"삭제된 파일 보기\"에서 확인할 수 있습니다.",
                () -> docService.deleteFile(bid, fileSn, reason, user.getEmpNo(), admin, lead));
    }

    /* ---------- 조견표 ---------- */

    /** 조견표 파일 등록 (경영지원실·제안팀 함께, 승인·반려 없음) (FR-072) */
    @PostMapping("/docs/cheat-file")
    public String uploadCheatFile(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                                  @RequestParam(value = "file", required = false) MultipartFile file, RedirectAttributes ra) {
        requireViewer(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "조견표 파일을 등록했습니다.", () -> docService.uploadCheatFile(bid, file, user.getEmpNo()));
    }

    /* ---------- 나라장터 등록 완료 (경영지원실) (FR-073) ---------- */

    @PostMapping("/docs/register")
    public String register(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid, RedirectAttributes ra) {
        requireMgmt(bid, user);
        return FlashRedirect.run(ra, PATH, bid, null, "나라장터 등록 완료로 처리했습니다. 상태가 \"제출 완료\"로 바뀌었습니다.",
                () -> docService.register(bid, user.getEmpNo()));
    }

    /** 제출서류·조견표 파일 내려받기 */
    @GetMapping("/docs/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser user,
                                             @RequestParam("bid") Long bid, @RequestParam("fileSn") Long fileSn) {
        requireViewer(bid, user);
        PrpFileDto f = docService.findForDownload(bid, fileSn);
        Resource body = storage.load(f.getFilePathNm(), f.getStrgFileNm());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getOrgnlFileNm(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /* ---------- 공통 처리 ---------- */

    private boolean isMgmt(LoginUser user) {
        return user.getRole() == Role.MGMT;
    }

    /** 이 사업건을 볼 수 있는 사람: 경영지원실(참여 확정 이후 사업건) 또는 제안팀원. 아니면 403 */
    private void requireViewer(Long bid, LoginUser user) {
        if (isMgmt(user)) {
            if (!docService.isMgmtVisible(bid)) {
                throw new AccessDeniedException("참여 확정 이후의 사업건만 볼 수 있습니다.");
            }
        } else {
            accessChecker.requireProposalMember(bid, user);
        }
    }

    /** 경영지원실 전용 기능. 아니면 403 */
    private void requireMgmt(Long bid, LoginUser user) {
        if (!isMgmt(user) || !docService.isMgmtVisible(bid)) {
            throw new AccessDeniedException("경영지원실만 할 수 있습니다.");
        }
    }
}
