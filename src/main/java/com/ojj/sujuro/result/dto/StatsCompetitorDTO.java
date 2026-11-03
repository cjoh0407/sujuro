package com.ojj.sujuro.result.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 통계(전체 종합)의 자주 만난 경쟁 업체 한 줄 (StatsMapper.competitors, 11/03).
 * 그 팀·연도에 결과가 등록된 공고의 개찰 업체 중 우리 회사가 아닌 곳이다.
 */
@Getter
@Setter
public class StatsCompetitorDTO {

    private String coNm;
    /** 같은 공고에 들어간 수 */
    private int metCnt;
    /** 그중 개찰 순위가 우리보다 뒤였던 수 */
    private int aheadCnt;
    /** 그중 그 업체가 낙찰자였던 수 */
    private int wonCnt;
}
