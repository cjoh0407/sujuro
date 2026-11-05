package com.ojj.sujuro.result.dto;

import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.result.code.ResultCode;
import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/**
 * 사업별 결과 분석 (FR-080, 081, 087). 통계의 과거 사업 이력에서 사업명을 누르면 온다.
 * 결과가 등록된 사업만 있다. 순위·점수는 수집이 나라장터 개찰 결과에서 우리 회사 사업자번호로 가져오고,
 * 평가표(항목·배점·위원별 점수)와 장단점 분석은 경영지원실이 입력한다.
 */
@Getter
@Setter
public class StatsArchiveDTO {

    /** 공고에 배점(비율)이 없고 평가표로도 알 수 없을 때 쓰는 기술 만점. SW 사업 협상에 의한 계약의 기술:가격 = 90:10 */
    public static final double DEFAULT_TECH_MAX = 90;
    private static final double TOTAL_MAX = 100;

    private Long pbancSn;
    private String pbancNm;
    private String orderInstNm;
    private Long teamSn;
    private String teamNm;
    private String ptcpShpCd;
    private String ptcpShpNm;
    private Date bidDdlnDt;

    /** 공고 상태. 아직 결과가 없는 사업(StatsMapper.findPendingBid)만 값이 있다 */
    private String pbancSttsCd;
    private String pbancSttsNm;

    /** 공고의 기술·가격 배점(비율). 입찰공고 API techAbltEvlRt·bidPrceEvlRt. 10/29 전에 등록한 공고는 비어 있을 수 있다 */
    private Double techScorScr;
    private Double prcScorScr;

    private String rsltCd;
    private String rsltNm;
    private Integer evlRnk;
    private Double techEvlScr;
    /** 차등점수제 적용 전 기술 원점수 */
    private Double techEvlOrgnlScr;
    private Double prcEvlScr;
    private Double snthsEvlScr;
    /** 수주 금액(원). 수주한 사업만 값이 있다 */
    private Long scsbdAmt;

    /**장단점 분석 (FR-081): 강점 · 탈락·감점 원인 · 차기 보완점 */
    private String strptCn;
    private String sbptCsCn;
    private String splmntCn;

    /** 평가 항목(대분류)별 득점률. 평가표가 없으면 빈 목록 */
    private List<StatsArticleDTO> articles = new ArrayList<>();

    /** 평가 항목별 점수 표 (11/03). 평가표 엑셀을 올린 업체가 열, 항목별 등수·종합 점수 1등(수주 업체) */
    private ArticleScoreTableDTO articleTable = ArticleScoreTableDTO.of(new ArrayList<>());

    /** 최종 제안서. 없으면 null */
    private StatsFileDTO finalFile;

    /** 경영지원실이 마지막으로 올린 평가표 엑셀. 없으면 null */
    private EvaluationFileDTO evaluationFile;

    /** 개찰 목록에 우리 회사 행이 있는지. 있어야 평가표를 넣을 수 있다(결과가 자동 등록된 사업) */
    private boolean ourCompanyListed;

    /** 평가표 입력 칸에 채울 값 (저장된 항목·위원별 점수, 없으면 빈 줄) */
    private EvaluationForm evaluationForm;

    /**
     * 통계 왼쪽 목록에서 이 사업이 들어가는 연도. 입찰 마감일의 연도이고, 마감일이 아직 없는 진행 중 사업은 올해다
     * (StatsMapper.inProgress 와 같은 기준). 그 밖에 마감일이 없으면 null
     */
    public String getListYear() {
        if (bidDdlnDt != null) {
            return new SimpleDateFormat("yyyy").format(bidDdlnDt);
        }
        return isInProgress() ? String.valueOf(Year.now().getValue()) : null;
    }

    public boolean isWon() {
        return ResultCode.WON.equals(rsltCd);
    }

    /** 아직 결과가 없어 공고 기본 정보만 보이는 사업인지 (진행 중·유찰, 11/04) */
    public boolean isPending() {
        return rsltCd == null;
    }

    /** 진행 중 사업인지: 참여 확정·제안 작성 중·제출 완료 */
    public boolean isInProgress() {
        return BidCode.STTS_JOINED.equals(pbancSttsCd) || BidCode.STTS_WRITING.equals(pbancSttsCd)
                || BidCode.STTS_SUBMITTED.equals(pbancSttsCd);
    }

