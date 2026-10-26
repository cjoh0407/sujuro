package com.ojj.sujuro.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.stats.StatsRows.Tally;
import com.ojj.sujuro.user.CodeOption;

/** 통계 (FR-083 ~ 088): 보는 범위, 수주율 계산, CSV */
class StatsServiceTest {

    private StatsMapper mapper;
    private StatsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(StatsMapper.class);
        service = new StatsService(mapper);
        when(mapper.findYears(any())).thenReturn(Arrays.asList("2026", "2025"));
    }

    private LoginUser user(Role role) {
        return new LoginUser("E1", "홍길동", "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }

    private Tally tally(String label, long won, long lost, long failed) {
        Tally t = new Tally();
        t.setLabel(label);
        t.setWon(won);
        t.setLost(lost);
        t.setFailed(failed);
        t.setJoined(won + lost + failed);
        return t;
    }

    private CodeOption team(String code, String name) {
        CodeOption o = new CodeOption();
        o.setCode(code);
        o.setName(name);
        return o;
    }

    @Test
    void 수주율은_유찰을_빼고_계산한다() {
        Tally t = tally("2026", 2, 1, 5);
        assertEquals(66.7, t.getRate());
        assertEquals("66.7%", t.getRateText());
    }

    @Test
    void 결과가_난_건이_없으면_수주율은_표시하지_않는다() {
        Tally t = tally("2026", 0, 0, 3);
        assertNull(t.getRate());
        assertEquals("-", t.getRateText());
    }

    @Test
    void 경영지원실은_전체_또는_고른_팀을_본다() {
        when(mapper.findDevTeams()).thenReturn(Arrays.asList(team("1", "개발1팀"), team("2", "개발2팀")));
        StatsView all = service.build(user(Role.MGMT), null, null);
        assertNull(all.getSelectedTeamSn());
        assertEquals("전체 개발팀 통계", all.getScopeText());

        StatsView one = service.build(user(Role.MGMT), null, 2L);
        assertEquals(Long.valueOf(2), one.getSelectedTeamSn());
        verify(mapper).byTeam("2026", 2L);
    }

    @Test
    void 없는_팀을_보내면_전체로_본다() {
        when(mapper.findDevTeams()).thenReturn(Collections.singletonList(team("1", "개발1팀")));
        assertNull(service.build(user(Role.MGMT), null, 99L).getSelectedTeamSn());
    }

    @Test
    void 팀장은_화면에서_보낸_팀이_아니라_자기_팀만_본다() {
        when(mapper.findMyTeamSn("E1")).thenReturn(7L);
        StatsView v = service.build(user(Role.LEADER), null, 2L);
        assertEquals(Long.valueOf(7), v.getSelectedTeamSn());
        verify(mapper).byTeam("2026", 7L);
        verify(mapper, never()).byTeam("2026", 2L);
        assertTrue(v.getTeams().isEmpty());
    }

    @Test
    void 소속_팀이_없는_팀장에게는_아무것도_보여_주지_않는다() {
        when(mapper.findMyTeamSn("E1")).thenReturn(null);
        StatsView v = service.build(user(Role.LEADER), null, null);
        assertTrue(v.getYears().isEmpty());
        verify(mapper, never()).findYears(any());
        verify(mapper, never()).byTeam(any(), any());
    }

    @Test
    void 영업부와_팀원은_들어올_수_없다() {
        assertThrows(AccessDeniedException.class, () -> service.build(user(Role.SALES), null, null));
        assertThrows(AccessDeniedException.class, () -> service.build(user(Role.MEMBER), null, null));
    }

    @Test
    void 연도가_이상하면_가장_최근_연도를_쓴다() {
        assertEquals("2026", service.build(user(Role.MGMT), "abcd", null).getYear());
        assertEquals("2026", service.build(user(Role.MGMT), "1999", null).getYear());
        assertEquals("2025", service.build(user(Role.MGMT), "2025", null).getYear());
    }

    @Test
    void 선택한_연도의_요약을_고른다() {
        when(mapper.byYear(any())).thenReturn(Arrays.asList(tally("2026", 1, 1, 0), tally("2025", 3, 1, 0)));
        assertEquals(1L, service.build(user(Role.MGMT), "2026", null).getCurrent().getWon());
        assertEquals(3L, service.build(user(Role.MGMT), "2025", null).getCurrent().getWon());
    }

    @Test
    void CSV는_쉼표_따옴표를_감싸고_수식으로_읽히는_글자를_막는다() {
        assertEquals("\"a,b\"", StatsService.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", StatsService.cell("say \"hi\""));
        assertEquals("'=1+1", StatsService.cell("=1+1"));
        assertEquals("'@SUM(A1)", StatsService.cell("@SUM(A1)"));
        assertEquals("-5", StatsService.cell("-5"));
        assertEquals("", StatsService.cell(null));
    }

    @Test
    void CSV는_화면과_같은_표를_내보낸다() {
        when(mapper.byYear(any())).thenReturn(Collections.singletonList(tally("2026", 1, 1, 0)));
        String csv = service.csv(user(Role.MGMT), "yearly", "2026", null);
        String[] lines = csv.split("\r\n");
        assertEquals("연도,참여,수주,탈락,유찰,수주율,수주 금액,평균 기술점수", lines[0]);
        assertEquals("2026,2,1,1,0,50.0%,0,-", lines[1]);
    }

    @Test
    void 없는_표는_내려받을_수_없다() {
        assertThrows(BusinessException.class, () -> service.csv(user(Role.MGMT), "../etc", null, null));
    }
}
