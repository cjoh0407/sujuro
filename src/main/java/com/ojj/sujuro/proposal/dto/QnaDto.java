package com.ojj.sujuro.proposal.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 질의응답 한 건 (TB_QSTN_ANS). 예상 Q&A(QA010)와 발표 Q&A(QA020)를 같은 표에 구분코드로 나눠 담는다. (FR-074~076)
 * v4.1부터 화면에서는 질문·답변·관련 요구사항만 받는다. 답변 담당자·관련 목차·일치 여부 컬럼은 남겨 두고 쓰지 않는다.
 */
@Getter
@Setter
public class QnaDto {

    /** 예상 Q&A: 제안 평가 전에 예상 질문과 준비 답변 (FR-074) */
    public static final String SE_EXPECTED = "QA010";
    /** 발표 Q&A: 제안 발표 때 실제로 나온 질문과 답변 (FR-075) */
    public static final String SE_PRESENTED = "QA020";

    private Long qstnAnsSn;
    private Long pbancSn;
    private String qstnAnsSeCd;
    private String qstnCn;
    private String ansCn;
    /** 관련 요구사항 (선택) (FR-074) */
    private Long rqtMttrSn;
    private String wrtrId;
    private Date wrtDt;

    /** 조회 때 함께 가져오는 관련 요구사항 ID·명칭과 그 요구사항이 연결된 목차 */
    private String rqtMttrNo;
    private String rqtMttrNm;
    private Long rqtTocSn;

    public void setQstnAnsSeCd(String qstnAnsSeCd) { this.qstnAnsSeCd = qstnAnsSeCd == null ? null : qstnAnsSeCd.trim(); }

    /** 답변이 아직 없는지 */
    public boolean isUnanswered() {
        return ansCn == null || ansCn.trim().isEmpty();
    }

    /** 발표 Q&A인지 (이전 사업 Q&A 보기에서 구분 표시) */
    public boolean isPresented() {
        return SE_PRESENTED.equals(qstnAnsSeCd);
    }

    /** 검색어가 질문이나 답변에 들어 있는지 (대소문자 구분 없음) (FR-076) */
    public boolean matches(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String k = keyword.toLowerCase();
        return (qstnCn != null && qstnCn.toLowerCase().contains(k))
                || (ansCn != null && ansCn.toLowerCase().contains(k));
    }
}
