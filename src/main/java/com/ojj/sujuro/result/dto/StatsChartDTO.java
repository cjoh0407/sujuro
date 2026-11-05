package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;

/**
 * 통계의 연도별 막대 차트 좌표 (FR-083). 서버에서 계산하고 stats/fragments/result-parts :: barChart 는 SVG 로 그리기만 한다.
 *
 *   of       : 연도별 참여·수주 건수. 연도마다 막대 두 개를 나란히 둔다.
 *   amountOf : 연도별 수주 금액(억 원). 연도마다 막대 하나다 (11/05).
 *
 * 세로축은 하나이고 0 부터 시작한다. 막대 위에 값을 쓰고, 막대에 마우스를 올리면 "2026년 참여 3건" 이 뜬다(title).
 * 막대 색은 css/stats.css 의 --chart-join · --chart-won 이다. 수주 금액도 수주라서 수주와 같은 색이다.
 * 고른 연도(selected)만 선명하고 나머지 연도는 흐리다 (css/stats.css 의 .yg).
 */
@Getter
public class StatsChartDTO {

    public static final int WIDTH = 300;
    public static final int HEIGHT = 210;
    private static final double LEFT = 26;    // 세로축 눈금 글자 자리
    private static final double RIGHT = 8;
    private static final double TOP = 16;     // 가장 긴 막대 위 값 자리
    private static final double BOTTOM = 28;  // 연도 글자 자리
    private static final double PLOT = HEIGHT - TOP - BOTTOM;
    private static final double BAR = 14;
    private static final double BAR_GAP = 6;  // 같은 연도 두 막대 사이
    private static final double AMOUNT_BAR = 20; // 연도마다 하나뿐인 수주 금액 막대
    private static final double RADIUS = 3;   // 막대 윗모서리만 둥글게
    private static final double WON_PER_EOK = 100_000_000d;

    private static final double BASELINE = TOP + PLOT;

    private final List<GridLine> gridLines = new ArrayList<>();
    private final List<YearGroup> groups = new ArrayList<>();
    private final String axisLeft = f(LEFT);
    private final String axisRight = f(WIDTH - RIGHT);
    private final String tickX = f(LEFT - 6);
    private final String yearLabelY = f(HEIGHT - 8);
    private String ariaLabel;

    /**
     * @param years      차트에 그릴 연도 (오래된 순)
     * @param byYear     연도 → 이 팀의 집계. 없는 연도는 0 으로 그린다
     * @param selected   지금 고른 연도. 이 연도만 선명하게 그린다
     */
    public static StatsChartDTO of(List<String> years, Map<String, StatsTallyDTO> byYear, String selected) {
        StatsChartDTO c = new StatsChartDTO();
        int max = 4;
        for (String y : years) {
            StatsTallyDTO t = byYear.get(y);
            if (t != null) {
                max = Math.max(max, t.getJoined());
            }
        }
        max += max % 2; // 가운데 눈금이 정수가 되도록 짝수로 올린다

        for (int v : new int[] {0, max / 2, max}) {
            c.gridLines.add(new GridLine(c.yOf(v, max), String.valueOf(v)));
        }

        double groupWidth = groupWidth(years);
        StringJoiner aria = new StringJoiner(", ", "연도별 참여·수주 건수: ", "");
        for (int i = 0; i < years.size(); i++) {
            String y = years.get(i);
            StatsTallyDTO t = byYear.get(y);
            int joined = t == null ? 0 : t.getJoined();
            int won = t == null ? 0 : t.getWon();
            double cx = LEFT + groupWidth * i + groupWidth / 2;

            YearGroup g = new YearGroup(y, cx, y.equals(selected));
            g.bars.add(c.bar("s-join", cx - BAR_GAP / 2 - BAR, BAR, cx - groupWidth / 2, groupWidth / 2, joined, max,
                    String.valueOf(joined), y + "년 참여 " + joined + "건"));
            g.bars.add(c.bar("s-won", cx + BAR_GAP / 2, BAR, cx, groupWidth / 2, won, max,
                    String.valueOf(won), y + "년 수주 " + won + "건"));
            c.groups.add(g);
            aria.add(y + "년 참여 " + joined + "건 수주 " + won + "건");
        }
        c.ariaLabel = aria.toString();
        return c;
    }

