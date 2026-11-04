package com.ojj.sujuro.proposal.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.proposal.dto.PrpFileDto;
import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.mapper.PrpFileMapper;
import com.ojj.sujuro.proposal.mapper.TocMapper;
import com.ojj.sujuro.proposal.service.PrpFileService;

import lombok.RequiredArgsConstructor;

/** 제안서 파일 관리 (FR-057~066, NFR-009) */
@Service
@RequiredArgsConstructor
public class PrpFileServiceImpl implements PrpFileService {

    private static final String ST_WRITING = "TS020";  // 작성 중
    private static final String ST_REVIEWED = "TS030"; // 검토 완료
    private static final String ST_BID_SUBMITTED = "PS050"; // 공고상태: 제출 완료
    private static final String ST_BID_RESULT = "PS060";    // 공고상태: 결과 등록

    private final PrpFileMapper mapper;
    private final TocMapper tocMapper;
    private final FileStorage storage;

    @Override
    public Map<Long, List<PrpFileDto>> tocFiles(Long pbancSn, String fileSeCd) {
        return PrpFileService.groupByToc(mapper.findByBid(pbancSn, fileSeCd));
    }

    @Override
    public List<PrpFileDto> fullFiles(Long pbancSn) {
        return mapper.findByBid(pbancSn, PrpFileDto.SE_FULL);
    }

    @Transactional
    @Override
    public String uploadToc(Long pbancSn, String fileSeCd, Long tocSn, MultipartFile file,
                            String userId, boolean lead) {
        String tocSe = PrpFileDto.SE_PART.equals(fileSeCd) ? TocDto.SE_PROPOSAL
                     : PrpFileDto.SE_SUMMARY.equals(fileSeCd) ? TocDto.SE_SUMMARY : null;
        if (tocSe == null) {
            throw new BusinessException("파일 구분이 올바르지 않습니다.");
        }
        TocDto t = tocSn == null ? null : tocMapper.findOne(tocSn);
        if (t == null || t.getPbancSn() == null || !t.getPbancSn().equals(pbancSn) || !tocSe.equals(t.getTocSeCd())) {
            throw new BusinessException("이 사업건의 목차가 아닙니다.");
        }
        if (t.isGroup()) {
            throw new BusinessException("파일은 최하위 목차에만 올릴 수 있습니다.");
        }
        if (!lead && !userId.equals(t.getPicId())) {
            throw new BusinessException("이 목차의 파일은 담당자와 주관팀 팀장만 올릴 수 있습니다.");
        }
        PrpFileDto f = newFile(pbancSn, fileSeCd, file, userId);
        // 같은 이름의 PDF·PPT는 한 버전으로 묶는다 (이미 그 형식이 있는 버전에는 붙이지 않는다)
        Integer pair = pairVersion(mapper.findByBid(pbancSn, fileSeCd), tocSn, f);
        String ver = String.valueOf(pair != null ? pair : mapper.nextTocVersion(pbancSn, fileSeCd, tocSn));
        f.setTocSn(tocSn);
        f.setVerNo(ver);
        store(f, file);
        if (!ST_WRITING.equals(trim(t.getPrgrsSttsCd()))) {
            tocMapper.updateProgress(tocSn, ST_WRITING); // 첫 파일이거나, 검토 완료 뒤 새 버전이 올라오면 작성 중
        }
        return ver;
    }

    @Transactional
    @Override
    public int uploadFull(Long pbancSn, MultipartFile file, String userId) {
        PrpFileDto f = newFile(pbancSn, PrpFileDto.SE_FULL, file, userId);
        Integer pair = pairVersion(mapper.findByBid(pbancSn, PrpFileDto.SE_FULL), null, f);
        int cycl = pair != null ? pair : mapper.nextIntgCycl(pbancSn);
        f.setIntgCycl(cycl);
        f.setVerNo(String.valueOf(cycl));
        store(f, file);
        return cycl;
    }

