package com.ojj.sujuro.bid.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.common.file.FileStorage;

/**
 * 공고 상세 화면 (11/04).
 *   마감 칸: 의견등록 마감은 사전규격 단계에서만 보이고, 입찰공고부터는 제출 마감만 보인다.
 *   담당 팀 칸: [공고 삭제]는 [불참 종결]과 같은 폼 안에 있지만 삭제 주소로 보낸다.
 *   첨부: 내려받기는 /notices/{sn}/files/{fileSn} 한 곳이다. 서버에 저장한 파일은 바로 주고, 아니면 나라장터 주소로 보낸다.
 * 로컬 Oracle 로 화면까지 돌리고 끝나면 모두 되돌린다(@Transactional).
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=NoticeDetailPageDbTest
 *
 * 공고는 모두 가상이다. 번호는 실제 나라장터 번호와 겹치지 않게 9999 로 시작한다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class NoticeDetailPageDbTest {

    /** 테스트 계정 (db/03_test_data.sql). 비밀번호는 db/README.md 의 공용 값 */
    private static final String MGMT = "2022-0009";
    private static final String SALES = "2023-0067";
    private static final String PASSWORD = "Demo!2026a";

    private static final String OPINION_LABEL = "<div class=\"k\">의견등록 마감</div>";
    private static final String SUBMIT_LABEL = "<div class=\"k\">제출 마감</div>";
    private static final String OPINION_AT = "2099-01-20 18:00";
    private static final String SUBMIT_AT = "2099-02-10 10:00";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private G2bApiClient api;
    @MockBean private FileStorage storage;

    @Test
    void 첨부파일_내려받기는_아이콘_하나이고_나라장터_첨부는_원래_주소로_보낸다() throws Exception {
        Long sn = insertNotice("R26BK99990055", "PP030");
        Long bidFile = insertUrlFile(sn, "FL010", "(가상) 제안요청서.hwpx", "https://example.invalid/rfp");
        Long preSpecFile = insertUrlFile(sn, "FL080", "(가상) 제안요청서(안).hwpx", "https://example.invalid/pre");
        MockHttpSession session = login(MGMT);

        String page = page(session, sn);
        assertTrue(page.contains("(가상) 제안요청서.hwpx"));
        assertTrue(page.contains(downloadIcon(sn, bidFile)), "입찰공고 첨부 아이콘 링크여야 합니다");
        assertTrue(page.contains(downloadIcon(sn, preSpecFile)), "사전규격 규격서 아이콘 링크여야 합니다");
        assertFalse(page.contains(">내려받기</a>"), "글자 버튼은 없습니다");
        assertFalse(page.contains(">제안요청서 초안</span>"), "사전규격 규격서도 입찰공고 첨부처럼 태그가 없습니다");
        assertTrue(page.contains("/css/proposal.css"), "아이콘 모양은 proposal.css 에 있습니다");

        mvc.perform(get("/notices/" + sn + "/files/" + bidFile).session(session))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()))
                .andExpect(r -> assertEquals("https://example.invalid/rfp", r.getResponse().getRedirectedUrl()));
    }

    /** 팀장 직급(JG020)은 제안팀 칩의 이름에만 바탕색을 칠하고, 제목 줄 오른쪽 끝에 그 색의 범례를 둔다. 사람은 모두 테스트 계정이다 */
    @Test
    void 팀장_표시는_제안팀_칩에만_있고_제목_줄에_범례가_있다() throws Exception {
        Long sn = insertNotice("R26BK99990059", "PP030");
        String leaderId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE JBGD_CD = 'JG020' AND ACNT_STTS_CD <> 'AS050' ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class);
        String leaderNm = jdbc.queryForObject("SELECT USER_NM FROM TB_USER WHERE USER_ID = ?", String.class, leaderId);
        String memberId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE JBGD_CD = 'JG010' AND ACNT_STTS_CD <> 'AS050' AND USER_NM <> ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, leaderNm);
        String memberNm = jdbc.queryForObject("SELECT USER_NM FROM TB_USER WHERE USER_ID = ?", String.class, memberId);
        String legend = "<span class=\"dot nd-leader-dot\"></span>팀장</span>";

        // 제안팀에 사원만 있으면 칠할 이름도 범례도 없다
        Long membersOnly = insertNotice("R26BK99990060", "PP030");
        jdbc.update("INSERT INTO TB_PRP_TMMBR (PBANC_SN, USER_ID, PTCP_SE_CD) VALUES (?, ?, 'PT010')", membersOnly, memberId);
        MockHttpSession session = login(MGMT);
        String plain = page(session, membersOnly);
        assertTrue(plain.contains("<h3>제안팀</h3>"));
        assertTrue(plain.contains("<b>" + memberNm + "</b>"));
        assertFalse(plain.contains(legend), "팀장이 없으면 범례도 없습니다");

        // 등록자도 팀장으로 하고, 제안팀에 팀장·사원을, 이력에 팀장 처리를 넣는다. 칠하는 곳은 제안팀 칩뿐이다
        jdbc.update("UPDATE TB_BID_PBANC SET RGTR_ID = ? WHERE PBANC_SN = ?", leaderId, sn);
        jdbc.update("INSERT INTO TB_PRP_TMMBR (PBANC_SN, USER_ID, PTCP_SE_CD) VALUES (?, ?, 'PT010')", sn, leaderId);
        jdbc.update("INSERT INTO TB_PRP_TMMBR (PBANC_SN, USER_ID, PTCP_SE_CD) VALUES (?, ?, 'PT010')", sn, memberId);
        jdbc.update("INSERT INTO TB_PBANC_PRCS_HSTRY (PBANC_SN, PRCS_SE_CD, PRCR_ID) VALUES (?, 'PC010', ?)", sn, leaderId);

        String page = page(session, sn);
        assertEquals(1, count(page, "class=\"nd-leader\""), "제안팀 칩 한 곳뿐입니다. 등록·진행 이력은 칠하지 않습니다");
        assertTrue(page.contains("<b class=\"nd-leader\" title=\"팀장\">" + leaderNm + "</b>"), "팀장 칩");
        assertTrue(page.contains("<b>" + memberNm + "</b>"), "사원은 칠하지 않습니다");
        assertTrue(page.contains("<span>" + leaderNm + "</span>"), "진행 이력의 이름은 그냥 글자입니다");
        assertTrue(page.contains(leaderNm + " · "), "등록의 이름도 그냥 글자입니다");
        assertTrue(page.matches("(?s).*<h3>제안팀</h3>\\s*<span class=\"fs-12_5\">" + java.util.regex.Pattern.quote(legend) + ".*"), "제안팀 제목 바로 옆(오른쪽 끝)에 범례가 있습니다");
    }

    /** 시드(db/05)의 공고 첨부처럼 서버에만 있고 나라장터 주소(ORGNL_URL)가 없는 파일도 아이콘으로 받는다 */
    @Test
    void 주소_없이_서버에만_저장한_첨부도_아이콘으로_내려받는다() throws Exception {
        byte[] body = { 1, 2, 3 };
        when(storage.load("seed/bid", "R26BK99990058-FL010-1.pdf")).thenReturn(new ByteArrayResource(body));
        Long sn = insertNotice("R26BK99990058", "PP030");
        jdbc.update("INSERT INTO TB_ATCH_FILE (PBANC_SN, FILE_SE_CD, ORGNL_FILE_NM, STRG_FILE_NM, FILE_PATH_NM, RGTR_ID) VALUES (?, 'FL010', '(가상) 제안요청서.pdf', "
                + "'R26BK99990058-FL010-1.pdf', 'seed/bid', (SELECT RGTR_ID FROM TB_BID_PBANC WHERE PBANC_SN = ?))", sn, sn);
        Long fileSn = fileSn(sn, "FL010");
        MockHttpSession session = login(MGMT);

        assertTrue(page(session, sn).contains(downloadIcon(sn, fileSn)), "주소가 없어도 링크가 있어야 합니다");

        MockHttpServletResponse res = mvc.perform(get("/notices/" + sn + "/files/" + fileSn).session(session)).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        assertTrue(res.getHeader("Content-Disposition").startsWith("attachment"));
        assertArrayEquals(body, res.getContentAsByteArray());
    }

    @Test
    void 기관은_수요기관_한_줄이고_수요기관이_없으면_발주기관_값을_쓴다() throws Exception {
        MockHttpSession session = login(MGMT);
        Long noDemand = insertNotice("R26BK99990053", "PP030");
        Long withDemand = insertNotice("R26BK99990054", "PP030");
        jdbc.update("UPDATE TB_BID_PBANC SET DMND_INST_NM = '(가상) 수요 기관' WHERE PBANC_SN = ?", withDemand);

        String demand = page(session, withDemand);
        assertTrue(demand.matches("(?s).*<div class=\"k\">수요기관</div>\\s*<div class=\"v\">\\(가상\\) 수요 기관</div>.*"), "수요기관 값을 보여 줍니다");
        assertFalse(demand.contains("(가상) 시험기관"), "수요기관이 있으면 발주기관 값은 보이지 않습니다");

        String fallback = page(session, noDemand);
        assertTrue(fallback.matches("(?s).*<div class=\"k\">수요기관</div>\\s*<div class=\"v\">\\(가상\\) 시험기관</div>.*"), "없으면 발주기관 값을 수요기관 칸에 씁니다");

        for (String page : new String[] { demand, fallback }) {
            assertEquals(1, count(page, "<div class=\"k\">수요기관</div>"));
            assertEquals(0, count(page, "<div class=\"k\">발주기관</div>"));
        }
    }

    @Test
    void 사전규격_단계는_의견등록_마감을_강조해_보인다() throws Exception {
        String page = page(login(MGMT), insertNotice("R26BK99990051", "PP020"));

        assertEquals(1, count(page, OPINION_LABEL));
        assertTrue(page.matches("(?s).*<div class=\"key\">\\s*" + OPINION_LABEL + ".*"), "의견등록 마감이 강조돼야 합니다");
        assertTrue(page.contains(OPINION_AT + " (D-"));
    }

    @Test
    void 입찰공고부터는_값이_있어도_의견등록_마감이_없고_제출_마감만_보인다() throws Exception {
        MockHttpSession session = login(MGMT);
        for (String[] step : new String[][] { { "R26BK99990052", "PP030" }, { "R26BK99990053", "PP040" }, { "R26BK99990054", "PP050" } }) {
            String page = page(session, insertNotice(step[0], step[1]));

            assertFalse(page.contains(OPINION_LABEL), step[1]);
            assertFalse(page.contains(OPINION_AT), step[1]);
            assertEquals(1, count(page, SUBMIT_LABEL), step[1]);
            assertTrue(page.contains(SUBMIT_AT), step[1]);
        }
    }

    @Test
    void 화면에서만_숨기고_DB_값은_지우지_않는다() throws Exception {
        Long sn = insertNotice("R26BK99990055", "PP030");
        page(login(MGMT), sn);

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_BID_PBANC WHERE PBANC_SN = ? AND OPNN_DDLN_DT IS NOT NULL", Integer.class, sn));
    }

    @Test
    void 공고_삭제_버튼은_불참_종결과_같은_크기이고_삭제_주소로_보낸다() throws Exception {
        Long sn = insertNotice("R26BK99990056", "PP030");
        MockHttpSession sales = login(SALES);
        String form = closeForm(page(sales, sn), sn);

        assertEquals(2, count(form, "class=\"btn danger\""), form);
        assertTrue(form.matches("(?s).*<button class=\"btn danger\"[^>]*formaction=\"/notices/" + sn + "/delete\"[^>]*>공고 삭제</button>.*"), form);
        // [불참 종결]은 폼 주소(/close)로 그대로 간다
        assertTrue(form.matches("(?s).*<button class=\"btn danger\" type=\"submit\"\\s+data-confirm=\"[^\"]*\">불참 종결</button>.*"), form);

        // 버튼이 보내는 주소로 보내면 정말 지워지고 불참으로 종결되지 않는다
        mvc.perform(post("/notices/" + sn + "/delete").session(sales).with(csrf()).param("reason", "입력돼 있던 사유"))
                .andExpect(r -> assertEquals("/notices", r.getResponse().getRedirectedUrl()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_BID_PBANC WHERE PBANC_SN = ?", Integer.class, sn));
    }

    @Test
    void 팀에_맡긴_적_있는_공고는_불참_종결만_있고_삭제_버튼이_없다() throws Exception {
        Long sn = insertNotice("R26BK99990057", "PP030");
        jdbc.update("UPDATE TB_BID_PBANC SET SPRVSN_TEAM_SN = (SELECT MIN(TEAM_SN) FROM TB_TEAM WHERE DEPT_CD = 'DP030' AND USE_YN = 'Y') WHERE PBANC_SN = ?", sn);

        String page = page(login(SALES), sn);
        assertTrue(closeForm(page, sn).contains(">불참 종결</button>"));
        assertFalse(page.contains(">공고 삭제</button>"));
        assertFalse(page.contains("/notices/" + sn + "/delete"));
    }

    /** 담당 팀 칸의 불참 종결 폼 */
    private static String closeForm(String page, Long sn) {
        int i = page.indexOf("action=\"/notices/" + sn + "/close\"");
        assertTrue(i >= 0, "불참 종결 폼이 없습니다");
        return page.substring(i, page.indexOf("</form>", i));
    }

    private static int count(String page, String text) {
        return page.split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }

    /** 첨부 한 줄의 내려받기 아이콘 */
    private static String downloadIcon(Long sn, Long fileSn) {
        return "<a class=\"ico\" title=\"내려받기\" aria-label=\"내려받기\" href=\"/notices/" + sn + "/files/" + fileSn + "\">"
                + "<span class=\"ic ic-download\"></span></a>";
    }

    /** 나라장터 주소만 있고 서버에 저장하지 않은 가상 첨부 */
    private Long insertUrlFile(Long sn, String fileSeCd, String fileNm, String url) {
        jdbc.update("INSERT INTO TB_ATCH_FILE (PBANC_SN, FILE_SE_CD, ORGNL_FILE_NM, ORGNL_URL, RGTR_ID) VALUES (?, ?, ?, ?, "
                + "(SELECT RGTR_ID FROM TB_BID_PBANC WHERE PBANC_SN = ?))", sn, fileSeCd, fileNm, url, sn);
        return fileSn(sn, fileSeCd);
    }

    private Long fileSn(Long sn, String fileSeCd) {
        return jdbc.queryForObject("SELECT FILE_SN FROM TB_ATCH_FILE WHERE PBANC_SN = ? AND FILE_SE_CD = ?", Long.class, sn, fileSeCd);
    }

    /** 의견등록 마감과 제출 마감이 둘 다 있는 배정 전 가상 공고 */
    private Long insertNotice(String bidNo, String stepCd) {
        String salesId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE AUTHRT_CD = ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, Role.CD_SALES);
        jdbc.update("INSERT INTO TB_BID_PBANC (BID_PBANC_NO, PBANC_STEP_CD, PBANC_NM, ORDER_INST_NM, ALTMNT_BGT_AMT, PBANC_STTS_CD, "
                + "OPNN_DDLN_DT, BID_DDLN_DT, RGTR_ID) VALUES (?, ?, '(가상) 공고 상세 확인 사업', '(가상) 시험기관', 500000000, 'PS010', "
                + "TO_DATE('" + OPINION_AT + "', 'YYYY-MM-DD HH24:MI'), TO_DATE('" + SUBMIT_AT + "', 'YYYY-MM-DD HH24:MI'), ?)",
                bidNo, stepCd, salesId);
        return jdbc.queryForObject("SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = ?", Long.class, bidNo);
    }

    private MockHttpSession login(String empNo) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("empNo").passwordParam("password")
                .user(empNo).password(PASSWORD))
                .andExpect(r -> assertEquals("/dashboard", r.getResponse().getRedirectedUrl()))
                .andReturn().getRequest().getSession(false);
    }

    private String page(MockHttpSession session, Long sn) throws Exception {
        return mvc.perform(get("/notices/" + sn).session(session))
                .andExpect(r -> assertEquals(200, r.getResponse().getStatus()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
