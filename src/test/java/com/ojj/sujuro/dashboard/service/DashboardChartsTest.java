package com.ojj.sujuro.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ojj.sujuro.dashboard.dto.DashboardRows.TeamStat;
import com.ojj.sujuro.dashboard.dto.DashboardRows.YearStat;
import com.ojj.sujuro.dashboard.dto.DashboardView.Chart;
import com.ojj.sujuro.dashboard.dto.DashboardView.MiniCal;
import com.ojj.sujuro.dashboard.dto.DashboardView.Radar;
import com.ojj.sujuro.schedule.dto.ScheduleEvent;
import com.ojj.sujuro.schedule.dto.ScheduleView;

/** 대시보드 그래프 계산: 레이더·버블 좌표, 0으로 나누지 않기, 다가오는 일정 */
class DashboardChartsTest {

    private static TeamStat team(String nm, int assigned, int inProgress, int submitted) {
        TeamStat t = new TeamStat();
        t.setTeamSn((long) nm.hashCode());
        t.setTeamNm(nm);
        t.setAssigned(assigned);
        t.setInProgress(inProgress);
        t.setSubmitted(submitted);
        return t;
    }

    private static YearStat year(int no, int submitted, int won, int lost, int failed, long wonAmt) {
        YearStat y = new YearStat();
        y.setYearNo(no);
        y.setSubmitted(submitted);
        y.setWon(won);
        y.setLost(lost);
        y.setFailed(failed);
        y.setWonAmt(wonAmt);
        return y;
    }

    @Test
    void 레이더는_팀마다_꼭짓점_눈금은_4겹_가장_많은_팀이_강조() {
        Radar r = DashboardCharts.radar(Arrays.asList(team("B", 0, 1, 0), team("A", 1, 2, 1), team("C", 0, 0, 0)));

        assertFalse(r.isBlank());
        assertEquals(3, r.getPoints().size());
        assertEquals(4, r.getRings().size());
        assertEquals(3, r.getSpokes().size());
        assertEquals(4, r.getRingLabels().size());
        assertEquals("8", r.getRingLabels().get(3).getLabel());           // 최대 4건 → 바깥 눈금 8 (4의 배수, 4보다 큼)
        assertEquals("4건", r.getPoints().get(1).getValueText());
        assertTrue(r.getPoints().get(1).isTop());
        assertFalse(r.getPoints().get(0).isTop());
        assertEquals(3, r.getShape().split(" ").length);
    }

    @Test
    void 레이더는_팀이_3개_미만이거나_없으면_빈_상태() {
        assertTrue(DashboardCharts.radar(null).isBlank());
        assertTrue(DashboardCharts.radar(Collections.<TeamStat>emptyList()).isBlank());
        assertTrue(DashboardCharts.radar(Arrays.asList(team("A", 1, 0, 0), team("B", 1, 0, 0))).isBlank());
    }

    @Test
    void 레이더는_모든_팀이_0건이어도_오류없이_그린다() {
        Radar r = DashboardCharts.radar(Arrays.asList(team("A", 0, 0, 0), team("B", 0, 0, 0), team("C", 0, 0, 0)));
        assertEquals(3, r.getPoints().size());
        assertEquals("4", r.getRingLabels().get(3).getLabel());
        assertFalse(r.getPoints().get(0).isTop());
    }

    @Test
    void 꺾은선은_3년치_점과_올해_요약_작년_대비_표시() {
        Chart c = DashboardCharts.yearLine(Arrays.asList(
                year(2024, 5, 2, 2, 1, 4_930_000_000L),
                year(2025, 6, 3, 2, 1, 11_240_000_000L),
                year(2026, 4, 2, 1, 0, 6_000_000_000L)));

        assertTrue(c.isLine());
        assertEquals(3, c.getPoints().size());
        assertEquals("2024", c.getPoints().get(0).getYear());
        assertTrue(c.getPoints().get(2).isCurrent());                     // 올해만 표시
        assertFalse(c.getPoints().get(0).isCurrent());
        assertEquals("50%", c.getPoints().get(0).getRateText());
        assertEquals("49.3억", c.getPoints().get(0).getAmtText());
        assertEquals("112.4억", c.getPoints().get(1).getAmtText());
        assertEquals(3, c.getRatePoints().split(" ").length);
        assertEquals(3, c.getAmtPoints().split(" ").length);
        assertEquals("제출", c.getKpis().get(0).getLabel());
        assertEquals("4", c.getKpis().get(0).getValue());
        assertEquals("수주율", c.getKpis().get(4).getLabel());
        assertEquals("67", c.getKpis().get(4).getValue());                // 수주 2 ÷ (수주 2 + 탈락 1)
        assertEquals("▲ 7%p", c.getKpis().get(4).getChip());              // 작년 60%
        assertTrue(c.getKpis().get(4).isChipUp());
        assertEquals("▼ 52.4억", c.getKpis().get(5).getChip());           // 112.4억 → 60.0억
        assertFalse(c.getKpis().get(5).isChipUp());
    }

