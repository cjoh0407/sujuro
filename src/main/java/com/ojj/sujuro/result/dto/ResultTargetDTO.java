package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 결과를 기다리는 공고: 제출 완료(PS050)인 공고 (ResultMapper.findResultTargets, findResultTarget) */
@Getter
@Setter
public class ResultTargetDTO {

    private Long pbancSn;
    private String bidPbancNo;
    private String pbancNm;
    /** 기술 배점(비율). 비어 있으면 결과를 등록할 때 입찰공고 API 에서 채운다(10/29 전에 등록한 공고) */
    private BigDecimal techScorScr;
}
