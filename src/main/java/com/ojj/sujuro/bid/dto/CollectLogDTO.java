package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 수집로그 한 행 (TB_CLCT_LOG). 수집이 한 번 돌 때 공고마다, 그리고 실패했을 때 남긴다.
 * 공고와 상관없는 실패(API 자체가 안 될 때)는 pbancSn 을 비워 둔다.
 */
@Getter
@RequiredArgsConstructor
public class CollectLogDTO {

    /** 대상 공고. 수집 전체가 실패했으면 null */
    private final Long pbancSn;

    /** 실행구분 (EXCSE) - 자동 EX010 / 수동 EX020 */
    private final String excnSeCd;

    /** [지금 수집] 을 누른 사람. 자동 실행이면 null */
    private final String exctrId;

    /** 수집결과 (CLRST) */
    private final String clctRsltCd;

    /** 실패했을 때의 메시지. 성공이면 null */
    private final String errMsgCn;
}
