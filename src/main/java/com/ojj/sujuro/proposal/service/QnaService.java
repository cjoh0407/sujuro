package com.ojj.sujuro.proposal.service;

import java.util.List;

import com.ojj.sujuro.proposal.dto.QnaBidDto;
import com.ojj.sujuro.proposal.dto.QnaDto;
import com.ojj.sujuro.proposal.dto.QnaGroupDto;
import com.ojj.sujuro.proposal.dto.RqtDto;

/** 질의응답 기록: 예상 Q&A·발표 Q&A 등록·수정·삭제·검색, 이전 사업 Q&A 보기 (FR-074~076-1) */
public interface QnaService {

    List<QnaDto> list(Long pbancSn);

    /** 한 구분(예상/발표)만 고른다 */
    List<QnaDto> filter(List<QnaDto> all, String seCd);

    /** 질문·답변에 검색어가 들어 있는 것만 고른다. 검색어가 비면 그대로 (FR-076) */
    List<QnaDto> search(List<QnaDto> items, String keyword);

    /**
     * 관련 요구사항이 연결된 1단계 목차별로 묶는다 (목차 순서대로).
     * 그 뒤에 "목차 미연결 요구사항", "관련 요구사항 없음" 묶음이 온다. 빈 묶음은 뺀다. (FR-076)
     */
    List<QnaGroupDto> group(Long pbancSn, List<QnaDto> items);

    /** 관련 요구사항으로 고를 수 있는 이 사업건 요구사항 (ID 순) */
    List<RqtDto> rqtOptions(Long pbancSn);

    /** 이전 사업 Q&A 보기에서 고를 사업건: 내가 제안팀이었고 Q&A가 있는 다른 사업건 (FR-076-1) */
    List<QnaBidDto> pastBids(String userId, Long currentPbancSn);

    /** 수정 화면에 채울 한 건. 이 사업건 것이 아니면 업무 예외 */
    QnaDto findForEdit(Long pbancSn, Long qstnAnsSn);

    void register(Long pbancSn, QnaDto in, String userId);

    void edit(Long pbancSn, Long qstnAnsSn, QnaDto in);

    /** 삭제는 작성자 또는 주관팀 팀장(lead)만 */
    void delete(Long pbancSn, Long qstnAnsSn, String userId, boolean lead);
}
