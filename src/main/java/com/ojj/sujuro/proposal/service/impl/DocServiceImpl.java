package com.ojj.sujuro.proposal.service.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.history.service.HistoryService;
import com.ojj.sujuro.history.code.ProcessType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.proposal.dto.DocDto;
import com.ojj.sujuro.proposal.dto.EvlArtclDto;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.PrpFileDto;
import com.ojj.sujuro.proposal.dto.SubmissionDto;
import com.ojj.sujuro.proposal.mapper.DocMapper;
import com.ojj.sujuro.proposal.service.DocService;

import lombok.RequiredArgsConstructor;

/** 제출서류 · 조견표 · 나라장터 등록 (FR-015, FR-067~073) */
@Service
@RequiredArgsConstructor
public class DocServiceImpl implements DocService {

    private static final String SE_DOC = "FL050";   // 제출서류
    private static final String SE_CHEAT = "FL060"; // 조견표 파일: v4.1부터 올리지 않는다. 예전 파일의 기록·내려받기만 남긴다

    private static final int ARTICLE_NAME_MAX = 100;
    private static final int PAGE_MAX = 20;
    private static final BigDecimal HUNDRED = new BigDecimal(100);
    /** 쪽번호: 12 / 12-15 / 12~15 를 쉼표로 이어 쓸 수 있다 */
    private static final Pattern PAGE = Pattern.compile("\\d{1,4}(\\s*[-~]\\s*\\d{1,4})?(\\s*,\\s*\\d{1,4}(\\s*[-~]\\s*\\d{1,4})?)*");

    private final DocMapper mapper;
    private final FileStorage storage;
    private final HistoryService historyService;
    private final NotificationService notificationService;

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
    public List<EvlArtclDto> articles(Long pbancSn) {
        return mapper.findArticles(pbancSn);
    }

    /* ---------- 체크리스트 ---------- */

