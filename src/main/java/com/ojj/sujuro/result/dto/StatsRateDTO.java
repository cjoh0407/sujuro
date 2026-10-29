package com.ojj.sujuro.result.dto;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/** 사업 유형(SI·유지보수) 또는 참여 형태(주관·컨소시엄 참여·하도급) 한 줄의 수주율 (FR-085) */
@Getter
@Setter
public class StatsRateDTO {

    /** 공통코드 이름. 공고에 값이 없으면 "미지정" */
    private String label;
    private int resultCnt;
    private int won;

    /** 막대 길이(%). 결과가 없으면 0 */
    public double getBarWidth() {
        Double r = StatsFormat.percent(won, resultCnt);
        return r == null ? 0 : r;
    }

    /** 예: "50% (1/2)", 결과가 없으면 "결과 없음" */
    public String getRateText() {
        Double r = StatsFormat.percent(won, resultCnt);
        return r == null ? "결과 없음" : StatsFormat.number(r) + "% (" + won + "/" + resultCnt + ")";
    }
}
