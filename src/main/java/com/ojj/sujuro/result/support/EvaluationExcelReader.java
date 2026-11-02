package com.ojj.sujuro.result.support;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.result.dto.EvaluationForm;

/**
 * 평가표 엑셀을 읽어 입력 폼(EvaluationForm)을 채운다 (FR-078·079). 저장은 하지 않는다. 팀장이 확인한 뒤 저장한다.
 *
 * 나라장터 개찰결과 → 기술평가점수 조회 → [엑셀다운로드] 파일 모양 (실물 확인 2026-11-02, 시트 '평가결과'):
 *   1행  평가항목 | 배점기준 | 평가위원1 | 평가위원2 | ... | 평가위원7
 *   2행~ 전략 및 방법론(15) | 15 | 14.5 | 14.0 | ...
 * 머리 줄은 앞쪽 몇 줄에서 찾는다(위에 제목 줄이 붙어도 읽는다). 항목 이름이 빈 줄이나 합계 줄에서 멈춘다.
 * 배점 칸이 비면 이름 끝의 "(15)"에서 꺼낸다. 엑셀 안에는 업체명이 없고 파일 이름 끝 "[ 업체명 ]"에만 있다.
 */
public final class EvaluationExcelReader {

    /** 머리 줄을 찾을 앞쪽 줄 수 */
    private static final int HEADER_SCAN_ROWS = 10;
    /** 항목 줄을 읽을 최대 줄 수. 입력은 30개까지라(MAX_ITEMS) 넘치면 저장할 때 걸린다 */
    private static final int MAX_ROWS = 50;
    private static final int MAX_CMSNR = 20;

    private static final Pattern CMSNR_HEAD = Pattern.compile("^평가위원\\d+$");
    private static final Pattern MAX_SUFFIX = Pattern.compile("\\(\\s*(\\d+(?:\\.\\d+)?)\\s*\\)\\s*$");
    private static final Pattern TOTAL_ROW = Pattern.compile("^(합계|계|총점|소계)$");
    /** 파일 이름 끝의 "[ 엑사아이엔티 주식회사 ]" */
    private static final Pattern FILE_COMPANY = Pattern.compile("\\[\\s*([^\\[\\]]+?)\\s*\\]\\s*(?:\\.[A-Za-z]+)?$");

    private EvaluationExcelReader() {
    }

    /** 엑셀(xls·xlsx)을 읽어 항목·배점·위원별 점수를 채운 폼. 모양이 다르면 BusinessException(이유) */
    public static EvaluationForm read(InputStream in) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                EvaluationForm f = readSheet(wb.getSheetAt(s));
                if (f != null) {
                    return f;
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("엑셀 파일을 열지 못했습니다. 아래에 직접 입력하세요.");
        }
        throw new BusinessException("엑셀에서 '평가항목 · 배점기준 · 평가위원1…' 머리 줄을 찾지 못했습니다. 아래에 직접 입력하세요.");
    }

    /** 이 시트에 평가표가 있으면 폼, 없으면 null */
    private static EvaluationForm readSheet(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS);
        for (int r = sheet.getFirstRowNum(); r <= last; r++) {
            Row head = sheet.getRow(r);
            if (head == null) {
                continue;
            }
            int nmCol = -1;
            int maxCol = -1;
            List<Integer> cmsnrCols = new ArrayList<>();
            for (Cell c : head) {
                String t = squeeze(text(c));
                if (t.equals("평가항목")) {
                    nmCol = c.getColumnIndex();
                } else if (t.startsWith("배점")) {
                    maxCol = c.getColumnIndex();
                } else if (CMSNR_HEAD.matcher(t).matches() && cmsnrCols.size() < MAX_CMSNR) {
                    cmsnrCols.add(c.getColumnIndex());
                }
            }
            if (nmCol >= 0 && !cmsnrCols.isEmpty()) {
                return readRows(sheet, r + 1, nmCol, maxCol, cmsnrCols);
            }
        }
        return null;
    }

    private static EvaluationForm readRows(Sheet sheet, int from, int nmCol, int maxCol, List<Integer> cmsnrCols) {
        EvaluationForm f = new EvaluationForm();
        f.setCmsnrCnt(String.valueOf(cmsnrCols.size()));
        for (int r = from; r <= sheet.getLastRowNum() && r < from + MAX_ROWS; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                break;
            }
            String nm = text(row.getCell(nmCol)).trim();
            if (nm.isEmpty() || TOTAL_ROW.matcher(squeeze(nm)).matches()) {
                break;
            }
            EvaluationForm.Row item = new EvaluationForm.Row();
            item.setNm(nm);
            String max = maxCol < 0 ? "" : text(row.getCell(maxCol)).trim();
            if (max.isEmpty()) {
                Matcher m = MAX_SUFFIX.matcher(nm);
                max = m.find() ? m.group(1) : "";
            }
            item.setMax(max);
            for (int col : cmsnrCols) {
                item.getScores().add(text(row.getCell(col)).trim());
            }
            f.getItems().add(item);
        }
        if (f.getItems().isEmpty()) {
            throw new BusinessException("엑셀의 머리 줄 아래에 평가 항목이 없습니다. 아래에 직접 입력하세요.");
        }
        return f;
    }

    /** 파일 이름 끝 "[ 업체명 ]"의 업체명. 없으면 null */
    public static String companyOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        Matcher m = FILE_COMPANY.matcher(fileName.trim());
        return m.find() ? m.group(1).trim() : null;
    }

    /** 업체명이 같은지. 띄어쓰기와 "주식회사·(주)·㈜" 표기 차이는 무시한다 */
    public static boolean sameCompany(String a, String b) {
        return companyKey(a).equals(companyKey(b));
    }

    private static String companyKey(String s) {
        return s == null ? "" : s.replace("주식회사", "").replace("(주)", "").replace("㈜", "").replaceAll("\\s+", "");
    }

    /** 칸의 값을 화면에 넣을 글자로. 숫자는 "14.0" → "14" 처럼 끝의 0을 뗀다 */
    private static String text(Cell c) {
        if (c == null) {
            return "";
        }
        CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        switch (type) {
            case NUMERIC:
                return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case STRING:
                return c.getStringCellValue();
            case BOOLEAN:
                return String.valueOf(c.getBooleanCellValue());
            default:
                return "";
        }
    }

    private static String squeeze(String s) {
        return s.replaceAll("\\s+", "");
    }
}
