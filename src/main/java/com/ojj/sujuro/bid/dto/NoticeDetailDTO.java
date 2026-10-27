package com.ojj.sujuro.bid.dto;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import com.ojj.sujuro.bid.BidCode;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고 상세 (FR-035). 목록 한 줄(BidNoticeDTO)에 상세 칸을 더한 것이다.
 * 목록과 같은 pill 색·예산 표기를 그대로 쓰려고 상속한다.
 *
 * 위쪽 칸은 BidNoticeMapper.findDetail 이 채우고, 아래쪽 목록(단계 이력, 변경 비교, 첨부파일)은
 * NoticeDetailServiceImpl 이 수집이력을 읽어 채운다.
 */
@Getter
@Setter
public class NoticeDetailDTO extends BidNoticeDTO {

    /** 공고 번호 셋. 뒤 단계로 넘어가도 앞 번호는 지우지 않으므로 이전 단계 연결 여부를 이것으로 본다. */
    private String orderPlanNo;
    private String bfSpecRgstNo;
    private String bidPbancNo;

    /** 입찰공고차수. 정정이 날 때마다 올라간다. */
    private Integer bidPbancCycl;

    /** 추적상태 (TRSTS). 발주계획·사전규격으로 등록한 공고만 값이 있다. */
    private String trckSttsCd;
    private String trckSttsNm;

    /** 실수요기관명. 발주기관과 다를 때만 의미가 있다. */
    private String dmndInstNm;

    /** 참여형태 (PTFRM). 팀 배정 때 영업부가 정한다. */
    private String ptcpShpNm;

    private String swBizYn;
    private String bizPrdCn;

    /** 사전규격 의견등록마감 */
    private Date opnnDdlnDt;
    private Date opengDt;
    private Date prsntnDt;
    private String evlMthdNm;
    private Integer lmtPageCnt;

    /** 나라장터 원문 링크 */
    private String pbancDtlUrl;

    private Long sprvsnTeamSn;

    /** 등록한 영업부 직원 이름 */
    private String rgtrNm;
    private Date regDt;

    /** 마지막으로 값이 바뀐 일시 / 바뀌지 않았어도 마지막으로 확인한 일시 */
    private Date clctDt;
    private Date lastCfmtnDt;

    /** 단계 이력. 공고수집이력 오래된 순 (FR-035) */
    private List<CollectHistoryDTO> stageHistories = List.of();

    /** 직전 수집과 지금 값 비교 (FR-035-1). 수집이 한 번뿐이면 null */
    private StageChangeDTO stageChange;

    /** 입찰공고 첨부파일 (FR-036). 입찰공고 전이거나 원문이 없으면 빈 목록 */
    private List<RfpFileDTO> rfpFiles = List.of();

    /**
     * 사업 내용. 사업 분야·계약 방법·공동수급·담당자처럼 API 가 주는 사업 설명 항목이다.
     * 값이 있는 항목만 들어 있다. 사업 목적·과업 범위 같은 서술은 API 에 없고 첨부 문서에만 있다.
     */
    private List<InfoItemDTO> bizInfos = List.of();

    /** 기술:가격 평가 비율. 예: "기술 80 : 가격 20". 협상에 의한 계약에만 있다. 없으면 null */
    private String evlRatioText;

    /** 입찰공고 단계 이후인지. 참여 결정과 제안요청서는 이 단계부터다. */
    public boolean isBidNoticeOrLater() {
        return !BidCode.is(getPbancStepCd(), BidCode.STEP_ORDER_PLAN)
            && !BidCode.is(getPbancStepCd(), BidCode.STEP_PRE_SPEC);
    }

    /** 발주계획보다 뒤 단계인지. 그래야 '발주계획 연결'을 물을 수 있다. */
    public boolean isAfterOrderPlan() {
        return !BidCode.is(getPbancStepCd(), BidCode.STEP_ORDER_PLAN);
    }

    /** 예산을 원 단위로. 예: "760,000,000원". 값이 없으면 "-" */
    public String getBudgetWonText() {
        String won = wonOf(getAltmntBgtAmt());
        return won == null ? "-" : won;
    }

    /** 금액을 "760,000,000원" 으로. 값이 없으면 null. 변경 비교(StageChangeDTO)도 같은 표기를 쓴다. */
    public static String wonOf(BigDecimal amt) {
        return amt == null ? null : new DecimalFormat("#,###").format(amt) + "원";
    }

    /** 정정 횟수. 차수 0 이 최초 공고다. */
    public int getCorrectionCnt() {
        return bidPbancCycl == null ? 0 : bidPbancCycl;
    }

    /** 입찰 마감까지 남은 날. "D-3", "D-day", 지났으면 "마감". 마감일이 없으면 null */
    public String getDdlnDdayText() {
        if (getBidDdlnDt() == null) {
            return null;
        }
        LocalDate ddln = getBidDdlnDt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(LocalDate.now(), ddln);
        if (days > 0) {
            return "D-" + days;
        }
        return days == 0 ? "D-day" : "마감";
    }

    /**
     * 평가 방식(낙찰방법)의 큰 갈래. API 값은 "갈래-세부" 모양이다.
     *   예) "협상에의한계약-협상에 의한 낙찰자 결정(SW사업)" → "협상에 의한 계약"
     *       "적격심사제-추정가격 2억원 미만인 용역"          → "적격심사제"
     */
    public String getEvlMthdMain() {
        if (evlMthdNm == null) {
            return null;
        }
        int dash = evlMthdNm.indexOf('-');
        String main = dash < 0 ? evlMthdNm : evlMthdNm.substring(0, dash);
        return main.replace("협상에의한계약", "협상에 의한 계약");
    }

    /** 평가 방식의 세부. 갈래와 같은 말이면 null */
    public String getEvlMthdDetail() {
        if (evlMthdNm == null || evlMthdNm.indexOf('-') < 0) {
            return null;
        }
        String detail = evlMthdNm.substring(evlMthdNm.indexOf('-') + 1).trim();
        return detail.isEmpty() || detail.equals(evlMthdNm.substring(0, evlMthdNm.indexOf('-'))) ? null : detail;
    }

    /** 실수요기관이 발주기관과 다른지. 같으면 화면에서 한 번만 보여 준다. */
    public boolean isDmndInstDifferent() {
        return dmndInstNm != null && !dmndInstNm.equals(getOrderInstNm());
    }
}
