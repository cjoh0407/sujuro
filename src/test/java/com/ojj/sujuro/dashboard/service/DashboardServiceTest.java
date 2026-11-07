package com.ojj.sujuro.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.dashboard.dto.DashboardRows;
import com.ojj.sujuro.dashboard.dto.DashboardRows.Bid;
import com.ojj.sujuro.dashboard.dto.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderCounts;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MyToc;
import com.ojj.sujuro.dashboard.dto.DashboardRows.ResultWait;
import com.ojj.sujuro.dashboard.dto.DashboardRows.Ring;
import com.ojj.sujuro.dashboard.dto.DashboardRows.SalesCounts;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TodoItem;
import com.ojj.sujuro.dashboard.dto.DashboardView;
import com.ojj.sujuro.dashboard.mapper.DashboardMapper;
import com.ojj.sujuro.notification.dto.NotificationDto;
import com.ojj.sujuro.notification.mapper.NotificationInboxMapper;
import com.ojj.sujuro.schedule.service.ScheduleService;

/** 대시보드 (FR-089, 090): 역할별 카드·링크·목록 범위 */
class DashboardServiceTest {

    private DashboardMapper mapper;
    private NotificationInboxMapper inbox;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DashboardMapper.class);
        inbox = mock(NotificationInboxMapper.class);
        // 미니 캘린더는 일정관리(ScheduleService)에서 가져온다. 여기서는 빈 달(null)로 둔다
        service = new DashboardService(mapper, inbox, mock(ScheduleService.class));
    }

    private LoginUser user(String empNo, Role role) {
        return new LoginUser(empNo, empNo, "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }

    // ---------------------------------------------------------------- 영업부

    @Test
    void 영업부는_카드_3개를_숫자만_보여_준다() {
        SalesCounts c = new SalesCounts();
        c.setAssignWait(2);
        c.setAwaitingTeam(1);
        c.setDueSoon(4);
        when(mapper.salesCounts()).thenReturn(c);

        DashboardView v = service.build(user("S1", Role.SALES));

        assertEquals("SALES", v.getRoleKey());
        assertEquals(3, v.getStats().size());
        assertEquals("배정 대기", v.getStats().get(0).getLabel());
        assertEquals(2, v.getStats().get(0).getValue());
        assertEquals(4, v.getStats().get(2).getValue());
        verify(mapper).teamStats(null, false); // 전체 팀
        assertTrue(v.getTeamLoad().getTeams().isEmpty()); // 팀 현황 데이터가 없으면 팀 막대 없음
        assertEquals(2, v.getTeamLoad().getUnassigned());   // 도넛의 미배정 = 배정 대기 카드 숫자
        assertTrue(v.getMiniCal().isCountMode()); // 모든 팀 일정이라 점 대신 건수
    }

    @Test
    void 영업부_처리할_공고는_쿼리가_준_목록_전체() {
        SalesCounts c = new SalesCounts();
        when(mapper.salesCounts()).thenReturn(c);
        List<Bid> bids = new ArrayList<>();
        int[] days = {-1, 0, 1, 2, 5, 9};
        for (int d : days) {
            Bid b = new Bid();
            b.setDday(d);
            bids.add(b);
        }
        when(mapper.salesTodo()).thenReturn(bids);

        DashboardView v = service.build(user("S1", Role.SALES));

        assertEquals(6, v.getTodoBids().size()); // 목록 전체는 스크롤로 보여 준다
    }

    // ---------------------------------------------------------------- 경영지원실

    private DocRow doc(int total, int missing, boolean registered, Integer dday) {
        DocRow r = new DocRow();
        r.setDocTotal(total);
        r.setMissingRequired(missing);
        r.setRegistered(registered);
        r.setDday(dday);
        return r;
    }

    @Test
    void 경영지원실은_누락_등록_대기_결과_대기_마감_임박을_센다() {
        when(mapper.docRows()).thenReturn(Arrays.asList(
                doc(3, 0, false, 10),  // 등록 대기
                doc(3, 2, false, 2),   // 필수 누락 + 마감 임박
                doc(3, 1, false, 20),  // 필수 누락
                doc(3, 0, true, 1),    // 이미 등록 (마감 임박에서 제외)
                doc(0, 0, false, 1))); // 서류 없음 + 마감 임박
        ResultWait rw = new ResultWait();
        rw.setCnt(5);
        rw.setMaxDays(12);
        when(mapper.resultWait()).thenReturn(rw);

        DashboardView v = service.build(user("G1", Role.MGMT));

        assertEquals("MGMT", v.getRoleKey());
        assertEquals(4, v.getStats().size());
        assertEquals(3, v.getStats().get(0).getValue());   // 필수 서류 누락 (2+1)
        assertEquals(1, v.getStats().get(1).getValue());   // 나라장터 등록 대기
        assertEquals(5, v.getStats().get(2).getValue());   // 결과 가져오기 대기
        assertEquals("제출 마감 임박", v.getStats().get(3).getLabel());
        assertEquals(2, v.getStats().get(3).getValue());   // 마감 3일 이내 + 서류 미완료
        assertTrue(v.getMainChart().isLine());
        assertTrue(v.getMiniCal().isCountMode());
    }

    @Test
    void 경영지원실은_결과_대기_데이터가_없어도_오류가_없다() {
        when(mapper.resultWait()).thenReturn(null);

        DashboardView v = service.build(user("G1", Role.MGMT));

        assertEquals(0, v.getStats().get(2).getValue());
        assertTrue(v.getDocRows().isEmpty());
        assertNull(v.getWinTotal());
    }

    // ---------------------------------------------------------------- 팀장

    private LeaderProject project(long sn, int rqtTotal, int rqtMapped) {
        LeaderProject p = new LeaderProject();
        p.setPbancSn(sn);
        p.setRqtTotal(rqtTotal);
        p.setRqtMapped(rqtMapped);
        return p;
    }

    @Test
    void 팀장은_자기_범위로_조회한다() {
        LeaderCounts c = new LeaderCounts();
        c.setDecisionWait(3);
        c.setInProgress(2);
        c.setUnmapped(4);
        c.setDueSoon(1);
        when(mapper.leaderCounts("L1")).thenReturn(c);
        when(mapper.leaderProjects("L1")).thenReturn(Arrays.asList(project(10, 5, 5), project(11, 6, 4)));

        DashboardView v = service.build(user("L1", Role.LEADER));

        assertEquals("LEADER", v.getRoleKey()); // 진행 중 사업: 링크 없음
        verify(mapper).leaderTodos("L1");
        verify(mapper, never()).salesCounts();
    }

    @Test
    void 처리할_일_버튼은_종류마다_갈_곳이_다르다() {
        assertEquals("/notices/7", todo(TodoItem.DECISION).getHref());
        assertEquals("/files?bid=7", todo(TodoItem.FINAL).getHref());
        assertEquals("/docs?bid=7", todo(TodoItem.DOCS).getHref());
        assertEquals("/proposal?bid=7&view=unmapped", todo(TodoItem.RQT).getHref());
        assertEquals("/results?bid=7", todo(TodoItem.RESULT).getHref());
    }

    private TodoItem todo(String kind) {
        TodoItem t = new TodoItem();
        t.setKind(kind);
        t.setPbancSn(7L);
        return t;
    }

    @Test
    void 작은_원은_0으로_나누지_않는다() {
        Ring none = new Ring(0, 0);
        assertEquals("-", none.getText());
        assertTrue(none.isEmpty());
        assertEquals(0, none.getPercent());
        Ring r = new Ring(2, 9);
        assertEquals("2/9", r.getText());
        assertEquals(22, r.getPercent());
        assertEquals(100, new Ring(12, 9).getPercent()); // 넘쳐도 100
    }

    // ---------------------------------------------------------------- 팀원

    @Test
    void 팀원은_내_담당_목차로_숫자_카드를_만든다() {
        MyToc a = new MyToc();
        a.setTocStatusCd("TS020");
        a.setDday(3);
        a.setDdlnText("2026-11-06 10:00");
        MyToc b = new MyToc();
        b.setTocStatusCd("TS030");
        b.setDday(20);
        b.setDdlnText("2026-11-23 10:00");
        when(mapper.myTocs("M1")).thenReturn(Arrays.asList(a, b));

        DashboardView v = service.build(user("M1", Role.MEMBER));

        assertEquals("MEMBER", v.getRoleKey());
        assertEquals(2, v.getStats().get(0).getValue()); // 내 담당 목차
        assertEquals(1, v.getStats().get(1).getValue()); // 작성 중
        assertEquals(1, v.getStats().get(2).getValue()); // 검토 완료
        assertEquals(1, v.getStats().get(3).getValue()); // 마감 7일 이내
        for (DashboardView.Stat s : v.getStats()) {
        }
        verify(mapper).memberProjects("M1");
    }

    @Test
    void 팀원_내_할_일_버튼은_검토_완료면_목차_보기_아니면_파일_등록() {
        MyToc done = new MyToc();
        done.setPbancSn(5L);
        done.setTocStatusCd("TS030");
        MyToc doing = new MyToc();
        doing.setPbancSn(5L);
        doing.setTocStatusCd("TS020");

        assertEquals("목차 보기", done.getActionLabel());
        assertEquals("/proposal?bid=5", done.getActionHref());
        assertEquals("파일 등록", doing.getActionLabel());
        assertEquals("/files?bid=5", doing.getActionHref());
    }

    // ---------------------------------------------------------------- 알림 · 공통 표시

    @Test
    void 최근_알림은_여덟_건만_불러온다() {
        when(inbox.findPage("M1", 0, 8)).thenReturn(Arrays.asList(
                new NotificationDto(), new NotificationDto(), new NotificationDto(), new NotificationDto(),
                new NotificationDto(), new NotificationDto(), new NotificationDto(), new NotificationDto()));

        assertEquals(8, service.build(user("M1", Role.MEMBER)).getAlerts().size());
    }

    @Test
    void 알림이_없어도_오류가_없다() {
        when(inbox.findPage("M1", 0, 8)).thenReturn(Collections.<NotificationDto>emptyList());

        assertTrue(service.build(user("M1", Role.MEMBER)).getAlerts().isEmpty());
    }

    @Test
    void 마감_표시는_남은_일수에_따라_달라진다() {
        assertEquals("D-3", DashboardRows.ddayLabel(3));
        assertEquals("오늘 마감", DashboardRows.ddayLabel(0));
        assertEquals("마감 지남", DashboardRows.ddayLabel(-1));
        assertEquals("-", DashboardRows.ddayLabel(null));
        assertEquals("D-day", DashboardRows.ddayChip(0));
        assertEquals("지남", DashboardRows.ddayChip(-2));
    }

    @Test
    void 마감_색은_3일_이내_빨강_7일_이내_갈색_그_밖_파랑() {
        assertEquals("danger", DashboardRows.ddayTone(-1));
        assertEquals("danger", DashboardRows.ddayTone(3));
        assertEquals("warning", DashboardRows.ddayTone(4));
        assertEquals("warning", DashboardRows.ddayTone(7));
        assertEquals("accent", DashboardRows.ddayTone(8));
        assertEquals("neutral", DashboardRows.ddayTone(null));
    }

    @Test
    void 날짜와_금액_글자() {
        assertEquals("11/05 (목) 10:00", DashboardRows.shortDate("2026-11-05 10:00"));
        assertEquals("엉뚱한 글자", DashboardRows.shortDate("엉뚱한 글자"));
        assertEquals("", DashboardRows.shortDate(null));
        assertEquals("12.4억", DashboardRows.eok(1_240_000_000L));
        assertEquals("-", DashboardRows.eok(0));
    }

    @Test
    void 제출_마감_임박은_등록_전_서류_미완료_마감_3일_이내만() {
        assertTrue(doc(3, 1, false, 3).isDueSoonUnready());
        assertTrue(doc(0, 0, false, 0).isDueSoonUnready());
        assertFalse(doc(3, 1, false, 4).isDueSoonUnready());   // 4일 남음
        assertFalse(doc(3, 1, false, -1).isDueSoonUnready());  // 이미 지남
        assertFalse(doc(3, 0, false, 1).isDueSoonUnready());   // 서류 다 됨 → 등록 대기
        assertFalse(doc(3, 1, true, 1).isDueSoonUnready());    // 이미 등록
        assertFalse(doc(3, 1, false, null).isDueSoonUnready());
    }
}
