package com.ojj.sujuro.result.dto;

import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.result.code.ResultCode;
import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/** 지난 사업 한 줄 (FR-087). 결과 등록·유찰 사업이고, 누르면 사업 이력 관리 오른쪽에 그 사업을 보여 준다. */
@Getter
@Setter
public class StatsHistoryDTO {

    private Long pbancSn;
    private String pbancNm;
    private String orderInstNm;
    private String teamNm;
    private String pbancSttsCd;
    private Long budget;
    private String rsltCd;
    private String rsltNm;

    public boolean isWon() {
        return ResultCode.WON.equals(rsltCd);
    }

    /** 목록의 알약 글자: 수주·탈락, 결과가 없으면 진행상태(유찰 등) */
    public String getPillText() {
        return rsltNm != null ? rsltNm : BidCode.progressNm(pbancSttsCd);
    }

    public String getBudgetText() {
        return StatsFormat.eok(budget);
    }
}
