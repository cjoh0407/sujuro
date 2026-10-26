package com.ojj.sujuro.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.dashboard.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.DashboardRows.LeaderCounts;
import com.ojj.sujuro.dashboard.DashboardRows.SalesCounts;
import com.ojj.sujuro.notification.NotificationDto;
import com.ojj.sujuro.notification.NotificationInboxMapper;

/** 대시보드 (FR-089, 090): 역할별로 보여 줄 항목과 범위 */
class DashboardServiceTest {

    private DashboardMapper mapper;
    private NotificationInboxMapper inbox;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DashboardMapper.class);
        inbox = mock(NotificationInboxMapper.class);
        service = new DashboardService(mapper, inbox);
    }

    private LoginUser user(String empNo, Role role) {
        return new LoginUser(empNo, empNo, "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }

    @Test
    void 영업부는_전체_팀_현황과_배정_대기를_본다() {
        SalesCounts c = new SalesCounts();
        c.setAssignWait(2);
        c.setAwaitingTeam(1);
        when(mapper.salesCounts()).thenReturn(c);

        DashboardView v = service.build(user("S1", Role.SALES));

        assertEquals("SALES", v.getRoleKey());
        assertEquals("배정 대기", v.getStats().get(0).getLabel());
        assertEquals(2, v.getStats().get(0).getValue());
        verify(mapper).teamStats(null, false); // 전체 개발팀
    }

    @Test
    void 팀장은_자기_팀_범위로_조회한다() {
        LeaderCounts c = new LeaderCounts();
        c.setDecisionWait(3);
        c.setUnmapped(4);
        when(mapper.leaderCounts("L1")).thenReturn(c);

        DashboardView v = service.build(user("L1", Role.LEADER));

        assertEquals("LEADER", v.getRoleKey());
        assertEquals(3, v.getStats().get(0).getValue());
        assertEquals(4, v.getStats().get(2).getValue());
        verify(mapper).leaderDecisionWait("L1");
        verify(mapper).teamStats("L1", true); // 내 팀만
        verify(mapper, never()).salesCounts();
    }

    @Test
    void 팀원은_내_담당_목차만_본다() {
        DashboardView v = service.build(user("M1", Role.MEMBER));

        assertEquals("MEMBER", v.getRoleKey());
        assertTrue(v.getStats().isEmpty());
        verify(mapper).myTocs("M1");
        verify(mapper).teamStats("M1", true);
    }

    private DocRow doc(int total, int missing, boolean registered) {
        DocRow r = new DocRow();
        r.setDocTotal(total);
        r.setMissingRequired(missing);
        r.setRegistered(registered);
        return r;
    }

    @Test
    void 경영지원실은_등록_대기와_누락_서류를_센다() {
        when(mapper.docRows()).thenReturn(Arrays.asList(
                doc(3, 0, false),   // 등록 대기
                doc(3, 2, false),   // 필수 누락
                doc(3, 0, true),    // 이미 등록
                doc(0, 0, false))); // 서류 없음
        when(mapper.resultWaitCount()).thenReturn(5);

        DashboardView v = service.build(user("G1", Role.MGMT));

        assertEquals("MGMT", v.getRoleKey());
        assertEquals(1, v.getStats().get(0).getValue()); // 나라장터 등록 대기
        assertEquals(2, v.getStats().get(1).getValue()); // 필수 서류 누락
        assertEquals(5, v.getStats().get(2).getValue()); // 결과 입력 대기
    }

    @Test
    void 최근_알림은_다섯_건까지만_보인다() {
        when(inbox.findMine("M1", false)).thenReturn(Arrays.asList(
                new NotificationDto(), new NotificationDto(), new NotificationDto(),
                new NotificationDto(), new NotificationDto(), new NotificationDto(), new NotificationDto()));

        assertEquals(5, service.build(user("M1", Role.MEMBER)).getAlerts().size());
    }

    @Test
    void 알림이_없어도_오류가_없다() {
        when(inbox.findMine("M1", false)).thenReturn(Collections.<NotificationDto>emptyList());

        assertTrue(service.build(user("M1", Role.MEMBER)).getAlerts().isEmpty());
    }

    @Test
    void 마감_표시는_남은_일수에_따라_달라진다() {
        assertEquals("D-3", DashboardRows.ddayLabel(3));
        assertEquals("오늘 마감", DashboardRows.ddayLabel(0));
        assertEquals("마감 지남", DashboardRows.ddayLabel(-1));
        assertEquals("-", DashboardRows.ddayLabel(null));
    }
}
