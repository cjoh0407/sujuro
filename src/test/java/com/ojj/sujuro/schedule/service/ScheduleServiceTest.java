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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

/** 일정 (FR-091~094): 보는 범위, 내부 일정 권한, 입력 검사. 지금 시각은 2026-10-20 10:00(한국 시간)으로 고정한다 */
class ScheduleServiceTest {

    private ScheduleMapper mapper;
    private ScheduleService service;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        mapper = mock(ScheduleMapper.class);
        notificationService = mock(NotificationService.class);
        service = new ScheduleService(mapper, notificationService);
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        service.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 20, 10, 0).atZone(seoul).toInstant(), seoul));
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
    void 사업_드롭다운에는_진행_중인_사업만_나온다() {
        when(mapper.findMyPbancs("M1")).thenReturn(Arrays.asList(opt("10", "A사업"), opt("11", "끝난 사업")));
        when(mapper.findMyActivePbancs("M1")).thenReturn(Arrays.asList(opt("10", "A사업")));
        noEvents();

        ScheduleView v = service.month(user("M1", Role.MEMBER), "2026-10", null, null, null);

        assertEquals(1, v.getPickProjects().size());
        assertEquals("A사업", v.getPickProjects().get(0).getName());
        assertEquals("전체 사업", v.getPickTitle());
        assertEquals(2, v.getProjects().size()); // 전체 이력은 그대로 (일정 판단용)
    }

    @Test
    void 끝난_사업이_골라져_들어오면_드롭다운에_지난_사업으로_한_줄_붙인다() {
        when(mapper.findMyPbancs("M1")).thenReturn(Arrays.asList(opt("10", "A사업"), opt("11", "끝난 사업")));
        when(mapper.findMyActivePbancs("M1")).thenReturn(Arrays.asList(opt("10", "A사업")));
        noEvents();

        ScheduleView v = service.month(user("M1", Role.MEMBER), "2026-10", null, 11L, null);

        assertEquals(Long.valueOf(11L), v.getSelectedPbancSn()); // 이력은 볼 수 있다
        assertEquals(2, v.getPickProjects().size());
        assertEquals("끝난 사업 (지난 사업)", v.getPickTitle());
        verify(mapper).findAutoEvents(eq((Long) null), eq("M1"), eq(11L), anyString(), anyString());
    }

    @Test
    void 사업이_모두_끝난_팀원도_지난_일정은_본다() {
        when(mapper.findMyPbancs("M1")).thenReturn(Arrays.asList(opt("11", "끝난 사업")));
        when(mapper.findMyActivePbancs("M1")).thenReturn(Collections.<CodeOption>emptyList());
        noEvents();

        ScheduleView v = service.month(user("M1", Role.MEMBER), "2026-10", null, null, null);

        assertTrue(v.getPickProjects().isEmpty());
        verify(mapper).findAutoEvents(eq((Long) null), eq("M1"), eq((Long) null), anyString(), anyString());
        verify(mapper).findInternalEvents(eq((Long) null), eq("M1"), eq((Long) null), anyString(), anyString());
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
        mine.setPbancSn(10L);
        ScheduleEvent others = event("2026-10-29", "주관팀 점검", "SC020", 6L);
        others.setTeamSn(2L);
        others.setPbancSn(10L);
        ScheduleEvent auto = event("2026-10-29", "제출 마감", "SC010", null);
        auto.setTeamSn(1L);
        when(mapper.findAutoEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(auto)));
        when(mapper.findInternalEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(mine, others)));

        ScheduleView v = service.month(user("L1", Role.LEADER), "2026-10", null, null, null);

        assertTrue(v.isCanEdit());
        assertEquals(1, v.getEditProjects().size());
        assertTrue(mine.isEditable());
        assertTrue(mine.isChangeable());
        assertFalse(others.isEditable()); // 다른 팀(주관팀)이 등록한 일정은 보기만 한다
        assertFalse(auto.isEditable());
    }

    @Test
    void 지난_일정과_끝난_사업의_일정은_삭제만_된다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findMyPbancs("L1")).thenReturn(Arrays.asList(opt("10", "A사업"), opt("11", "끝난 사업")));
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업"))); // 11은 결과 등록으로 끝남
        ScheduleEvent past = event("2026-10-19", "지난 점검", "SC020", 5L);
        past.setTeamSn(1L);
        past.setPbancSn(10L);
        ScheduleEvent closed = event("2026-10-29", "끝난 사업 점검", "SC020", 6L);
        closed.setTeamSn(1L);
        closed.setPbancSn(11L);
        when(mapper.findAutoEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<ScheduleEvent>());
        when(mapper.findInternalEvents(any(), any(), any(), anyString(), anyString())).thenReturn(new ArrayList<>(Arrays.asList(past, closed)));

        service.month(user("L1", Role.LEADER), "2026-10", null, null, null);

        assertTrue(past.isEditable());
        assertFalse(past.isChangeable());
        assertTrue(closed.isEditable());
        assertFalse(closed.isChangeable());
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
    void 참여팀_팀장은_일정을_추가할_수_없다() {
        when(mapper.findMyTeamSn("L2")).thenReturn(2L);
        // 2팀은 20번 사업의 참여팀일 뿐 주관팀이 아니다. 등록할 수 있는 사업(주관 사업)이 없다
        when(mapper.findEditablePbancs(2L)).thenReturn(Collections.<CodeOption>emptyList());

        assertThrows(BusinessException.class,
                () -> service.add(user("L2", Role.LEADER), 20L, "참여팀 점검", "2026-10-28", "09:00"));
        verify(mapper, never()).insert(any(), any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 지난_날짜는_등록할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        LoginUser l = user("L1", Role.LEADER);

        assertThrows(BusinessException.class, () -> service.add(l, 10L, "점검", "2026-10-19", ""));      // 어제
        assertThrows(BusinessException.class, () -> service.add(l, 10L, "점검", "2026-10-20", "09:30")); // 오늘, 지난 시각
        service.add(l, 10L, "점검", "2026-10-20", "");       // 오늘 하루 종일은 된다
        service.add(l, 10L, "점검", "2026-10-20", "10:00");  // 지금 시각은 된다
        service.add(l, 10L, "점검", "2026-10-21", "08:00");  // 내일

        verify(mapper).insert(1L, 10L, "SC020", "점검", "2026-10-20 00:00", "L1");
        verify(mapper).insert(1L, 10L, "SC020", "점검", "2026-10-20 10:00", "L1");
        verify(mapper).insert(1L, 10L, "SC020", "점검", "2026-10-21 08:00", "L1");
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

    private ScheduleEvent mineOn(String date, Long pbancSn) {
        ScheduleEvent e = event(date, "중간 점검", "SC020", 9L);
        e.setTeamSn(1L);
        e.setPbancSn(pbancSn);
        e.setPbancNm("A사업");
        return e;
    }

    @Test
    void 남의_팀_일정은_수정할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        // 남의 팀 일정이면 내 팀 기준으로 찾지 못한다(null)

        assertThrows(BusinessException.class,
                () -> service.edit(user("L1", Role.LEADER), 9L, "점검", "2026-10-28", ""));
        verify(mapper, never()).update(any(), any(), anyString(), anyString());
        verify(notificationService, never()).sendToProposalTeam(any(), anyString(), anyString());
    }

    @Test
    void 수정해도_붙은_사업은_바뀌지_않는다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업"), opt("11", "B사업")));
        when(mapper.findInternalOne(9L, 1L)).thenReturn(mineOn("2026-10-25", 10L));
        when(mapper.update(9L, 1L, "점검", "2026-10-26 00:00")).thenReturn(1);

        service.edit(user("L1", Role.LEADER), 9L, "점검", "2026-10-26", "");

        verify(mapper).update(9L, 1L, "점검", "2026-10-26 00:00");
        verify(notificationService).sendToProposalTeam(eq(10L), eq(NotificationType.SCHEDULE_CHANGED), contains("변경"));
    }

    @Test
    void 지난_일정은_수정할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        when(mapper.findInternalOne(9L, 1L)).thenReturn(mineOn("2026-10-19", 10L));

        assertThrows(BusinessException.class,
                () -> service.edit(user("L1", Role.LEADER), 9L, "점검", "2026-10-26", ""));
        verify(mapper, never()).update(any(), any(), anyString(), anyString());
    }

    @Test
    void 지난_날짜로_옮길_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        when(mapper.findInternalOne(9L, 1L)).thenReturn(mineOn("2026-10-25", 10L));

        assertThrows(BusinessException.class,
                () -> service.edit(user("L1", Role.LEADER), 9L, "점검", "2026-10-19", ""));
        verify(mapper, never()).update(any(), any(), anyString(), anyString());
    }

    @Test
    void 끝난_사업의_일정은_수정할_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업"))); // 11번 사업은 끝남
        when(mapper.findInternalOne(9L, 1L)).thenReturn(mineOn("2026-10-25", 11L));

        assertThrows(BusinessException.class,
                () -> service.edit(user("L1", Role.LEADER), 9L, "점검", "2026-10-26", ""));
        verify(mapper, never()).update(any(), any(), anyString(), anyString());
    }

    @Test
    void 지난_일정도_삭제는_된다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findInternalOne(9L, 1L)).thenReturn(mineOn("2026-10-19", 10L));
        when(mapper.delete(9L, 1L)).thenReturn(1);

        service.delete(user("L1", Role.LEADER), 9L);

        verify(mapper).delete(9L, 1L);
    }

    @Test
    void 캘린더_글자는_마감과_직접_등록을_구분한다() {
        ScheduleEvent auto = event("2026-10-29", "제출 마감 · A사업", "SC010", null);
        auto.setTimeText("10:00");
        auto.setPbancNm("A사업");
        ScheduleEvent mine = event("2026-10-29", "내부 점검", "SC020", 5L);
        mine.setTimeText("14:00");
        ScheduleEvent allDay = event("2026-10-29", "자료 정리", "SC020", 6L);

        assertEquals("[마감] 10:00 A사업", auto.getLabel());
        assertEquals("14:00 내부 점검", mine.getLabel());
        assertEquals("자료 정리", allDay.getLabel());
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

    @Test
    void 일정_이름은_30자까지다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        LoginUser l = user("L1", Role.LEADER);
        String thirty = "가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나";
        assertEquals(30, thirty.length());

        service.add(l, 10L, thirty, "2026-10-28", "");
        assertThrows(BusinessException.class, () -> service.add(l, 10L, thirty + "다", "2026-10-28", ""));
    }

    @Test
    void 끌어서_옮기면_날짜만_바뀌고_이름과_시간은_그대로다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        ScheduleEvent e = mineOn("2026-10-25", 10L);
        e.setTimeText("14:00");
        when(mapper.findInternalOne(9L, 1L)).thenReturn(e);
        when(mapper.update(9L, 1L, "중간 점검", "2026-10-28 14:00")).thenReturn(1);

        service.move(user("L1", Role.LEADER), 9L, "2026-10-28");

        verify(mapper).update(9L, 1L, "중간 점검", "2026-10-28 14:00");
        verify(notificationService).sendToProposalTeam(eq(10L), eq(NotificationType.SCHEDULE_CHANGED), contains("변경"));
    }

    @Test
    void 지난_날짜나_남의_팀_일정은_끌어서_옮길_수_없다() {
        when(mapper.findMyTeamSn("L1")).thenReturn(1L);
        when(mapper.findEditablePbancs(1L)).thenReturn(Arrays.asList(opt("10", "A사업")));
        when(mapper.findInternalOne(9L, 1L)).thenReturn(mineOn("2026-10-25", 10L));
        LoginUser l = user("L1", Role.LEADER);

        assertThrows(BusinessException.class, () -> service.move(l, 9L, "2026-10-19")); // 지난 날짜
        assertThrows(BusinessException.class, () -> service.move(l, 8L, "2026-10-28")); // 우리 팀 일정이 아님
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.move(user("M1", Role.MEMBER), 9L, "2026-10-28"));          // 팀원
        verify(mapper, never()).update(any(), any(), anyString(), anyString());
    }
}
