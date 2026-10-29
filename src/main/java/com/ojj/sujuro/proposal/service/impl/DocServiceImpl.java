package com.ojj.sujuro.proposal.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.history.HistoryService;
import com.ojj.sujuro.history.ProcessType;
import com.ojj.sujuro.notification.NotificationService;
import com.ojj.sujuro.notification.NotificationType;
import com.ojj.sujuro.proposal.dto.DocDto;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.PrpFileDto;
import com.ojj.sujuro.proposal.dto.SubmissionDto;
import com.ojj.sujuro.proposal.mapper.DocMapper;
import com.ojj.sujuro.proposal.service.DocService;

/** 제출서류 · 조견표 · 나라장터 등록 (FR-015, FR-067~073) */
@Service
public class DocServiceImpl implements DocService {

    private static final String SE_DOC = "FL050";   // 제출서류
    private static final String SE_CHEAT = "FL060"; // 조견표

    private final DocMapper mapper;
    private final FileStorage storage;
    private final HistoryService historyService;
    private final NotificationService notificationService;

    public DocServiceImpl(DocMapper mapper, FileStorage storage,
                          HistoryService historyService, NotificationService notificationService) {
        this.mapper = mapper;
        this.storage = storage;
        this.historyService = historyService;
        this.notificationService = notificationService;
    }

    @Override
    public List<ProposalBidDto> bidsForMgmt() {
        return mapper.findBidsForMgmt();
    }

    @Override
    public boolean isMgmtVisible(Long pbancSn) {
        SubmissionDto s = pbancSn == null ? null : mapper.findSubmission(pbancSn);
        return s != null && (s.isSubmittable() || s.isSubmitted());
    }

    @Override
    public ProposalBidDto bid(Long pbancSn) {
        ProposalBidDto b = mapper.findBid(pbancSn);
        if (b == null) {
            throw new BusinessException("사업건을 찾을 수 없습니다.");
        }
        return b;
    }

    @Override
    public SubmissionDto submission(Long pbancSn) {
        return mapper.findSubmission(pbancSn);
    }

    @Override
    public List<DocDto> docs(Long pbancSn) {
        return mapper.findDocs(pbancSn);
    }

    @Override
    public List<PrpFileDto> cheatFiles(Long pbancSn) {
        return mapper.findCheatFiles(pbancSn);
    }

    /* ---------- 체크리스트 ---------- */

