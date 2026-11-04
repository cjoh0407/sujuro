package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Year;

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

/**
 * 통계의 왼쪽 목록 (11/04): 전체 종합(연도·팀 팝업)과 고른 연도의 진행 사업(진행 중 + 지난 사업), 결과가 없는 사업의 오른쪽 화면.
 * 평가·결과 입력은 메뉴 없이 통계에서 들어간다.
 * 로컬 Oracle 로 화면까지 돌리고 끝나면 모두 되돌린다(@Transactional).
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=StatsBidListDbTest
 *
 * 공고는 모두 가상이다. 번호는 실제 나라장터 번호와 겹치지 않게 9999 로 시작하고, 연도는 다른 데이터와 섞이지 않게 2018·2019 를 쓴다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class StatsBidListDbTest {

    /** 테스트 계정 (db/03_test_data.sql). 비밀번호는 db/README.md 의 공용 값 */
    private static final String MGMT = "2022-0009";
    private static final String LEADER = "2024-0031";
    private static final String PASSWORD = "Demo!2026a";

    private static final String WON_NM = "(가상) 2019 수주 사업";
    private static final String FAILED_NM = "(가상) 2018 유찰 사업";
    private static final String SUBMITTED_NM = "(가상) 2019 제출 완료 사업";
    private static final String WRITING_NM = "(가상) 마감일 없는 제안 작성 사업";
    private static final String IN_PROGRESS_MSG = "아직 입찰 진행 중인 사업건입니다.";
    private static final String AMOUNT_LABEL = "<div class=\"k\">수주 금액</div>";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private G2bApiClient api;
    @MockBean private NotificationService notifications;

    private Long teamSn;
    private MockHttpSession mgmt;
    private Long won;
    private Long failed;
    private Long submitted;
    private Long writing;

    @BeforeEach
    void data() throws Exception {
        teamSn = jdbc.queryForObject("SELECT TEAM_SN FROM TB_USER WHERE USER_ID = ?", Long.class, LEADER);
        mgmt = login(MGMT);
        won = insertNotice("R26BK99990041", WON_NM, "PS050", "DATE '2019-06-30'");
        failed = insertNotice("R26BK99990042", FAILED_NM, "PS070", "DATE '2018-06-30'");
        submitted = insertNotice("R26BK99990043", SUBMITTED_NM, "PS050", "DATE '2019-05-30'");
        writing = insertNotice("R26BK99990044", WRITING_NM, "PS040", "NULL");
        // 수주 결과를 넣어 결과 등록으로 만든다 (컨소시엄 참여로 넣은 공고라 직접 입력한다)
        mvc.perform(post("/results/manual").session(mgmt).with(csrf()).param("bid", won.toString())
                .param("bidRsltCd", "BR010").param("scsbdAmt", "100000000"))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()));
    }

    @Test
    void 진행_사업은_고른_연도의_진행_중과_지난_사업을_합치고_누르면_통계_오른쪽이_바뀐다() throws Exception {
        String page2019 = page(mgmt, stats("2019"));
        String y2019 = yearPart(page2019);
        assertTrue(y2019.contains("2019년 진행 사업"));
        assertTrue(y2019.contains("href=\"" + link("2019", submitted) + "\""));
        assertTrue(y2019.contains("href=\"" + link("2019", won) + "\""));
        assertTrue(y2019.indexOf(SUBMITTED_NM) < y2019.indexOf(WON_NM), "진행 중 사업이 먼저입니다");
        assertFalse(y2019.contains(WRITING_NM));
        assertFalse(y2019.contains(FAILED_NM));
        assertTrue(listPart(page2019).matches("(?s).*<h3>사업 목록</h3>\\s*<span[^>]*>2건</span>.*"), "건수는 둘을 더한 값입니다");

        String y2018 = yearPart(page(mgmt, stats("2018")));
        assertTrue(y2018.contains("2018년 진행 사업"));
        assertTrue(y2018.contains("href=\"" + link("2018", failed) + "\""));
        assertTrue(y2018.contains(">유찰<"));
        assertFalse(y2018.contains(SUBMITTED_NM));
        assertFalse(y2018.contains(WON_NM));
        assertFalse(y2018.contains(WRITING_NM));
    }

    @Test
    void 고른_연도에_이_팀_사업이_없으면_안내가_나온다() throws Exception {
        // 다른 팀 공고로 2017년을 고를 수 있게만 한다
        insertNotice("R26BK99990048", "(가상) 다른 팀 2017 사업", "PS070", "DATE '2017-06-30'", otherTeam());

        String page = page(mgmt, stats("2017"));
        assertTrue(yearPart(page).contains("2017년 진행 사업이 없습니다."));
        assertTrue(listPart(page).matches("(?s).*<h3>사업 목록</h3>\\s*<span[^>]*>0건</span>.*"));
    }

    @Test
    void 마감일이_없는_진행_중_사업은_올해_목록에서_찾는다() throws Exception {
        String thisYear = String.valueOf(Year.now().getValue());
        String progress = yearPart(page(mgmt, stats(thisYear)));
        assertTrue(progress.contains(thisYear + "년 진행 사업"));
        assertTrue(progress.contains("href=\"" + link(thisYear, writing) + "\""));

        // 눌렀을 때도 올해 목록에서 강조한다. 다른 연도를 보던 중이어도 마찬가지다
        String selected = yearPart(page(mgmt, stats("2018") + "&bid=" + writing));
        assertTrue(selected.contains(thisYear + "년 진행 사업"));
        assertTrue(selected.matches("(?s).*bid-item on\"[^>]*bid=" + writing + "\".*"), selected);
    }

    @Test
    void 진행_중_사업은_공고_기본_정보만_있고_세_칸_가운데에_안내_문구가_있다() throws Exception {
        String page = page(mgmt, stats("2018") + "&bid=" + submitted);

        assertEquals(3, count(page, IN_PROGRESS_MSG));
        assertEquals(3, count(page, "class=\"pending-msg"));
        assertTrue(page.contains("공고 기본 정보"));
        assertTrue(page.contains("(가상) 시험기관"));
        assertTrue(page.contains("제출 완료"));
        assertTrue(page.contains("결과가 등록된 사업만 제출 파일을 보여 줍니다."));
        assertFalse(page.contains("기술점수"));
        assertFalse(page.contains("기록 없음"));
        // 예산 대신 수주 금액 칸이고, 아직 금액이 없어 비어 있다 (가상 공고의 예산은 5.0억)
        assertTrue(page.contains(AMOUNT_LABEL + "<div class=\"v\">-</div>"));
        assertFalse(page.contains("예산"));
        assertFalse(page.contains("5.0억"));
        // 결과를 넣으러 가는 길은 그대로 있다
        assertTrue(page.contains("href=\"/results?bid=" + submitted + "\""));

        // 고른 사업을 강조하고, 왼쪽 목록은 보던 연도(2018)가 아니라 그 사업의 연도(2019)로 맞춘다
        String left = listPart(page);
        assertTrue(left.matches("(?s).*bid-item on\"[^>]*bid=" + submitted + "\".*"), left);
        assertTrue(left.contains("2019년 진행 사업"));
    }

    @Test
    void 유찰_사업은_유찰_안내가_나오고_결과가_있는_사업은_안내가_없다() throws Exception {
        String failedPage = page(mgmt, "/stats?bid=" + failed);
        assertEquals(3, count(failedPage, "유찰된 사업건입니다."));
        assertEquals(0, count(failedPage, IN_PROGRESS_MSG));
        assertTrue(listPart(failedPage).matches("(?s).*bid-item on\"[^>]*bid=" + failed + "\".*"));
        assertTrue(failedPage.contains(AMOUNT_LABEL + "<div class=\"v\">-</div>"));

        // 수주한 사업은 수주 금액이 한 칸만 보이고, 입력한 금액이다 (가상 공고의 예산 5.0억이 아니다)
        String wonPage = page(mgmt, "/stats?bid=" + won);
        assertEquals(0, count(wonPage, "class=\"pending-msg"));
        assertEquals(1, count(wonPage, AMOUNT_LABEL));
        assertTrue(wonPage.contains(AMOUNT_LABEL + "<div class=\"v\">1.0억</div>"));
        assertFalse(wonPage.contains("5.0억"));
    }

    @Test
    void 탈락한_사업은_수주_금액이_비어_있다() throws Exception {
        Long lost = insertNotice("R26BK99990047", "(가상) 탈락 사업", "PS050", "DATE '2019-04-30'");
        mvc.perform(post("/results/manual").session(mgmt).with(csrf()).param("bid", lost.toString()).param("bidRsltCd", "BR020"))
                .andExpect(r -> assertEquals(302, r.getResponse().getStatus()));

        String page = page(mgmt, "/stats?bid=" + lost);
        assertEquals(0, count(page, "class=\"pending-msg"));
        assertTrue(page.contains(AMOUNT_LABEL + "<div class=\"v\">-</div>"));
    }

    @Test
    void 볼_수_없는_사업은_열리지_않는다() throws Exception {
        Long others = insertNotice("R26BK99990045", "(가상) 다른 팀 사업", "PS040", "NULL", otherTeam());
        mvc.perform(get("/stats?bid=" + others).session(login(LEADER)))
                .andExpect(r -> assertEquals(403, r.getResponse().getStatus()));

        // 참여 확정 전·불참 공고는 통계 대상이 아니다
        Long declined = insertNotice("R26BK99990046", "(가상) 불참 사업", "PS090", "NULL");
        mvc.perform(get("/stats?bid=" + declined).session(mgmt))
                .andExpect(r -> assertEquals(404, r.getResponse().getStatus()));
    }

    @Test
    void 전체_종합은_누르면_연도_팀_팝업이_열리고_예전_블록들은_없다() throws Exception {
        String page = page(mgmt, stats("2019"));
        String left = listPart(page);
        assertTrue(left.contains("<button type=\"button\" class=\"bid-item on\" data-scope-open"), left);
        assertFalse(left.contains("href=\"/stats?team=" + teamSn + "&amp;year=2019\""));
        assertFalse(left.contains("연도 · 팀"));
        assertFalse(page.contains("scope-btn"));
        assertFalse(page.contains("진행했던 사업"));
        // 진행 사업은 제안서 관리의 지난 사업처럼 접혀 있는 드롭다운이고, 줄에 건수가 보인다
        assertTrue(left.contains("<details class=\"bid-past\">"), left);
        assertTrue(left.contains("<summary class=\"bid-grp\">2019년 진행 사업 (2)</summary>"), left);
        // 팝업은 연도·팀만 보내서 [보기]를 누르면 그 연도·팀의 전체 종합으로 간다
        String popup = page.substring(page.indexOf("id=\"statsScope\""), page.indexOf("</form>", page.indexOf("id=\"statsScope\"")));
        assertTrue(popup.contains("action=\"/stats\""));
        assertTrue(popup.contains("name=\"year\""));
        assertTrue(popup.contains("name=\"team\""));
        assertFalse(popup.contains("name=\"bid\""));

        // 사업을 보는 중에도 같은 버튼이고 강조만 풀린다
        assertTrue(listPart(page(mgmt, "/stats?bid=" + won)).contains("<button type=\"button\" class=\"bid-item\" data-scope-open"));
    }

    @Test
    void 연도_없이_바로_오면_그_사업의_연도_목록에서_강조한다() throws Exception {
        // 평가·결과 입력의 [사업별 결과 분석] 버튼처럼 연도 없이 온다
        String list = yearPart(page(mgmt, "/stats?bid=" + won));
        assertTrue(list.contains("2019년 진행 사업"));
        assertTrue(listPart(page(mgmt, "/stats?bid=" + won)).contains("<details class=\"bid-past\" open=\"open\">"), "고른 사업이 보이게 펼칩니다");
        assertTrue(listPart(page(mgmt, "/stats?bid=" + submitted)).contains("<details class=\"bid-past\" open=\"open\">"), "진행 중 사업을 골라도 펼칩니다");
        assertTrue(list.matches("(?s).*bid-item on\"[^>]*bid=" + won + "\".*"), list);
    }

    @Test
    void 평가_결과_입력은_메뉴와_사업건_목록이_없고_통계에서_들어간다() throws Exception {
        for (MockHttpSession session : new MockHttpSession[] { mgmt, login(LEADER) }) {
            String page = page(session, "/results?bid=" + submitted);
            // 머리: 사업명과 그 사업의 통계로 돌아가는 버튼
            assertTrue(page.contains("<h2>" + SUBMITTED_NM + "</h2>"));
            assertTrue(page.contains("href=\"/stats?bid=" + submitted + "\""));
            assertFalse(page.contains("사업건 목록"));
            assertFalse(page.contains("bid-item"));
            // 평가표를 올리지 않는 사업이라 왼쪽 칸 없이 넓게 보인다
            assertFalse(page.contains("class=\"split\""));
            assertFalse(page.contains("href=\"/results\""));
            // 메뉴에는 없고 통계에 불이 들어온다
            assertTrue(page.matches("(?s).*<a href=\"/stats\" class=\"on\">통계</a>.*"));
        }

        // 사업건 없이 오면 고를 수 있는 통계로 보낸다 (대시보드의 [결과 관리] 등)
        mvc.perform(get("/results").session(mgmt))
                .andExpect(r -> assertEquals("/stats", r.getResponse().getRedirectedUrl()));
    }

    @Test
    void 메뉴는_통계_일정_관리_순서다() throws Exception {
        String nav = page(mgmt, "/dashboard");
        int stats = nav.indexOf(">통계</a>");
        int schedule = nav.indexOf(">일정 관리</a>");
        assertTrue(stats >= 0 && stats < schedule, "통계가 일정 관리보다 앞이어야 합니다");
        assertFalse(nav.contains(">평가·결과 입력</a>"));
        assertTrue(nav.indexOf(">사용자 관리</a>") > schedule, "사용자 관리는 그대로 있어야 합니다");
    }

    private String stats(String year) {
        return "/stats?team=" + teamSn + "&year=" + year;
    }

    /** 목록 줄의 주소 (화면에는 &amp; 로 나온다) */
    private String link(String year, Long sn) {
        return "/stats?team=" + teamSn + "&amp;year=" + year + "&amp;bid=" + sn;
    }

    private static int count(String page, String text) {
        return page.split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }

    /** 왼쪽 사업 목록 */
    private static String listPart(String page) {
        int i = page.indexOf("<aside class=\"split-l\">");
        assertTrue(i >= 0, "사업 목록이 없습니다");
        return page.substring(i, page.indexOf("</aside>", i));
    }

    /** 사업 목록 블록의 고른 연도 진행 사업 (전체 종합 줄 아래) */
    private static String yearPart(String page) {
        String list = listPart(page);
        return list.substring(list.indexOf("class=\"bid-grp\""));
    }

    /** 이 팀이 아닌 개발팀 */
    private Long otherTeam() {
        return jdbc.queryForObject("SELECT TEAM_SN FROM TB_TEAM WHERE DEPT_CD = 'DP030' AND USE_YN = 'Y' AND TEAM_SN <> ? "
                + "ORDER BY TEAM_SN FETCH FIRST 1 ROWS ONLY", Long.class, teamSn);
    }

    private Long insertNotice(String bidNo, String nm, String sttsCd, String ddln) {
        return insertNotice(bidNo, nm, sttsCd, ddln, teamSn);
    }

    /** 가상 공고. ddln 은 SQL 날짜 표현 */
    private Long insertNotice(String bidNo, String nm, String sttsCd, String ddln, Long team) {
        String salesId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE AUTHRT_CD = ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, Role.CD_SALES);
        jdbc.update("INSERT INTO TB_BID_PBANC (BID_PBANC_NO, PBANC_STEP_CD, PBANC_NM, ORDER_INST_NM, ALTMNT_BGT_AMT, PBANC_STTS_CD, "
                + "SPRVSN_TEAM_SN, PTCP_SHP_CD, BID_DDLN_DT, RGTR_ID) VALUES (?, 'PP040', ?, '(가상) 시험기관', 500000000, ?, ?, 'PF020', "
                + ddln + ", ?)", bidNo, nm, sttsCd, team, salesId);
        return jdbc.queryForObject("SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = ?", Long.class, bidNo);
    }

    private MockHttpSession login(String empNo) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("empNo").passwordParam("password")
                .user(empNo).password(PASSWORD))
                .andExpect(r -> assertEquals("/dashboard", r.getResponse().getRedirectedUrl()))
                .andReturn().getRequest().getSession(false);
    }

    private String page(MockHttpSession session, String url) throws Exception {
        return mvc.perform(get(url).session(session))
                .andExpect(r -> assertEquals(200, r.getResponse().getStatus()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
