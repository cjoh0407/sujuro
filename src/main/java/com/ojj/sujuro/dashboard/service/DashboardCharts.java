package com.ojj.sujuro.dashboard.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ojj.sujuro.dashboard.dto.DashboardRows;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TeamStat;
import com.ojj.sujuro.dashboard.dto.DashboardRows.YearStat;
import com.ojj.sujuro.dashboard.dto.DashboardView.Chart;
import com.ojj.sujuro.dashboard.dto.DashboardView.Kpi;
import com.ojj.sujuro.dashboard.dto.DashboardView.MiniCal;
import com.ojj.sujuro.dashboard.dto.DashboardView.LineYear;
import com.ojj.sujuro.dashboard.dto.DashboardView.TeamBar;
import com.ojj.sujuro.dashboard.dto.DashboardView.TeamLoad;
import com.ojj.sujuro.dashboard.dto.DashboardView.Upcoming;
import com.ojj.sujuro.schedule.dto.ScheduleEvent;
import com.ojj.sujuro.schedule.dto.ScheduleView;

/**
 * 대시보드 그래프·미니 캘린더 만들기. 숫자를 받아 화면이 그대로 그릴 값(팀별 막대 길이, 꺾은선 좌표)를 만든다.
 * 0으로 나누는 일이 없게 합계가 0이면 그래프 대신 안내 문구를 둔다. 라이브러리는 쓰지 않는다.
 */
final class DashboardCharts {

    // 꺾은선(경영지원실 '연도별 성과'): 560×240 (11/07 가로로 넓혀 칸 폭을 채운다. fragments.html 의 viewBox 와 같아야 한다).
    // 수주율은 0~110%, 수주 금액은 가장 큰 해의 1.2배까지를 각각 자기 크기에 맞춘다
    private static final double LINE_W = 560;
    private static final double LINE_H = 240;
    private static final double LINE_L = 64;
    private static final double LINE_R = 64;
    private static final double LINE_T = 30;
    private static final double LINE_B = 36;

    private DashboardCharts() {
    }

    /**
     * 팀별 진행 현황 (영업부): 도넛(배정/미배정)과 팀별 막대.
     * 팀 값 = 그 팀이 주관하는 공고 중 지금 작업 중인 것(팀장 결정 대기 + 참여 확정·제안 작성 중). 제출 완료·결과 등록은 넣지 않는다.
     * 막대 길이는 가장 많은 팀을 100으로 본다. 모든 팀이 0건이면 막대는 0, 도넛은 미배정만 칠한다.
     */
    static TeamLoad teamLoad(List<TeamStat> stats, int unassigned) {
        TeamLoad t = new TeamLoad();
        t.setUnassigned(Math.max(unassigned, 0));
        int[] vals = new int[stats == null ? 0 : stats.size()];
        int max = 0;
        int sum = 0;
        for (int i = 0; i < vals.length; i++) {
            TeamStat s = stats.get(i);
            vals[i] = s.getAssigned() + s.getInProgress();
            max = Math.max(max, vals[i]);
            sum += vals[i];
        }
        for (int i = 0; i < vals.length; i++) {
            String name = stats.get(i).getTeamNm() == null ? "-" : stats.get(i).getTeamNm();
            t.getTeams().add(new TeamBar(name, vals[i], percent(vals[i], max)));
        }
        t.setAssigned(sum);
        t.setAssignedPct(percent(sum, t.getTotal()));
        return t;
    }

