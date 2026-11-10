package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * 평가·결과 입력 화면(results/index.html) 한 장 (FR-077~081). 경영지원실과 팀장(자기 팀 사업만)이 본다.
 * 고른 사업건 하나를 보여 준다. 결과가 등록된 사업이면 사업별 결과 분석과 같은 값(archive)과 개찰 업체 목록이 같이 온다.
 */
@Getter
@Setter
public class ResultsViewDTO {

    /** [나라장터에서 결과 가져오기]를 할 수 있는지 (경영지원실) */
    private boolean canImport;

    /** 컨소시엄 참여·하도급에서 주관사·원도급사 사업자번호를 넣을 수 있는지 (이 사업을 맡은 팀의 팀장, 11/10) */
    private boolean canLinkPartner;

    /**
     * 평가표 엑셀을 올리고 평가 항목·점수와 장단점을 입력할 수 있는지 (이 사업을 맡은 팀의 팀장. 회의 결정, 2026-11-02 반영).
     * 11/10 엑셀 올리기도 팀장만 해서 따로 두던 canUpload(경영지원실·팀장)를 이것으로 합쳤다
     */
    private boolean canScore;

    /** 이 사용자가 볼 수 있는 사업건: 참여 확정 뒤 (제출 완료 → 결과 등록 → 제안 작성 중 → 참여 확정 → 유찰 순). 고른 사업건이 이 안에 있는지 본다 */
    private List<ResultBidDTO> bids = new ArrayList<>();
    private ResultBidDTO bid;

    /** 우리 회사 사업자번호가 설정돼 있는지. 없으면 결과를 가져올 수 없다 */
    private boolean ready;

    /** 결과 등록 사업만: 점수·평가 항목·평가표·장단점 (StatsService.findArchive) */
    private StatsArchiveDTO archive;

    /** 제출 완료·결과 등록 사업: 참여한 개찰 업체 전체 (순위 순) */
    private List<ResultCompanyDTO> companies = new ArrayList<>();

    /* 평가표는 개찰 업체마다 따로 올린다. 아래는 화면에서 고른 업체(기본은 우리 회사)의 것이다 */

    /** 고른 업체. 우리 회사 행이 없으면 null */
    private ResultCompanyDTO company;
    /** 고른 업체가 저장한 항목·위원별 점수. 없으면 빈 줄 */
    private EvaluationForm companyForm;
    /** 고른 업체의 마지막 평가표 엑셀. 없으면 null */
    private EvaluationFileDTO companyFile;
    /** 업체별 항목 점수 비교. 점수를 저장한 업체만 열로 나온다 */
    private EvaluationCompareDTO evaluationCompare;

    /*
     * 평가표 엑셀에서 읽어 채운 입력 칸 (팀장 화면만). 저장 전이라 팀장이 확인한 뒤 [평가표 저장]을 누른다.
     * 고른 업체가 저장한 점수가 없을 때, 또는 엑셀을 새로 올리고 아직 저장하지 않았을 때 채운다.
     */
    private EvaluationForm excelForm;
    /** 읽어 채웠다는 안내 */
    private String excelNote;
    /** 엑셀 모양이 달라 읽지 못한 이유. 이때는 직접 입력한다 */
    private String excelError;

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
