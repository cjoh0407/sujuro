package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * 평가·결과 입력 화면(results/index.html) 한 장 (FR-077~081). 경영지원실 전용.
 * 고른 사업건 하나를 보여 준다. 결과가 등록된 사업이면 사업별 결과 분석과 같은 값(archive)과 개찰 업체 목록이 같이 온다.
 */
@Getter
@Setter
public class ResultsViewDTO {

    /** 선택 목록: 참여 확정 뒤의 사업건 (제출 완료 → 결과 등록 → 제안 작성 중 → 참여 확정 → 유찰 순) */
    private List<ResultBidDTO> bids = new ArrayList<>();
    private ResultBidDTO bid;

    /** 우리 회사 사업자번호가 설정돼 있는지. 없으면 결과를 가져올 수 없다 */
    private boolean ready;

    /** 결과 등록 사업만: 점수·평가 항목·평가표·장단점 (StatsService.findArchive) */
    private StatsArchiveDTO archive;

    /** 결과 등록 사업만: 개찰 업체 (순위 순) */
    private List<ResultCompanyDTO> companies = new ArrayList<>();

    /**
     * 우리와 비교할 업체. 우리가 1위면 2위, 아니면 1위. 개찰 목록에 우리나 비교할 업체가 없으면 null.
     * 장단점 분석의 근거로 쓴다 (총점 차이를 기술·가격으로 나눠 본다).
     */
    public Compare getCompare() {
        ResultCompanyDTO ours = companies.stream().filter(ResultCompanyDTO::isOurs).findFirst().orElse(null);
        if (ours == null || ours.getOpengRnk() == null) {
            return null;
        }
        int otherRank = ours.getOpengRnk() == 1 ? 2 : 1;
        ResultCompanyDTO other = companies.stream()
                .filter(c -> !c.isOurs() && c.getOpengRnk() != null && c.getOpengRnk() == otherRank)
                .findFirst().orElse(null);
        return other == null ? null : new Compare(ours, other);
    }

    /** 우리 - 상대 점수 차이 */
    @Getter
    public static class Compare {
        private final String otherNm;
        private final int otherRank;
        private final String totalDiff;
        private final String techDiff;
        private final String prcDiff;
        private final String orgnlDiff;

        Compare(ResultCompanyDTO ours, ResultCompanyDTO other) {
            this.otherNm = other.getCoNm();
            this.otherRank = other.getOpengRnk();
            this.totalDiff = diff(ours.getSnthsEvlScr(), other.getSnthsEvlScr());
            this.techDiff = diff(ours.getTechEvlScr(), other.getTechEvlScr());
            this.prcDiff = diff(ours.getPrcEvlScr(), other.getPrcEvlScr());
            this.orgnlDiff = diff(ours.getTechEvlOrgnlScr(), other.getTechEvlOrgnlScr());
        }

        /** 예: "+3.008", "−0.72". 한쪽이라도 없으면 null */
        static String diff(Double a, Double b) {
            if (a == null || b == null) {
                return null;
            }
            BigDecimal d = BigDecimal.valueOf(a).subtract(BigDecimal.valueOf(b));
            String text = StatsFormat.score(d.abs().doubleValue());
            return d.signum() < 0 ? "−" + text : (d.signum() > 0 ? "+" + text : "0");
        }
    }
}
