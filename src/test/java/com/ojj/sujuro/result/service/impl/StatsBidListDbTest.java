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
 * 사업 이력 관리 (11/05): 왼쪽 연도·팀 드롭다운과 고른 연도·팀의 사업(진행 중 + 지난 사업), 결과가 없는 사업의 오른쪽 화면.
 * 평가·결과 입력은 메뉴 없이 사업 이력 관리에서 들어간다. 통계(전체 종합)는 지웠다.
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
    private static final String SALES = "2023-0067";
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
        String page2019 = page(mgmt, scope("2019"));
        String y2019 = yearPart(page2019);
        assertTrue(listPart(page2019).contains(yearOption("2019")));
        assertTrue(y2019.contains("href=\"" + link("2019", submitted) + "\""));
        assertTrue(y2019.contains("href=\"" + link("2019", won) + "\""));
        assertTrue(y2019.indexOf(SUBMITTED_NM) < y2019.indexOf(WON_NM), "진행 중 사업이 먼저입니다");
        assertFalse(y2019.contains(WRITING_NM));
        assertFalse(y2019.contains(FAILED_NM));
        assertEquals(2, count(y2019, "class=\"bid-item"), "진행 중과 지난 사업을 합친 줄 수입니다");

        String page2018 = page(mgmt, scope("2018"));
        String y2018 = yearPart(page2018);
        assertTrue(listPart(page2018).contains(yearOption("2018")));
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

        String page = page(mgmt, scope("2017"));
        assertTrue(yearPart(page).contains("2017년 진행 사업이 없습니다."));
        assertEquals(0, count(yearPart(page), "class=\"bid-item"));
        // 오른쪽에 볼 사업이 없으니 머리는 탭 이름이고 칸 가운데에 안내가 있다
        assertTrue(page.contains("<h2>사업 이력 관리</h2>"));
        assertEquals(1, count(page, "class=\"pending-msg"));
        assertTrue(page.contains("2017년 ") && page.contains(" 사업이 없습니다."));
    }

    @Test
    void 마감일이_없는_진행_중_사업은_올해_목록에서_찾는다() throws Exception {
        String thisYear = String.valueOf(Year.now().getValue());
        String thisYearPage = page(mgmt, scope(thisYear));
        assertTrue(listPart(thisYearPage).contains(yearOption(thisYear)));
        assertTrue(yearPart(thisYearPage).contains("href=\"" + link(thisYear, writing) + "\""));

        // 눌렀을 때도 올해 목록에서 강조한다. 다른 연도를 보던 중이어도 마찬가지다
        String selected = listPart(page(mgmt, scope("2018") + "&bid=" + writing));
        assertTrue(selected.contains(yearOption(thisYear)));
        assertTrue(selected.matches("(?s).*bid-item on\"[^>]*bid=" + writing + "\".*"), selected);
    }

    @Test
    void 진행_중_사업은_공고_기본_정보만_있고_세_칸_가운데에_안내_문구가_있다() throws Exception {
        String page = page(mgmt, scope("2018") + "&bid=" + submitted);

        assertEquals(3, count(page, IN_PROGRESS_MSG));
        assertEquals(3, count(page, "class=\"pending-msg"));
        assertTrue(page.contains("공고 기본 정보"));
        assertTrue(page.contains("(가상) 시험기관"));
        assertTrue(page.contains("제출 완료"));
        assertFalse(page.contains("제출 산출물"));
        assertEquals(1, count(page, "class=\"two even\""), "공고 기본 정보 옆에 점수 칸만 나란합니다");
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
        assertTrue(left.contains(yearOption("2019")));
    }

    @Test
    void 유찰_사업은_유찰_안내가_나오고_결과가_있는_사업은_안내가_없다() throws Exception {
        String failedPage = page(mgmt, "/bid-history?bid=" + failed);
        assertEquals(3, count(failedPage, "유찰된 사업건입니다."));
        assertEquals(0, count(failedPage, IN_PROGRESS_MSG));
        assertTrue(listPart(failedPage).matches("(?s).*bid-item on\"[^>]*bid=" + failed + "\".*"));
        assertTrue(failedPage.contains(AMOUNT_LABEL + "<div class=\"v\">-</div>"));

        // 수주한 사업은 수주 금액이 한 칸만 보이고, 입력한 금액이다 (가상 공고의 예산 5.0억이 아니다)
        String wonPage = page(mgmt, "/bid-history?bid=" + won);
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

        String page = page(mgmt, "/bid-history?bid=" + lost);
        assertEquals(0, count(page, "class=\"pending-msg"));
        assertTrue(page.contains(AMOUNT_LABEL + "<div class=\"v\">-</div>"));
    }

    @Test
    void 볼_수_없는_사업은_열리지_않는다() throws Exception {
        Long others = insertNotice("R26BK99990045", "(가상) 다른 팀 사업", "PS040", "NULL", otherTeam());
        mvc.perform(get("/bid-history?bid=" + others).session(login(LEADER)))
                .andExpect(r -> assertEquals(403, r.getResponse().getStatus()));

        // 참여 확정 전·불참 공고는 사업 이력 대상이 아니다
        Long declined = insertNotice("R26BK99990046", "(가상) 불참 사업", "PS090", "NULL");
        mvc.perform(get("/bid-history?bid=" + declined).session(mgmt))
                .andExpect(r -> assertEquals(404, r.getResponse().getStatus()));
    }

    @Test
    void 경영지원실은_연도와_팀을_고르고_사업은_접지_않고_바로_보인다() throws Exception {
        String page = page(mgmt, scope("2019"));
        String left = listPart(page);
        String form = scopeForm(left);
        assertTrue(form.contains("action=\"/bid-history\""));
        assertTrue(form.contains(yearOption("2019")));
        assertTrue(form.contains("name=\"team\""));
        assertTrue(form.contains("<option value=\"" + teamSn + "\" selected=\"selected\">"), form);
        assertTrue(form.contains("<option value=\"" + otherTeam() + "\">"), form);
        assertFalse(form.contains("name=\"bid\""), "고르면 그 연도·팀의 사업으로 바뀝니다");
        assertFalse(form.contains("type=\"submit\""), "고르면 바로 바뀌어서 버튼이 없습니다");

        // 드롭다운은 제목 줄 오른쪽에 있고 건수 글자는 없다. 접는 줄 없이 그 아래에 사업이 보인다
        assertTrue(left.matches("(?s).*<div class=\"card-head scope-head\">\\s*<h3>사업 목록</h3>\\s*<form class=\"scope-form\".*"), left);
        assertFalse(left.contains("건</span>"), left);
        assertFalse(left.contains("<details"));
        assertFalse(left.contains("진행 사업 ("));
        assertTrue(left.indexOf("</form>") < left.indexOf(WON_NM), left);
    }

    @Test
    void 결과가_있는_사업은_공고_기본_정보_옆에_점수이고_그_아래_평가_항목별_점수를_넓게_쓴다() throws Exception {
        // 나라장터 결과가 들어오는 주관 사업으로 바꾼다 (컨소시엄 참여·하도급은 점수가 없다)
        jdbc.update("UPDATE TB_BID_PBANC SET PTCP_SHP_CD = 'PF010' WHERE PBANC_SN = ?", won);
        String page = page(mgmt, "/bid-history?bid=" + won);

        int info = page.indexOf("<h3>공고 기본 정보</h3>");
        int score = page.indexOf("<h3>점수</h3>");
        int article = page.indexOf("<h3>평가 항목별 점수</h3>");
        int analysis = page.indexOf("<h3>장단점 분석</h3>");
        assertTrue(info >= 0 && info < score && score < article && article < analysis, "공고 기본 정보, 점수, 평가 항목별 점수, 장단점 분석 순서입니다");
        assertEquals(1, count(page, "class=\"two even\""));
        assertTrue(page.indexOf("class=\"two even\"") < info, "위 줄만 두 칸입니다");
        assertFalse(page.contains("제출 산출물"));
        assertTrue(page.contains("class=\"pad grid3 analysis\""), "장단점 세 칸 사이에 구분선이 있습니다");
    }

    @Test
    void 공고_기본_정보의_기관은_수요기관이고_없으면_발주기관_값을_쓴다() throws Exception {
        // 결과가 있는 사업(won)과 아직 결과가 없는 사업(failed) 모두 같다
        jdbc.update("UPDATE TB_BID_PBANC SET DMND_INST_NM = '(가상) 수요 기관' WHERE PBANC_SN IN (?, ?)", won, failed);
        for (Long sn : new Long[] { won, failed }) {
            String page = page(mgmt, "/bid-history?bid=" + sn);
            assertTrue(page.contains("<div class=\"k\">수요기관</div><div class=\"v\">(가상) 수요 기관</div>"), "사업 " + sn);
            assertFalse(page.contains("(가상) 시험기관"), "수요기관이 있으면 발주기관 값은 보이지 않습니다");
            assertFalse(page.contains("<div class=\"k\">발주기관</div>"));
        }

        String fallback = page(mgmt, "/bid-history?bid=" + submitted);
        assertTrue(fallback.contains("<div class=\"k\">수요기관</div><div class=\"v\">(가상) 시험기관</div>"), "없으면 발주기관 값을 수요기관 칸에 씁니다");
    }

    @Test
    void 평가_항목별_점수는_항목마다_업체_수만큼_막대로_그리고_항목_1등만_표시한다() throws Exception {
        jdbc.update("UPDATE TB_BID_PBANC SET PTCP_SHP_CD = 'PF010' WHERE PBANC_SN = ?", won);
        Long ours = company(won, "9999999900", "(가상) 우리 회사", 1, "Y", "Y");
        Long rival = company(won, "9999999901", "(가상) 업체 A", 2, "N", "N");
        Long strategy = article(won, "전략 및 방법론", 20, 1);
        Long skill = article(won, "기술 및 기능", 30, 2);
        score(ours, strategy, 18);
        score(ours, skill, 27);
        score(rival, strategy, 19);
        score(rival, skill, 21);

        String page = page(mgmt, "/bid-history?bid=" + won);
        assertTrue(page.contains("class=\"score-chart\""));
        assertFalse(page.contains("<table"), "표 대신 그래프입니다");
        assertEquals(4, count(page, "class=\"co-bar"), "항목 2개 × 업체 2곳");
        assertEquals(2, count(page, "class=\"co-bar first\""), "전략은 업체 A, 기술은 우리 회사가 1등입니다");
        assertTrue(page.matches("(?s).*class=\"co-bar first\">\\s*<title>\\(가상\\) 업체 A · 전략 및 방법론 .*"), "그 항목 1등 막대가 표시됩니다");
        assertTrue(page.matches("(?s).*class=\"co-bar first\">\\s*<title>\\(가상\\) 우리 회사 · 기술 및 기능 .*"));
        assertTrue(page.contains("<title>(가상) 업체 A · 전략 및 방법론 19 / 20 · 1등</title>"), "마우스를 올리면 항목 등수까지 보입니다");
        // 범례는 종합 점수와 등수, 아래에는 수주 업체
        assertTrue(page.contains("종합 45점 · 1등"), "18 + 27");
        assertTrue(page.contains("종합 40점 · 2등"), "19 + 21");
        assertTrue(page.contains(">우리 회사</span>"));
        assertTrue(page.contains("<h3>평가 항목별 점수</h3>"));
        assertTrue(page.contains("class=\"evl-win\""));
    }

    @Test
    void 평가표가_없으면_그래프_대신_안내가_나온다() throws Exception {
        jdbc.update("UPDATE TB_BID_PBANC SET PTCP_SHP_CD = 'PF010' WHERE PBANC_SN = ?", won);
        String page = page(mgmt, "/bid-history?bid=" + won);
        assertTrue(page.contains("평가표 엑셀이 올라오지 않아 항목별 점수가 없습니다."));
        assertFalse(page.contains("class=\"score-chart\""));
        assertFalse(page.contains("단위: 점"));
    }

    @Test
    void 컨소시엄_참여는_점수가_없어_공고_기본_정보를_넓게_쓴다() throws Exception {
        String page = page(mgmt, "/bid-history?bid=" + won); // 가상 공고는 컨소시엄 참여다
        assertTrue(page.contains("<h3>공고 기본 정보</h3>"));
        assertTrue(page.contains("<h3>장단점 분석</h3>"));
        assertFalse(page.contains("<h3>점수</h3>"));
        assertFalse(page.contains("<h3>평가 항목별 점수</h3>"));
        assertFalse(page.contains("class=\"two even\""));
    }

    @Test
    void 팀장은_연도만_고르고_다른_팀을_주소로_넣어도_자기_팀만_본다() throws Exception {
        MockHttpSession leader = login(LEADER);
        String form = scopeForm(listPart(page(leader, "/bid-history?year=2019")));
        assertTrue(form.contains("name=\"year\""));
        assertTrue(form.contains(yearOption("2019")));
        assertFalse(form.contains("name=\"team\""), "팀장에게는 팀 칸이 없습니다");

        // 다른 팀 공고가 그 팀 번호로 들어와도 보이지 않고, 자기 팀 사업이 보인다
        Long others = insertNotice("R26BK99990049", "(가상) 다른 팀 2019 사업", "PS070", "DATE '2019-02-28'", otherTeam());
        String left = listPart(page(leader, "/bid-history?team=" + otherTeam() + "&year=2019"));
        assertFalse(left.contains("bid=" + others), left);
        assertTrue(left.contains(WON_NM), left);
    }

    @Test
    void 사업을_고르지_않으면_목록_맨_위_사업을_보여_준다() throws Exception {
        // 2019년: 진행 중(제출 완료)이 맨 위라 공고 기본 정보와 안내 문구가 나온다
        String page = page(mgmt, scope("2019"));
        assertTrue(listPart(page).matches("(?s).*bid-item on\"[^>]*bid=" + submitted + "\".*"), listPart(page));
        assertEquals(1, count(listPart(page), "bid-item on"));
        assertTrue(page.contains("<h2>" + SUBMITTED_NM + "</h2>"));
        assertEquals(3, count(page, IN_PROGRESS_MSG));
        assertTrue(page.contains("href=\"/results?bid=" + submitted + "\""));

        // 2018년: 진행 중이 없으면 지난 사업 맨 위
        String past = page(mgmt, scope("2018"));
        assertTrue(listPart(past).matches("(?s).*bid-item on\"[^>]*bid=" + failed + "\".*"), listPart(past));
        assertTrue(past.contains("<h2>" + FAILED_NM + "</h2>"));
    }

    @Test
    void 통계는_없어지고_권한은_경영지원실과_팀장만이다() throws Exception {
        String page = page(mgmt, scope("2019"));
        for (String gone : new String[] { "전체 종합", "연도별 참여·수주", "연도별 수주 금액", "참여 형태별 수주율", "자주 만난", "class=\"chart\"", "statsScope", "data-scope" }) {
            assertFalse(page.contains(gone), gone);
        }
        assertTrue(page.contains("사업 이력 관리</h1>"), "페이지 제목");

        // 예전 주소는 없다
        for (String old : new String[] { "/stats", "/stats/export", "/stats/bids/" + won, "/bid-history/export", "/bid-history/bids/" + won + "/final-file" }) {
            mvc.perform(get(old).session(mgmt)).andExpect(r -> assertEquals(404, r.getResponse().getStatus(), old));
        }
        mvc.perform(get("/bid-history").session(login(SALES)))
                .andExpect(r -> assertEquals(403, r.getResponse().getStatus()));
        assertEquals(200, mvc.perform(get("/bid-history").session(login(LEADER))).andReturn().getResponse().getStatus());
    }

    @Test
    void 연도_없이_바로_오면_그_사업의_연도_목록에서_강조한다() throws Exception {
        // 평가·결과 입력의 [사업별 결과 분석] 버튼처럼 연도 없이 온다
        String list = listPart(page(mgmt, "/bid-history?bid=" + won));
        assertTrue(list.contains(yearOption("2019")));
        assertTrue(list.matches("(?s).*bid-item on\"[^>]*bid=" + won + "\".*"), list);
    }

    @Test
    void 평가_결과_입력은_메뉴와_사업건_목록이_없고_사업_이력_관리에서_들어간다() throws Exception {
        for (MockHttpSession session : new MockHttpSession[] { mgmt, login(LEADER) }) {
            String page = page(session, "/results?bid=" + submitted);
            // 머리: 사업명과 그 사업의 이력으로 돌아가는 버튼
            assertTrue(page.contains("<h2>" + SUBMITTED_NM + "</h2>"));
            assertTrue(page.contains("href=\"/bid-history?bid=" + submitted + "\""));
            assertFalse(page.contains("사업건 목록"));
            assertFalse(page.contains("bid-item"));
            // 평가표를 올리지 않는 사업이라 왼쪽 칸 없이 넓게 보인다
            assertFalse(page.contains("class=\"split\""));
            assertFalse(page.contains("href=\"/results\""));
            // 메뉴에는 없고 사업 이력 관리에 불이 들어온다
            assertTrue(page.matches("(?s).*<a href=\"/bid-history\" class=\"on\">사업 이력 관리</a>.*"));
        }

        // 사업건 없이 오면 고를 수 있는 사업 이력 관리로 보낸다 (대시보드의 [결과 관리] 등)
        mvc.perform(get("/results").session(mgmt))
                .andExpect(r -> assertEquals("/bid-history", r.getResponse().getRedirectedUrl()));
    }

    @Test
    void 메뉴는_사업_이력_관리_일정_관리_순서다() throws Exception {
        String nav = page(mgmt, "/dashboard");
        int history = nav.indexOf(">사업 이력 관리</a>");
        int schedule = nav.indexOf(">일정 관리</a>");
        assertTrue(history >= 0 && history < schedule, "사업 이력 관리가 일정 관리보다 앞이어야 합니다");
        assertFalse(nav.contains(">통계</a>"));
        assertFalse(nav.contains(">평가·결과 입력</a>"));
        assertTrue(nav.indexOf(">사용자 관리</a>") > schedule, "사용자 관리는 그대로 있어야 합니다");
    }

    private String scope(String year) {
        return "/bid-history?team=" + teamSn + "&year=" + year;
    }

    /** 목록 줄의 주소 (화면에는 &amp; 로 나온다) */
    private String link(String year, Long sn) {
        return "/bid-history?team=" + teamSn + "&amp;year=" + year + "&amp;bid=" + sn;
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

    /** 사업 목록 블록의 고른 연도 사업 (드롭다운 아래) */
    private static String yearPart(String page) {
        String list = listPart(page);
        return list.substring(list.indexOf("class=\"bid-body year-body\""));
    }

    /** 사업 목록 블록의 연도·팀 드롭다운 */
    private static String scopeForm(String list) {
        int i = list.indexOf("<form class=\"scope-form\"");
        assertTrue(i >= 0, "연도·팀 드롭다운이 없습니다");
        return list.substring(i, list.indexOf("</form>", i));
    }

    /** 연도 드롭다운에서 골라져 있는 칸 */
    private static String yearOption(String year) {
        return "<option value=\"" + year + "\" selected=\"selected\">" + year + "년</option>";
    }

    /** 평가표 엑셀을 올린 개찰 업체 한 줄 */
    private Long company(Long sn, String bizNo, String coNm, int rank, String ourCoYn, String scsbdYn) {
        jdbc.update("INSERT INTO TB_CO_EVL_RSLT (PBANC_SN, BIZ_NO, CO_NM, OPENG_RNK, OUR_CO_YN, SCSBD_YN, EVL_TBL_UPLD_YN) VALUES (?, ?, ?, ?, ?, ?, 'Y')",
                sn, bizNo, coNm, rank, ourCoYn, scsbdYn);
        return jdbc.queryForObject("SELECT CO_EVL_RSLT_SN FROM TB_CO_EVL_RSLT WHERE PBANC_SN = ? AND BIZ_NO = ?", Long.class, sn, bizNo);
    }

    private Long article(Long sn, String nm, double max, int sort) {
        jdbc.update("INSERT INTO TB_EVL_ARTCL (PBANC_SN, EVL_ARTCL_NM, EVL_SCOR_SCR, SORT_SEQ) VALUES (?, ?, ?, ?)", sn, nm, max, sort);
        return jdbc.queryForObject("SELECT EVL_ARTCL_SN FROM TB_EVL_ARTCL WHERE PBANC_SN = ? AND EVL_ARTCL_NM = ?", Long.class, sn, nm);
    }

    private void score(Long coEvlRsltSn, Long artclSn, double scr) {
        jdbc.update("INSERT INTO TB_EVL_CMSNR_SCR (CO_EVL_RSLT_SN, EVL_ARTCL_SN, CMSNR_NO, EVL_SCR) VALUES (?, ?, 1, ?)", coEvlRsltSn, artclSn, scr);
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
