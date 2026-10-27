package com.ojj.sujuro.bid.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 입찰공고를 기다리는 공고 한 줄. 수집이 신규 사전규격과 맞춰 볼 때 쓴다.
 *
 * 발주계획과 사전규격은 서로를 가리키는 번호가 API 에 없어서, 기관명과 사업명으로 맞춰야 한다.
 * ({@link com.ojj.sujuro.bid.BidCode#sameBizCase}) 비교에 쓰는 값만 담는다.
 */
@Getter
@Setter
public class TrackingNoticeDTO {

    private Long pbancSn;

    /** 발주계획번호 (R26DD…) */
    private String orderPlanNo;

    /** 사업명. 발주계획 API 의 bizNm 을 넣어 둔 값이다. */
    private String pbancNm;

    /** 발주기관명. 사전규격의 실수요기관명(rlDminsttNm)과 맞춰 본다. */
    private String orderInstNm;

    /** 배정예산금액. 후보가 여럿일 때 하나로 좁히는 데 쓴다. */
    private BigDecimal altmntBgtAmt;
}