    /**
     * 연도별 성과 (경영지원실): 위쪽 요약(올해 제출·수주·탈락·유찰·수주율·수주 금액, 작년 대비)과 최근 3년 꺾은선.
     * 선 두 개: 수주율(수주 ÷ (수주+탈락))과 수주 금액. 점 옆에 값을 바로 적는다.
     * 두 값은 단위가 달라 각자 자기 크기로 높이를 잡는다. 위에 있는 선의 값은 점 위에, 아래 선의 값은 점 아래에 적어 겹치지 않게 한다.
     * years 는 오래된 해부터 오름차순이고 마지막이 올해다.
     */
    static Chart yearLine(List<YearStat> years) {
        Chart c = new Chart();
        c.setKind("line");
        c.setTitle("최근 3년 성과");
        c.setEmptyText("연도별 결과가 아직 없습니다.");
        if (years == null || years.isEmpty()) {
            return c;
        }
        // 11/07: 제목은 '최근 N년 성과' (연도는 그래프 아래에 이미 적혀 있다)
        c.setTitle("최근 " + years.size() + "년 성과");
        boolean any = false;
        for (YearStat y : years) {
            any = any || y.getSubmitted() > 0 || y.getWon() > 0 || y.getLost() > 0 || y.getFailed() > 0;
        }
        if (!any) {
            return c;
        }
        int n = years.size();
        YearStat cur = years.get(n - 1);
        YearStat prev = n >= 2 ? years.get(n - 2) : null;

        // 위쪽 요약
        c.getKpis().add(new Kpi("제출", String.valueOf(cur.getSubmitted()), "건", null, true));
        c.getKpis().add(new Kpi("수주", String.valueOf(cur.getWon()), "건", null, true));
        c.getKpis().add(new Kpi("실주", String.valueOf(cur.getLost()), "건", null, true));
        c.getKpis().add(new Kpi("유찰", String.valueOf(cur.getFailed()), "건", null, true));
        Integer rate = cur.getRate();
        String rateChip = null;
        boolean rateUp = true;
        if (rate != null && prev != null && prev.getRate() != null) {
            int diff = rate - prev.getRate();
            rateChip = (diff >= 0 ? "▲ " : "▼ ") + Math.abs(diff) + "%p";
            rateUp = diff >= 0;
        }
        c.getKpis().add(new Kpi("수주율", rate == null ? "-" : String.valueOf(rate), rate == null ? "" : "%", rateChip, rateUp));
        double curEok = cur.getWonAmt() / 1e8;
        String amtChip = null;
        boolean amtUp = true;
        if (prev != null && (cur.getWonAmt() > 0 || prev.getWonAmt() > 0)) {
            double diff = curEok - prev.getWonAmt() / 1e8;
            amtChip = (diff >= 0 ? "▲ " : "▼ ") + String.format(Locale.ROOT, "%.1f억", Math.abs(diff));
            amtUp = diff >= 0;
        }
        c.getKpis().add(new Kpi("수주 금액", cur.getWonAmt() > 0 ? String.format(Locale.ROOT, "%.1f", curEok) : "-",
                cur.getWonAmt() > 0 ? "억" : "", amtChip, amtUp));

        double maxEok = 0;
        for (YearStat y : years) {
            maxEok = Math.max(maxEok, y.getWonAmt() / 1e8);
        }
        double axisAmt = maxEok > 0 ? maxEok * 1.2 : 1;
        double bottom = LINE_H - LINE_B;
        double ph = bottom - LINE_T;
        double pw = LINE_W - LINE_L - LINE_R;
        StringBuilder rateLine = new StringBuilder();
        StringBuilder amtLine = new StringBuilder();
        StringBuilder aria = new StringBuilder("연도별 성과: ");
        for (int i = 0; i < n; i++) {
            YearStat y = years.get(i);
            boolean empty = y.getWon() + y.getLost() + y.getFailed() == 0 && y.getSubmitted() == 0;
            Integer r = y.getRate();
            double eok = y.getWonAmt() / 1e8;
            double x = n > 1 ? LINE_L + pw * i / (n - 1) : LINE_W / 2;
            LineYear ly = new LineYear();
            ly.setYear(String.valueOf(y.getYearNo()));
            ly.setX(f(x));
            ly.setYearY(f(LINE_H - 10));
            ly.setCurrent(i == n - 1);
            ly.setEmpty(empty);
            ly.setHasRate(r != null);
            ly.setHasAmt(!empty);
            double ry = r == null ? 0 : bottom - ph * Math.min(r, 110) / 110.0;
            double ay = bottom - ph * eok / axisAmt;
            boolean rateAbove = r != null && !empty && ry <= ay; // 수주율 점이 위에 있다
            if (r != null) {
                ly.setRateY(f(ry));
                ly.setRateText(r + "%");
                ly.setRateLabelY(f(!empty && ry > ay ? ry + 27 : ry - 17));
                rateLine.append(rateLine.length() > 0 ? " " : "").append(f(x)).append(',').append(f(ry));
            }
            if (!empty) {
                ly.setAmtY(f(ay));
                ly.setAmtText(eok > 0 ? String.format(Locale.ROOT, "%.1f억", eok) : "0억");
                ly.setAmtLabelY(f(r != null && rateAbove ? ay + 20 : ay - 12));
                amtLine.append(amtLine.length() > 0 ? " " : "").append(f(x)).append(',').append(f(ay));
            }
            c.getPoints().add(ly);
            aria.append(y.getYearNo()).append("년 수주율 ").append(r == null ? "-" : r + "%")
                .append(", 수주 금액 ").append(String.format(Locale.ROOT, "%.1f억", eok));
            aria.append(i < n - 1 ? " / " : "");
        }
        c.setRatePoints(rateLine.toString());
        c.setAmtPoints(amtLine.toString());
        c.setAria(aria.toString());
        return c;
    }

