package com.ojj.sujuro.bid.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ojj.sujuro.bid.code.BidCode;

import lombok.Getter;
import lombok.Setter;

/**
 * 발주계획 행을 사전규격 단계로 올릴 때 넘기는 값 (BidNoticeMapper.updateToPreSpec).
 *
 * 필드 이름은 컬럼명을 그대로 따른다. update SQL 이 #{bfSpecRgstNo} 처럼 직접 참조하기 때문이다.
 * {@code fromStepCd} 는 WHERE 에만 쓴다. 지금 단계가 발주계획일 때만 고치게 해서 같은 전이가
 * 두 번 일어나지 않게 한다 (NFR-012 상태 전이 규칙).
 */
@Getter
@Setter
public class PreSpecLinkDTO {

    private Long pbancSn;

    /** 맞춰 낸 사전규격등록번호 (R26BD…) */
    private String bfSpecRgstNo;

    /** 고치기 전 단계. 발주계획(PP010)일 때만 전이한다. */
    private String fromStepCd = BidCode.STEP_ORDER_PLAN;

    /** 고친 뒤 단계. 사전규격(PP020) */
    private String toStepCd = BidCode.STEP_PRE_SPEC;

    /** 사전규격의 사업명. 발주계획 때 받은 이름보다 이쪽이 실제 공고명에 가깝다. */
    private String pbancNm;

    /** 실수요기관명 */
    private String dmndInstNm;

    /** 소프트웨어사업 여부 (Y/N). 발주계획 응답에는 없고 사전규격부터 나온다. */
    private String swBizYn;

    /** 배정예산금액. 사전규격 값으로 덮는다. 바뀌었으면 수집이력에 둘 다 남는다. */
    private BigDecimal altmntBgtAmt;

    /** 의견등록마감일시. 사전규격 단계에서만 있는 값이다. */
    private LocalDateTime opnnDdlnDt;
}