    @Transactional
    @Override
    public void addDoc(Long pbancSn, String name, boolean essential, String ownerCd, String due) {
        requireOpen(pbancSn);
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty()) {
            throw new BusinessException("서류명을 입력해 주세요.");
        }
        if (nm.length() > 100) {
            throw new BusinessException("서류명은 100자까지 입력할 수 있습니다.");
        }
        if (nm.replace(" ", "").contains("조견표")) {
            throw new BusinessException("조견표는 체크리스트에 넣지 않고 아래 조견표에서 평가 항목별 쪽번호로 입력합니다.");
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
        // 서류를 지우면 그 서류에 올린 파일도 기록과 함께 모두 지운다 (삭제한 파일 기록 포함)
        List<PrpFileDto> files = d.getFileTotal() > 0 ? mapper.findFilesOfDoc(dcmntSn) : new ArrayList<>();
        mapper.deleteFilesOfDoc(dcmntSn);
        mapper.deleteDoc(dcmntSn);
        for (PrpFileDto f : files) { // DB를 먼저 지운 뒤 서버의 파일을 지운다
            if (f.getStrgFileNm() != null && f.getFilePathNm() != null) {
                storage.deleteQuietly(f.getFilePathNm(), f.getStrgFileNm());
            }
        }
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

    /* ---------- 조견표 (FR-071) ---------- */

    @Transactional
    @Override
    public void addArticle(Long pbancSn, String name, String score) {
        requireOpen(pbancSn);
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty()) {
            throw new BusinessException("평가 항목 이름을 입력해 주세요.");
        }
        if (nm.length() > ARTICLE_NAME_MAX) {
            throw new BusinessException("평가 항목 이름은 " + ARTICLE_NAME_MAX + "자까지 입력할 수 있습니다.");
        }
        BigDecimal scr;
        try {
            scr = new BigDecimal(score == null ? "" : score.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("배점은 숫자로 입력해 주세요.");
        }
        if (scr.signum() <= 0 || scr.compareTo(HUNDRED) > 0) {
            throw new BusinessException("배점은 0보다 크고 100 이하로 입력해 주세요.");
        }
        if (scr.scale() > 3) {
            throw new BusinessException("배점은 소수점 아래 세 자리까지 입력할 수 있습니다.");
        }
        BigDecimal sum = scr;
        for (EvlArtclDto a : mapper.findArticles(pbancSn)) {
            if (a.getEvlArtclNm().equals(nm)) {
                throw new BusinessException("같은 이름의 평가 항목이 이미 있습니다.");
            }
            if (a.getEvlScorScr() != null) {
                sum = sum.add(a.getEvlScorScr());
            }
        }
        if (sum.compareTo(HUNDRED) > 0) {
            throw new BusinessException("배점 합계가 100점을 넘습니다 (" + sum.stripTrailingZeros().toPlainString() + "점).");
        }
        EvlArtclDto a = new EvlArtclDto();
        a.setPbancSn(pbancSn);
        a.setEvlArtclNm(nm);
        a.setEvlScorScr(scr);
        mapper.insertArticle(a);
    }

    @Transactional
    @Override
    public void deleteArticle(Long pbancSn, Long evlArtclSn) {
        requireOpen(pbancSn);
        EvlArtclDto a = ownArticle(pbancSn, evlArtclSn);
        if (mapper.countArticleUses(evlArtclSn) > 0) {
            throw new BusinessException("'" + a.getEvlArtclNm() + "'은 결과 분석에서 쓰고 있어 지울 수 없습니다.");
        }
        mapper.deleteArticle(evlArtclSn);
    }

    @Transactional
    @Override
    public int savePages(Long pbancSn, List<Long> evlArtclSns, List<String> pageNos) {
        requireOpen(pbancSn);
        if (evlArtclSns == null || evlArtclSns.isEmpty()) {
            throw new BusinessException("저장할 평가 항목이 없습니다.");
        }
        if (pageNos == null || pageNos.size() != evlArtclSns.size()) {
            throw new BusinessException("쪽번호를 다시 입력해 주세요.");
        }
        List<String> clean = new ArrayList<>();
        for (int i = 0; i < evlArtclSns.size(); i++) {
            EvlArtclDto a = ownArticle(pbancSn, evlArtclSns.get(i));
            String p = pageNos.get(i) == null ? "" : pageNos.get(i).trim();
            if (!p.isEmpty() && (p.length() > PAGE_MAX || !PAGE.matcher(p).matches())) {
                throw new BusinessException("'" + a.getEvlArtclNm() + "'의 쪽번호 형식이 올바르지 않습니다. 예: 12-15, 3, 20-21");
            }
            clean.add(p.isEmpty() ? null : p);
        }
        for (int i = 0; i < evlArtclSns.size(); i++) {
            mapper.updateArticlePage(evlArtclSns.get(i), clean.get(i));
        }
        return evlArtclSns.size();
    }

    @Transactional
    @Override
    public void saveArticles(Long pbancSn, boolean admin, List<Long> sns, List<String> names, List<String> scores,
                             List<String> pages, List<Long> delSns, List<String> newNames, List<String> newScores) {
        requireOpen(pbancSn);
        if (!admin) { // 제안팀: 쪽번호만
            savePages(pbancSn, sns, pages);
            return;
        }
        List<Long> keep = sns == null ? new ArrayList<>() : sns;
        List<String> nms = names == null ? new ArrayList<>() : names; // 모두 지웠으면 빈 목록
        List<String> scs = scores == null ? new ArrayList<>() : scores;
        if (nms.size() != keep.size() || scs.size() != keep.size()) {
            throw new BusinessException("조견표를 다시 열어 주세요. (항목 수가 맞지 않습니다)");
        }
        // 1) 지울 항목: 결과 분석에서 쓰는 항목은 지우지 않는다
        if (delSns != null) {
            for (Long sn : delSns) {
                deleteArticle(pbancSn, sn);
            }
        }
        // 2) 남길 항목 + 새 항목을 모두 검사 (이름 중복, 배점, 합계 100점 이하)
        java.util.Set<String> seen = new java.util.HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        List<BigDecimal> keepScr = new ArrayList<>();
        List<String> keepNm = new ArrayList<>();
        for (int i = 0; i < keep.size(); i++) {
            ownArticle(pbancSn, keep.get(i));
            String nm = articleName(nms.get(i), seen);
            BigDecimal scr = articleScore(scs.get(i), nm);
            keepNm.add(nm);
            keepScr.add(scr);
            sum = sum.add(scr);
        }
        List<String> addNm = new ArrayList<>();
        List<BigDecimal> addScr = new ArrayList<>();
        int n = newNames == null ? 0 : newNames.size();
        for (int i = 0; i < n; i++) {
            String rawNm = newNames.get(i) == null ? "" : newNames.get(i).trim();
            String rawScr = newScores == null || i >= newScores.size() || newScores.get(i) == null ? "" : newScores.get(i).trim();
            if (rawNm.isEmpty() && rawScr.isEmpty()) {
                continue; // 비워 둔 새 줄은 건너뛴다
            }
            String nm = articleName(rawNm, seen);
            BigDecimal scr = articleScore(rawScr, nm);
            addNm.add(nm);
            addScr.add(scr);
            sum = sum.add(scr);
        }
        if (sum.compareTo(HUNDRED) > 0) {
            throw new BusinessException("배점 합계가 100점을 넘습니다 (" + sum.stripTrailingZeros().toPlainString() + "점).");
        }
        // 3) 저장
        for (int i = 0; i < keep.size(); i++) {
            mapper.updateArticle(keep.get(i), keepNm.get(i), keepScr.get(i));
        }
        for (int i = 0; i < addNm.size(); i++) {
            EvlArtclDto a = new EvlArtclDto();
            a.setPbancSn(pbancSn);
            a.setEvlArtclNm(addNm.get(i));
            a.setEvlScorScr(addScr.get(i));
            mapper.insertArticle(a);
        }
    }

    /** 평가 항목 이름 검사 (비었거나, 길거나, 같은 이름이 있으면 업무 예외) */
    private String articleName(String raw, java.util.Set<String> seen) {
        String nm = raw == null ? "" : raw.trim();
        if (nm.isEmpty()) {
            throw new BusinessException("평가 항목 이름을 입력해 주세요.");
        }
        if (nm.length() > ARTICLE_NAME_MAX) {
            throw new BusinessException("평가 항목 이름은 " + ARTICLE_NAME_MAX + "자까지 입력할 수 있습니다.");
        }
        if (!seen.add(nm)) {
            throw new BusinessException("같은 이름의 평가 항목이 있습니다. (" + nm + ")");
        }
        return nm;
    }

    /** 배점 검사: 0보다 크고 100 이하, 소수점 아래 세 자리까지 */
    private BigDecimal articleScore(String raw, String nm) {
        BigDecimal scr;
        try {
            scr = new BigDecimal(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("'" + nm + "'의 배점은 숫자로 입력해 주세요.");
        }
        if (scr.signum() <= 0 || scr.compareTo(HUNDRED) > 0) {
            throw new BusinessException("'" + nm + "'의 배점은 0보다 크고 100 이하로 입력해 주세요.");
        }
        if (scr.scale() > 3) {
            throw new BusinessException("'" + nm + "'의 배점은 소수점 아래 세 자리까지 입력할 수 있습니다.");
        }
        return scr;
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
            throw new BusinessException("등록 완료 처리하지 못했습니다. 화면을 새로 고쳐 상태를 확인해 주세요.");
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
            throw new BusinessException("이 사업건의 제출서류 파일이 아닙니다.");
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

    private EvlArtclDto ownArticle(Long pbancSn, Long evlArtclSn) {
        EvlArtclDto a = evlArtclSn == null ? null : mapper.findArticle(evlArtclSn);
        if (a == null || !a.getPbancSn().equals(pbancSn)) {
            throw new BusinessException("이 사업건의 평가 항목이 아닙니다.");
        }
        return a;
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
        LocalDate day;
        try {
            day = LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("제출 기한 날짜 형식이 올바르지 않습니다.");
        }
        if (day.isBefore(LocalDate.now())) {
            throw new BusinessException("제출 기한은 오늘 이후 날짜로 정해 주세요.");
        }
        return Date.valueOf(day);
    }

}
