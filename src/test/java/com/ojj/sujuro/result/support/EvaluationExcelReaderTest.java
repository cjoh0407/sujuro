package com.ojj.sujuro.result.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.result.dto.EvaluationForm;

class EvaluationExcelReaderTest {

    /** 나라장터 기술평가점수 조회 [엑셀다운로드] 모양 (R26BK01541118 엑사아이엔티 평가표 앞 두 줄. 배점은 글자, 점수는 숫자) */
    private static final Object[][] G2B = {
            {"평가항목", "배점기준", "평가위원1", "평가위원2", "평가위원3"},
            {"전략 및 방법론(15)", "15", 14.5, 14.0, 15.0},
            {"기술 및 기능(30)", "30", 27.9, 28.4, 28.4},
    };

    @Test
    void 나라장터_평가표의_항목_배점_위원별_점수를_읽는다() {
        EvaluationForm f = EvaluationExcelReader.read(xlsx(G2B));

        assertEquals("3", f.getCmsnrCnt());
        assertEquals(2, f.getItems().size());
        EvaluationForm.Row first = f.getItems().get(0);
        assertEquals("전략 및 방법론(15)", first.getNm()); // "(15)"는 저장할 때 뗀다(ResultEntryServiceImpl.articleName)
        assertEquals("15", first.getMax());
        assertEquals(Arrays.asList("14.5", "14", "15"), first.getScores()); // 14.0 → 14
        assertEquals("27.9", f.getItems().get(1).scoreAt(0));
    }

    @Test
    void 옛_엑셀_형식_xls도_읽는다() {
        EvaluationForm f = EvaluationExcelReader.read(write(new HSSFWorkbook(), G2B));
        assertEquals(2, f.getItems().size());
        assertEquals("28.4", f.getItems().get(1).scoreAt(2));
    }

    @Test
    void 위의_제목_줄은_건너뛰고_빈_줄이나_합계_줄에서_멈춘다() {
        EvaluationForm f = EvaluationExcelReader.read(xlsx(new Object[][] {
                {"기술평가점수 조회"},
                {},
                {"평가 항목", "배점", "평가위원 1", "평가위원 2"},
                {"전략", "", 9.5, 10},
                {"합 계", "", 9.5, 10},
                {"남은 줄", "5", 1, 1},
        }));
        assertEquals("2", f.getCmsnrCnt());
        assertEquals(1, f.getItems().size());
        assertEquals("", f.getItems().get(0).getMax()); // 이름에도 배점 표시가 없으면 비워 둔다(저장할 때 걸린다)
    }

    @Test
    void 배점_칸이_비면_이름_끝의_배점을_쓴다() {
        EvaluationForm f = EvaluationExcelReader.read(xlsx(new Object[][] {
                {"평가항목", "배점기준", "평가위원1"},
                {"성능 및 품질(25.5)", "", 24},
        }));
        assertEquals("25.5", f.getItems().get(0).getMax());
    }

    @Test
    void 모양이_다르거나_엑셀이_아니면_이유를_알린다() {
        assertTrue(message(() -> EvaluationExcelReader.read(xlsx(new Object[][] {{"순위", "업체명", "점수"}, {1, "가", 90}})))
                .contains("머리 줄을 찾지 못했습니다"));
        assertTrue(message(() -> EvaluationExcelReader.read(xlsx(new Object[][] {{"평가항목", "배점기준", "평가위원1"}})))
                .contains("평가 항목이 없습니다"));
        assertTrue(message(() -> EvaluationExcelReader.read(new ByteArrayInputStream("not excel".getBytes(StandardCharsets.UTF_8))))
                .contains("열지 못했습니다"));
    }

    @Test
    void 파일_이름에서_업체명을_꺼내고_표기_차이는_무시한다() {
        assertEquals("엑사아이엔티 주식회사",
                EvaluationExcelReader.companyOf("20261029_1756_고용노동 데이터분석 서비스 개선 사업_[ 엑사아이엔티 주식회사 ].xlsx"));
        assertNull(EvaluationExcelReader.companyOf("평가결과.xlsx"));
        assertTrue(EvaluationExcelReader.sameCompany("엑사아이엔티 주식회사", "엑사아이엔티(주)"));
        assertTrue(EvaluationExcelReader.sameCompany("㈜지엔소프트", "지엔소프트(주)"));
        assertFalse(EvaluationExcelReader.sameCompany("엑사아이엔티 주식회사", "지엔소프트(주)"));
    }

    private static String message(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(BusinessException.class, call).getMessage();
    }

    static InputStream xlsx(Object[][] rows) {
        return write(new XSSFWorkbook(), rows);
    }

    private static InputStream write(Workbook wb, Object[][] rows) {
        try (Workbook w = wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = w.createSheet("평가결과");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v instanceof Number) {
                        row.createCell(c).setCellValue(((Number) v).doubleValue());
                    } else {
                        row.createCell(c).setCellValue((String) v);
                    }
                }
            }
            w.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
