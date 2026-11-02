package com.ojj.sujuro.proposal.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.proposal.dto.QnaBidDto;
import com.ojj.sujuro.proposal.dto.QnaDto;
import com.ojj.sujuro.proposal.dto.QnaGroupDto;
import com.ojj.sujuro.proposal.dto.RqtDto;
import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.mapper.QnaMapper;
import com.ojj.sujuro.proposal.mapper.RqtMapper;
import com.ojj.sujuro.proposal.mapper.TocMapper;
import com.ojj.sujuro.proposal.service.QnaService;

import lombok.RequiredArgsConstructor;

/** 질의응답 기록: 예상 Q&A·발표 Q&A 등록·수정·삭제·검색, 이전 사업 Q&A 보기 (FR-074~076-1) */
@Service
@RequiredArgsConstructor
public class QnaServiceImpl implements QnaService {

    private static final int MAX_TEXT = 4000;
    private static final String TITLE_UNMAPPED = "목차 미연결 요구사항";
    private static final String TITLE_NONE = "관련 요구사항 없음";

    private final QnaMapper mapper;
    private final RqtMapper rqtMapper;
    private final TocMapper tocMapper;

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
    public List<QnaDto> search(List<QnaDto> items, String keyword) {
        String k = trim(keyword);
        if (k == null) {
            return items;
        }
        List<QnaDto> r = new ArrayList<>();
        for (QnaDto q : items) {
            if (q.matches(k)) {
                r.add(q);
            }
        }
        return r;
    }

    @Override
    public List<QnaGroupDto> group(Long pbancSn, List<QnaDto> items) {
        // 목차 순서대로 1단계 목차 묶음을 만들어 두고, 각 목차가 어느 1단계 목차 아래인지 기억한다
        Map<Long, QnaGroupDto> byTop = new LinkedHashMap<>();
        Map<Long, Long> topOf = new HashMap<>();
        for (TocDto t : tocMapper.findByBid(pbancSn, TocDto.SE_PROPOSAL)) {
            if (t.getUpTocSn() == null) {
                byTop.put(t.getTocSn(), new QnaGroupDto(QnaGroupDto.KIND_TOC, t.getLabel()));
                topOf.put(t.getTocSn(), t.getTocSn());
            } else {
                topOf.put(t.getTocSn(), topOf.get(t.getUpTocSn())); // 부모가 먼저 나온다 (계층 순서 조회)
            }
        }
        QnaGroupDto unmapped = new QnaGroupDto(QnaGroupDto.KIND_UNMAPPED, TITLE_UNMAPPED);
        QnaGroupDto none = new QnaGroupDto(QnaGroupDto.KIND_NONE, TITLE_NONE);
        for (QnaDto q : items) {
            if (q.getRqtMttrSn() == null) {
                none.getItems().add(q);
                continue;
            }
            Long top = q.getRqtTocSn() == null ? null : topOf.get(q.getRqtTocSn());
            QnaGroupDto g = top == null ? null : byTop.get(top);
            (g == null ? unmapped : g).getItems().add(q);
        }
        List<QnaGroupDto> r = new ArrayList<>();
        for (QnaGroupDto g : byTop.values()) {
            if (!g.getItems().isEmpty()) {
                r.add(g);
            }
        }
        if (!unmapped.getItems().isEmpty()) {
            r.add(unmapped);
        }
        if (!none.getItems().isEmpty()) {
            r.add(none);
        }
        return r;
    }

    @Override
    public List<RqtDto> rqtOptions(Long pbancSn) {
        List<RqtDto> r = new ArrayList<>(rqtMapper.findByBid(pbancSn));
        r.sort(Comparator.comparing(RqtDto::getRqtMttrNo, Comparator.nullsLast(Comparator.naturalOrder())));
        return r;
    }

    @Override
    public List<QnaBidDto> pastBids(String userId, Long currentPbancSn) {
        return mapper.findPastBids(userId, currentPbancSn);
    }

    @Override
    public List<QnaBidDto> allPastBids(Long currentPbancSn) {
        return mapper.findAllPastBids(currentPbancSn);
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
        own(pbancSn, qstnAnsSn);
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

    /** 입력값 정리와 확인: 질문 필수, 길이, 관련 요구사항은 이 사업건 요구사항 */
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

        if (in.getRqtMttrSn() != null && mapper.countRqt(pbancSn, in.getRqtMttrSn()) == 0) {
            throw new BusinessException("이 사업건의 요구사항이 아닙니다.");
        }
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
