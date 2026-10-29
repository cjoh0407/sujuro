package com.ojj.sujuro.result.dto;

import com.ojj.sujuro.result.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * 연도·팀 한 칸의 집계 (FR-083, 084). 요약 카드, 연도별 차트, CSV 가 같이 쓴다.
 *
 * 참여 = 참여 확정부터(참여 확정·제안 작성 중·제출 완료·결과 등록·유찰), 결과 = 결과 등록이고 수주·탈락이 있는 공고.
 * 수주율 = 수주 ÷ 결과. 유찰은 결과가 아니라서 수주율에서 빠진다.
 */
@Getter
@Setter
public class StatsTallyDTO {

    private String year;
    private Long teamSn;
    private String teamNm;

    private int joined;      // 참여
    private int resultCnt;   // 결과 등록 (수주 + 탈락)
    private int won;         // 수주
    private int failed;      // 유찰
    private long wonAmt;     // 수주 금액 = 수주한 사업의 낙찰금액 합계 (원)
    private Double avgTech;  // 결과 등록 공고의 평균 기술점수

    /** 해당 칸에 공고가 없을 때 쓰는 빈 집계 */
    public static StatsTallyDTO empty(String year, Long teamSn, String teamNm) {
        StatsTallyDTO t = new StatsTallyDTO();
        t.setYear(year);
        t.setTeamSn(teamSn);
        t.setTeamNm(teamNm);
        return t;
    }

    public int getLost() {
        return resultCnt - won;
    }

    /** 수주율(%). 결과가 없으면 null */
    public Double getRate() {
        return StatsFormat.percent(won, resultCnt);
    }

    /** 예: "66.7%", 결과가 없으면 "-" */
    public String getRateText() {
        Double r = getRate();
        return r == null ? "-" : StatsFormat.number(r) + "%";
    }

    public String getWonAmtText() {
        return StatsFormat.eok(wonAmt);
    }

    /** 예: "82.4", 결과가 없으면 "-" */
    public String getAvgTechText() {
        return StatsFormat.number(StatsFormat.round1(avgTech));
    }
}
