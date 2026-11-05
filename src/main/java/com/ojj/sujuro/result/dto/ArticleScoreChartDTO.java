package com.ojj.sujuro.result.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;

/**
 * 사업별 결과 분석의 평가 항목별 점수 그래프 (11/05). 서버에서 좌표를 계산하고 stats/fragments/result-parts :: articleChart 는 SVG 로 그리기만 한다.
 *
 * x축은 평가 항목이고, 항목마다 업체 수만큼 막대를 놓는다. 막대 순서는 ArticleScoreTableDTO 의 업체 순서다(우리 회사 먼저, 나머지는 개찰 순위 순).
 * 세로축은 하나이고 0 부터 시작한다. 항목마다 1등 막대만 진하고 나머지는 흐리다(css/stats.css 의 .co-bar). 같은 점수면 함께 진하다.
 * 색은 업체 순서로 정해서 모든 항목에서 같고, 우리 회사는 항상 --accent 다. 다섯 색을 다 쓰면 여섯째 경쟁 업체부터 회색이다.
 * 값은 막대 위에 쓰고, 막대가 좁으면 1등과 우리 회사 값만 겹치지 않게 쓴다. 마우스를 올리면 업체·항목·점수·등수가 뜬다(title).
 */
@Getter
public class ArticleScoreChartDTO {

    private static final double WIDTH = 760;   // stats/fragments/result-parts 의 viewBox 와 같다
    private static final double HEIGHT = 300;
    private static final double LEFT = 30;     // 세로축 눈금 글자 자리
    private static final double RIGHT = 6;
    private static final double TOP = 20;      // 가장 긴 막대 위 값 자리
    private static final double BOTTOM = 54;   // 항목 이름 두 줄과 배점 한 줄 자리
    private static final double PLOT = HEIGHT - TOP - BOTTOM;
    private static final double BASELINE = TOP + PLOT;
    private static final double MAX_BAR = 30;
    private static final double BAR_GAP = 2;
    private static final double GROUP_FILL = 0.74; // 항목 칸에서 막대 묶음이 차지하는 폭
    private static final double RADIUS = 3;
    private static final double VALUE_CHAR = 5.2;  // 값 글자 한 자 폭 (9.5px 숫자)
    private static final int RIVAL_COLORS = 5;     // css/stats.css 의 --co-1 ~ --co-5

    private final List<Company> companies = new ArrayList<>();
    private final List<GridLine> gridLines = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final String axisLeft = f(LEFT);
    private final String axisRight = f(WIDTH - RIGHT);
    private final String tickX = f(LEFT - 6);
    private final String nameY = f(BASELINE + 16);
    private String ariaLabel;

