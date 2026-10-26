package com.ojj.sujuro.proposal.rqt;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;

/** 요구사항 등록·수정·삭제와 현황 계산 (FR-050, FR-052~054). 목차 매핑·담당자 지정은 10/22에 붙인다. */
@Service
public class RqtServiceImpl implements RqtService {

    private final RqtMapper mapper;

    public RqtServiceImpl(RqtMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RqtDto> list(Long pbancSn) {
        return mapper.findByBid(pbancSn);
    }

    /** 필터: 전체 / 미매핑 / 내 담당(연결된 목차의 담당자가 나) (FR-054) */
    @Override
    public List<RqtDto> filter(List<RqtDto> all, String filter, String userId) {
        if (FILTER_UNMAPPED.equals(filter)) {
            List<RqtDto> r = new ArrayList<>();
            for (RqtDto q : all) {
                if (q.isUnmapped()) {
                    r.add(q);
                }
            }
            return r;
        }
        if (FILTER_MINE.equals(filter)) {
            List<RqtDto> r = new ArrayList<>();
            for (RqtDto q : all) {
                if (userId.equals(q.getPicId())) {
                    r.add(q);
                }
            }
            return r;
        }
        return all;
    }

    @Override
    public int unmappedCount(List<RqtDto> all) {
        int n = 0;
        for (RqtDto q : all) {
            if (q.isUnmapped()) {
                n++;
            }
        }
        return n;
    }

    /** 매핑률(%). 요구사항이 없으면 0 (FR-053) */
    @Override
    public int progress(List<RqtDto> all) {
        if (all.isEmpty()) {
            return 0;
        }
        return (int) Math.round((all.size() - unmappedCount(all)) * 100.0 / all.size());
    }

    /** 수정 화면에 채울 요구사항 한 건. 이 사업건 것이 아니면 예외 */
    @Override
    public RqtDto findForEdit(Long pbancSn, Long rqtMttrSn) {
        return own(pbancSn, rqtMttrSn);
    }

    @Transactional
    @Override
    public void register(Long pbancSn, RqtDto in, String userId) {
        validate(in);
        String no = in.getRqtMttrNo() == null ? "" : in.getRqtMttrNo().trim();
        if (no.isEmpty()) {
            throw new BusinessException("요구사항 ID를 입력해 주세요.");
        }
        if (no.length() > 50) {
            throw new BusinessException("요구사항 ID는 50자까지 입력할 수 있습니다.");
        }
        if (mapper.countByNo(pbancSn, no) > 0) {
            throw new BusinessException("이미 등록된 요구사항 ID입니다. (" + no + ")");
        }
        in.setPbancSn(pbancSn);
        in.setRqtMttrNo(no);
        in.setRgtrId(userId);
        mapper.insert(in);
    }

    @Transactional
    @Override
    public void edit(Long pbancSn, Long rqtMttrSn, RqtDto in) {
        own(pbancSn, rqtMttrSn);
        validate(in);
        in.setRqtMttrSn(rqtMttrSn);
        mapper.update(in);
    }

    @Transactional
    @Override
    public void delete(Long pbancSn, Long rqtMttrSn) {
        own(pbancSn, rqtMttrSn);
        mapper.delete(rqtMttrSn);
    }

    private RqtDto own(Long pbancSn, Long rqtMttrSn) {
        RqtDto q = mapper.findOne(rqtMttrSn);
        if (q == null || !q.getPbancSn().equals(pbancSn)) {
            throw new BusinessException("이 사업건의 요구사항이 아닙니다.");
        }
        return q;
    }

    private void validate(RqtDto in) {
        in.setRqtMttrNm(trim(in.getRqtMttrNm()));
        in.setClsfNm(blankToNull(in.getClsfNm()));
        in.setRqtMttrDfn(blankToNull(in.getRqtMttrDfn()));
        in.setDtlCn(blankToNull(in.getDtlCn()));
        in.setPageNo(blankToNull(in.getPageNo()));
        if (in.getRqtMttrNm().isEmpty()) {
            throw new BusinessException("요구사항 명칭을 입력해 주세요.");
        }
        max(in.getRqtMttrNm(), 300, "요구사항 명칭은 300자");
        max(in.getClsfNm(), 100, "분류는 100자");
        max(in.getRqtMttrDfn(), 2000, "정의는 2000자");
        max(in.getDtlCn(), 4000, "세부 내용은 4000자");
        max(in.getPageNo(), 20, "RFP 쪽은 20자");
    }

    private void max(String v, int n, String label) {
        if (v != null && v.length() > n) {
            throw new BusinessException(label + "까지 입력할 수 있습니다.");
        }
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
