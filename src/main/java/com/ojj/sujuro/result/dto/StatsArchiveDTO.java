package com.ojj.sujuro.result.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ojj.sujuro.result.code.ResultCode;
import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * 사업별 결과 분석 (FR-080, 081, 087). 통계의 과거 사업 이력에서 사업명을 누르면 온다.
 * 결과가 등록된 사업만 있다. 점수는 나라장터 평가 결과(TB_BID_RSLT) 기준이다.
 */
@Getter
@Setter
public class StatsArchiveDTO {

    /** 평가표가 없어 기술점수 만점을 알 수 없을 때 쓰는 값. SW 사업 협상에 의한 계약의 기술:가격 = 90:10 */
    public static final double DEFAULT_TECH_MAX = 90;
    private static final double TOTAL_MAX = 100;

    private Long pbancSn;
    private String pbancNm;
    private String orderInstNm;
    private Long teamSn;
    private String teamNm;
    private String bizTypeNm;
    private String ptcpShpNm;
    private Long budget;
    private Date bidDdlnDt;

    private String rsltCd;
    private String rsltNm;
    private Integer evlRnk;
    private Double techEvlScr;
    private Double prcEvlScr;
    private Double snthsEvlScr;

    /** 장단점 분석 (FR-081): 강점 · 탈락·감점 원인 · 차기 보완점 */
    private String strptCn;
    private String sbptCsCn;
    private String splmntCn;

    /** 수행 인력 (FR-082): PM 과 투입 인원 이름(쉼표로 이음) */
    private String pmNm;
    private String inputNms;

    /** 평가 항목(대분류)별 득점률. 평가표가 없으면 빈 목록 */
    private List<StatsArticleDTO> articles = new ArrayList<>();

    /** 최종 제안서. 없으면 null */
    private StatsFileDTO finalFile;

    public boolean isWon() {
        return ResultCode.WON.equals(rsltCd);
    }

    /** 예: "수주 · 1위" */
    public String getResultText() {
        return (rsltNm == null ? "-" : rsltNm) + (evlRnk == null ? "" : " · " + evlRnk + "위");
    }

    public String getBudgetText() {
        return StatsFormat.eok(budget);
    }

    /** PM / 투입 인원. 수행 인력은 수주한 사업에만 기록한다 */
    public String getStaffText() {
        if (!isWon()) {
            return "수주 사업에만 기록";
        }
        if (pmNm == null && inputNms == null) {
            return "기록 없음";
        }
        return (pmNm == null ? "-" : pmNm) + " / " + (inputNms == null ? "-" : inputNms);
    }

    /**
     * 기술점수 만점. 평가표의 배점 합이 기술 만점이다(가격 = 100 - 기술).
     * 평가표가 없거나 배점 합이 100 이상이면(가격 몫이 없어 기술 배점으로 볼 수 없다) 90 으로 본다.
     */
    public double getTechMax() {
        double sum = articles.stream().mapToDouble(a -> a.getMaxSum()).sum();
        return sum > 0 && sum < TOTAL_MAX ? StatsFormat.round1(sum) : DEFAULT_TECH_MAX;
    }

    /** 기술 만점을 평가표에서 못 읽어 90 으로 본 경우. 화면에 그렇게 적는다 */
    public boolean isTechMaxAssumed() {
        double sum = articles.stream().mapToDouble(a -> a.getMaxSum()).sum();
        return !(sum > 0 && sum < TOTAL_MAX);
    }

    /** 점수 막대 세 줄: 기술 · 가격 · 종합 */
    public List<ScoreRow> getScores() {
        double techMax = getTechMax();
        List<ScoreRow> rows = new ArrayList<>();
        rows.add(new ScoreRow("기술점수", techEvlScr, techMax));
        rows.add(new ScoreRow("가격점수", prcEvlScr, TOTAL_MAX - techMax));
        rows.add(new ScoreRow("종합점수", snthsEvlScr, TOTAL_MAX));
        return rows;
    }

    /** 점수 막대 한 줄 */
    @Getter
    public static class ScoreRow {
        private final String label;
        private final String text;
        private final double width;

        ScoreRow(String label, Double score, double max) {
            this.label = label;
            this.text = StatsFormat.number(StatsFormat.round1(score)) + " / " + StatsFormat.number(max);
            Double w = score == null ? null : StatsFormat.percent(score, max);
            this.width = w == null ? 0 : Math.min(100, w);
        }
    }
}