    public static ArticleScoreChartDTO of(ArticleScoreTableDTO t) {
        ArticleScoreChartDTO c = new ArticleScoreChartDTO();
        int rival = 0;
        for (ArticleScoreTableDTO.Column col : t.getColumns()) {
            String cls = "co-ours";
            if (!col.isOurs()) {
                rival++;
                cls = rival <= RIVAL_COLORS ? "co-" + rival : "co-etc";
            }
            c.companies.add(new Company(col.getCoNm(), col.isOurs(), cls, col.getTotalText(), col.getTotalRank()));
        }

        double scale = 0;
        int valueChars = 0;
        for (ArticleScoreTableDTO.Row row : t.getRows()) {
            scale = Math.max(scale, row.getMax());
            for (ArticleScoreTableDTO.Cell cell : row.getCells()) {
                if (cell.getScore() != null) {
                    scale = Math.max(scale, cell.getScore());
                    valueChars = Math.max(valueChars, cell.getText().length());
                }
            }
        }
        double step = stepOf(scale);
        scale = scale <= 0 ? step * 5 : Math.ceil(scale / step) * step;
        for (double v = 0; v <= scale + step / 2; v += step) {
            c.gridLines.add(new GridLine(yOf(v, scale), StatsFormat.score(v)));
        }

        int items = t.getRows().size();
        int cos = c.companies.size();
        double groupWidth = (WIDTH - LEFT - RIGHT) / Math.max(1, items);
        double bar = Math.min(MAX_BAR, (groupWidth * GROUP_FILL - BAR_GAP * (cos - 1)) / Math.max(1, cos));
        double all = bar * cos + BAR_GAP * (cos - 1);
        double pitch = bar + BAR_GAP;
        double valueWidth = valueChars * VALUE_CHAR;

        StringJoiner aria = new StringJoiner(". ", "평가 항목별 점수. ", "");
        for (int i = 0; i < items; i++) {
            ArticleScoreTableDTO.Row row = t.getRows().get(i);
            double cx = LEFT + groupWidth * i + groupWidth / 2;
            Group g = new Group(row.getCatNm(), row.getMaxText(), cx, wrap(row.getCatNm(), groupWidth - 6));
            StringJoiner line = new StringJoiner(", ", row.getCatNm() + "(배점 " + row.getMaxText() + "): ", "");
            boolean[] labelled = labelled(row.getCells(), c.companies, pitch, valueWidth);
            for (int j = 0; j < cos; j++) {
                Company co = c.companies.get(j);
                ArticleScoreTableDTO.Cell cell = row.getCells().get(j);
                double x = cx - all / 2 + pitch * j;
                String title = co.coNm + " · " + row.getCatNm() + " " + cell.getText() + " / " + row.getMaxText()
                        + (cell.getRank() != null ? " · " + cell.getRank() + "등" : "");
                g.bars.add(new Bar(co, x, bar, cell.getScore(), scale, isFirst(cell), labelled[j] ? cell.getText() : null, title));
                line.add(co.coNm + " " + cell.getText());
            }
            c.groups.add(g);
            aria.add(line.toString());
        }
        c.ariaLabel = aria.toString();
        return c;
    }

    /** 이 항목 1등인지. 같은 점수면 모두 1등이고, 업체가 하나뿐이면 등수가 없어도 진하게 그린다 */
    private static boolean isFirst(ArticleScoreTableDTO.Cell cell) {
        return cell.getScore() != null && (cell.getRank() == null || cell.getRank() == 1);
    }

