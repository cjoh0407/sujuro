package com.ojj.sujuro.result.dto;

import com.ojj.sujuro.result.ResultCode;
import com.ojj.sujuro.result.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/** 과거 사업 이력 한 줄 (FR-087). 결과가 등록된 사업만, 누르면 사업별 결과 분석으로 간다. */
@Getter
@Setter
public class StatsHistoryDTO {

    private Long pbancSn;
    private String pbancNm;
    private String orderInstNm;
    private String teamNm;
    private Long budget;
    private String rsltCd;
    private String rsltNm;

    /** 팀장이 확정한 최종 제안서(통합본)가 있으면 Y */
    private String finalFileYn;

    public boolean isWon() {
        return ResultCode.WON.equals(rsltCd);
    }

    public boolean isFinalFileKept() {
        return "Y".equals(finalFileYn);
    }

    public String getBudgetText() {
        return StatsFormat.eok(budget);
    }
}
