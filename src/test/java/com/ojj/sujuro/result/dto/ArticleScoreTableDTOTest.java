package com.ojj.sujuro.result.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * 사업별 결과 분석의 평가 항목별 점수 표 (11/03): 업체별 점수·항목 등수·종합 점수 등수, 종합 점수 1등을 수주 업체로.
 * 점수는 테스트 데이터(db/03_test_data.sql 9번 사업)의 대분류 점수를 빌렸다.
 */
class ArticleScoreTableDTOTest {

    @Test
    void 항목마다_등수를_매기고_종합_점수_1등을_수주_업체로_본다() {
        ArticleScoreTableDTO t = ArticleScoreTableDTO.of(Arrays.asList(
                s(21L, "(테스트) 우리 회사", "Y", "전략 및 방법론", 1, 15, 14.4285714),
                s(21L, "(테스트) 우리 회사", "Y", "기술 및 기능", 2, 30, 28.2),
                s(22L, "(테스트) 경쟁사 A", "N", "전략 및 방법론", 1, 15, 14.5),
                s(22L, "(테스트) 경쟁사 A", "N", "기술 및 기능", 2, 30, 28.0285714)));

        assertEquals(Arrays.asList("전략 및 방법론", "기술 및 기능"),
                t.getRows().stream().map(ArticleScoreTableDTO.Row::getCatNm).collect(Collectors.toList()));
        assertEquals(Arrays.asList(2, 1), ranks(t.getRows().get(0)));   // 전략 및 방법론: 경쟁사 A 가 앞섬
        assertEquals(Arrays.asList(1, 2), ranks(t.getRows().get(1)));
        assertEquals("14.429", t.getRows().get(0).getCells().get(0).getText());
        assertEquals("45", t.getMaxTotalText());

        assertEquals("42.629", t.getColumns().get(0).getTotalText());  // 14.4285714 + 28.2
        assertEquals(1, t.getColumns().get(0).getTotalRank());
        assertEquals("(테스트) 우리 회사", t.getWinnerNm());
        assertEquals("42.629", t.getWinnerTotalText());
    }

    @Test
    void 같은_점수는_같은_등수이고_1등이_여럿이면_모두_수주_업체로_보인다() {
        ArticleScoreTableDTO t = ArticleScoreTableDTO.of(Arrays.asList(
                s(1L, "가", "Y", "기술", 1, 10, 9.0),
                s(2L, "나", "N", "기술", 1, 10, 9.0004),   // 소수 셋째 자리까지 같으면 같은 점수
                s(3L, "다", "N", "기술", 1, 10, 8.0)));

        assertEquals(Arrays.asList(1, 1, 3), ranks(t.getRows().get(0)));
        assertEquals("가, 나", t.getWinnerNm());
    }

    @Test
    void 업체가_하나뿐이면_등수와_수주_업체를_매기지_않는다() {
        ArticleScoreTableDTO t = ArticleScoreTableDTO.of(Arrays.asList(s(21L, "우리", "Y", "기술", 1, 10, 9.0)));

        assertFalse(t.isRanked());
        assertNull(t.getRows().get(0).getCells().get(0).getRank());
        assertNull(t.getColumns().get(0).getTotalRank());
        assertNull(t.getWinnerNm());
    }

    @Test
    void 평가표가_없으면_빈_표다() {
        assertTrue(ArticleScoreTableDTO.of(new ArrayList<>()).isEmpty());
    }

    private static List<Integer> ranks(ArticleScoreTableDTO.Row row) {
        return row.getCells().stream().map(ArticleScoreTableDTO.Cell::getRank).collect(Collectors.toList());
    }

    private static CompanyCategoryScoreDTO s(Long co, String nm, String ours, String cat, int ord, double max, double score) {
        CompanyCategoryScoreDTO d = new CompanyCategoryScoreDTO();
        d.setCoEvlRsltSn(co);
        d.setCoNm(nm);
        d.setOurCoYn(ours);
        d.setCatNm(cat);
        d.setOrd(ord);
        d.setMaxSum(max);
        d.setScoreSum(score);
        return d;
    }
}