    /** 결과가 없는 사업의 점수·평가 항목별 점수·장단점 칸에 보일 안내 */
    public String getPendingMessage() {
        if (isInProgress()) {
            return "아직 입찰 진행 중인 사업건입니다.";
        }
        return BidCode.STTS_FAILED_BID.equals(pbancSttsCd) ? "유찰된 사업건입니다." : "아직 결과가 입력되지 않은 사업건입니다.";
    }

    /** 예: "수주 · 1위" */
    public String getResultText() {
        return (rsltNm == null ? "-" : rsltNm) + (evlRnk == null ? "" : " · " + evlRnk + "위");
    }

    /** 컨소시엄 참여·하도급이라 결과를 직접 입력한 사업인지. 순위·점수·평가표가 없다 (11/04) */
    public boolean isManualResult() {
        return BidCode.isManualResult(ptcpShpCd);
    }

    /** 수주 금액. 예: "15.9억". 탈락·유찰·진행 중이면 null */
    public String getScsbdAmtText() {
        return scsbdAmt == null ? null : StatsFormat.eok(scsbdAmt);
    }

    /**
     * 기술점수 만점. 공고의 기술 배점(비율)이 있으면 그 값이다.
     * 없으면 평가표의 배점 합(100 미만일 때만. 100 이면 가격 몫이 없어 기술 배점으로 볼 수 없다), 그것도 없으면 90 으로 본다.
     */
    public double getTechMax() {
        if (hasScoring()) {
            return techScorScr;
        }
        double sum = articleMaxSum();
        return sum > 0 && sum < TOTAL_MAX ? StatsFormat.round1(sum) : DEFAULT_TECH_MAX;
    }

    /** 가격점수 만점. 공고의 가격 배점이 있으면 그 값, 없으면 100 - 기술 만점 */
    public double getPrcMax() {
        return hasScoring() && prcScorScr != null ? prcScorScr : TOTAL_MAX - getTechMax();
    }

    /** 기술 만점을 공고·평가표 어디서도 못 읽어 90 으로 본 경우. 화면에 그렇게 적는다 */
    public boolean isTechMaxAssumed() {
        double sum = articleMaxSum();
        return !hasScoring() && !(sum > 0 && sum < TOTAL_MAX);
    }

    /** 예: "기술 90 : 가격 10". 공고에 배점이 없으면 null */
    public String getScoringText() {
        return hasScoring() ? "기술 " + StatsFormat.score(techScorScr) + " : 가격 " + StatsFormat.score(getPrcMax()) : null;
    }

    /** 기술 원점수. 없으면 null */
    public String getTechOrgnlText() {
        return techEvlOrgnlScr == null ? null : StatsFormat.score(techEvlOrgnlScr);
    }

    private boolean hasScoring() {
        return techScorScr != null && techScorScr > 0 && techScorScr <= TOTAL_MAX;
    }

    private double articleMaxSum() {
        return articles.stream().mapToDouble(a -> a.getMaxSum()).sum();
    }

    /** 점수 막대 세 줄: 기술 · 가격 · 종합 */
    public List<ScoreRow> getScores() {
        List<ScoreRow> rows = new ArrayList<>();
        rows.add(new ScoreRow("기술점수", techEvlScr, getTechMax()));
        rows.add(new ScoreRow("가격점수", prcEvlScr, getPrcMax()));
        rows.add(new ScoreRow("종합점수", snthsEvlScr, TOTAL_MAX));
        return rows;
    }

    /** 가장 많이 잃은 평가 항목 (배점 - 득점이 가장 큰 것). 평가표가 없으면 null */
    public StatsArticleDTO getMostLost() {
        return articles.stream().max(Comparator.comparingDouble(StatsArticleDTO::getLoss)).orElse(null);
    }

    /** 점수 막대 한 줄 */
    @Getter
    public static class ScoreRow {
        private final String label;
        private final String text;
        private final double width;

        ScoreRow(String label, Double score, double max) {
            this.label = label;
            this.text = StatsFormat.score(score) + " / " + StatsFormat.number(max);
            Double w = score == null ? null : StatsFormat.percent(score, max);
            this.width = w == null ? 0 : Math.min(100, w);
        }
    }
}
