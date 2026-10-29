package com.ojj.sujuro.proposal.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.proposal.dto.QnaDto;
import com.ojj.sujuro.proposal.mapper.QnaMapper;
import com.ojj.sujuro.proposal.service.QnaService;

/** 질의응답 기록: 예상 Q&A·발표 Q&A 등록·수정·삭제 (FR-074~076) */
@Service
public class QnaServiceImpl implements QnaService {

    private static final int MAX_TEXT = 4000;

    private final QnaMapper mapper;

    public QnaServiceImpl(QnaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<QnaDto> list(Long pbancSn) {
        return mapper.findByBid(pbancSn);
    }

    @Override
    public List<QnaDto> filter(List<QnaDto> all, String seCd) {
        List<QnaDto> r = new ArrayList<>();
        for (QnaDto q : all) {
            if (seCd.equals(q.getQstnAnsSeCd())) {
                r.add(q);
            }
        }
        return r;
    }

    @Override
    public QnaDto findForEdit(Long pbancSn, Long qstnAnsSn) {
        return own(pbancSn, qstnAnsSn);
    }

    @Transactional
    @Override
    public void register(Long pbancSn, QnaDto in, String userId) {
        String se = in.getQstnAnsSeCd();
        if (!QnaDto.SE_EXPECTED.equals(se) && !QnaDto.SE_PRESENTED.equals(se)) {
            throw new BusinessException("예상 Q&A인지 발표 Q&A인지 알 수 없습니다.");
        }
        clean(pbancSn, in);
        in.setPbancSn(pbancSn);
        in.setWrtrId(userId);
        mapper.insert(in);
    }

    @Transactional
    @Override
    public void edit(Long pbancSn, Long qstnAnsSn, QnaDto in) {
        QnaDto cur = own(pbancSn, qstnAnsSn);
        in.setQstnAnsSeCd(cur.getQstnAnsSeCd());
        clean(pbancSn, in);
        in.setQstnAnsSn(qstnAnsSn);
        mapper.update(in);
    }

    @Transactional
    @Override
    public void delete(Long pbancSn, Long qstnAnsSn, String userId, boolean lead) {
        QnaDto cur = own(pbancSn, qstnAnsSn);
        if (!lead && !userId.equals(cur.getWrtrId())) {
            throw new BusinessException("작성한 사람이나 주관팀 팀장만 삭제할 수 있습니다.");
        }
        mapper.delete(qstnAnsSn);
    }

    /* ---------- 내부 처리 ---------- */

    private QnaDto own(Long pbancSn, Long qstnAnsSn) {
        QnaDto q = qstnAnsSn == null ? null : mapper.findOne(qstnAnsSn);
        if (q == null || !q.getPbancSn().equals(pbancSn)) {
            throw new BusinessException("이 사업건의 질의응답이 아닙니다.");
        }
        return q;
    }

    /** 입력값 정리와 확인: 질문 필수, 길이, 답변 담당자는 제안팀원, 관련 목차는 이 사업건 목차, 일치 여부는 발표 Q&A만 */
    private void clean(Long pbancSn, QnaDto in) {
        String qstn = trim(in.getQstnCn());
        if (qstn == null) {
            throw new BusinessException("질문을 입력하세요.");
        }
        String ans = trim(in.getAnsCn());
        if (qstn.length() > MAX_TEXT || (ans != null && ans.length() > MAX_TEXT)) {
            throw new BusinessException("질문과 답변은 각각 " + MAX_TEXT + "자까지 입력할 수 있습니다.");
        }
        in.setQstnCn(qstn);
        in.setAnsCn(ans);

        String answr = trim(in.getAnswrId());
        if (answr != null && mapper.countMember(pbancSn, answr) == 0) {
            throw new BusinessException("답변 담당자는 제안팀원 중에서 고르세요.");
        }
        in.setAnswrId(answr);

        if (in.getTocSn() != null && mapper.countToc(pbancSn, in.getTocSn()) == 0) {
            throw new BusinessException("이 사업건의 목차가 아닙니다.");
        }

        String yn = trim(in.getCcrncYn());
        if (!QnaDto.SE_PRESENTED.equals(in.getQstnAnsSeCd()) || (yn != null && !"Y".equals(yn) && !"N".equals(yn))) {
            yn = null;
        }
        in.setCcrncYn(yn);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
