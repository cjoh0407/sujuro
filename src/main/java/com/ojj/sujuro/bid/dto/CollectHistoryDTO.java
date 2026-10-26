package com.ojj.sujuro.bid.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고수집이력 한 행 (TB_PBANC_CLCT_HSTRY).
 * API에서 가져온 값의 스냅샷이고, 한 번 넣으면 고치지 않는다.
 *
 * 필드 이름은 컬럼명을 그대로 따른다. insert SQL 이 #{clctSeq} 처럼 직접 참조하기 때문이다.
 */
@Getter
@Setter
public class CollectHistoryDTO {

    private Long pbancSn;

    /** 공고 안에서 1부터 */
    private int clctSeq;

    private String pbancStepCd;

    /** 그 시점에 조회한 번호 (사전규격번호 또는 입찰공고번호) */
    private String pbancNo;

    private Integer bidPbancCycl;
    private String ntceKindNm;
    private BigDecimal altmntBgtAmt;
    private LocalDateTime bidDdlnDt;
    private LocalDateTime opengDt;

    /** API 응답 원문(JSON). 나중에 원인을 따질 때 쓴다. */
    private String apiRspnsCn;
}
