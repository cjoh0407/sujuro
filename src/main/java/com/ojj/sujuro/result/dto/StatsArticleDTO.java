package com.ojj.sujuro.result.dto;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * 평가 항목(대분류) 한 줄의 득점률 (FR-080, 086). 우리 회사 평가표 엑셀이 올라온 공고만 대상이다 (11/03).
 * 득점률 = 우리 회사 득점 합 ÷ 배점 합. 세부 항목의 득점은 평가위원 점수의 평균이다.
 * 평가·결과 입력의 분석 기록과 점수 계산(StatsArchiveDTO)이 쓴다. 사업별 결과 분석의 업체 비교 표는 ArticleScoreTableDTO 다.
 */
@Getter
@Setter
public class StatsArticleDTO {

    /** 득점률이 이 값보다 낮으면 막대를 빨갛게 그린다 */
    public static final double LOW_RATE = 85;

    private String catNm;
    private double scoreSum;
    private double maxSum;
    private int bidCnt;

    public double getRate() {
        Double r = StatsFormat.percent(scoreSum, maxSum);
        return r == null ? 0 : r;
    }

    public boolean isLow() {
        return getRate() < LOW_RATE;
    }

    public String getRateText() {
        return StatsFormat.number(getRate()) + "%";
    }

    /** 잃은 점수 = 배점 - 득점 */
    public double getLoss() {
        return Math.max(0, maxSum - scoreSum);
    }

    /** 화면 표시용: 배점 "15", 득점 "14.5", 손실 "0.5" */
    public String getMaxText() {
        return StatsFormat.score(maxSum);
    }

    public String getScoreSumText() {
        return StatsFormat.score(scoreSum);
    }

    public String getLossText() {
        return StatsFormat.score(getLoss());
    }
}
