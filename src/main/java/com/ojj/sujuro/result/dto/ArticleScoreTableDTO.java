package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;

/**
 * 사업별 결과 분석의 평가 항목별 점수 (11/03 수정사항). 화면에는 표 대신 그래프로 그린다 (11/05, ArticleScoreChartDTO).
 * 평가표 엑셀을 올린 업체가 열(우리 회사 먼저, 나머지는 개찰 순위 순), 평가 항목(대분류)이 행이다.
 * 칸은 위원 평균 점수와 그 항목에서의 등수, 맨 아래 줄은 종합 점수(항목 점수 합)와 등수다.
 * 종합 점수 1등 업체를 수주 업체로 보여 준다. 업체가 하나뿐이면 등수와 수주 업체를 매기지 않는다.
 * 등수는 점수를 소수 셋째 자리로 반올림해 비교하고, 같은 점수는 같은 등수다(1, 1, 3).
 */
@Getter
public class ArticleScoreTableDTO {

    private final List<Column> columns = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    /** 배점 합. 예: "100" */
    private String maxTotalText;

    /** 업체 한 곳 (열) */
    @Getter
    public static class Column {
        private final String coNm;
        private final boolean ours;
        private double total;
        private String totalText;
        /** 종합 점수 등수. 업체가 하나뿐이면 null */
        private Integer totalRank;

        Column(CompanyCategoryScoreDTO s) {
            this.coNm = s.getCoNm();
            this.ours = s.isOurs();
        }

        /** 종합 점수 1등 (수주 업체로 표시) */
        public boolean isTop() {
            return totalRank != null && totalRank == 1;
        }
    }

    /** 평가 항목 한 줄 */
    @Getter
    public static class Row {
        private final String catNm;
        private final double max;
        private final String maxText;
        private final List<Cell> cells = new ArrayList<>();

        Row(String catNm, double max) {
            this.catNm = catNm;
            this.max = max;
            this.maxText = StatsFormat.score(max);
        }
    }

    /** 항목 × 업체 한 칸 */
    @Getter
    public static class Cell {
        /** 위원 평균 점수. 그 업체가 이 항목 점수가 없으면 null */
        private final Double score;
        /** 보여 줄 점수. 그 업체가 이 항목 점수가 없으면 "-" */
        private final String text;
        /** 이 항목 등수. 업체가 하나뿐이거나 점수가 없으면 null */
        private final Integer rank;

        Cell(Double score, Integer rank) {
            this.score = score;
            this.text = StatsFormat.score(score);
            this.rank = rank;
        }
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    /** 화면에 그릴 그래프 좌표 */
    public ArticleScoreChartDTO getChart() {
        return ArticleScoreChartDTO.of(this);
    }

    /** 등수를 매길 수 있는지 (업체 둘 이상) */
    public boolean isRanked() {
        return columns.size() >= 2;
    }

    /** 종합 점수 1등 업체들 (같은 점수면 여럿). 등수를 매길 수 없으면 빈 목록 */
    public List<Column> getWinners() {
        return columns.stream().filter(Column::isTop).collect(Collectors.toList());
    }

    /** 수주 업체 이름. 1등이 여럿이면 쉼표로 잇는다. 없으면 null */
    public String getWinnerNm() {
        List<Column> w = getWinners();
        return w.isEmpty() ? null : w.stream().map(Column::getCoNm).collect(Collectors.joining(", "));
    }

    public String getWinnerTotalText() {
        List<Column> w = getWinners();
        return w.isEmpty() ? null : w.get(0).getTotalText();
    }

    /** @param scores 업체 × 대분류 점수 (StatsMapper.companyArticleScores, 업체 순서대로) */
    public static ArticleScoreTableDTO of(List<CompanyCategoryScoreDTO> scores) {
        ArticleScoreTableDTO t = new ArticleScoreTableDTO();
        Map<Long, Column> cols = new LinkedHashMap<>();
        Map<Long, Map<String, Double>> byCompany = new HashMap<>();
        Map<String, CompanyCategoryScoreDTO> cats = new LinkedHashMap<>();
        for (CompanyCategoryScoreDTO s : scores) {
            cols.computeIfAbsent(s.getCoEvlRsltSn(), k -> new Column(s));
            byCompany.computeIfAbsent(s.getCoEvlRsltSn(), k -> new HashMap<>()).put(s.getCatNm(), s.getScoreSum());
            cats.merge(s.getCatNm(), s, (a, b) -> a.getOrd() <= b.getOrd() ? a : b);
        }
        t.columns.addAll(cols.values());
        boolean ranked = t.isRanked();

        List<CompanyCategoryScoreDTO> ordered = new ArrayList<>(cats.values());
        ordered.sort(Comparator.comparingInt(CompanyCategoryScoreDTO::getOrd).thenComparing(CompanyCategoryScoreDTO::getCatNm));
        double maxTotal = 0;
        for (CompanyCategoryScoreDTO c : ordered) {
            Row row = new Row(c.getCatNm(), c.getMaxSum());
            maxTotal += c.getMaxSum();
            List<Double> all = new ArrayList<>();
            for (Long co : cols.keySet()) {
                all.add(byCompany.get(co).get(c.getCatNm()));
            }
            for (Double score : all) {
                row.cells.add(new Cell(score, ranked ? rankOf(score, all) : null));
            }
            t.rows.add(row);
        }
        t.maxTotalText = StatsFormat.score(maxTotal);

        List<Double> totals = new ArrayList<>();
        for (Map.Entry<Long, Column> e : cols.entrySet()) {
            double total = byCompany.get(e.getKey()).values().stream()
                    .filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
            e.getValue().total = total;
            e.getValue().totalText = StatsFormat.score(total);
            totals.add(total);
        }
        if (ranked) {
            for (Column col : t.columns) {
                col.totalRank = rankOf(col.total, totals);
            }
        }
        return t;
    }

    /** 나보다 점수가 높은 업체 수 + 1. 점수가 없으면 null */
    private static Integer rankOf(Double score, Collection<Double> all) {
        if (score == null) {
            return null;
        }
        BigDecimal mine = round3(score);
        return 1 + (int) all.stream().filter(Objects::nonNull).filter(x -> round3(x).compareTo(mine) > 0).count();
    }

    private static BigDecimal round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }
}
