package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고 목록 탭 하나 (BidNoticeMapper.findStatusCounts).
 * 건수가 0인 상태도 탭으로 보여 주기 때문에 TB_COM_CD 를 기준으로 만든다.
 */
@Getter
@Setter
public class StatusCountDTO {

    /** 공고상태코드 (PBSTS) */
    private String sttsCd;
    private String sttsNm;
    private int cnt;
}