    @Transactional
    @Override
    public void markReviewed(Long pbancSn, Long tocSn, boolean done) {
        TocDto t = tocSn == null ? null : tocMapper.findOne(tocSn);
        if (t == null || t.getPbancSn() == null || !t.getPbancSn().equals(pbancSn)) {
            throw new BusinessException("이 사업건의 목차가 아닙니다.");
        }
        if (done && t.getFileCnt() == 0) {
            throw new BusinessException("올린 파일이 없는 목차는 검토 완료로 표시할 수 없습니다.");
        }
        tocMapper.updateProgress(tocSn, done ? ST_REVIEWED : ST_WRITING);
    }

    @Transactional
    @Override
    public void confirmFinal(Long pbancSn, Long fileSn, String userId) {
        PrpFileDto f = fileSn == null ? null : mapper.findOne(fileSn);
        if (f == null || !f.getPbancSn().equals(pbancSn) || !PrpFileDto.SE_FULL.equals(f.getFileSeCd())) {
            throw new BusinessException("최종본은 이 사업건의 통합본 중에서만 고를 수 있습니다.");
        }
        mapper.clearFinal(pbancSn);
        mapper.markFinal(pbancSn, fileSn, userId); // 같은 차수의 PDF·PPT가 함께 최종본이 된다
    }

    @Override
    public List<PrpFileDto> deletedFiles(Long pbancSn, String fileSeCd) {
        return mapper.findDeleted(pbancSn, fileSeCd);
    }

    @Override
    public boolean isSubmitted(Long pbancSn) {
        String st = mapper.findBidStatus(pbancSn);
        return st != null && (ST_BID_SUBMITTED.equals(st.trim()) || ST_BID_RESULT.equals(st.trim()));
    }

    @Override
    public boolean canDelete(PrpFileDto f, String tocProgress, boolean submitted, String userId, boolean lead) {
        if (f == null || f.isDeleted() || f.isLast() || submitted) {
            return false;
        }
        if (f.getTocSn() != null && ST_REVIEWED.equals(trim(tocProgress))) {
            return false; // 검토 완료 목차의 파일은 지우지 않는다
        }
        return lead || userId.equals(f.getRgtrId());
    }

    @Transactional
    @Override
    public void deleteFile(Long pbancSn, Long fileSn, String reason, String userId, boolean lead) {
        PrpFileDto f = fileSn == null ? null : mapper.findOne(fileSn);
        if (f == null || !f.getPbancSn().equals(pbancSn)
                || !(PrpFileDto.SE_PART.equals(f.getFileSeCd()) || PrpFileDto.SE_FULL.equals(f.getFileSeCd())
                     || PrpFileDto.SE_SUMMARY.equals(f.getFileSeCd()))) {
            throw new BusinessException("이 사업건의 제안서 파일이 아닙니다.");
        }
        if (f.isDeleted()) {
            throw new BusinessException("이미 삭제된 파일입니다.");
        }
        if (isSubmitted(pbancSn)) {
            throw new BusinessException("나라장터에 제출한 뒤에는 파일을 삭제할 수 없습니다.");
        }
        if (f.isLast()) {
            throw new BusinessException("최종본으로 확정한 통합본은 삭제할 수 없습니다.");
        }
        String progress = null;
        if (f.getTocSn() != null) {
            TocDto t = tocMapper.findOne(f.getTocSn());
            progress = t == null ? null : t.getPrgrsSttsCd();
            if (ST_REVIEWED.equals(trim(progress))) {
                throw new BusinessException("검토 완료한 목차의 파일은 삭제할 수 없습니다. 검토 완료를 먼저 풀어 주세요.");
            }
        }
        if (!lead && !userId.equals(f.getRgtrId())) {
            throw new BusinessException("파일은 올린 사람과 주관팀 팀장만 삭제할 수 있습니다.");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new BusinessException("삭제 사유를 입력해 주세요.");
        }
        if (r.length() > 1000) {
            throw new BusinessException("삭제 사유는 1000자까지 입력할 수 있습니다.");
        }
        mapper.markDeleted(fileSn, userId, r);
    }

