package com.ojj.sujuro.schedule.service;

import com.ojj.sujuro.schedule.dto.ScheduleEvent;
import com.ojj.sujuro.schedule.dto.ScheduleView;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.schedule.mapper.ScheduleMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.access.AccessDeniedException;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.user.dto.CodeOption;

/** 일정 (FR-091~094): 보는 범위, 내부 일정 권한, 입력 검사 */
class ScheduleServiceTest {

    private ScheduleMapper mapper;
    private ScheduleService service;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        mapper = mock(ScheduleMapper.class);
        notificationService = mock(NotificationService.class);
        service = new ScheduleService(mapper, notificationService);
    }

    private LoginUser user(String empNo, Role role) {
        return new LoginUser(empNo, empNo, "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }

    private CodeOption opt(String code, String nm) {
        CodeOption c = new CodeOption();
        c.setCode(code);
        c.setName(nm);
        return c;
    }

    private ScheduleEvent event(String date, String title, String kind, Long id) {
        ScheduleEvent e = new ScheduleEvent();
        e.setEventDate(date);
        e.setTitle(title);
        e.setKindCd(kind);
        e.setId(id);
        return e;
    }

    private void noEvents() {
        when(mapper.findAutoEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());
        when(mapper.findInternalEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());
    }

    @Test
    void 영업부는_고른_팀의_사업_일정을_본다() {
        when(mapper.findDevTeams()).thenReturn(Arrays.asList(opt("1", "공공사업1Div"), opt("2", "공공사업2Div")));
        noEvents();

        ScheduleView v = service.month(user("S1", Role.SALES), "2026-10", 2L, null, null);

        assertEquals(Long.valueOf(2L), v.getSelectedTeamSn());
        assertFalse(v.isCanEdit());
        // 팀 범위만 걸고, 제안팀원 제한(memberId)은 없다
        verify(mapper).findAutoEvents(eq(2L), eq((String) null), eq((Long) null), anyString(), anyString());
    }

    @Test
    void 없는_팀을_고르면_전체로_본다() {
        when(mapper.findDevTeams()).thenReturn(Arrays.asList(opt("1", "공공사업1Div")));
        noEvents();

        ScheduleView v = service.month(user("G1", Role.MGMT), "2026-10", 999L, null, null);

        assertNull(v.getSelectedTeamSn());
    }

    @Test
    void 팀원은_제안팀원으로_들어간_사업만_본다() {
        when(mapper.findMyPbancs("M1")).thenReturn(Arrays.asList(opt("10", "A사업")));
        noEvents();

        ScheduleView v = service.month(user("M1", Role.MEMBER), "2026-10", 2L, null, null);

        assertFalse(v.isCanEdit());
        assertNull(v.getSelectedTeamSn()); // 요청한 팀은 무시한다
        verify(mapper, never()).findDevTeams();
        verify(mapper).findAutoEvents(eq((Long) null), eq("M1"), eq((Long) null), anyString(), anyString());
    }

    @Test
    void 사업별로_고르면_그_사업만_본다_목록에_없는_사업은_전체로_본다() {
        when(mapper.findMyPbancs("M1")).thenReturn(Arrays.asList(opt("10", "A사업"), opt("11", "B사업")));
        noEvents();

        ScheduleView picked = service.month(user("M1", Role.MEMBER), "2026-10", null, 11L, null);
        ScheduleView wrong = service.month(user("M1", Role.MEMBER), "2026-10", null, 99L, null);

        assertEquals(Long.valueOf(11L), picked.getSelectedPbancSn());
        assertNull(wrong.getSelectedPbancSn());
        verify(mapper).findAutoEvents(eq((Long) null), eq("M1"), eq(11L), anyString(), anyString());
    }

    @Test
    void 참여하는_사업이_없는_팀원에게는_일정을_보여_주지_않는다() {
        when(mapper.findMyPbancs("M1")).thenReturn(Collections.<CodeOption>emptyList());

        ScheduleView v = service.month(user("M1", Role.MEMBER), "2026-10", null, null, null);

        verify(mapper, never()).findAutoEvents(any(), any(), any(), anyString(), anyString());
        verify(mapper, never()).findInternalEvents(any(), any(), any(), anyString(), anyString());
        assertTrue(v.getCells().stream().allMatch(c -> c.getEvents().isEmpty()));
    }

    @Test
    void 팀장은_자기_팀이_만든_내부_일정만_고칠_수_있다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findMyPbancs("L1")).thenReturn(Arrays.asList(opt("10", "A사업")));
        when(mapper.findKinds()).thenReturn(Arrays.asList(opt("SC010", "제출 마감"), opt("SC020", "내부 일정"),
                opt("SC030", "내부 2차 마감"), opt("SC040", "발표"), opt("SC050", "서류 마감")));
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        ScheduleEvent mine = event("2026-10-29", "우리 점검", "SC020", 5L);
        mine.setTeamSn(1L);
        ScheduleEvent others = event("2026-10-29", "주관팀 점검", "SC020", 6L);
        others.setTeamSn(2L);
        ScheduleEvent auto = event("2026-10-29", "제출 마감", "SC010", null);
        auto.setTeamSn(1L);
        when(mapper.findAutoEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(auto)));
        when(mapper.findInternalEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(mine, others)));

        ScheduleView v = service.month(user("L1", Role.LEADER), "2026-10", null, null, null);

        assertTrue(v.isCanEdit());
        assertEquals(1, v.getEditProjects().size());
        assertTrue(mine.isEditable());
        assertFalse(others.isEditable()); // 참여팀 팀장은 주관팀 일정을 보기만 한다
        assertFalse(auto.isEditable());
    }

    @Test
    void 팀원은_일정을_추가할_수_없다() {
        assertThrows(AccessDeniedException.class,
                () -> service.add(user("M1", Role.MEMBER), 10L, "중간 점검", "2026-10-28", "14:00"));
        verify(mapper, never()).insert(any(), any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 팀장은_사업을_골라_시간과_함께_추가하고_제안팀원에게_알린다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));

        service.add(user("L1", Role.LEADER), 10L, "  중간 점검  ", "2026-10-28", "14:00");

        verify(mapper).insert(1L, 10L, "SC020", "중간 점검", "2026-10-28 14:00", "L1");
        // 등록한 본인도 받도록 제안팀원 전원에게 보낸다 (제외 없음)
        verify(notificationService).sendToProposalTeam(eq(10L), eq(NotificationType.SCHEDULE_CHANGED), contains("중간 점검"));
    }

    @Test
    void 시간을_비우면_00시로_저장한다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));

        service.add(user("L1", Role.LEADER), 10L, "점검", "2026-10-28", "");

        verify(mapper).insert(1L, 10L, "SC020", "점검", "2026-10-28 00:00", "L1");
    }

    @Test
    void 우리_팀이_주관하지_않는_사업에는_추가할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        LoginUser l = user("L1", Role.LEADER);

        assertThrows(BusinessException.class, () -> service.add(l, 99L, "점검", "2026-10-28", "09:00"));
        assertThrows(BusinessException.class, () -> service.add(l, null, "점검", "2026-10-28", "09:00"));
        verify(mapper, never()).insert(any(), any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 이름_날짜_시간을_검사한다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        LoginUser l = user("L1", Role.LEADER);
        assertThrows(BusinessException.class, () -> service.add(l, 10L, "   ", "2026-10-28", ""));
        assertThrows(BusinessException.class, () -> service.add(l, 10L, "점검", "2026-13-40", ""));
        assertThrows(BusinessException.class, () -> service.add(l, 10L, "점검", "", ""));
        assertThrows(BusinessException.class, () -> service.add(l, 10L, "점검", "2026-10-28", "25:99"));
    }

    @Test
    void 남의_팀_일정은_수정할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        when(mapper.update(9L, 1L, 10L, "점검", "2026-10-28 00:00")).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.edit(user("L1", Role.LEADER), 9L, 10L, "점검", "2026-10-28", ""));
        verify(notificationService, never()).sendToProposalTeam(any(), anyString(), anyString());
    }

    @Test
    void 남의_팀_일정은_삭제할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.delete(9L, 1L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.delete(user("L1", Role.LEADER), 9L));
        verify(notificationService, never()).sendToProposalTeam(any(), anyString(), anyString());
    }

    @Test
    void 삭제하면_제안팀원에게_알린다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        ScheduleEvent before = event("2026-10-28", "중간 점검", "SC020", 9L);
        before.setPbancSn(10L);
        before.setPbancNm("A사업");
        when(mapper.findInternalOne(9L, 1L)).thenReturn(before);
        when(mapper.delete(9L, 1L)).thenReturn(1);

        service.delete(user("L1", Role.LEADER), 9L);

        verify(notificationService).sendToProposalTeam(eq(10L), eq(NotificationType.SCHEDULE_CHANGED), contains("삭제"));
    }

    @Test
    void 캘린더는_일요일부터_시작하고_날짜별로_일정을_담는다() {
        when(mapper.findDevTeams()).thenReturn(Collections.<CodeOption>emptyList());
        when(mapper.findAutoEvents(any(), any(), any(), anyString(), anyString()))
                .thenReturn(new ArrayList<>(Arrays.asList(event("2026-10-29", "제출 마감 · A사업", "SC010", null))));
        when(mapper.findInternalEvents(any(), any(), any(), anyString(), anyString()))
                .thenReturn(new ArrayList<>(Arrays.asList(event("2026-10-29", "내부 점검", "SC020", 5L))));

        ScheduleView v = service.month(user("S1", Role.SALES), "2026-10", null, null, null);

        // 2026-10-01 은 목요일이라 앞에 빈 칸 4개가 있다. 29일은 그 뒤 29번째 칸.
        assertEquals(4 + 31, v.getCells().size());
        assertTrue(v.getCells().get(0).isBlank());
        assertEquals(2, v.getCells().get(4 + 28).getEvents().size());
        assertEquals("2026년 10월", v.getLabel());
        assertEquals("2026-09", v.getPrevYm());
        assertEquals("2026-11", v.getNextYm());
    }

    @Test
    void 일정_구분은_번호로_색을_정한다() {
        assertEquals(1, ScheduleEvent.kindNo("SC010"));
        assertEquals(5, ScheduleEvent.kindNo("SC050"));
        assertEquals(3, ScheduleEvent.kindNo(null));
    }

    @Test
    void 팀_색_번호는_1에서_6이다() {
        assertEquals(0, ScheduleEvent.teamNo(null));
        assertEquals(2, ScheduleEvent.teamNo(1L));
        assertEquals(1, ScheduleEvent.teamNo(6L));
    }

    @Test
    void 자동_일정은_id가_없다() {
        assertTrue(event("2026-10-29", "x", "SC010", null).isAuto());
        assertFalse(event("2026-10-29", "x", "SC020", 1L).isAuto());
    }
}
