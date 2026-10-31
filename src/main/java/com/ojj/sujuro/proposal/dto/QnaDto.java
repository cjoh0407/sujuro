package com.ojj.sujuro.proposal.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 질의응답 한 건 (TB_QSTN_ANS). 예상 Q&A(QA010)와 발표 Q&A(QA020)를 같은 표에 구분코드로 나눠 담는다. (FR-074~076)
 */
@Getter
@Setter
public class QnaDto {

    /** 예상 Q&A: 제안 평가 전에 예상 질문·준비 답변·답변 담당자 (FR-074) */
    public static final String SE_EXPECTED = "QA010";
    /** 발표 Q&A: 제안 발표 때 실제로 나온 질문과 답변 (FR-075) */
    public static final String SE_PRESENTED = "QA020";

    private Long qstnAnsSn;
    private Long pbancSn;
    private String qstnAnsSeCd;
    private String qstnCn;
    private String ansCn;
    /** 답변 담당자 (제안팀원 중 선택) */
    private String answrId;
    /** 관련 목차 (선택) */
    private Long tocSn;
    /** 발표 Q&A가 예상 질문과 일치했는지 (Y/N, 비어 있으면 미확인) */
    private String ccrncYn;
    private String wrtrId;
    private Date wrtDt;

    /** 조회 때 함께 가져오는 이름 */
    private String answrNm;
    private String wrtrNm;
    private String tocNo;
    private String tocNm;

    /** 관련 목차 표시 이름 (번호 포함). 없으면 null */
    public String getTocLabel() {
        if (tocNm == null) {
            return null;
        }
        return tocNo == null || tocNo.isEmpty() ? tocNm : tocNo + " " + tocNm;
    }

    /** 답변이 아직 없는지 */
    public boolean isUnanswered() {
        return ansCn == null || ansCn.trim().isEmpty();
    }
}