    @Transactional
    @Override
    public void addDoc(Long pbancSn, String name, boolean essential, String ownerCd, String due) {
        requireOpen(pbancSn);
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty()) {
            throw new BusinessException("서류명을 입력하세요.");
        }
        if (nm.length() > 100) {
            throw new BusinessException("서류명은 100자까지 입력할 수 있습니다.");
        }
        for (DocDto d : mapper.findDocs(pbancSn)) {
            if (d.getDcmntNm().equals(nm)) {
                throw new BusinessException("같은 이름의 서류가 이미 있습니다.");
            }
        }
        DocDto d = new DocDto();
        d.setPbancSn(pbancSn);
        d.setDcmntNm(nm);
        d.setEsntlYn(essential ? "Y" : "N");
        d.setTkcgSeCd(DocDto.OWNER_TEAM.equals(ownerCd) ? DocDto.OWNER_TEAM : DocDto.OWNER_MGMT);
        d.setSbmsnDdlnDt(parseDate(due));
        mapper.insertDoc(d);
    }

    @Transactional
    @Override
    public void deleteDoc(Long pbancSn, Long dcmntSn) {
        requireOpen(pbancSn);
        DocDto d = own(pbancSn, dcmntSn);
        if (d.getFileTotal() > 0) {
            throw new BusinessException("'" + d.getDcmntNm() + "'에 올린 파일(삭제한 파일 기록 포함)이 있어 지울 수 없습니다. (파일 이력은 보관합니다)");
        }
        mapper.deleteDoc(dcmntSn);
    }

    @Transactional
    @Override
    public void changeDue(Long pbancSn, Long dcmntSn, String due) {
        requireOpen(pbancSn);
        own(pbancSn, dcmntSn);
        mapper.updateDocDue(dcmntSn, parseDate(due));
    }

    @Transactional
    @Override
    public void changeStatus(Long pbancSn, Long dcmntSn, String statusCd, boolean admin) {
        requireOpen(pbancSn);
        DocDto d = own(pbancSn, dcmntSn);
        if (!admin && !d.isTeamOwned()) {
            throw new BusinessException("경영지원실 담당 서류의 상태는 경영지원실이 바꿉니다.");
        }
        if (!(DocDto.ST_NONE.equals(statusCd) || DocDto.ST_DOING.equals(statusCd) || DocDto.ST_READY.equals(statusCd))) {
            throw new BusinessException("준비 상태가 올바르지 않습니다.");
        }
        mapper.updateDocStatus(dcmntSn, statusCd);
    }

    @Transactional
    @Override
    public void uploadDoc(Long pbancSn, Long dcmntSn, MultipartFile file, String userId) {
        requireOpen(pbancSn);
        DocDto d = own(pbancSn, dcmntSn);
        PrpFileDto f = newFile(pbancSn, SE_DOC, file, userId, String.valueOf(d.getFileTotal() + 1)); // 삭제한 버전도 세어 번호를 되돌리지 않는다
        store(f, file, dcmntSn);
        if (DocDto.ST_NONE.equals(d.getPrgrsSttsCd())) {
            mapper.updateDocStatus(dcmntSn, DocDto.ST_DOING);
        }
    }

    /* ---------- 조견표 ---------- */

    @Transactional
    @Override
    public void uploadCheatFile(Long pbancSn, MultipartFile file, String userId) {
        requireOpen(pbancSn);
        PrpFileDto f = newFile(pbancSn, SE_CHEAT, file, userId, String.valueOf(mapper.countCheatFiles(pbancSn) + 1));
        // 승인·반려 없이 올린 파일을 경영지원실과 제안팀이 함께 확인한다 (FR-072)
        store(f, file, null);
    }

    /* ---------- 나라장터 등록 ---------- */

    @Transactional
    @Override
    public void register(Long pbancSn, String userId) {
        SubmissionDto s = mapper.findSubmission(pbancSn);
        if (s == null || s.isSubmitted()) {
            throw new BusinessException("이미 나라장터 등록 완료 처리된 사업건입니다.");
        }
        if (!s.isSubmittable()) {
            throw new BusinessException("참여 확정 또는 제안 작성 중인 사업건만 등록 완료 처리할 수 있습니다.");
        }
        List<DocDto> docs = mapper.findDocs(pbancSn);
        if (docs.isEmpty()) {
            throw new BusinessException("제출서류 체크리스트가 비어 있습니다. 서류 항목을 먼저 만드세요.");
        }
        List<String> missing = new ArrayList<>();
        for (DocDto d : docs) {
            if (d.isEssential() && !d.isReady()) {
                missing.add(d.getDcmntNm());
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("준비되지 않은 필수 서류가 있습니다: " + String.join(", ", missing));
        }
        if (mapper.updateSubmitted(pbancSn, userId) == 0) {
            throw new BusinessException("등록 완료 처리하지 못했습니다. 화면을 새로 고쳐 상태를 확인하세요.");
        }
        historyService.record(pbancSn, ProcessType.REGISTERED_NARAJANGTER, userId);
        ProposalBidDto b = mapper.findBid(pbancSn);
        String msg = "'" + b.getLabel() + "' 나라장터 등록이 완료되었습니다.";
        notificationService.sendToProposalTeam(pbancSn, NotificationType.SUBMITTED, msg);
        notificationService.sendToRole(Role.SALES, NotificationType.SUBMITTED, pbancSn, msg);
    }

    @Override
    public List<PrpFileDto> deletedFiles(Long pbancSn) {
        return mapper.findDeletedFiles(pbancSn);
    }

    @Override
    public boolean canDeleteFile(String rgtrId, boolean submitted, String userId, boolean admin, boolean lead) {
        return !submitted && (admin || lead || userId.equals(rgtrId));
    }

    @Transactional
    @Override
    public void deleteFile(Long pbancSn, Long fileSn, String reason, String userId, boolean admin, boolean lead) {
        PrpFileDto f = fileSn == null ? null : mapper.findFile(fileSn);
        if (f == null || !f.getPbancSn().equals(pbancSn)
                || !(SE_DOC.equals(f.getFileSeCd()) || SE_CHEAT.equals(f.getFileSeCd()))) {
            throw new BusinessException("이 사업건의 제출서류·조견표 파일이 아닙니다.");
        }
        if (f.isDeleted()) {
            throw new BusinessException("이미 삭제된 파일입니다.");
        }
        requireOpen(pbancSn); // 나라장터 등록 뒤에는 삭제할 수 없다
        if (!canDeleteFile(f.getRgtrId(), false, userId, admin, lead)) {
            throw new BusinessException("파일은 올린 사람, 주관팀 팀장, 경영지원실만 삭제할 수 있습니다.");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new BusinessException("삭제 사유를 입력해 주세요.");
        }
        if (r.length() > 1000) {
            throw new BusinessException("삭제 사유는 1000자까지 입력할 수 있습니다.");
        }
        mapper.markFileDeleted(fileSn, userId, r); // 준비 상태는 담당자가 직접 바꾼다
    }

    @Override
    public PrpFileDto findForDownload(Long pbancSn, Long fileSn) {
        PrpFileDto f = fileSn == null ? null : mapper.findFile(fileSn);
        if (f == null || !f.getPbancSn().equals(pbancSn)
                || !(SE_DOC.equals(f.getFileSeCd()) || SE_CHEAT.equals(f.getFileSeCd())) || f.getStrgFileNm() == null) {
            throw new BusinessException("내려받을 수 없는 파일입니다.");
        }
        return f;
    }

    /* ---------- 내부 처리 ---------- */

    /** 나라장터 등록 뒤에는 고칠 수 없다. */
    private void requireOpen(Long pbancSn) {
        SubmissionDto s = mapper.findSubmission(pbancSn);
        if (s == null) {
            throw new BusinessException("사업건을 찾을 수 없습니다.");
        }
        if (s.isSubmitted()) {
            throw new BusinessException("나라장터 등록이 끝난 사업건은 수정할 수 없습니다.");
        }
    }

    private DocDto own(Long pbancSn, Long dcmntSn) {
        DocDto d = dcmntSn == null ? null : mapper.findDoc(dcmntSn);
        if (d == null || !d.getPbancSn().equals(pbancSn)) {
            throw new BusinessException("이 사업건의 서류가 아닙니다.");
        }
        return d;
    }

    private PrpFileDto newFile(Long pbancSn, String fileSeCd, MultipartFile file, String userId, String ver) {
        String original = FileStorage.checkUpload(file);
        PrpFileDto f = new PrpFileDto();
        f.setPbancSn(pbancSn);
        f.setFileSeCd(fileSeCd);
        f.setVerNo(ver);
        f.setOrgnlFileNm(original);
        f.setFileSz(file.getSize());
        f.setFileExtnNm(FileStorage.extColumn(FileStorage.extension(original)));
        f.setRgtrId(userId);
        f.setFilePathNm("docs/" + pbancSn);
        return f;
    }

    /** 서버에 저장한 뒤 DB에 기록한다. DB 기록이 실패하면 저장한 파일을 지운다. */
    private void store(PrpFileDto f, MultipartFile file, Long dcmntSn) {
        String stored = storage.save(file, f.getFilePathNm(), FileStorage.extension(f.getOrgnlFileNm()));
        f.setStrgFileNm(stored);
        try {
            mapper.insertFile(f, dcmntSn);
        } catch (RuntimeException e) {
            storage.deleteQuietly(f.getFilePathNm(), stored);
            throw e;
        }
    }

    private Date parseDate(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Date.valueOf(LocalDate.parse(s.trim()));
        } catch (DateTimeParseException e) {
            throw new BusinessException("제출 기한 날짜 형식이 올바르지 않습니다.");
        }
    }

}
