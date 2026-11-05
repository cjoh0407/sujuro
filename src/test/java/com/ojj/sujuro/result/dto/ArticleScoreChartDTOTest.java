package com.ojj.sujuro.result.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** 평가 항목별 점수 그래프 (11/05): 항목마다 업체 수만큼 막대, 항목 1등 막대만 진하게. 점수는 모두 가상 값이다. */
class ArticleScoreChartDTOTest {

    @Test
    void 항목마다_업체_수만큼_막대가_있고_그_항목_1등만_표시한다() {
        ArticleScoreChartDTO c = chart(Arrays.asList(
                s(1L, "우리", "Y", "전략", 1, 15, 14.429), s(1L, "우리", "Y", "기술", 2, 30, 28.2),
                s(2L, "가", "N", "전략", 1, 15, 14.5), s(2L, "가", "N", "기술", 2, 30, 28.0),
                s(3L, "나", "N", "전략", 1, 15, 12), s(3L, "나", "N", "기술", 2, 30, 20)));

        assertEquals(2, c.getGroups().size());
        for (ArticleScoreChartDTO.Group g : c.getGroups()) {
            assertEquals(3, g.getBars().size());
            assertEquals(Arrays.asList("co-ours", "co-1", "co-2"), classes(g), "색은 모든 항목에서 업체마다 같습니다");
        }
        assertEquals(Arrays.asList(false, true, false), firsts(c.getGroups().get(0)), "전략은 가 가 1등이라 우리 회사도 흐립니다");
        assertEquals(Arrays.asList(true, false, false), firsts(c.getGroups().get(1)), "기술은 우리 회사가 1등입니다");
        ArticleScoreChartDTO.Bar rival = c.getGroups().get(0).getBars().get(1);
        assertEquals("14.5", rival.getValue());
        assertEquals("가 · 전략 14.5 / 15 · 1등", rival.getTitle());
        assertEquals("우리", c.getCompanies().get(0).getCoNm());
        assertEquals(1, c.getCompanies().get(0).getTotalRank()); // 14.429 + 28.2 = 42.629
        assertTrue(c.getAriaLabel().contains("전략(배점 15): 우리 14.429, 가 14.5, 나 12"), c.getAriaLabel());
    }

    @Test
    void 눈금은_0부터_가장_큰_배점까지_깔끔하게_올린다() {
        assertEquals(Arrays.asList("0", "10", "20", "30"), ticks(30, 28));
        assertEquals(Arrays.asList("0", "5", "10", "15", "20", "25"), ticks(25, 24));
        assertEquals(Arrays.asList("0", "5", "10", "15", "20"), ticks(16, 15)); // 16 → 20 까지
        assertEquals(Arrays.asList("0", "2", "4", "6", "8", "10"), ticks(10, 9));
        assertEquals(Arrays.asList("0", "10", "20", "30", "40"), ticks(30, 35), "점수가 배점을 넘어도 막대가 그래프 안에 있습니다");
    }

    @Test
    void 키는_점수에_비례하고_점수가_없으면_막대와_값이_없다() {
        ArticleScoreChartDTO c = chart(Arrays.asList(
                s(1L, "우리", "Y", "전략", 1, 20, 20), s(1L, "우리", "Y", "기술", 2, 20, 10),
                s(2L, "가", "N", "전략", 1, 20, 5)));   // 가 는 기술 점수가 없다

        double full = heightOf(c.getGroups().get(0).getBars().get(0));
        double half = heightOf(c.getGroups().get(1).getBars().get(0));
        assertEquals(full / 2, half, 0.2);

        ArticleScoreChartDTO.Bar none = c.getGroups().get(1).getBars().get(1);
        assertEquals("", none.getPath());
        assertNull(none.getValue());
        assertTrue(none.getTitle().contains("- / 20"), none.getTitle());
    }

    @Test
    void 막대가_좁으면_1등과_우리_회사_값만_겹치지_않게_쓴다() {
        ArticleScoreChartDTO c = chart(narrow(5, 8, 2)); // 1등은 업체 4 (index 3)
        List<ArticleScoreChartDTO.Bar> bars = c.getGroups().get(0).getBars();
        assertTrue(bars.get(3).isFirst());
        assertNotNull(bars.get(3).getValue(), "1등 값");
        assertNotNull(bars.get(0).getValue(), "멀리 있으면 우리 회사 값도 씁니다");
        assertNull(bars.get(1).getValue(), "좁으면 다른 업체 값은 마우스로 봅니다");
        assertNull(bars.get(2).getValue());

        // 우리 회사가 1등 바로 옆이라 글자가 겹치면 1등 값만 쓴다
        List<ArticleScoreChartDTO.Bar> near = chart(narrow(5, 8, 0)).getGroups().get(0).getBars(); // 1등은 업체 2 (index 1)
        assertNotNull(near.get(1).getValue());
        assertNull(near.get(0).getValue());

        // 업체가 둘이면 넉넉해서 모두 쓴다
        ArticleScoreChartDTO two = chart(Arrays.asList(s(1L, "우리", "Y", "전략", 1, 15, 14.429), s(2L, "가", "N", "전략", 1, 15, 14.5)));
        assertNotNull(two.getGroups().get(0).getBars().get(0).getValue());
        assertNotNull(two.getGroups().get(0).getBars().get(1).getValue());
    }

