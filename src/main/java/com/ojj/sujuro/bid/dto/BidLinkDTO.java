package com.ojj.sujuro.bid.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ojj.sujuro.bid.code.BidCode;

import lombok.Getter;
import lombok.Setter;

/**
 * 입찰공고 값으로 공고 행을 고칠 때 넘기는 값. 두 곳에서 쓴다.
 *   발주계획·사전규격 → 입찰공고 전환 (BidNoticeMapper.updateToBidNotice)
 *   입찰공고 정정·취소 반영          (BidNoticeMapper.updateBidCorrection)
 *
 * 필드 이름은 컬럼명을 그대로 따른다. update SQL 이 #{bidPbancNo} 처럼 직접 참조하기 때문이다.
 * {@code fromTrckSttsCd}, {@code fromBidPbancCycl} 은 WHERE 에만 쓴다. 고치기 전 상태가 맞을 때만 고치게 해서
 * 같은 전이·정정이 두 번 일어나지 않게 한다 (NFR-012 상태 전이 규칙).
 */
@Getter
@Setter
public class BidLinkDTO {

    private Long pbancSn;

    /** 고치기 전 추적상태. 입찰공고 대기(TR010)일 때만 전이한다. */
    private String fromTrckSttsCd = BidCode.TRACK_WAITING;

    /** 고친 뒤 단계. 입찰공고(PP030) */
    private String toStepCd = BidCode.STEP_BID_NOTICE;

    /** 고친 뒤 추적상태. 연결됨(TR020) */
    private String toTrckSttsCd = BidCode.TRACK_LINKED;

    /** 이어 붙인 입찰공고번호 (R26BK…) */
    private String bidPbancNo;

    /** 입찰공고 차수. 정정이 났으면 0보다 크다. */
    private Integer bidPbancCycl;

    /** 정정 반영 때만: 고치기 전에 저장돼 있던 차수. 이 차수일 때만 고친다. */
    private Integer fromBidPbancCycl;

    /**
     * 비어 있던 번호를 채울 때만 값이 있다. 발주계획으로 등록했는데 사전규격 매칭을 거치지 않은 경우
     * 입찰공고 응답에 사전규격번호가 같이 들어 있다. 반대로 사전규격 행에는 발주계획번호를 채운다.
     */
    private String bfSpecRgstNo;
    private String orderPlanNo;

    private String pbancTypeCd;
    private String pbancNm;
    private String orderInstNm;
    private String dmndInstNm;
    private BigDecimal altmntBgtAmt;
    private LocalDateTime bidDdlnDt;
    private LocalDateTime opengDt;
    private String evlMthdNm;
    /** 기술·가격 배점(비율). 입찰공고 API techAbltEvlRt·bidPrceEvlRt */
    private BigDecimal techScorScr;
    private BigDecimal prcScorScr;
    private String pbancDtlUrl;
}
