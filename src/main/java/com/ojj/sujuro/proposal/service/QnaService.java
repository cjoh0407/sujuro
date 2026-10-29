package com.ojj.sujuro.proposal.service;

import java.util.List;

import com.ojj.sujuro.proposal.dto.QnaDto;

/** 질의응답 기록: 예상 Q&A·발표 Q&A 등록·수정·삭제 (FR-074~076) */
public interface QnaService {

    List<QnaDto> list(Long pbancSn);

    /** 한 구분(예상/발표)만 고른다 */
    List<QnaDto> filter(List<QnaDto> all, String seCd);

    /** 수정 화면에 채울 한 건. 이 사업건 것이 아니면 업무 예외 */
    QnaDto findForEdit(Long pbancSn, Long qstnAnsSn);

    void register(Long pbancSn, QnaDto in, String userId);

    void edit(Long pbancSn, Long qstnAnsSn, QnaDto in);

    /** 삭제는 작성자 또는 주관팀 팀장(lead)만 */
    void delete(Long pbancSn, Long qstnAnsSn, String userId, boolean lead);
}