    @Test
    void 꺾은선은_위에_있는_값은_점_위에_아래_값은_점_아래에_적는다() {
        Chart c = DashboardCharts.yearLine(Arrays.asList(
                year(2025, 6, 3, 2, 1, 11_240_000_000L), year(2026, 4, 2, 1, 0, 1_000_000_000L)));
        for (com.ojj.sujuro.dashboard.dto.DashboardView.LineYear p : c.getPoints()) {
            double ry = Double.parseDouble(p.getRateY()), ay = Double.parseDouble(p.getAmtY());
            double rl = Double.parseDouble(p.getRateLabelY()), al = Double.parseDouble(p.getAmtLabelY());
            assertTrue(ry <= ay ? (rl < ry && al > ay) : (rl > ry && al < ay));
        }
    }

    @Test
    void 꺾은선은_결과가_없는_해는_점을_찍지_않고_수주율을_구할_수_없어도_오류_없이_그린다() {
        Chart c = DashboardCharts.yearLine(Arrays.asList(
                year(2024, 0, 0, 0, 0, 0), year(2025, 1, 0, 0, 1, 0), year(2026, 0, 0, 0, 0, 0)));

        assertEquals(3, c.getPoints().size());
        assertTrue(c.getPoints().get(0).isEmpty());
        assertFalse(c.getPoints().get(0).isHasRate());
        assertFalse(c.getPoints().get(0).isHasAmt());
        assertFalse(c.getPoints().get(1).isHasRate());                    // 수주·탈락이 없어 수주율 없음
        assertTrue(c.getPoints().get(1).isHasAmt());
        assertEquals("0억", c.getPoints().get(1).getAmtText());
        assertEquals("", c.getRatePoints());
        assertEquals("-", c.getKpis().get(4).getValue());
        assertNull(c.getKpis().get(4).getChip());
    }

    @Test
    void 꺾은선은_데이터가_전혀_없으면_안내() {
        assertTrue(DashboardCharts.yearLine(Collections.<YearStat>emptyList()).isBlank());
        assertTrue(DashboardCharts.yearLine(null).isBlank());
        assertTrue(DashboardCharts.yearLine(Arrays.asList(year(2025, 0, 0, 0, 0, 0), year(2026, 0, 0, 0, 0, 0))).isBlank());
        assertEquals("연도별 결과가 아직 없습니다.", DashboardCharts.yearLine(null).getEmptyText());
    }

    private static ScheduleView monthWith(String... dates) {
        ScheduleView v = new ScheduleView();
        for (String d : dates) {
            ScheduleEvent e = new ScheduleEvent();
            e.setEventDate(d);
            e.setTitle("일정 " + d);
            e.setTeamSn(3L);
            List<ScheduleEvent> list = new ArrayList<>();
            list.add(e);
            v.getCells().add(new ScheduleView.Cell(Integer.parseInt(d.substring(8)), false, list));
        }
        return v;
    }

    @Test
    void 다가오는_일정은_오늘부터_이번_달_모자라면_다음_달에서_3건() {
        MiniCal m = DashboardCharts.miniCal(monthWith("2026-11-01", "2026-11-03", "2026-11-20"),
                monthWith("2026-12-02", "2026-12-09"), LocalDate.of(2026, 11, 3));

        assertEquals("11월 일정", m.getTitle());
        assertEquals(3, m.getUpcoming().size());
        assertEquals("11/03", m.getUpcoming().get(0).getDate()); // 지난 11/01 은 빠진다
        assertEquals("12/02", m.getUpcoming().get(2).getDate());
        assertEquals(0, DashboardCharts.miniCal(null, null, LocalDate.of(2026, 11, 3)).getUpcoming().size());
        assertTrue(DashboardCharts.miniCal(null, null, LocalDate.of(2026, 11, 3), true).isCountMode());
    }

    @Test
    void 앞에서_n개만_원본은_그대로() {
        List<Integer> all = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), DashboardCharts.top(all, 5));
        assertEquals(7, all.size());
        assertEquals(2, DashboardCharts.top(Arrays.asList(1, 2), 5).size());
        assertTrue(DashboardCharts.top(null, 5).isEmpty());
    }

    @Test
    void 비율은_0으로_나누지_않는다() {
        assertEquals(0, DashboardCharts.percent(0, 0));
        assertEquals(50, DashboardCharts.percent(1, 2));
    }

    private static void assertFalse(boolean b) {
        org.junit.jupiter.api.Assertions.assertFalse(b);
    }
}
