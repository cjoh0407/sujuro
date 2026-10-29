package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 우리 회사의 입찰 결과 (TB_BID_RSLT). 개찰 목록의 우리 행과 최종 낙찰자로 만든다 */
@Getter
@Setter
public class BidRsltDTO {

    private Long pbancSn;
    private String bidRsltCd;
    private Integer evlRnk;
    private BigDecimal techEvlScr;
    /** 차등점수제 적용 전 기술 원점수 (techEvlNaturVal) */
    private BigDecimal techEvlOrgnlScr;
    private BigDecimal prcEvlScr;
    private BigDecimal snthsEvlScr;
    /** 수주했을 때만. 최종 낙찰금액 */
    private Long scsbdAmt;
}
