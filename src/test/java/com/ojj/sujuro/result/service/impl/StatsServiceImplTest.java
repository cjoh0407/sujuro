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
import com.ojj.sujuro.result.dto.ResultBidDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsArticleDTO;
import com.ojj.sujuro.result.dto.StatsHistoryDTO;
import com.ojj.sujuro.result.dto.StatsTeamDTO;
import com.ojj.sujuro.result.dto.StatsViewDTO;
import com.ojj.sujuro.result.mapper.StatsMapper;

/** 사업 이력 범위 규칙 (FR-016): 경영지원실은 개발팀을 골라 보고, 팀장은 자기 팀만, 그 밖은 403 */
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
        verify(mapper).history(3L, "2026");
        verify(mapper, never()).history(eq(4L), anyString());
        verify(mapper).inProgress(3L, "2026");
        verify(mapper, never()).inProgress(eq(4L), anyString());
    }

    @Test
    void 왼쪽_목록은_고른_연도_것만_부르고_없는_연도면_가장_최근_연도() {
        service.build(user("2022-0009", Role.MGMT), 3L, "2025");
        verify(mapper).history(3L, "2025");
        verify(mapper).inProgress(3L, "2025");

        service.build(user("2022-0009", Role.MGMT), 4L, "1999");
        verify(mapper).history(4L, "2026");
        verify(mapper).inProgress(4L, "2026");
    }

    @Test
    void 왼쪽_목록의_연도는_마감일_연도이고_마감일_없는_진행_사업만_올해다() {
        StatsArchiveDTO a = new StatsArchiveDTO();
        a.setPbancSttsCd("PS040");
        assertEquals(String.valueOf(java.time.Year.now().getValue()), a.getListYear());

        a.setPbancSttsCd("PS060");
        assertNull(a.getListYear());

        a.setBidDdlnDt(java.sql.Date.valueOf("2024-06-30"));
        assertEquals("2024", a.getListYear());
        a.setPbancSttsCd("PS040");
        assertEquals("2024", a.getListYear());
    }

    @Test
    void 개발팀이_아닌_팀장은_잠금_안내만() {
        StatsViewDTO v = service.build(user("9999-0001", Role.LEADER), null, null);
        assertTrue(v.isLocked());
        verify(mapper, never()).history(any(), any());
    }

    @Test
    void 영업부와_팀원은_403() {
        assertThrows(AccessDeniedException.class, () -> service.build(user("2023-0067", Role.SALES), null, null));
        assertThrows(AccessDeniedException.class, () -> service.build(user("2025-0001", Role.MEMBER), null, null));
    }

    @Test
    void 팀장은_다른_팀_사업의_결과_분석을_열_수_없다() {
        StatsArchiveDTO other = new StatsArchiveDTO();
        other.setTeamSn(4L);
        when(mapper.findArchive(10L)).thenReturn(other);
        assertThrows(AccessDeniedException.class, () -> service.findArchive(user("2024-0031", Role.LEADER), 10L));
        assertThrows(AccessDeniedException.class, () -> service.findEvaluationFile(user("2024-0031", Role.LEADER), 10L));
        verify(mapper, never()).findEvaluationFile(anyLong());

        assertNotNull(service.findArchive(user("2022-0009", Role.MGMT), 10L));
        when(mapper.findArchive(11L)).thenReturn(null);
        assertNull(service.findArchive(user("2022-0009", Role.MGMT), 11L));
    }

    @Test
    void 결과가_없는_사업도_다른_팀이면_열_수_없다() {
        StatsArchiveDTO other = new StatsArchiveDTO();
        other.setTeamSn(4L);
        when(mapper.findPendingBid(10L)).thenReturn(other);

        assertThrows(AccessDeniedException.class, () -> service.findPending(user("2024-0031", Role.LEADER), 10L));
        assertNotNull(service.findPending(user("2022-0009", Role.MGMT), 10L));
        assertNull(service.findPending(user("2022-0009", Role.MGMT), 11L));
    }

    @Test
    void 결과가_없는_사업의_안내_문구는_상태에_따른다() {
        StatsArchiveDTO a = new StatsArchiveDTO();
        assertTrue(a.isPending());
        for (String stts : Arrays.asList("PS030", "PS040", "PS050")) {
            a.setPbancSttsCd(stts);
            assertTrue(a.isInProgress(), stts);
            assertEquals("아직 입찰 진행 중인 사업건입니다.", a.getPendingMessage(), stts);
        }
        a.setPbancSttsCd("PS070");
        assertFalse(a.isInProgress());
        assertEquals("유찰된 사업건입니다.", a.getPendingMessage());
        a.setPbancSttsCd("PS060");
        assertFalse(a.isInProgress());
        assertEquals("아직 결과가 입력되지 않은 사업건입니다.", a.getPendingMessage());

        a.setRsltCd("BR010");
        assertFalse(a.isPending());
    }

    @Test
    void 고른_사업이_없을_때_보여_줄_사업은_진행_중이_먼저이고_없으면_지난_사업이다() {
        StatsViewDTO v = new StatsViewDTO();
        assertNull(v.getFirstPbancSn());

        StatsHistoryDTO past = new StatsHistoryDTO();
        past.setPbancSn(2L);
        v.setHistory(Collections.singletonList(past));
        assertEquals(2L, v.getFirstPbancSn());

        ResultBidDTO going = new ResultBidDTO();
        going.setPbancSn(1L);
        v.setInProgress(Collections.singletonList(going));
        assertEquals(1L, v.getFirstPbancSn());
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