    /** 값을 쓸 막대. 1등이 먼저, 다음은 우리 회사, 막대가 넉넉하면 나머지도 쓴다. 앞서 고른 값과 글자가 겹치면 쓰지 않는다 */
    private static boolean[] labelled(List<ArticleScoreTableDTO.Cell> cells, List<Company> cos, double pitch, double valueWidth) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (isFirst(cells.get(i))) {
                order.add(i);
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            if (!order.contains(i) && (cos.get(i).ours || pitch >= valueWidth)) {
                order.add(i);
            }
        }
        boolean[] on = new boolean[cells.size()];
        for (int i : order) {
            boolean clear = true;
            for (int j = 0; j < on.length; j++) {
                clear &= !on[j] || Math.abs(i - j) * pitch >= valueWidth;
            }
            on[i] = clear;
        }
        return on;
    }

    /** 눈금 간격. 눈금이 다섯 칸을 넘지 않는 가장 작은 값이다. 값이 없으면 2 */
    private static double stepOf(double max) {
        for (double s : new double[] {1, 2, 5, 10, 20, 25, 50, 100, 200, 500}) {
            if (max / s <= 5) {
                return max <= 0 ? 2 : s;
            }
        }
        return Math.ceil(max / 5);
    }

    private static double yOf(double value, double top) {
        return TOP + PLOT - value / top * PLOT;
    }

    /** 항목 이름을 칸 폭에 맞춰 두 줄까지 나눈다. 넘치면 둘째 줄 끝을 … 로 줄인다 (전체 이름은 title 로 볼 수 있다) */
    static List<String> wrap(String name, double maxWidth) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : name.trim().split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (widthOf(next) <= maxWidth) {
                line = next;
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line);
            }
            while (widthOf(word) > maxWidth && word.length() > 1) {
                int cut = fit(word, maxWidth);
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line = word;
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        if (lines.size() <= 2) {
            return lines;
        }
        String rest = String.join(" ", lines.subList(1, lines.size()));
        List<String> two = new ArrayList<>();
        two.add(lines.get(0));
        two.add(rest.substring(0, fit(rest, maxWidth - widthOf("…"))) + "…");
        return two;
    }

    /** 폭 안에 들어가는 글자 수. 적어도 한 글자 */
    private static int fit(String s, double maxWidth) {
        int n = 0;
        double w = 0;
        while (n < s.length()) {
            w += widthOf(s.substring(n, n + 1));
            if (w > maxWidth) {
                break;
            }
            n++;
        }
        return Math.max(1, n);
    }

    /** 11.5px 글자의 대략의 폭. 한글은 넓고 영문·숫자·기호는 좁다 */
    private static double widthOf(String s) {
        double w = 0;
        for (char ch : s.toCharArray()) {
            w += ch == ' ' ? 3.5 : ch < 128 || ch == '·' ? 6.5 : 11.5;
        }
        return w;
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 범례 한 칸: 업체와 종합 점수 */
    @Getter
    public static class Company {
        private final String coNm;
        private final boolean ours;
        private final String cls;
        private final String totalText;
        /** 종합 점수 등수. 업체가 하나뿐이면 null */
        private final Integer totalRank;

        Company(String coNm, boolean ours, String cls, String totalText, Integer totalRank) {
            this.coNm = coNm;
            this.ours = ours;
            this.cls = cls;
            this.totalText = totalText;
            this.totalRank = totalRank;
        }
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

    /** 평가 항목 하나의 막대 묶음 */
    @Getter
    public static class Group {
        private final String catNm;
        private final String maxText;
        private final String labelX;
        /** 항목 이름 줄 (한두 줄) */
        private final List<String> nameLines;
        private final List<Bar> bars = new ArrayList<>();

        Group(String catNm, String maxText, double cx, List<String> nameLines) {
            this.catNm = catNm;
            this.maxText = maxText;
            this.labelX = f(cx);
            this.nameLines = nameLines;
        }
    }

    /** 막대 하나. path 가 빈 문자열이면 점수가 없거나 0 이다. value 가 null 이면 값을 쓰지 않는다. hit 은 마우스를 받는 넓은 칸 */
    @Getter
    public static class Bar {
        private final String cls;
        /** 이 항목 1등. 이 막대만 진하게 그린다 */
        private final boolean first;
        private final String path;
        private final String value;
        private final String labelX;
        private final String labelY;
        private final String hitX;
        private final String hitY;
        private final String hitWidth;
        private final String hitHeight;
        private final String title;

        Bar(Company co, double x, double width, Double score, double scale, boolean first, String value, String title) {
            double y = score == null ? BASELINE : yOf(score, scale);
            this.cls = co.cls;
            this.first = first;
            this.path = pathOf(x, y, width);
            this.value = score == null ? null : value;
            this.labelX = f(x + width / 2);
            this.labelY = f(y - 4);
            this.hitX = f(x - BAR_GAP / 2);
            this.hitY = f(TOP);
            this.hitWidth = f(width + BAR_GAP);
            this.hitHeight = f(PLOT);
            this.title = title;
        }

        /** 윗모서리만 둥근 막대 */
        private static String pathOf(double x, double y, double width) {
            double h = BASELINE - y;
            if (h <= 0) {
                return "";
            }
            double r = Math.min(RADIUS, Math.min(h, width / 2));
            double x1 = x + width;
            return "M" + f(x) + "," + f(BASELINE) + " V" + f(y + r)
                    + " Q" + f(x) + "," + f(y) + " " + f(x + r) + "," + f(y)
                    + " H" + f(x1 - r)
                    + " Q" + f(x1) + "," + f(y) + " " + f(x1) + "," + f(y + r)
                    + " V" + f(BASELINE) + " Z";
        }
    }
}