    @Override
    public PrpFileDto findForDownload(Long pbancSn, Long fileSn) {
        PrpFileDto f = fileSn == null ? null : mapper.findOne(fileSn);
        if (f == null || !f.getPbancSn().equals(pbancSn)
                || !(PrpFileDto.SE_PART.equals(f.getFileSeCd()) || PrpFileDto.SE_FULL.equals(f.getFileSeCd())
                     || PrpFileDto.SE_SUMMARY.equals(f.getFileSeCd()))
                || f.getStrgFileNm() == null) {
            throw new BusinessException("내려받을 수 없는 파일입니다.");
        }
        return f;
    }

    /* ---------- 내부 처리 ---------- */

    /** 파일 검사(용량·실행 파일, FileStorage.checkUpload)를 하고 DB에 넣을 정보를 채운다. (FR-065) */
    private PrpFileDto newFile(Long pbancSn, String fileSeCd, MultipartFile file, String userId) {
        long max = PrpFileDto.SE_FULL.equals(fileSeCd) ? FileStorage.MAX_SIZE_FULL : FileStorage.MAX_SIZE;
        String original = FileStorage.checkUpload(file, max);
        if (PrpFileService.formatOf(original).isEmpty()) {
            throw new BusinessException("제안서 파일은 PDF 또는 PPT(PPTX) 형식만 올릴 수 있습니다.");
        }
        PrpFileDto f = new PrpFileDto();
        f.setPbancSn(pbancSn);
        f.setFileSeCd(fileSeCd);
        f.setOrgnlFileNm(original);
        f.setFileSz(file.getSize());
        f.setFileExtnNm(FileStorage.extColumn(FileStorage.extension(original)));
        f.setRgtrId(userId);
        f.setFilePathNm("proposal/" + pbancSn);
        return f;
    }

    /**
     * 같은 이름(확장자 제외)의 파일이 이미 있고 그 버전에 같은 형식이 없으면, 그 버전 번호(통합본은 차수)를 돌려준다.
     * 여러 버전이 해당하면 가장 최근 것. 없으면 null (새 버전).
     * tocSn이 null이면 통합본이라 전체에서 찾고, 아니면 그 목차의 파일에서만 찾는다.
     */
    private Integer pairVersion(List<PrpFileDto> existing, Long tocSn, PrpFileDto nf) {
        String fmt = PrpFileService.formatOf(nf.getOrgnlFileNm());
        String base = PrpFileService.baseName(nf.getOrgnlFileNm());
        Map<Integer, List<PrpFileDto>> byVer = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (PrpFileDto e : existing) {
            if (tocSn == null || tocSn.equals(e.getTocSn())) {
                byVer.computeIfAbsent(PrpFileService.versionKey(e), k -> new java.util.ArrayList<>()).add(e);
            }
        }
        for (Map.Entry<Integer, List<PrpFileDto>> en : byVer.entrySet()) {
            boolean sameName = false;
            boolean hasFmt = false;
            for (PrpFileDto e : en.getValue()) {
                sameName |= base.equals(e.getBaseNm());
                hasFmt |= fmt.equals(e.getFmt());
            }
            if (sameName && !hasFmt) {
                return en.getKey();
            }
        }
        return null;
    }

    /** 서버에 저장한 뒤 DB에 기록한다. DB 기록이 실패하면 저장한 파일을 지운다. */
    private void store(PrpFileDto f, MultipartFile file) {
        String stored = storage.save(file, f.getFilePathNm(), FileStorage.extension(f.getOrgnlFileNm()));
        f.setStrgFileNm(stored);
        try {
            mapper.insert(f);
        } catch (RuntimeException e) {
            storage.deleteQuietly(f.getFilePathNm(), stored);
            throw e;
        }
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
