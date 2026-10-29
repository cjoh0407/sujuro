package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.EvaluationScoreDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsArticleDTO;
import com.ojj.sujuro.result.dto.StatsChartDTO;
import com.ojj.sujuro.result.dto.StatsTallyDTO;
import com.ojj.sujuro.result.dto.StatsTeamDTO;
import com.ojj.sujuro.result.dto.StatsViewDTO;
import com.ojj.sujuro.result.mapper.StatsMapper;

/** 통계 범위 규칙 (FR-016): 경영지원실은 개발팀을 골라 보고, 팀장은 자기 팀만, 그 밖은 403 */
class StatsServiceImplTest {

    private StatsMapper mapper;
    private StatsServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(StatsMapper.class);
        service = new StatsServiceImpl(mapper);
        when(mapper.findDevTeams()).thenReturn(Arrays.asList(team(3L, "개발1팀"), team(4L, "개발2팀")));
        when(mapper.findYears(any())).thenReturn(Arrays.asList("2025", "2026"));
        when(mapper.findDevTeamSnOf("2024-0031")).thenReturn(3L);
    }

    @Test
    void 경영지원실은_고른_팀을_보고_없는_팀이면_첫_팀() {
        StatsViewDTO v = service.build(user("2022-0009", Role.MGMT), 4L, "2025");
        assertEquals(4L, v.getTeamSn());
        assertEquals("2025", v.getYear());
        assertEquals(2, v.getTeams().size());

        StatsViewDTO w = service.build(user("2022-0009", Role.MGMT), 999L, "abcd");
        assertEquals(3L, w.getTeamSn());
        assertEquals("2026", w.getYear()); // 잘못된 연도는 가장 최근 연도
    }

    @Test
    void 팀장은_다른_팀_번호를_보내도_자기_팀만() {
        StatsViewDTO v = service.build(user("2024-0031", Role.LEADER), 4L, null);
        assertEquals(3L, v.getTeamSn());
        assertEquals(1, v.getTeams().size());
        verify(mapper).history(3L);
        verify(mapper, never()).history(4L);
    }

    @Test
    void 개발팀이_아닌_팀장은_잠금_안내만() {
        StatsViewDTO v = service.build(user("9999-0001", Role.LEADER), null, null);
        assertTrue(v.isLocked());
        verify(mapper, never()).tallyByYearTeam(any());
    }

    @Test
    void 영업부와_팀원은_403() {
        assertThrows(AccessDeniedException.class, () -> service.build(user("2023-0067", Role.SALES), null, null));
        assertThrows(AccessDeniedException.class, () -> service.csv(user("2025-0001", Role.MEMBER)));
    }

    @Test
    void 팀장은_다른_팀_사업의_결과_분석을_열_수_없다() {
        StatsArchiveDTO other = new StatsArchiveDTO();
        other.setTeamSn(4L);
        when(mapper.findArchive(10L)).thenReturn(other);
        assertThrows(AccessDeniedException.class, () -> service.findArchive(user("2024-0031", Role.LEADER), 10L));
        assertThrows(AccessDeniedException.class, () -> service.findFinalFile(user("2024-0031", Role.LEADER), 10L));
        verify(mapper, never()).findFinalFile(anyLong());

        assertNotNull(service.findArchive(user("2022-0009", Role.MGMT), 10L));
        when(mapper.findArchive(11L)).thenReturn(null);
        assertNull(service.findArchive(user("2022-0009", Role.MGMT), 11L));
    }

    @Test
    void 요약은_고른_팀_연도의_집계이고_없으면_0() {
        StatsTallyDTO t = StatsTallyDTO.empty("2026", 3L, "개발1팀");
        t.setJoined(5);
        t.setResultCnt(3);
        t.setWon(2);
        t.setFailed(1);
        t.setWonAmt(1_590_000_000L);
        when(mapper.tallyByYearTeam(any())).thenReturn(Collections.singletonList(t));

        StatsViewDTO v = service.build(user("2022-0009", Role.MGMT), 3L, "2026");
        assertEquals("66.7%", v.getSummary().getRateText());
        assertEquals("15.9억", v.getSummary().getWonAmtText());
        assertEquals(1, v.getSummary().getLost());

        StatsViewDTO w = service.build(user("2022-0009", Role.MGMT), 4L, "2026");
        assertEquals(0, w.getSummary().getJoined());
        assertEquals("-", w.getSummary().getRateText());
        verify(mapper).ptcpShpRates(eq(4L), anyString());
    }

    @Test
    void 공고에_배점이_있으면_그_값이_만점이다() {
        StatsArchiveDTO a = new StatsArchiveDTO();
        a.setTechScorScr(80.0);
        a.setPrcScorScr(20.0);
        a.setArticles(Arrays.asList(article(70), article(30))); // 평가표 배점 합 100 은 무시
        assertEquals(80, a.getTechMax());
        assertEquals(20, a.getPrcMax());
        assertFalse(a.isTechMaxAssumed());
        assertEquals("기술 80 : 가격 20", a.getScoringText());
        assertEquals("15 / 20", scoreText(a, 15));

        a.setTechEvlOrgnlScr(85.284);
        assertEquals("85.284", a.getTechOrgnlText());
    }

    @Test
    void 점수는_소수_셋째_자리까지_보여_준다() {
        StatsArchiveDTO a = new StatsArchiveDTO();
        a.setTechEvlScr(85.284);
        a.setPrcEvlScr(10.0);
        assertEquals("85.284 / 90", a.getScores().get(0).getText());
        assertEquals("10 / 10", a.getScores().get(1).getText());
        assertEquals("- / 100", a.getScores().get(2).getText());
    }

    @Test
    void 사업별_결과_분석은_평가표_파일과_입력_칸을_같이_준다() {
        StatsArchiveDTO mine = new StatsArchiveDTO();
        mine.setTeamSn(3L);
        when(mapper.findArchive(8L)).thenReturn(mine);
        when(mapper.countOurCompany(8L)).thenReturn(1);
        when(mapper.findEvaluationScores(8L)).thenReturn(Arrays.asList(
                saved(1L, "전략 및 방법론", "15", 1, "14.5"), saved(1L, "전략 및 방법론", "15", 2, "14"),
                saved(2L, "기술 및 기능", "30", 1, "27.9"), saved(2L, "기술 및 기능", "30", 2, "28.4")));

        StatsArchiveDTO a = service.findArchive(user("2024-0031", Role.LEADER), 8L);
        assertTrue(a.isOurCompanyListed());
        EvaluationForm f = a.getEvaluationForm();
        assertEquals(2, f.getCmsnrCount());
        assertEquals("전략 및 방법론", f.getItems().get(0).getNm());
        assertEquals("15", f.getItems().get(0).getMax());
        assertEquals("28.4", f.getItems().get(1).scoreAt(1));

        // 저장된 게 없으면 빈 줄 5개, 위원 3명
        assertEquals(5, EvaluationForm.fromSaved(Collections.emptyList()).getItems().size());
        assertEquals(3, EvaluationForm.fromSaved(null).getCmsnrCount());

        // 팀장도 같은 범위 규칙: 다른 팀 사업의 평가표는 내려받을 수 없다
        StatsArchiveDTO other = new StatsArchiveDTO();
        other.setTeamSn(4L);
        when(mapper.findArchive(10L)).thenReturn(other);
        assertThrows(AccessDeniedException.class, () -> service.findEvaluationFile(user("2024-0031", Role.LEADER), 10L));
    }

    private static EvaluationScoreDTO saved(Long sn, String nm, String max, int cmsnr, String scr) {
        EvaluationScoreDTO s = new EvaluationScoreDTO();
        s.setEvlArtclSn(sn);
        s.setEvlArtclNm(nm);
        s.setEvlScorScr(new BigDecimal(max));
        s.setCmsnrNo(cmsnr);
        s.setEvlScr(new BigDecimal(scr));
        return s;
    }

    @Test
    void CSV는_모든_연도와_팀을_빈_칸까지_쓰고_수식은_막는다() {
        when(mapper.tallyByYearTeam(any())).thenReturn(Collections.emptyList());
        String csv = service.csv(user("2022-0009", Role.MGMT));
        assertEquals(5, csv.split("\r\n").length); // 머리 1 + 2년 × 2팀
        assertTrue(csv.contains("2025,개발2팀,0,0,0,0,0,,0,"));

        assertEquals("'=1+1", StatsServiceImpl.cell("=1+1"));
        assertEquals("-3", StatsServiceImpl.cell("-3"));
        assertEquals("\"a,b\"", StatsServiceImpl.cell("a,b"));
    }

    @Test
    void 기술_만점은_배점_합이_100_미만일_때만_평가표에서() {
        StatsArchiveDTO a = new StatsArchiveDTO();
        assertEquals(90, a.getTechMax());
        assertTrue(a.isTechMaxAssumed());

        a.setArticles(Arrays.asList(article(50), article(30)));
        assertEquals(80, a.getTechMax());
        assertEquals("9.8 / 20", scoreText(a, 9.8));

        a.setArticles(Arrays.asList(article(70), article(30)));
        assertEquals(90, a.getTechMax());
        assertTrue(a.isTechMaxAssumed());
    }

    @Test
    void 차트_눈금은_가장_큰_참여의_짝수_올림_최소_4() {
        StatsTallyDTO t = StatsTallyDTO.empty("2026", 3L, "개발1팀");
        t.setJoined(7);
        StatsChartDTO c = StatsChartDTO.of(Arrays.asList("2025", "2026"), Collections.singletonMap("2026", t), "2026");
        assertEquals(8, c.getGridLines().get(2).getLabel());
        assertEquals("", c.getGroups().get(0).getBars().get(0).getPath()); // 2025 참여 0건은 막대 없이 숫자만
        assertTrue(c.getGroups().get(1).isSelected());
    }

    private static String scoreText(StatsArchiveDTO a, double price) {
        a.setPrcEvlScr(price);
        return a.getScores().get(1).getText();
    }

    private static StatsArticleDTO article(double max) {
        StatsArticleDTO a = new StatsArticleDTO();
        a.setMaxSum(max);
        a.setScoreSum(max * 0.8);
        return a;
    }

    private static StatsTeamDTO team(Long sn, String nm) {
        StatsTeamDTO t = new StatsTeamDTO();
        t.setTeamSn(sn);
        t.setTeamNm(nm);
        return t;
    }

    private static LoginUser user(String empNo, Role role) {
        return new LoginUser(empNo, empNo, "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }
}