    /** 업체 cos 곳 × 항목 items 개. 업체 (2 + firstAt) 가 모든 항목 1등이다 */
    private static List<CompanyCategoryScoreDTO> narrow(int cos, int items, int firstAt) {
        List<CompanyCategoryScoreDTO> scores = new ArrayList<>();
        for (long co = 1; co <= cos; co++) {
            for (int i = 1; i <= items; i++) {
                scores.add(s(co, "업체" + co, co == 1 ? "Y" : "N", "항목" + i, i, 15, co == 2 + firstAt ? 14.5 : 13.429));
            }
        }
        return scores;
    }

    @Test
    void 같은_점수면_함께_진하고_좁아도_값_글자는_겹치지_않는다() {
        ArticleScoreChartDTO tie = chart(Arrays.asList(
                s(1L, "우리", "Y", "전략", 1, 15, 14), s(2L, "가", "N", "전략", 1, 15, 14), s(3L, "나", "N", "전략", 1, 15, 12)));
        assertEquals(Arrays.asList(true, true, false), firsts(tie.getGroups().get(0)));

        // 모두 동점이면 전부 진하다. 막대가 좁으면 값은 겹치지 않는 것만 쓴다
        List<CompanyCategoryScoreDTO> scores = new ArrayList<>();
        for (long co = 1; co <= 5; co++) {
            for (int i = 1; i <= 8; i++) {
                scores.add(s(co, "업체" + co, co == 1 ? "Y" : "N", "항목" + i, i, 15, 14.429));
            }
        }
        List<ArticleScoreChartDTO.Bar> bars = chart(scores).getGroups().get(0).getBars();
        assertTrue(bars.stream().allMatch(ArticleScoreChartDTO.Bar::isFirst));
        assertNotNull(bars.get(0).getValue());
        assertNull(bars.get(1).getValue());
    }

    @Test
    void 경쟁_업체가_색보다_많으면_회색이고_우리_회사가_없어도_그린다() {
        List<CompanyCategoryScoreDTO> scores = new ArrayList<>();
        for (long co = 1; co <= 7; co++) {
            scores.add(s(co, "업체" + co, "N", "전략", 1, 15, 10 + co * 0.1));
        }
        ArticleScoreChartDTO c = chart(scores);
        assertEquals(Arrays.asList("co-1", "co-2", "co-3", "co-4", "co-5", "co-etc", "co-etc"), classes(c.getGroups().get(0)));
        assertEquals(1, firsts(c.getGroups().get(0)).stream().filter(x -> x).count());
        assertFalse(c.getCompanies().stream().anyMatch(ArticleScoreChartDTO.Company::isOurs));
    }

    @Test
    void 업체가_하나면_등수_없이_진하게_그린다() {
        ArticleScoreChartDTO c = chart(Collections.singletonList(s(1L, "우리", "Y", "전략", 1, 15, 14)));
        assertNull(c.getCompanies().get(0).getTotalRank());
        assertEquals("우리 · 전략 14 / 15", c.getGroups().get(0).getBars().get(0).getTitle());
        assertTrue(c.getGroups().get(0).getBars().get(0).isFirst());
    }

    @Test
    void 항목_이름은_칸_폭에_맞춰_두_줄까지_나눈다() {
        assertEquals(Collections.singletonList("전략 및 방법론"), ArticleScoreChartDTO.wrap("전략 및 방법론", 130));
        assertEquals(Arrays.asList("기술 및 기능", "구현"), ArticleScoreChartDTO.wrap("기술 및 기능 구현", 80));
        // 띄어쓰기가 없으면 글자에서 자르고, 두 줄을 넘으면 … 로 줄인다
        assertEquals(Arrays.asList("가나다", "라마바"), ArticleScoreChartDTO.wrap("가나다라마바", 40));
        List<String> cut = ArticleScoreChartDTO.wrap("품질 및 보안 관리 방안 수립과 운영 체계", 60);
        assertEquals(2, cut.size());
        assertTrue(cut.get(1).endsWith("…"), cut.toString());
    }

    private static double heightOf(ArticleScoreChartDTO.Bar b) {
        String[] parts = b.getPath().split(" ");
        double base = Double.parseDouble(parts[0].split(",")[1]);
        double top = Double.parseDouble(parts[2].substring(1).split(",")[1]); // Q 제어점의 y 가 막대 윗변이다
        return base - top;
    }

    private static List<String> ticks(double max, double score) {
        return chart(Collections.singletonList(s(1L, "우리", "Y", "전략", 1, max, score))).getGridLines().stream()
                .map(ArticleScoreChartDTO.GridLine::getLabel).collect(Collectors.toList());
    }

    private static List<Boolean> firsts(ArticleScoreChartDTO.Group g) {
        return g.getBars().stream().map(ArticleScoreChartDTO.Bar::isFirst).collect(Collectors.toList());
    }

    private static List<String> classes(ArticleScoreChartDTO.Group g) {
        return g.getBars().stream().map(ArticleScoreChartDTO.Bar::getCls).collect(Collectors.toList());
    }

    private static ArticleScoreChartDTO chart(List<CompanyCategoryScoreDTO> scores) {
        return ArticleScoreTableDTO.of(scores).getChart();
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
