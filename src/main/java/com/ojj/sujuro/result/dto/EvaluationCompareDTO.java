package com.ojj.sujuro.result.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;

/**
 * 업체별 평가표 비교 (평가·결과 입력). 항목마다 업체별 위원 평균 점수와 우리 회사와의 차이를 보여 준다.
 * 평가표 점수를 저장한 업체만 열이 된다. 우리 회사가 첫 열, 나머지는 개찰 순위 순이다.
 * 차이는 "우리 − 그 업체"다 (ResultsViewDTO.Compare 와 같은 방향). 음수면 그 항목에서 우리가 뒤졌다.
 */
@Getter
public class EvaluationCompareDTO {

    private final List<Column> columns = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    /** 업체 한 곳 (열) */
    @Getter
    public static class Column {
        private final String coNm;
        private final Integer rank;
        private final boolean ours;
        private String totalText;
        /** 합계의 우리 − 이 업체. 우리 열이나 우리 점수가 없으면 null */
        private String totalDiff;

        Column(ResultCompanyDTO c) {
            this.coNm = c.getCoNm();
            this.rank = c.getOpengRnk();
            this.ours = c.isOurs();
        }
    }

    /** 평가 항목 한 줄 */
    @Getter
    public static class Row {
        private final String nm;
        private final String maxText;
        private final List<Cell> cells = new ArrayList<>();

        Row(String nm, String maxText) {
            this.nm = nm;
            this.maxText = maxText;
        }
    }

    /** 항목 × 업체 한 칸 */
    @Getter
    public static class Cell {
        /** 위원 평균. 그 업체가 이 항목 점수가 없으면 "-" */
        private final String text;
        /** 우리 − 이 업체. 우리 열이거나 한쪽 점수가 없으면 null */
        private final String diff;
        /** 이 항목에서 우리가 뒤졌는지 */
        private final boolean behind;

        Cell(Double score, Double ours, boolean isOurs) {
            this.text = StatsFormat.score(score);
            this.diff = isOurs ? null : ResultsViewDTO.Compare.diff(ours, score);
            this.behind = !isOurs && score != null && ours != null && ours < score;
        }
    }

    /** 비교할 업체가 둘 이상인지. 하나뿐이면 화면에 비교 표 대신 안내를 보인다 */
    public boolean isComparable() {
        return columns.size() >= 2;
    }

    /**
     * @param companies 개찰 업체 (순위 순, ResultMapper.findCompanies)
     * @param scores    업체별·항목별 평균 (ResultMapper.findCompanyArticleScores, 항목 순서대로)
     */
    public static EvaluationCompareDTO of(List<ResultCompanyDTO> companies, List<CompanyArticleScoreDTO> scores) {
        EvaluationCompareDTO v = new EvaluationCompareDTO();
        Map<Long, Map<Long, Double>> byCompany = new HashMap<>();
        Map<Long, CompanyArticleScoreDTO> articles = new LinkedHashMap<>();
        for (CompanyArticleScoreDTO s : scores) {
            byCompany.computeIfAbsent(s.getCoEvlRsltSn(), k -> new HashMap<>()).put(s.getEvlArtclSn(), s.getAvgScr());
            articles.putIfAbsent(s.getEvlArtclSn(), s);
        }

        // 열: 점수가 있는 업체만. 우리 회사를 맨 앞에
        List<ResultCompanyDTO> cols = new ArrayList<>();
        companies.stream().filter(c -> c.isOurs() && byCompany.containsKey(c.getCoEvlRsltSn())).forEach(cols::add);
        companies.stream().filter(c -> !c.isOurs() && byCompany.containsKey(c.getCoEvlRsltSn())).forEach(cols::add);
        Map<Long, Double> ours = cols.isEmpty() || !cols.get(0).isOurs()
                ? new HashMap<>() : byCompany.get(cols.get(0).getCoEvlRsltSn());

        for (CompanyArticleScoreDTO a : articles.values()) {
            Row row = new Row(a.getEvlArtclNm(),
                    a.getEvlScorScr() == null ? "-" : StatsFormat.score(a.getEvlScorScr().doubleValue()));
            Double o = ours.get(a.getEvlArtclSn());
            for (ResultCompanyDTO c : cols) {
                row.cells.add(new Cell(byCompany.get(c.getCoEvlRsltSn()).get(a.getEvlArtclSn()), o, c.isOurs()));
            }
            v.rows.add(row);
        }

        Double ourTotal = ours.isEmpty() ? null : sum(ours);
        for (ResultCompanyDTO c : cols) {
            Column col = new Column(c);
            Double total = sum(byCompany.get(c.getCoEvlRsltSn()));
            col.totalText = StatsFormat.score(total);
            col.totalDiff = c.isOurs() ? null : ResultsViewDTO.Compare.diff(ourTotal, total);
            v.columns.add(col);
        }
        return v;
    }

    private static Double sum(Map<Long, Double> m) {
        return m.values().stream().filter(x -> x != null).mapToDouble(Double::doubleValue).sum();
    }
}
