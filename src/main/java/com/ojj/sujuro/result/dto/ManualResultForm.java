package com.ojj.sujuro.result.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 컨소시엄 참여·하도급 사업의 결과 직접 입력 (11/04, 경영지원실).
 * 나라장터에서 가져올 수 없는 값 중 통계에 필요한 것만 받는다. 순위·점수·평가표는 주관사·원도급사의 것이라 받지 않는다.
 */
@Getter
@Setter
public class ManualResultForm {

    /** 입찰결과코드 (BDRST): 수주·탈락 */
    private String bidRsltCd;

    /** 우리 회사가 받는 금액(원). 수주일 때만. 콤마가 섞여 와도 된다 */
    private String scsbdAmt;
}
