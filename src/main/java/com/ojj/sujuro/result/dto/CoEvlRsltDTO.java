package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 개찰 목록의 업체 한 곳 (TB_CO_EVL_RSLT). 우리 회사 행과 낙찰자 행에 표시를 단다 */
@Getter
@Setter
public class CoEvlRsltDTO {

    private Long pbancSn;
    private String bizNo;
    private String coNm;
    private Integer opengRnk;
    private Long bidAmt;
    private BigDecimal techEvlScr;
    /** 차등점수제 적용 전 기술 원점수 (techEvlNaturVal) */
    private BigDecimal techEvlOrgnlScr;
    private BigDecimal prcEvlScr;
    private BigDecimal snthsEvlScr;
    private String ourCoYn;
    private String scsbdYn;
}
