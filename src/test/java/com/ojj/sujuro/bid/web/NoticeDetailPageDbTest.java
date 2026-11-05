package com.ojj.sujuro.bid.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;

/**
 * 공고 상세 화면 (11/04).
 *   마감 칸: 의견등록 마감은 사전규격 단계에서만 보이고, 입찰공고부터는 제출 마감만 보인다.
 *   담당 팀 칸: [공고 삭제]는 [불참 종결]과 같은 폼 안에 있지만 삭제 주소로 보낸다.
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
