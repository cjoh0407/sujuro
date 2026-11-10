package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.result.dto.ResultOutcomeDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;
import com.ojj.sujuro.result.mapper.ResultMapper;
import com.ojj.sujuro.result.service.ResultCollectService;

/**
 * 컨소시엄 참여·하도급 사업의 결과 (11/10). 개찰 목록에 우리 회사 대신 주관사·원도급사가 올라와,
 * 그 사업을 맡은 팀의 팀장이 넣은 그 업체의 사업자번호로 결과·순위·점수·낙찰 금액을 주관 사업과 똑같이 가져온다.
 * 번호는 따로 저장하지 않고 개찰 목록의 그 업체 행(OUR_CO_YN = 'Y')에 남아 자동 수집이 이어받는다. 11/04 의 결과 직접 입력은 없앴다.
 *
 * 화면 → 저장 → 사업 이력 관리까지 로컬 Oracle 로 실제로 돌린다. 로그인도 진짜로 한다(테스트 계정).
 * 나라장터·알림은 목이고, 끝나면 모두 되돌린다(@Transactional). [참여 업체 결과 가져오기] 버튼은 나라장터를 부르는 동안
 * 트랜잭션을 잡지 않아(NOT_SUPPORTED) 되돌릴 수 없으므로, 같은 일을 하는 ResultCollectService.register 를 직접 부른다.
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=PartnerResultDbTest
 *
 * 공고·업체는 모두 가상이다. 공고 번호는 실제 나라장터 번호와 겹치지 않게 9999 로, 사업자번호는 999 로 시작한다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class PartnerResultDbTest {

    /** 테스트 계정. 비밀번호는 db/README.md 의 공용 값 */
    private static final String MGMT = "2022-0009";
    private static final String LEADER = "2024-0031";
    private static final String PASSWORD = "Demo!2026a";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ResultMapper resultMapper;
    @Autowired private ResultCollectService collect;

    @MockBean private G2bApiClient api;
    @MockBean private NotificationService notifications;

    private Long teamSn;

    @BeforeEach
    void team() {
        teamSn = jdbc.queryForObject("SELECT TEAM_SN FROM TB_USER WHERE USER_ID = ?", Long.class, LEADER);
    }

    @Test
    void 주관사_번호를_넣기_전의_컨소시엄_하도급은_자동_결과_수집_대상이_아니다() {
        Long consortium = insertNotice("R26BK99990011", "PF020", "(가상) 컨소시엄 사업");
        Long subcontract = insertNotice("R26BK99990012", "PF030", "(가상) 하도급 사업");
        Long lead = insertNotice("R26BK99990013", "PF010", "(가상) 주관 사업");
        Long unset = insertNotice("R26BK99990014", null, "(가상) 참여 형태 미정 사업");

        List<Long> targets = targetSns();
        assertFalse(targets.contains(consortium));
        assertFalse(targets.contains(subcontract));
        assertTrue(targets.contains(lead));
        assertTrue(targets.contains(unset));

        // 우리 회사 번호로 가져오기(주관)와 주관사 번호로 가져오기(컨소시엄·하도급)는 대상이 갈린다
        assertNull(resultMapper.findResultTarget(consortium));
        assertEquals(lead, resultMapper.findResultTarget(lead).getPbancSn());
        assertEquals(subcontract, resultMapper.findPartnerTarget(subcontract).getPbancSn());
        assertNull(resultMapper.findPartnerTarget(lead));
        assertNull(resultMapper.findPartnerTarget(unset));
    }

    @Test
    void 주관사_번호를_넣으면_그_업체의_결과와_낙찰_금액을_가져오고_자동_수집이_이어받는다() throws Exception {
        String no = "R26BK99990051";
        Long sn = insertNotice(no, "PF020", "(가상) 컨소시엄 사업");
        jdbc.update("UPDATE TB_BID_PBANC SET TECH_SCOR_SCR = 90, PRC_SCOR_SCR = 10 WHERE PBANC_SN = ?", sn); // 배점이 있어 입찰공고를 다시 묻지 않는다
        when(api.findOpeningCompanies(no)).thenReturn(response("["
                + row("1", "9999999901", "(가상) 주관 업체") + "," + row("2", "9999999902", "(가상) 다른 업체") + "]"));
        when(api.findAward(no)).thenReturn(response("[]"));
        MockHttpSession mgmt = login(MGMT);
        MockHttpSession leader = login(LEADER);

        // 번호를 넣기 전: 자동 수집 대상이 아니고, 이 사업을 맡은 팀의 팀장에게만 번호 칸이 보인다. 직접 입력 칸은 없다
        assertFalse(targetSns().contains(sn));
        assertNull(resultMapper.findResultBid(sn).getPartnerBizNo());
        String before = page(leader, "/results?bid=" + sn);
        assertTrue(before.contains("action=\"/results/partner-import\""));
        assertTrue(before.contains("함께 참여한 주관사 사업자번호"));
        assertFalse(before.contains("/results/manual"));
        assertFalse(before.contains("<h3>개찰 업체 점수</h3>"));
        String mgmtBefore = page(mgmt, "/results?bid=" + sn);
        assertFalse(mgmtBefore.contains("action=\"/results/partner-import\""));
        assertTrue(mgmtBefore.contains("담당 팀 팀장이 주관사 사업자번호를 넣으면"));

        // 번호가 10자리가 아니면 나라장터를 부르기 전에 막고, 넣은 값을 돌려준다
        mvc.perform(post("/results/partner-import").session(leader).with(csrf()).param("bid", sn.toString()).param("bizNo", "999-99-9990"))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()));
        String failed = page(leader, "/results?bid=" + sn);
        assertTrue(failed.contains("사업자번호 10자리를 넣어 주세요"));
        assertTrue(failed.contains("value=\"999-99-9990\""));
        verify(api, never()).findAward(anyString());

        // 경영지원실은 번호를 넣을 수 없다
        mvc.perform(post("/results/partner-import").session(mgmt).with(csrf()).param("bid", sn.toString()).param("bizNo", "999-99-99901"))
                .andExpect(r -> assertEquals(403, r.getResponse().getStatus()));

        // 낙찰자 전: 주관 업체 행을 우리 쪽으로 저장하고 기다린다. 그 뒤로는 자동 수집 대상이고 번호가 따라온다
        ResultTargetDTO target = resultMapper.findPartnerTarget(sn);
        target.setPartnerBizNo("999-99-99901");
        assertEquals(ResultOutcomeDTO.Kind.WAITING, collect.register(target).getKind());
        assertEquals("PS050", stts(sn));
        assertEquals("9999999901", jdbc.queryForObject(
                "SELECT BIZ_NO FROM TB_CO_EVL_RSLT WHERE PBANC_SN = ? AND OUR_CO_YN = 'Y'", String.class, sn));
        ResultTargetDTO auto = resultMapper.findResultTargets().stream().filter(t -> t.getPbancSn().equals(sn)).findFirst().orElse(null);
        assertEquals("9999999901", auto.getPartnerBizNo());
        assertEquals("9999999901", resultMapper.findResultBid(sn).getPartnerBizNo());
        String waiting = page(leader, "/results?bid=" + sn);
        assertTrue(waiting.contains("<h3>개찰 업체 점수</h3>"));
        assertTrue(waiting.contains("value=\"999-99-99901\"")); // 넣어 둔 번호가 칸에 채워진다
        assertTrue(waiting.contains("저장한 업체: 999-99-99901"));

        // 낙찰자가 나오면 자동 수집(같은 번호)으로 등록된다. 수주 금액은 주관사의 낙찰 금액이다
        when(api.findAward(no)).thenReturn(response(
                "[{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"bidwinnrBizno\":\"9999999901\",\"sucsfbidAmt\":\"450000000\"}]"));
        assertEquals(ResultOutcomeDTO.Kind.REGISTERED, collect.register(auto).getKind());
        assertEquals("PS060", stts(sn));
        Map<String, Object> row = jdbc.queryForMap("SELECT TRIM(BID_RSLT_CD) CD, EVL_RNK, SNTHS_EVL_SCR, SCSBD_AMT FROM TB_BID_RSLT WHERE PBANC_SN = ?", sn);
        assertEquals("BR010", row.get("CD"));
        assertEquals(1, ((Number) row.get("EVL_RNK")).intValue());
        assertEquals(89.5, ((Number) row.get("SNTHS_EVL_SCR")).doubleValue());
        assertEquals(450000000L, ((Number) row.get("SCSBD_AMT")).longValue());

        // 결과 화면: 순위·점수·개찰 업체·평가표가 주관 사업처럼 보이고, 우리 쪽 행은 주관사로 표시된다. 입력 칸은 없다
        String after = page(mgmt, "/results?bid=" + sn);
        assertTrue(after.contains("<h3>입찰 결과 · 기술·가격·종합 점수 (API)</h3>"));
        assertTrue(after.contains("<h3>개찰 업체 점수</h3>"));
        assertTrue(after.contains("<h3>평가 항목·배점 (평가표) · (가상) 주관 업체</h3>")); // 제목 뒤에 고른 업체(우리 쪽 행)가 붙는다
        assertTrue(after.contains("1위 (가상) 주관 업체 (주관사)"));
        assertTrue(after.contains("개찰 목록의 주관사(사업자번호 999-99-99901) 결과를 우리 결과로 봅니다"));
        assertFalse(after.contains("action=\"/results/partner-import\"")); // 결과가 나온 뒤에는 번호를 바꾸지 않는다
        assertFalse(after.contains("/results/manual"));

        // 사업 이력 관리: 점수 칸과 수주 금액(4.5억)이 보이고, 팀 목록에 이 사업이 있다
        String archive = page(leader, "/bid-history?bid=" + sn);
        assertTrue(archive.contains("<h3>점수</h3>"));
        assertTrue(archive.contains("4.5억"));
        assertTrue(page(mgmt, "/bid-history?team=" + teamSn + "&year=2026").contains("(가상) 컨소시엄 사업"));
    }

    @Test
    void 결과_직접_입력은_없다() throws Exception {
        Long sn = insertNotice("R26BK99990061", "PF030", "(가상) 하도급 사업");
        mvc.perform(post("/results/manual").session(login(MGMT)).with(csrf()).param("bid", sn.toString())
                .param("bidRsltCd", "BR010").param("scsbdAmt", "120000000"))
                .andExpect(r -> assertTrue(r.getResponse().getStatus() == 404 || r.getResponse().getStatus() == 405,
                        "처리하는 곳이 없어야 한다: " + r.getResponse().getStatus()));
        assertEquals("PS050", stts(sn));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_BID_RSLT WHERE PBANC_SN = ?", Integer.class, sn));
    }

    private List<Long> targetSns() {
        return resultMapper.findResultTargets().stream().map(ResultTargetDTO::getPbancSn).collect(Collectors.toList());
    }

    private static String row(String rank, String bizNo, String nm) {
        return "{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"opengRank\":\"" + rank + "\",\"prcbdrBizno\":\"" + bizNo + "\","
                + "\"prcbdrNm\":\"" + nm + "\",\"bidprcAmt\":\"450000000\",\"bidPrceEvlVal\":\"9.5\",\"techEvlVal\":\"80\","
                + "\"totalEvlAmtVal\":\"89.5\",\"techEvlNaturVal\":\"78\"}";
    }

    private static G2bResponse response(String items) throws Exception {
        return new G2bResponse(JSON.readTree(items), items, JSON.readTree(items).size());
    }

    /** 제출 완료까지 간 가상 공고 (개찰 단계) */
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
