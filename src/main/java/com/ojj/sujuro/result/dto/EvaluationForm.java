package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 평가표 직접 입력 (경영지원실). 나라장터 기술평가점수 조회의 엑셀을 올린 뒤, 그 엑셀을 보며
 * 평가 항목·배점·평가위원별 점수를 옮겨 적는다. 우리 회사 점수만 적는다.
 * 숫자 검사는 ResultEntryServiceImpl 이 맡아 문자열로 받는다. 틀리면 입력한 그대로 다시 채워 준다.
 *
 * 화면 이름: cmsnrCnt, items[0].nm, items[0].max, items[0].scores[0] ...
 */
@Getter
@Setter
public class EvaluationForm {

    /** 화면에 처음 보여 줄 빈 줄 수와 위원 수 */
    public static final int DEFAULT_ROWS = 5;
    public static final int DEFAULT_CMSNR = 3;

    private String cmsnrCnt;
    private List<Row> items = new ArrayList<>();

    /** 평가 항목 한 줄 */
    @Getter
    @Setter
    public static class Row {
        private String nm;
        private String max;
        private List<String> scores = new ArrayList<>();

        /** 화면에서 위원 칸 j 의 값. 없으면 빈 칸 */
        public String scoreAt(int j) {
            return j < scores.size() && scores.get(j) != null ? scores.get(j) : "";
        }

        boolean isBlank() {
            return blank(nm) && blank(max) && scores.stream().allMatch(EvaluationForm::blank);
        }
    }

    /** 위원 수. 숫자가 아니면 기본값 */
    public int getCmsnrCount() {
        try {
            int n = Integer.parseInt(cmsnrCnt == null ? "" : cmsnrCnt.trim());
            return n > 0 ? n : DEFAULT_CMSNR;
        } catch (NumberFormatException e) {
            return DEFAULT_CMSNR;
        }
    }

    /** 다 비어 있는 줄을 뺀 항목 */
    public List<Row> getFilledItems() {
        List<Row> rows = new ArrayList<>();
        for (Row r : items) {
            if (!r.isBlank()) {
                rows.add(r);
            }
        }
        return rows;
    }

    /** 저장된 항목·점수로 입력 칸을 채운다. 저장된 것이 없으면 empty() */
    public static EvaluationForm fromSaved(List<EvaluationScoreDTO> saved) {
        if (saved == null || saved.isEmpty()) {
            return empty();
        }
        EvaluationForm f = new EvaluationForm();
        Map<Long, Row> byArticle = new LinkedHashMap<>();
        int cmsnr = 0;
        for (EvaluationScoreDTO s : saved) {
            Row r = byArticle.get(s.getEvlArtclSn());
            if (r == null) {
                r = new Row();
                r.setNm(s.getEvlArtclNm());
                r.setMax(text(s.getEvlScorScr()));
                byArticle.put(s.getEvlArtclSn(), r);
            }
            if (s.getCmsnrNo() != null && s.getCmsnrNo() > 0) {
                int j = s.getCmsnrNo() - 1;
                while (r.getScores().size() <= j) {
                    r.getScores().add("");
                }
                r.getScores().set(j, text(s.getEvlScr()));
                cmsnr = Math.max(cmsnr, s.getCmsnrNo());
            }
        }
        f.getItems().addAll(byArticle.values());
        f.setCmsnrCnt(String.valueOf(cmsnr > 0 ? cmsnr : DEFAULT_CMSNR));
        return f;
    }

    private static String text(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    /** 아무것도 입력하지 않은 화면: 빈 줄 5개, 위원 3명 */
    public static EvaluationForm empty() {
        EvaluationForm f = new EvaluationForm();
        f.setCmsnrCnt(String.valueOf(DEFAULT_CMSNR));
        for (int i = 0; i < DEFAULT_ROWS; i++) {
            f.getItems().add(new Row());
        }
        return f;
    }

    static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
