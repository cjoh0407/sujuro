package com.ojj.sujuro.bid.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 수집이 확인할 공고 한 줄. 단계마다 쓰는 칸이 다르다.
 *
 *   발주계획 → 사전규격 : 기관명·사업명·예산으로 신규 사전규격과 맞춘다 ({@link com.ojj.sujuro.bid.BidCode#sameBizCase})
 *   → 입찰공고          : 사전규격번호(없으면 발주계획번호)로 통합공개 API 에 묻는다
 *   정정·취소           : 입찰공고번호·차수와 마감일·예산 같은 값을 나라장터 최신값과 비교한다
 *   개찰 · 계약         : 입찰공고번호로 묻는다. 개찰은 개찰일이 지난 공고만, 계약은 유찰이 아닌 공고만
 */
@Getter
@Setter
public class TrackingNoticeDTO {

    private Long pbancSn;

    /** 발주계획번호 (R26DD…) */
    private String orderPlanNo;

    /** 사전규격등록번호 (R26BD…). 입찰공고 전환 때 통합공개 API 에 이 번호로 묻는다. */
    private String bfSpecRgstNo;

    /** 입찰공고번호 (R26BK…). 정정·개찰·계약 확인에 쓴다. */
    private String bidPbancNo;

    /** 지금 저장된 입찰공고차수. 나라장터 차수가 이보다 크면 정정이 난 것이다. */
    private Integer bidPbancCycl;

    /** 지금 단계 (PBSTP) */
    private String pbancStepCd;

    /** 공고상태 (PBSTS). 유찰(PS070)이면 계약을 확인하지 않는다. */
    private String pbancSttsCd;

    /** 공고유형 (PBTYP). 취소공고면 개찰을 확인하지 않는다. */
    private String pbancTypeCd;

    /** 사업명. 발주계획 API 의 bizNm 을 넣어 둔 값이다. */
    private String pbancNm;

    /** 발주기관명. 사전규격의 실수요기관명(rlDminsttNm)과 맞춰 본다. */
    private String orderInstNm;

    /** 배정예산금액. 후보가 여럿일 때 하나로 좁히는 데, 그리고 정정 비교에 쓴다. */
    private BigDecimal altmntBgtAmt;

    /** 입찰마감일시·개찰일시. 정정 비교에 쓰고, 개찰일시가 지난 공고만 개찰 결과를 묻는다. */
    private LocalDateTime bidDdlnDt;
    private LocalDateTime opengDt;
}
