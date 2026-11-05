package com.ojj.sujuro.result.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.notification.service.NotificationService;

/**
 * 평가·결과 입력의 평가표 입력 칸 (11/05): 올린 엑셀이 입력 칸에 자동으로 채워지고, 위원·항목은 작은 [−][+], 항목은 칸 안에서 스크롤, 내려받기는 없다.
 * 로컬 Oracle 로 화면까지 돌리고 끝나면 모두 되돌린다(@Transactional). 파일 저장소는 가짜라 디스크에 아무것도 남지 않는다.
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=EvaluationFormPageDbTest
 *
 * 공고·업체·점수는 모두 가상이다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class EvaluationFormPageDbTest {

    /** 테스트 계정 (db/README.md 의 공용 값) */
    private static final String MGMT = "2022-0009";
    private static final String LEADER = "2024-0031";
    private static final String PASSWORD = "Demo!2026a";

    private static final String EXCEL_ITEM = "value=\"전략 및 방법론(15)\"";
    private static final String SAVED_ITEM = "value=\"(가상) 저장한 항목\"";
    private static final String EXCEL_NOTE = "올린 엑셀에서 평가 항목 1개와 평가위원 2명의 점수를 읽어 채웠습니다.";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private G2bApiClient api;
    @MockBean private NotificationService notifications;
    @MockBean private FileStorage storage;

    private Long bid;
    private Long ours;
    private String salesId;

    @BeforeEach
    void data() {
        when(storage.load(any(), any())).thenReturn(new ByteArrayResource(excelBytes()));
        Long team = jdbc.queryForObject("SELECT TEAM_SN FROM TB_USER WHERE USER_ID = ?", Long.class, LEADER);
        salesId = jdbc.queryForObject("SELECT USER_ID FROM TB_USER WHERE AUTHRT_CD = ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, Role.CD_SALES);
        jdbc.update("INSERT INTO TB_BID_PBANC (BID_PBANC_NO, PBANC_STEP_CD, PBANC_NM, ORDER_INST_NM, ALTMNT_BGT_AMT, PBANC_STTS_CD, SPRVSN_TEAM_SN, PTCP_SHP_CD, BID_DDLN_DT, RGTR_ID) "
                + "VALUES ('R26BK99990070', 'PP040', '(가상) 평가표 입력 사업', '(가상) 시험기관', 500000000, 'PS060', ?, 'PF010', DATE '2019-05-30', ?)", team, salesId);
        bid = jdbc.queryForObject("SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK99990070'", Long.class);
        jdbc.update("INSERT INTO TB_BID_RSLT (PBANC_SN, BID_RSLT_CD) VALUES (?, 'BR010')", bid);
        jdbc.update("INSERT INTO TB_CO_EVL_RSLT (PBANC_SN, BIZ_NO, CO_NM, OPENG_RNK, OUR_CO_YN, SCSBD_YN, EVL_TBL_UPLD_YN) VALUES (?, '9999999900', '(가상) 우리 회사', 1, 'Y', 'Y', 'Y')", bid);
        ours = jdbc.queryForObject("SELECT CO_EVL_RSLT_SN FROM TB_CO_EVL_RSLT WHERE PBANC_SN = ?", Long.class, bid);
    }

    @Test
    void 엑셀을_새로_올리면_저장한_점수가_있어도_입력_칸에_자동으로_채운다() throws Exception {
        savedScores();
        upload(null); // 올리기만 하고 아직 저장하지 않았다

        String page = page(LEADER);
        assertTrue(page.contains(EXCEL_ITEM), "입력 칸이 엑셀 값이어야 합니다");
        assertFalse(page.contains(SAVED_ITEM));
        assertTrue(page.contains(EXCEL_NOTE));
        assertTrue(page.contains("<input type=\"hidden\" id=\"evl-cnt\" name=\"cmsnrCnt\" value=\"2\">"), "위원 수도 엑셀에서 읽습니다");
        assertTrue(page.contains(">위원2</th>"));
        assertFalse(page.contains(">위원3</th>"));
        // 점수는 팀장이 저장할 때까지 그대로다
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_EVL_CMSNR_SCR WHERE CO_EVL_RSLT_SN = ?", Integer.class, ours));
    }

    @Test
    void 저장한_적_없는_엑셀도_입력_칸에_채운다() throws Exception {
        upload(null);
        String page = page(LEADER);
        assertTrue(page.contains(EXCEL_ITEM));
        assertTrue(page.contains(EXCEL_NOTE));
    }

    @Test
    void 저장을_마치면_저장한_값을_보여_준다() throws Exception {
        savedScores();
        upload(1); // 저장하면 위원 수가 기록된다

        String page = page(LEADER);
        assertTrue(page.contains(SAVED_ITEM), "저장한 값이어야 합니다");
        assertFalse(page.contains(EXCEL_ITEM));
        assertFalse(page.contains(EXCEL_NOTE));
    }

    @Test
    void 위원과_항목은_작은_버튼이고_다시_불러오기와_내려받기와_엑셀_아이콘이_없다() throws Exception {
        savedScores();
        upload(1);

        String page = page(LEADER);
        // 위원 [−][+]: 마지막 위원 오른쪽. 1명이면 [−] 가 잠긴다. 서버로는 숨은 칸이 간다
        assertTrue(page.indexOf(">위원1</th>") < page.indexOf("id=\"evl-cm-minus\""), "마지막 위원 오른쪽에 있습니다");
        assertTrue(page.indexOf("</thead>", page.indexOf("id=\"evl-cm-minus\"")) < page.indexOf("id=\"evl-rows\""), "머리글 안에 있습니다");
        assertTrue(page.matches("(?s).*id=\"evl-cm-minus\"[^>]*disabled=\"disabled\".*"), "1명이면 줄일 수 없습니다");
        assertFalse(page.matches("(?s).*id=\"evl-cm-plus\"[^>]*disabled=\"disabled\".*"));
        assertTrue(page.contains("<input type=\"hidden\" id=\"evl-cnt\" name=\"cmsnrCnt\" value=\"1\">"), page);
        assertFalse(page.contains("type=\"number\" min=\"1\" max=\"20\""), "숫자 입력 칸은 없습니다");
        // 항목 [−][+]: 마지막 항목 왼쪽. 1줄이면 [−] 가 잠긴다
        assertTrue(page.indexOf("id=\"evl-row-ctl\"") < page.indexOf("class=\"nm\""), "항목 이름 칸 왼쪽에 있습니다");
        assertTrue(page.matches("(?s).*id=\"evl-row-minus\"[^>]*disabled=\"disabled\".*"), "1줄이면 지울 수 없습니다");
        assertFalse(page.matches("(?s).*id=\"evl-row-plus\"[^>]*disabled=\"disabled\".*"));
        // 예전 버튼은 없다
        assertFalse(page.contains("id=\"evl-add\""));
        assertFalse(page.contains("class=\"evl-step\""));

        assertTrue(page.contains("class=\"ts evl-scroll\""), "항목은 칸 안에서 스크롤합니다");
        assertFalse(page.contains("다시 불러오기"));
        assertFalse(page.contains("fromFile"));
        assertFalse(page.contains("📊"));
        assertFalse(page.contains("ic-download"));
        assertFalse(page.contains("/results/evaluation-file?"), "내려받기 링크가 없습니다");
        assertTrue(page.contains("(가상) 평가표.xlsx"), "파일 이름 글자는 남습니다");
        assertTrue(page.contains("action=\"/results/evaluation-file\""), "올리기는 그대로입니다");

        // 내려받기 주소도 없다 (같은 주소의 POST 올리기만 남아 405)
        mvc.perform(get("/results/evaluation-file?bid=" + bid).session(login(LEADER)))
                .andExpect(r -> assertEquals(405, r.getResponse().getStatus()));
    }

    @Test
    void 항목_버튼은_마지막_줄에만_있다() throws Exception {
        savedScores();
        jdbc.update("INSERT INTO TB_EVL_ARTCL (PBANC_SN, EVL_ARTCL_NM, EVL_SCOR_SCR, SORT_SEQ) VALUES (?, '(가상) 둘째 항목', 20, 2)", bid);
        Long second = jdbc.queryForObject("SELECT EVL_ARTCL_SN FROM TB_EVL_ARTCL WHERE PBANC_SN = ? AND SORT_SEQ = 2", Long.class, bid);
        jdbc.update("INSERT INTO TB_EVL_CMSNR_SCR (CO_EVL_RSLT_SN, EVL_ARTCL_SN, CMSNR_NO, EVL_SCR) VALUES (?, ?, 1, 18)", ours, second);
        upload(1);

        String page = page(LEADER);
        assertEquals(1, page.split("id=\"evl-row-ctl\"", -1).length - 1);
        int first = page.indexOf("value=\"(가상) 저장한 항목\"");
        int ctl = page.indexOf("id=\"evl-row-ctl\"");
        int last = page.indexOf("value=\"(가상) 둘째 항목\"");
        assertTrue(first >= 0 && first < ctl && ctl < last, "둘째 줄(마지막) 이름 칸 왼쪽이어야 합니다");
        assertFalse(page.matches("(?s).*id=\"evl-row-minus\"[^>]*disabled=\"disabled\".*"), "2줄이면 지울 수 있습니다");
    }

    @Test
    void 경영지원실은_입력_칸_없이_파일_이름만_보고_엑셀을_읽지_않는다() throws Exception {
        upload(null);
        String page = page(MGMT);
        assertTrue(page.contains("(가상) 평가표.xlsx"));
        assertFalse(page.contains("id=\"evl-form\""));
        assertFalse(page.contains(EXCEL_NOTE));
        assertFalse(page.contains("ic-download"));
    }

    /** 평가표 업로드 기록. 위원 수는 저장해야 채워진다 */
    private void upload(Integer cmsnrCnt) {
        jdbc.update("INSERT INTO TB_ATCH_FILE (PBANC_SN, FILE_SE_CD, ORGNL_FILE_NM, STRG_FILE_NM, FILE_PATH_NM, LAST_YN, RGTR_ID) "
                + "VALUES (?, 'FL070', '(가상) 평가표.xlsx', 'virtual.xlsx', 'evaluation/virtual', 'Y', ?)", bid, salesId);
        Long file = jdbc.queryForObject("SELECT FILE_SN FROM TB_ATCH_FILE WHERE PBANC_SN = ? AND FILE_SE_CD = 'FL070'", Long.class, bid);
        jdbc.update("INSERT INTO TB_EVL_TBL_UPLD (CO_EVL_RSLT_SN, FILE_SN, CMSNR_CNT, LAST_YN, RGTR_ID) VALUES (?, ?, ?, 'Y', ?)", ours, file, cmsnrCnt, salesId);
    }

    /** 전에 저장한 점수 한 줄 */
    private void savedScores() {
        jdbc.update("INSERT INTO TB_EVL_ARTCL (PBANC_SN, EVL_ARTCL_NM, EVL_SCOR_SCR, SORT_SEQ) VALUES (?, '(가상) 저장한 항목', 10, 1)", bid);
        Long article = jdbc.queryForObject("SELECT EVL_ARTCL_SN FROM TB_EVL_ARTCL WHERE PBANC_SN = ?", Long.class, bid);
        jdbc.update("INSERT INTO TB_EVL_CMSNR_SCR (CO_EVL_RSLT_SN, EVL_ARTCL_SN, CMSNR_NO, EVL_SCR) VALUES (?, ?, 1, 9)", ours, article);
    }

    private static byte[] excelBytes() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("평가결과");
            Row head = s.createRow(0);
            head.createCell(0).setCellValue("평가항목");
            head.createCell(1).setCellValue("배점기준");
            head.createCell(2).setCellValue("평가위원1");
            head.createCell(3).setCellValue("평가위원2");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("전략 및 방법론(15)");
            r.createCell(1).setCellValue("15");
            r.createCell(2).setCellValue(14.5);
            r.createCell(3).setCellValue(14.0);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpSession login(String empNo) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("empNo").passwordParam("password")
                .user(empNo).password(PASSWORD))
                .andExpect(r -> assertEquals("/dashboard", r.getResponse().getRedirectedUrl()))
                .andReturn().getRequest().getSession(false);
    }

    private String page(String empNo) throws Exception {
        return mvc.perform(get("/results?bid=" + bid).session(login(empNo)))
                .andExpect(r -> assertEquals(200, r.getResponse().getStatus()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
