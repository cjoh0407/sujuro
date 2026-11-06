package com.ojj.sujuro.bid.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/**
 * 공고 관리 목록 (11/06): 주관팀 배정 필터, 전체 누적 순번, 공고명 칸.
 * 로컬 Oracle 로 화면까지 돌리고 끝나면 모두 되돌린다(@Transactional).
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=NoticeListPageDbTest
 *
 * 공고는 모두 가상이다. 번호는 실제 나라장터 번호와 겹치지 않게 9999 로 시작한다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class NoticeListPageDbTest {

    /** 테스트 계정 (db/03_test_data.sql). 비밀번호는 db/README.md 의 공용 값 */
    private static final String MGMT = "2022-0009";
    private static final String PASSWORD = "Demo!2026a";

    /** 가상 공고만 걸러 보는 검색어 */
    private static final String KEYWORD = "목록확인사업";

    /** 번호 칸. 옆의 공고번호 칸도 같은 모양이지만 숫자만 있는 칸은 번호 칸뿐이다 */
    private static final Pattern ROW_NO = Pattern.compile("<td class=\"mono faint\">(\\d+)</td>");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private G2bApiClient api;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = (MockHttpSession) mvc.perform(formLogin("/login").userParameter("empNo").passwordParam("password")
                .user(MGMT).password(PASSWORD))
                .andExpect(r -> assertEquals("/dashboard", r.getResponse().getRedirectedUrl()))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void 주관팀_필터는_배정과_미배정으로_거르고_탭과_페이지_주소에도_이어진다() throws Exception {
        insertNotice("R26BK99990061", "(가상) " + KEYWORD + " 배정");
        jdbc.update("UPDATE TB_BID_PBANC SET SPRVSN_TEAM_SN = (SELECT MIN(TEAM_SN) FROM TB_TEAM WHERE USE_YN = 'Y') WHERE BID_PBANC_NO = 'R26BK99990061'");
        insertNotice("R26BK99990062", "(가상) " + KEYWORD + " 미배정");

        String assigned = list("&assign=assigned");
        assertTrue(assigned.contains(KEYWORD + " 배정"));
        assertFalse(assigned.contains(KEYWORD + " 미배정"));
        assertTrue(assigned.contains("<option value=\"assigned\" selected=\"selected\">주관팀 배정</option>"), "고른 값이 남아 있습니다");
        assertTrue(assigned.contains("assign=assigned"), "탭·페이지 주소에도 이어집니다");
        assertTrue(assigned.contains("총 1건"));

        String unassigned = list("&assign=unassigned");
        assertFalse(unassigned.contains(KEYWORD + " 배정"));
        assertTrue(unassigned.contains(KEYWORD + " 미배정"));

        for (String all : new String[] { list(""), list("&assign=이상한값") }) {
            assertTrue(all.contains(KEYWORD + " 배정"));
            assertTrue(all.contains(KEYWORD + " 미배정"));
            assertTrue(all.contains("총 2건"));
        }
    }

    @Test
    void 정렬_드롭다운은_없고_최신_등록_순이다() throws Exception {
        insertNotice("R26BK99990063", "(가상) " + KEYWORD + " 먼저 등록");
        insertNotice("R26BK99990064", "(가상) " + KEYWORD + " 나중 등록");

        String page = list("");
        assertFalse(page.contains("name=\"sort\""));
        assertFalse(page.contains("마감일 빠른 순"));
        assertFalse(page.contains("sort="), "주소에도 정렬이 남지 않습니다");
        assertTrue(page.contains("<select name=\"assign\""));
        assertTrue(page.indexOf(KEYWORD + " 나중 등록") < page.indexOf(KEYWORD + " 먼저 등록"));

        // 예전 주소를 북마크해 뒀어도 순서는 같다
        String old = list("&sort=no");
        assertTrue(old.indexOf(KEYWORD + " 나중 등록") < old.indexOf(KEYWORD + " 먼저 등록"));
    }

    @Test
    void 번호는_전체_누적_순번이라_2페이지는_11부터다() throws Exception {
        for (int i = 1; i <= 12; i++) {
            insertNotice(String.format("R26BK999901%02d", i), "(가상) " + KEYWORD + " " + i + "번째");
        }

        String first = list("");
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), rowNos(first));

        String second = list("&page=2");
        assertEquals(List.of(11, 12), rowNos(second));
        assertTrue(second.contains("총 12건 · 2 / 2 페이지"));

        // 번호가 PBANC_SN 이 아니어도 상세 주소는 그대로 공고 PK 다
        Long sn = jdbc.queryForObject("SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK99990101'", Long.class);
        assertTrue(second.contains("href=\"/notices/" + sn + "\""));
    }

    @Test
    void 공고명_칸은_머리와_본문_모두_표시용_클래스가_있다() throws Exception {
        insertNotice("R26BK99990065", "(가상) " + KEYWORD + " 칸 확인");

        String page = list("");
        assertTrue(page.contains("<th class=\"c-name\">공고명</th>"));
        assertTrue(page.contains("<td class=\"c-name\""));
    }

    private List<Integer> rowNos(String page) {
        List<Integer> nos = new ArrayList<>();
        Matcher m = ROW_NO.matcher(page);
        while (m.find()) {
            nos.add(Integer.valueOf(m.group(1)));
        }
        return nos;
    }

    /** 배정 전 가상 공고 */
    private void insertNotice(String bidNo, String name) {
        String salesId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE AUTHRT_CD = ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, Role.CD_SALES);
        jdbc.update("INSERT INTO TB_BID_PBANC (BID_PBANC_NO, PBANC_STEP_CD, PBANC_NM, ORDER_INST_NM, ALTMNT_BGT_AMT, PBANC_STTS_CD, RGTR_ID) "
                + "VALUES (?, 'PP030', ?, '(가상) 시험기관', 500000000, 'PS010', ?)", bidNo, name, salesId);
    }

    /** 가상 공고만 보이게 공고명으로 검색한 목록 */
    private String list(String query) throws Exception {
        return mvc.perform(get("/notices?keyword={keyword}" + query, KEYWORD).session(session))
                .andExpect(r -> assertEquals(200, r.getResponse().getStatus()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
