package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * 개찰 업체 (11/04): 참여한 업체를 모두 저장해 평가·결과 입력의 개찰 업체 점수와 평가표 업체 칸에 똑같이 보인다.
 * 로컬 Oracle 로 저장 → 화면까지 돌린다. 나라장터·알림은 목이고, 끝나면 모두 되돌린다(@Transactional).
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=OpeningCompaniesDbTest
 *
 * 공고·업체는 모두 가상이다. 사업자번호는 실제와 겹치지 않게 999 로 시작한다. 우리 회사만 설정값(company.biz-no)을 쓴다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class OpeningCompaniesDbTest {

    private static final String BID_NO = "R26BK99990041";
    private static final String MGMT = "2022-0009";
    private static final String LEADER = "2024-0031";
    private static final String PASSWORD = "Demo!2026a";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ResultMapper resultMapper;
    @Autowired private ResultCollectService collect;
    /** 우리 회사 번호 설정. 쉼표로 여러 개일 수 있어(11/10) 첫 번호를 우리 회사 행에 쓴다 */
    @Value("${company.biz-no}") private String bizNoSetting;
    private String ourBizNo;

    @MockBean private G2bApiClient api;
    @MockBean private NotificationService notifications;

    private Long sn;

    @BeforeEach
    void notice() {
        ourBizNo = bizNoSetting.split(",")[0].trim();
        Long teamSn = jdbc.queryForObject("SELECT TEAM_SN FROM TB_USER WHERE USER_ID = ?", Long.class, LEADER);
        String salesId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE AUTHRT_CD = ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, Role.CD_SALES);
        jdbc.update("INSERT INTO TB_BID_PBANC (BID_PBANC_NO, PBANC_STEP_CD, PBANC_NM, ORDER_INST_NM, ALTMNT_BGT_AMT, PBANC_STTS_CD, "
                + "SPRVSN_TEAM_SN, PTCP_SHP_CD, TECH_SCOR_SCR, PRC_SCOR_SCR, BID_DDLN_DT, RGTR_ID) "
                + "VALUES (?, 'PP040', '(가상) 개찰 업체 시험 사업', '(가상) 시험기관', 500000000, 'PS050', ?, 'PF010', 90, 10, DATE '2026-06-30', ?)",
                BID_NO, teamSn, salesId);
        sn = jdbc.queryForObject("SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = ?", Long.class, BID_NO);
    }

    @Test
    void 낙찰자가_없어도_참여_업체가_보이고_낙찰자가_나오면_평가표_업체_칸에_모두_나온다() throws Exception {
        stubOpening(row("1", "9999999901", "(가상) 업체 A"), row("2", ourBizNo, "(가상) 우리 회사"), row("3", "9999999902", "(가상) 업체 B"));
        when(api.findAward(BID_NO)).thenReturn(response("[]"));
        MockHttpSession mgmt = login(MGMT);

        // 낙찰자 전: 결과는 등록하지 않고 개찰 업체는 세 곳 다 보인다. 평가표 칸은 아직 없다
        assertEquals(ResultOutcomeDTO.Kind.WAITING, register().getKind());
        assertEquals("PS050", stts());
        String before = page(mgmt);
        assertTrue(before.contains("개찰 업체 점수"));
        assertCompanies(tableOf(before), "(가상) 업체 A", "(가상) 우리 회사", "(가상) 업체 B");
        assertFalse(before.contains("평가 항목·배점 (평가표)"));

        // 낙찰자가 나오면 결과 등록. 개찰 업체 표와 평가표 업체 칸이 같은 세 곳이다. 같은 업체가 두 번 생기지 않는다
        when(api.findAward(BID_NO)).thenReturn(response(
                "[{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"bidwinnrBizno\":\"9999999901\",\"sucsfbidAmt\":\"450000000\"}]"));
        assertEquals(ResultOutcomeDTO.Kind.REGISTERED, register().getKind());
        assertEquals("PS060", stts());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CO_EVL_RSLT WHERE PBANC_SN = ?", Integer.class, sn));

        String after = page(mgmt);
        assertCompanies(tableOf(after), "(가상) 업체 A", "(가상) 우리 회사", "(가상) 업체 B");
        assertCompanies(dropdownOf(after), "(가상) 업체 A", "(가상) 우리 회사", "(가상) 업체 B");
        assertEquals("Y", jdbc.queryForObject("SELECT SCSBD_YN FROM TB_CO_EVL_RSLT WHERE PBANC_SN = ? AND BIZ_NO = '9999999901'", String.class, sn));
    }

    @Test
    void 우리_회사가_목록에_없어도_참여_업체는_보이고_결과는_등록하지_않는다() throws Exception {
        stubOpening(row("1", "9999999901", "(가상) 업체 A"), row("2", "9999999902", "(가상) 업체 B"));
        when(api.findAward(BID_NO)).thenReturn(response(
                "[{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"bidwinnrBizno\":\"9999999901\",\"sucsfbidAmt\":\"450000000\"}]"));

        assertEquals(ResultOutcomeDTO.Kind.NOT_LISTED, register().getKind());
        assertEquals("PS050", stts());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_BID_RSLT WHERE PBANC_SN = ?", Integer.class, sn));
        assertCompanies(tableOf(page(login(MGMT))), "(가상) 업체 A", "(가상) 업체 B");
    }

    @Test
    void 팀장도_같은_업체_목록을_본다() throws Exception {
        stubOpening(row("1", "9999999901", "(가상) 업체 A"), row("2", ourBizNo, "(가상) 우리 회사"));
        when(api.findAward(BID_NO)).thenReturn(response(
                "[{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"bidwinnrBizno\":\"9999999901\",\"sucsfbidAmt\":\"450000000\"}]"));
        register();

        String page = page(login(LEADER));
        assertCompanies(tableOf(page), "(가상) 업체 A", "(가상) 우리 회사");
        assertCompanies(dropdownOf(page), "(가상) 업체 A", "(가상) 우리 회사");
    }

    private ResultOutcomeDTO register() {
        ResultTargetDTO target = resultMapper.findResultTarget(sn);
        return collect.register(target);
    }

    private void stubOpening(String... rows) throws Exception {
        when(api.findOpeningCompanies(BID_NO)).thenReturn(response("[" + String.join(",", rows) + "]"));
    }

    private static String row(String rank, String bizNo, String nm) {
        return "{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"opengRank\":\"" + rank + "\",\"prcbdrBizno\":\"" + bizNo + "\","
                + "\"prcbdrNm\":\"" + nm + "\",\"bidprcAmt\":\"450000000\",\"bidPrceEvlVal\":\"9.5\",\"techEvlVal\":\"80\","
                + "\"totalEvlAmtVal\":\"89.5\",\"techEvlNaturVal\":\"78\"}";
    }

    private static G2bResponse response(String items) throws Exception {
        return new G2bResponse(JSON.readTree(items), items, JSON.readTree(items).size());
    }

    private String stts() {
        return jdbc.queryForObject("SELECT TRIM(PBANC_STTS_CD) FROM TB_BID_PBANC WHERE PBANC_SN = ?", String.class, sn);
    }

    /** 개찰 업체 점수 표 부분 */
    private static String tableOf(String page) {
        int i = page.indexOf("<h3>개찰 업체 점수</h3>");
        assertTrue(i >= 0, "개찰 업체 점수 카드가 없습니다");
        return page.substring(i, page.indexOf("</table>", i));
    }

    /** 평가표의 업체 고르는 칸 */
    private static String dropdownOf(String page) {
        int i = page.indexOf("id=\"evl-co\"");
        assertTrue(i >= 0, "평가표 업체 칸이 없습니다");
        return page.substring(i, page.indexOf("</select>", i));
    }

    /** 이 순서대로 이 업체들만 있는지. 사업자번호가 아니라 이름으로 본다 */
    private static void assertCompanies(String html, String... names) {
        Matcher m = Pattern.compile("\\(가상\\) [^<(]*").matcher(html);
        java.util.List<String> found = new java.util.ArrayList<>();
        while (m.find()) {
            found.add(m.group().trim());
        }
        assertEquals(List.of(names), found);
    }

    private MockHttpSession login(String empNo) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("empNo").passwordParam("password")
                .user(empNo).password(PASSWORD))
                .andExpect(r -> assertEquals("/dashboard", r.getResponse().getRedirectedUrl()))
                .andReturn().getRequest().getSession(false);
    }

    private String page(MockHttpSession session) throws Exception {
        return mvc.perform(get("/results?bid=" + sn).session(session))
                .andExpect(r -> assertEquals(200, r.getResponse().getStatus()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