    /**
     * 연도별 수주 금액. 수주한 사업의 낙찰금액 합계(StatsTallyDTO.wonAmt)이고 요약 카드의 수주 금액과 같은 값이다.
     * 매개변수는 {@link #of} 와 같다.
     */
    public static StatsChartDTO amountOf(List<String> years, Map<String, StatsTallyDTO> byYear, String selected) {
        StatsChartDTO c = new StatsChartDTO();
        double maxEok = 0;
        for (String y : years) {
            maxEok = Math.max(maxEok, wonAmtOf(byYear.get(y)) / WON_PER_EOK);
        }
        double top = niceTop(maxEok);

        for (double v : new double[] {0, top / 2, top}) {
            c.gridLines.add(new GridLine(c.yOf(v, top), plain(v)));
        }

        double groupWidth = groupWidth(years);
        StringJoiner aria = new StringJoiner(", ", "연도별 수주 금액: ", "");
        for (int i = 0; i < years.size(); i++) {
            String y = years.get(i);
            long won = wonAmtOf(byYear.get(y));
            String text = StatsFormat.eok(won);
            double cx = LEFT + groupWidth * i + groupWidth / 2;

            YearGroup g = new YearGroup(y, cx, y.equals(selected));
            g.bars.add(c.bar("s-won", cx - AMOUNT_BAR / 2, AMOUNT_BAR, cx - groupWidth / 2, groupWidth, won / WON_PER_EOK, top,
                    won == 0 ? "0" : text.replace("억", ""), y + "년 수주 금액 " + text));
            c.groups.add(g);
            aria.add(y + "년 " + text);
        }
        c.ariaLabel = aria.toString();
        return c;
    }

    private static long wonAmtOf(StatsTallyDTO t) {
        return t == null ? 0 : t.getWonAmt();
    }

    private static double groupWidth(List<String> years) {
        return (WIDTH - LEFT - RIGHT) / Math.max(1, years.size());
    }

    /** 세로축 맨 위 눈금. 가장 큰 값 이상에서 1·2·4·5·10 배수 중 가장 작은 값이라 가운데 눈금도 깔끔하다. 값이 없으면 10 */
    private static double niceTop(double max) {
        if (max <= 0) {
            return 10;
        }
        double unit = Math.pow(10, Math.floor(Math.log10(max)));
        for (double n : new double[] {1, 2, 4, 5}) {
            if (n * unit >= max) {
                return n * unit;
            }
        }
        return 10 * unit;
    }

    /** 눈금 글자. 소수 둘째 자리까지, 뒤의 0 은 뗀다. 예: "25", "0.2" */
    private static String plain(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private double yOf(double value, double max) {
        return TOP + PLOT - value / max * PLOT;
    }

    private Bar bar(String cls, double x, double width, double hitX, double hitWidth, double value, double max, String label, String title) {
        double top = yOf(value, max);
        double h = BASELINE - top;
        String path = "";
        if (h > 0) {
            double r = Math.min(RADIUS, Math.min(h, width / 2));
            double x1 = x + width;
            path = "M" + f(x) + "," + f(BASELINE) + " V" + f(top + r)
                    + " Q" + f(x) + "," + f(top) + " " + f(x + r) + "," + f(top)
                    + " H" + f(x1 - r)
                    + " Q" + f(x1) + "," + f(top) + " " + f(x1) + "," + f(top + r)
                    + " V" + f(BASELINE) + " Z";
        }
        return new Bar(cls, path, label, f(x + width / 2), f(top - 4), f(hitX), f(hitWidth), f(TOP), f(PLOT), title);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 세로축 눈금 한 줄 */
    @Getter
    public static class GridLine {
        private final String y;
        private final String label;

        GridLine(double y, String label) {
            this.y = f(y);
            this.label = label;
        }
    }

    /** 한 연도의 막대 묶음 */
    @Getter
    public static class YearGroup {
        private final String year;
        private final String label;
        private final String labelX;
        private final boolean selected;
        private final List<Bar> bars = new ArrayList<>();

        YearGroup(String year, double cx, boolean selected) {
            this.year = year;
            this.label = year + "년";
            this.labelX = f(cx);
            this.selected = selected;
        }
    }

    /** 막대 하나. path 가 빈 문자열이면 값이 0 이라 막대 없이 숫자만 쓴다. hit 은 마우스를 받는 넓은 투명 칸 */
    @Getter
    public static class Bar {
        private final String cls;
        private final String path;
        private final String value;
        private final String labelX;
        private final String labelY;
        private final String hitX;
        private final String hitWidth;
        private final String hitY;
        private final String hitHeight;
        private final String title;

        Bar(String cls, String path, String value, String labelX, String labelY,
            String hitX, String hitWidth, String hitY, String hitHeight, String title) {
            this.cls = cls;
            this.path = path;
            this.value = value;
            this.labelX = labelX;
            this.labelY = labelY;
            this.hitX = hitX;
            this.hitWidth = hitWidth;
            this.hitY = hitY;
            this.hitHeight = hitHeight;
            this.title = title;
        }
    }
}
