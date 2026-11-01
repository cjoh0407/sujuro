package com.ojj.sujuro.proposal.dto;

import lombok.Getter;
import lombok.Setter;

/** 이전 사업 Q&A 보기에서 고를 사업건: 내가 제안팀이었고 Q&A가 있는 사업건 (FR-076-1) */
@Getter
@Setter
public class QnaBidDto {

    private Long pbancSn;
    private String bidPbancNo;
    private String pbancNm;
    private int qnaCnt;

    public String getLabel() {
        return pbancNm != null && !pbancNm.isEmpty() ? pbancNm : bidPbancNo;
    }
}