    /**
     * 미니 캘린더. 이번 달 칸은 일정관리 화면(ScheduleService.month)의 것을 그대로 쓴다(범위·팀 색 같음).
     * 다가오는 일정은 오늘부터 이번 달, 모자라면 다음 달에서 3건.
     */
    static MiniCal miniCal(ScheduleView month, ScheduleView next, LocalDate today) {
        return miniCal(month, next, today, false);
    }

    /** countMode: 날짜마다 팀 색 점 대신 일정 건수 숫자 (영업부·경영지원실은 모든 팀 일정이 보여 점이 너무 많다) */
    static MiniCal miniCal(ScheduleView month, ScheduleView next, LocalDate today, boolean countMode) {
        MiniCal m = new MiniCal();
        // 보는 달 (11/07 화살표로 다른 달을 볼 수 있다). 일정관리 화면이 정한 달을 따르고, 없으면 오늘이 있는 달
        YearMonth ym = month != null && month.getYm() != null ? YearMonth.parse(month.getYm()) : YearMonth.from(today);
        m.setTitle(ym.getYear() + "년 " + ym.getMonthValue() + "월");
        m.setYm(ym.toString());
        m.setPrevYm(ym.minusMonths(1).toString());
        m.setNextYm(ym.plusMonths(1).toString());
        m.setCurrent(ym.equals(YearMonth.from(today)));
        m.setCountMode(countMode);
        if (month != null) {
            m.getCells().addAll(month.getCells());
        }
        List<ScheduleEvent> ahead = new ArrayList<>();
        String todayText = today.toString();
        for (ScheduleView v : new ScheduleView[] {month, next}) {
            if (v == null) {
                continue;
            }
            for (ScheduleView.Cell cell : v.getCells()) {
                for (ScheduleEvent e : cell.getEvents()) {
                    if (e.getEventDate() != null && e.getEventDate().compareTo(todayText) >= 0) {
                        ahead.add(e);
                    }
                }
            }
        }
        for (ScheduleEvent e : ahead) {
            if (m.getUpcoming().size() >= 3) {
                break;
            }
            String d = e.getEventDate();
            m.getUpcoming().add(new Upcoming(d.substring(5, 7) + "/" + d.substring(8, 10), e.getLabel(), e.getTeamNo()));
        }
        return m;
    }


    /** 앞에서 n개. 원본은 그대로 둔다 */
    static <T> List<T> top(List<T> list, int n) {
        if (list == null) {
            return new ArrayList<>();
        }
        return list.size() <= n ? list : new ArrayList<>(list.subList(0, n));
    }

    static int percent(int part, int total) {
        return total <= 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    // ---- 내부 ----

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
