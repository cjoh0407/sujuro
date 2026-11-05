package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.result.dto.ResultTargetDTO;
import com.ojj.sujuro.result.mapper.ResultMapper;

/**
 * 컨소시엄 참여·하도급 사업의 결과 직접 입력 (11/04). 화면 → 저장 → 사업 이력 관리까지 로컬 Oracle 로 실제로 돌린다.
 * 로그인도 진짜로 한다(테스트 계정). 나라장터·알림은 목이고, 끝나면 모두 되돌린다(@Transactional).
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=ManualResultDbTest
 *
 * 공고는 모두 가상이다. 번호는 실제 나라장터 번호와 겹치지 않게 9999 로 시작한다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class ManualResultDbTest {

    /** 테스트 계정 (db/03_test_data.sql). 비밀번호는 db/README.md 의 공용 값 */
    private static final String MGMT = "2022-0009";
    private static final String LEADER = "2024-0031";
    private static final String PASSWORD = "Demo!2026a";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ResultMapper resultMapper;

    @MockBean private G2bApiClient api;
    @MockBean private NotificationService notifications;

    private Long teamSn;

    @BeforeEach
    void team() {
        teamSn = jdbc.queryForObject("SELECT TEAM_SN FROM TB_USER WHERE USER_ID = ?", Long.class, LEADER);
    }

    @Test
    void 컨소시엄_하도급은_자동_결과_수집_대상이_아니다() {
        Long consortium = insertNotice("R26BK99990011", "PF020", "(가상) 컨소시엄 사업");
        Long subcontract = insertNotice("R26BK99990012", "PF030", "(가상) 하도급 사업");
        Long lead = insertNotice("R26BK99990013", "PF010", "(가상) 주관 사업");
        Long unset = insertNotice("R26BK99990014", null, "(가상) 참여 형태 미정 사업");

        List<Long> targets = resultMapper.findResultTargets().stream().map(ResultTargetDTO::getPbancSn).collect(Collectors.toList());
        assertFalse(targets.contains(consortium));
        assertFalse(targets.contains(subcontract));
        assertTrue(targets.contains(lead));
        assertTrue(targets.contains(unset));

        assertNull(resultMapper.findResultTarget(consortium));
        assertEquals(lead, resultMapper.findResultTarget(lead).getPbancSn());
    }

    @Test
    void 경영지원실이_입력하면_결과_등록이_되고_팀장과_사업_이력_관리에_보인다() throws Exception {
        Long sn = insertNotice("R26BK99990021", "PF020", "(가상) 컨소시엄 사업");
        MockHttpSession mgmt = login(MGMT);
        MockHttpSession leader = login(LEADER);

        // 제출 완료 화면: 경영지원실은 입력 칸을 보고 [나라장터에서 결과 가져오기]는 없다. 팀장은 입력 칸이 없다
        String before = page(mgmt, "/results?bid=" + sn);
        assertTrue(before.contains("입찰 결과 (직접 입력)"));
        assertTrue(before.contains("action=\"/results/manual\""));
        assertFalse(before.contains("나라장터에서 결과 가져오기"));
        String leaderBefore = page(leader, "/results?bid=" + sn);
        assertFalse(leaderBefore.contains("action=\"/results/manual\""));
        assertTrue(leaderBefore.contains("아직 입력하지 않았습니다"));

        // 수주인데 금액이 없으면 저장하지 않고 입력한 값을 되돌려 준다
        mvc.perform(post("/results/manual").session(mgmt).with(csrf()).param("bid", sn.toString()).param("bidRsltCd", "BR010"))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()));
        assertEquals("PS050", stts(sn));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_BID_RSLT WHERE PBANC_SN = ?", Integer.class, sn));
        String failed = page(mgmt, "/results?bid=" + sn); // 브라우저처럼 돌아간 화면을 연다
        assertTrue(failed.contains("수주하면 우리 회사가 받는 금액을 입력해 주세요"));
        assertTrue(failed.contains("<option value=\"BR010\" selected=\"selected\">"));

        // 팀장은 저장할 수 없다
        mvc.perform(post("/results/manual").session(leader).with(csrf()).param("bid", sn.toString())
                .param("bidRsltCd", "BR010").param("scsbdAmt", "120000000"))
                .andExpect(r -> assertEquals(403, r.getResponse().getStatus()));
        assertEquals("PS050", stts(sn));

        // 경영지원실이 수주·금액을 넣는다
        mvc.perform(post("/results/manual").session(mgmt).with(csrf()).param("bid", sn.toString())
                .param("bidRsltCd", "BR010").param("scsbdAmt", "120,000,000"))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()));
        assertEquals("PS060", stts(sn));
        Map<String, Object> row = jdbc.queryForMap("SELECT TRIM(BID_RSLT_CD) CD, SCSBD_AMT, EVL_RNK, TECH_EVL_SCR, CLCT_DT, MDFR_ID FROM TB_BID_RSLT WHERE PBANC_SN = ?", sn);
        assertEquals("BR010", row.get("CD"));
        assertEquals(120000000L, ((Number) row.get("SCSBD_AMT")).longValue());
        assertNull(row.get("EVL_RNK"));
        assertNull(row.get("TECH_EVL_SCR"));
        assertNull(row.get("CLCT_DT"));
        assertEquals(MGMT, row.get("MDFR_ID"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PBANC_PRCS_HSTRY WHERE PBANC_SN = ? AND PRCS_SE_CD = 'PC100'", Integer.class, sn));

        // 결과 등록 화면: 입력 칸에 저장한 값이 채워지고, 개찰 업체·평가표는 없고, 장단점은 있다
        String after = page(mgmt, "/results?bid=" + sn);
        assertTrue(after.contains("결과 고치기"));
        assertTrue(after.contains("value=\"120000000\""));
        assertTrue(after.contains("<h3>장단점 분석</h3>")); // 제목으로 본다. 그냥 글자는 HTML 주석에도 있어서 카드가 빠져도 통과한다
        assertFalse(after.contains("<h3>개찰 업체 점수</h3>"));
        assertFalse(after.contains("<h3>평가 항목·배점 (평가표)</h3>"));

        // 팀장: 읽기만 하고 장단점은 입력한다
        String leaderAfter = page(leader, "/results?bid=" + sn);
        assertFalse(leaderAfter.contains("action=\"/results/manual\""));
        assertTrue(leaderAfter.contains("1.2억"));
        assertTrue(leaderAfter.contains("장단점 저장"));
        assertEquals(3, leaderAfter.split("class=\"analysis-input\"", -1).length - 1, "세 칸 모두 높이가 고정이고 넘치면 칸 안에서 스크롤합니다");

        // 사업별 결과 분석: 수주 금액이 보이고 점수·평가표 칸은 없다. 왼쪽 목록에 이 사업이 있다
        String archive = page(leader, "/bid-history?bid=" + sn);
        assertTrue(archive.contains("수주 금액"));
        assertTrue(archive.contains("1.2억"));
        assertFalse(archive.contains("<h3>점수</h3>"));
        assertFalse(archive.contains("평가 항목별 점수"));
        assertTrue(archive.contains("class=\"split\""));
        assertTrue(archive.contains("(가상) 컨소시엄 사업"));

        // 고치면 값만 바뀌고 이력은 늘지 않는다. 탈락이면 금액을 비운다
        mvc.perform(post("/results/manual").session(mgmt).with(csrf()).param("bid", sn.toString())
                .param("bidRsltCd", "BR020").param("scsbdAmt", "120000000"))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()));
        Map<String, Object> fixed = jdbc.queryForMap("SELECT TRIM(BID_RSLT_CD) CD, SCSBD_AMT FROM TB_BID_RSLT WHERE PBANC_SN = ?", sn);
        assertEquals("BR020", fixed.get("CD"));
        assertNull(fixed.get("SCSBD_AMT"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PBANC_PRCS_HSTRY WHERE PBANC_SN = ? AND PRCS_SE_CD = 'PC100'", Integer.class, sn));
    }

    @Test
    void 결과를_넣으면_수주_금액이_저장되고_사업_이력_관리_목록에_보인다() throws Exception {
        Long sn = insertNotice("R26BK99990031", "PF030", "(가상) 하도급 사업");
        Number wonBefore = wonAmt();
        MockHttpSession mgmt = login(MGMT);

        mvc.perform(post("/results/manual").session(mgmt).with(csrf()).param("bid", sn.toString())
                .param("bidRsltCd", "BR010").param("scsbdAmt", "300000000"))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()));

        assertEquals(wonBefore.longValue() + 300000000L, wonAmt().longValue());
        String history = page(mgmt, "/bid-history?team=" + teamSn + "&year=2026");
        assertTrue(history.contains("(가상) 하도급 사업"));
    }

    private Number wonAmt() {
        return jdbc.queryForObject("SELECT NVL(SUM(R.SCSBD_AMT), 0) FROM TB_BID_RSLT R JOIN TB_BID_PBANC P ON P.PBANC_SN = R.PBANC_SN "
                + "WHERE P.SPRVSN_TEAM_SN = ? AND R.BID_RSLT_CD = 'BR010' AND P.PBANC_STTS_CD = 'PS060'", Long.class, teamSn);
    }

    /** 제출 완료까지 간 가상 공고 */
    private Long insertNotice(String bidNo, String shpCd, String nm) {
        String salesId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE AUTHRT_CD = ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, Role.CD_SALES);
        jdbc.update("INSERT INTO TB_BID_PBANC (BID_PBANC_NO, PBANC_STEP_CD, PBANC_NM, ORDER_INST_NM, ALTMNT_BGT_AMT, PBANC_STTS_CD, "
                + "SPRVSN_TEAM_SN, PTCP_SHP_CD, BID_DDLN_DT, RGTR_ID) VALUES (?, 'PP040', ?, '(가상) 시험기관', 500000000, 'PS050', ?, ?, DATE '2026-06-30', ?)",
                bidNo, nm, teamSn, shpCd, salesId);
        return jdbc.queryForObject("SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = ?", Long.class, bidNo);
    }

    private String stts(Long sn) {
        return jdbc.queryForObject("SELECT TRIM(PBANC_STTS_CD) FROM TB_BID_PBANC WHERE PBANC_SN = ?", String.class, sn);
    }

    private MockHttpSession login(String empNo) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("empNo").passwordParam("password")
                .user(empNo).password(PASSWORD))
                .andExpect(r -> assertEquals("/dashboard", r.getResponse().getRedirectedUrl()))
                .andReturn().getRequest().getSession(false);
    }

    private String page(MockHttpSession session, String url) throws Exception {
        return mvc.perform(get(url).session(session))
                .andExpect(r -> assertEquals(200, r.getResponse().getStatus(), url))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
