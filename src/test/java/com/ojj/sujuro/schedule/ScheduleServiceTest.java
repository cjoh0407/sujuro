package com.ojj.sujuro.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.user.CodeOption;

/** 일정 (FR-091~094): 보는 범위, 내부 일정 권한, 입력 검사 */
class ScheduleServiceTest {

    private ScheduleMapper mapper;
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ScheduleMapper.class);
        service = new ScheduleService(mapper);
    }

    private LoginUser user(String empNo, Role role) {
        return new LoginUser(empNo, empNo, "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }

    private CodeOption team(String sn, String nm) {
        CodeOption c = new CodeOption();
        c.setCode(sn);
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

    @Test
    void 영업부는_고른_팀의_일정을_본다() {
        when(mapper.findDevTeams()).thenReturn(Arrays.asList(team("1", "개발1팀"), team("2", "개발2팀")));
        when(mapper.findAutoEvents(eq(2L), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());
        when(mapper.findInternalEvents(eq(2L), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());

        ScheduleView v = service.month(user("S1", Role.SALES), "2026-10", 2L, null);

        assertEquals(Long.valueOf(2L), v.getSelectedTeamSn());
        assertFalse(v.isCanEdit());
        verify(mapper).findAutoEvents(eq(2L), anyString(), anyString());
    }

    @Test
    void 없는_팀을_고르면_전체로_본다() {
        when(mapper.findDevTeams()).thenReturn(Arrays.asList(team("1", "개발1팀")));
        when(mapper.findAutoEvents(eq((Long) null), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());
        when(mapper.findInternalEvents(eq((Long) null), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());

        ScheduleView v = service.month(user("G1", Role.MGMT), "2026-10", 999L, null);

        assertNull(v.getSelectedTeamSn());
    }

    @Test
    void 팀원은_요청한_팀과_상관없이_자기_팀만_본다() {
        when(mapper.findMyTeamSn("M1")).thenReturn(1L);
        when(mapper.findAutoEvents(eq(1L), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());
        when(mapper.findInternalEvents(eq(1L), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());

        ScheduleView v = service.month(user("M1", Role.MEMBER), "2026-10", 2L, null);

        assertEquals(Long.valueOf(1L), v.getSelectedTeamSn());
        assertFalse(v.isCanEdit());
        verify(mapper, never()).findDevTeams();
    }

    @Test
    void 소속_팀이_없는_팀원에게는_일정을_보여_주지_않는다() {
        when(mapper.findMyTeamSn("M1")).thenReturn(null);

        ScheduleView v = service.month(user("M1", Role.MEMBER), "2026-10", null, null);

        verify(mapper, never()).findAutoEvents(any(), anyString(), anyString());
        verify(mapper, never()).findInternalEvents(any(), anyString(), anyString());
        assertTrue(v.getCells().stream().allMatch(c -> c.getEvents().isEmpty()));
    }

    @Test
    void 팀장만_내부_일정을_관리할_수_있다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findKinds()).thenReturn(Arrays.asList(team("SC010", "제출 마감"), team("SC020", "내부 1차 마감"),
                team("SC030", "내부 2차 마감"), team("SC040", "발표"), team("SC050", "서류 마감")));
        when(mapper.findAutoEvents(eq(1L), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());
        when(mapper.findInternalEvents(eq(1L), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());

        ScheduleView v = service.month(user("L1", Role.LEADER), "2026-10", null, null);

        assertTrue(v.isCanEdit());
        assertEquals(3, v.getEditableKinds().size()); // 내부 1차·2차 마감, 서류 마감
    }

    @Test
    void 팀원은_내부_일정을_추가할_수_없다() {
        assertThrows(AccessDeniedException.class,
                () -> service.add(user("M1", Role.MEMBER), "SC020", "중간 점검", "2026-10-28"));
        verify(mapper, never()).insert(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 팀장은_자기_팀에_내부_일정을_추가한다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);

        service.add(user("L1", Role.LEADER), "SC020", "  중간 점검  ", "2026-10-28");

        verify(mapper).insert(1L, "SC020", "중간 점검", "2026-10-28", "L1");
    }

    @Test
    void 제출_마감과_발표는_직접_만들_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.add(user("L1", Role.LEADER), "SC010", "제출", "2026-10-28"));
        assertThrows(BusinessException.class, () -> service.add(user("L1", Role.LEADER), "SC040", "발표", "2026-10-28"));
    }

    @Test
    void 이름과_날짜를_검사한다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        LoginUser l = user("L1", Role.LEADER);
        assertThrows(BusinessException.class, () -> service.add(l, "SC020", "   ", "2026-10-28"));
        assertThrows(BusinessException.class, () -> service.add(l, "SC020", "점검", "2026-13-40"));
        assertThrows(BusinessException.class, () -> service.add(l, "SC020", "점검", ""));
    }

    @Test
    void 남의_팀_일정은_수정할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.update(9L, 1L, "SC020", "점검", "2026-10-28")).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.edit(user("L1", Role.LEADER), 9L, "SC020", "점검", "2026-10-28"));
    }

    @Test
    void 남의_팀_일정은_삭제할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.delete(9L, 1L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.delete(user("L1", Role.LEADER), 9L));
    }

    @Test
    void 캘린더는_일요일부터_시작하고_날짜별로_일정을_담는다() {
        when(mapper.findDevTeams()).thenReturn(Collections.<CodeOption>emptyList());
        when(mapper.findAutoEvents(eq((Long) null), anyString(), anyString()))
                .thenReturn(new ArrayList<>(Arrays.asList(event("2026-10-29", "제출 마감 · A사업", "SC010", null))));
        when(mapper.findInternalEvents(eq((Long) null), anyString(), anyString()))
                .thenReturn(new ArrayList<>(Arrays.asList(event("2026-10-29", "내부 점검", "SC020", 5L))));

        ScheduleView v = service.month(user("S1", Role.SALES), "2026-10", null, null);

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
    void 자동_일정은_id가_없다() {
        assertTrue(event("2026-10-29", "x", "SC010", null).isAuto());
        assertFalse(event("2026-10-29", "x", "SC020", 1L).isAuto());
    }
}
