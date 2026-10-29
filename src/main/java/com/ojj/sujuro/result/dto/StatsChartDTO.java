package com.ojj.sujuro.result.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import lombok.Getter;

/**
 * 연도별 참여·수주 막대 차트의 좌표 (FR-083). 서버에서 계산하고 stats/index.html 은 SVG 로 그리기만 한다.
 *
 * 연도마다 막대 두 개(참여·수주)를 나란히 둔다. 세로축은 하나이고 0 부터 시작한다.
 * 막대 위에 건수를 쓰고, 막대에 마우스를 올리면 "2026년 참여 3건" 이 뜬다(title).
 * 막대 색은 css/stats.css 의 --chart-join · --chart-won 이다.
 */
@Getter
public class StatsChartDTO {

    public static final int WIDTH = 300;
    public static final int HEIGHT = 210;
    private static final double LEFT = 26;    // 세로축 눈금 글자 자리
    private static final double RIGHT = 8;
    private static final double TOP = 16;     // 가장 긴 막대 위 건수 자리
    private static final double BOTTOM = 28;  // 연도 글자 자리
    private static final double PLOT = HEIGHT - TOP - BOTTOM;
    private static final double BAR = 14;
    private static final double BAR_GAP = 6;  // 같은 연도 두 막대 사이
    private static final double RADIUS = 3;   // 막대 윗모서리만 둥글게

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
     * @param selected   지금 고른 연도. 연도 글자를 강조한다
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
            c.gridLines.add(new GridLine(c.yOf(v, max), v));
        }

        double groupWidth = (WIDTH - LEFT - RIGHT) / Math.max(1, years.size());
        StringJoiner aria = new StringJoiner(", ", "연도별 참여·수주 건수: ", "");
        for (int i = 0; i < years.size(); i++) {
            String y = years.get(i);
            StatsTallyDTO t = byYear.get(y);
            int joined = t == null ? 0 : t.getJoined();
            int won = t == null ? 0 : t.getWon();
            double cx = LEFT + groupWidth * i + groupWidth / 2;

            YearGroup g = new YearGroup(y + "년", cx, y.equals(selected));
            g.bars.add(c.bar("s-join", cx - BAR_GAP / 2 - BAR, cx - groupWidth / 2, groupWidth / 2, joined, max, y + "년 참여 " + joined + "건"));
            g.bars.add(c.bar("s-won", cx + BAR_GAP / 2, cx, groupWidth / 2, won, max, y + "년 수주 " + won + "건"));
            c.groups.add(g);
            aria.add(y + "년 참여 " + joined + "건 수주 " + won + "건");
        }
        c.ariaLabel = aria.toString();
        return c;
    }

    private double yOf(int value, int max) {
        return TOP + PLOT - (double) value / max * PLOT;
    }

    private Bar bar(String cls, double x, double hitX, double hitWidth, int value, int max, String title) {
        double top = yOf(value, max);
        double h = BASELINE - top;
        String path = "";
        if (h > 0) {
            double r = Math.min(RADIUS, Math.min(h, BAR / 2));
            double x1 = x + BAR;
            path = "M" + f(x) + "," + f(BASELINE) + " V" + f(top + r)
                    + " Q" + f(x) + "," + f(top) + " " + f(x + r) + "," + f(top)
                    + " H" + f(x1 - r)
                    + " Q" + f(x1) + "," + f(top) + " " + f(x1) + "," + f(top + r)
                    + " V" + f(BASELINE) + " Z";
        }
        return new Bar(cls, path, value, f(x + BAR / 2), f(top - 4), f(hitX), f(hitWidth), f(TOP), f(PLOT), title);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 세로축 눈금 한 줄 */
    @Getter
    public static class GridLine {
        private final String y;
        private final int label;

        GridLine(double y, int label) {
            this.y = f(y);
            this.label = label;
        }
    }

    /** 한 연도의 막대 묶음 */
    @Getter
    public static class YearGroup {
        private final String label;
        private final String labelX;
        private final boolean selected;
        private final List<Bar> bars = new ArrayList<>();

        YearGroup(String label, double cx, boolean selected) {
            this.label = label;
            this.labelX = f(cx);
            this.selected = selected;
        }
    }

    /** 막대 하나. path 가 빈 문자열이면 0 건이라 막대 없이 숫자만 쓴다. hit 은 마우스를 받는 넓은 투명 칸 */
    @Getter
    public static class Bar {
        private final String cls;
        private final String path;
        private final int value;
        private final String labelX;
        private final String labelY;
        private final String hitX;
        private final String hitWidth;
        private final String hitY;
        private final String hitHeight;
        private final String title;

        Bar(String cls, String path, int value, String labelX, String labelY,
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
