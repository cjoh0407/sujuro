package com.ojj.sujuro.result.dto;

import java.util.Date;

import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/** 사업건 한 줄 (평가·결과 입력의 머리 줄, 사업 이력 관리의 진행 중 목록). 참여 확정 뒤의 공고만 온다 */
@Getter
@Setter
public class ResultBidDTO {

    private static final String ST_CONFIRMED = "PS030";   // 참여 확정
    private static final String ST_WRITING = "PS040";     // 제안 작성 중
    private static final String ST_SUBMITTED = "PS050";   // 제출 완료
    private static final String ST_RESULT = "PS060";      // 결과 등록
    private static final String ST_FAILED = "PS070";      // 유찰

    private Long pbancSn;
    private String pbancNm;
    private String bidPbancNo;
    private String orderInstNm;
    private String teamNm;
    private String pbancSttsCd;
    private String pbancSttsNm;
    private Date bidDdlnDt;
    private Date opengDt;
    private String evlMthdNm;
    private Double techScorScr;
    private Double prcScorScr;
    /** 주관 팀 (이 사업 하나를 읽을 때만 값이 있다, findResultBid) */
    private Long sprvsnTeamSn;
    /** 참여 형태 (PTFRM). 이 사업 하나를 읽을 때만 값이 있다 */
    private String ptcpShpCd;
    private String ptcpShpNm;
    /** 컨소시엄 참여·하도급에서 넣어 둔 참여 업체(주관사·원도급사) 사업자번호. 넣지 않았거나 다른 참여 형태면 null (11/10) */
    private String partnerBizNo;

    /** 컨소시엄 참여·하도급이라 주관사·원도급사 사업자번호로 결과를 가져오는지 (11/10, BidCode.isViaPartner) */
    public boolean isViaPartner() {
        return BidCode.isViaPartner(ptcpShpCd);
    }

    /** 컨소시엄 참여·하도급인데 참여 업체 사업자번호를 넣어 그 업체의 결과를 나라장터에서 가져오는지 (11/10) */
    public boolean isPartnerLinked() {
        return isViaPartner() && partnerBizNo != null;
    }

    /** 순위·점수·개찰 업체·평가표를 나라장터 개찰 결과로 보여 주는지. 주관 사업이거나 참여 업체를 넣은 컨소시엄 참여·하도급 */
    public boolean isNaraResult() {
        return !isViaPartner() || isPartnerLinked();
    }

    /** 참여 업체 사업자번호. 예: "220-86-57703" */
    public String getPartnerBizNoText() {
        return StatsFormat.bizNo(partnerBizNo);
    }

    /** 개찰 목록에서 우리 쪽 행에 붙일 이름: 자사, 컨소시엄 참여면 참여, 하도급이면 원도급사 (11/10) */
    public String getOursTag() {
        if (BidCode.is(ptcpShpCd, BidCode.PTCP_SHP_CONSORTIUM)) {
            return "참여";
        }
        return BidCode.is(ptcpShpCd, BidCode.PTCP_SHP_SUBCONTRACT) ? "원도급사" : "자사";
    }

    /** 진행상태 이름. 공고 화면과 같은 일곱 가지(배정 전 … 유찰·불참)로 보여 준다 (11/03, BidCode.progressNm) */
    public String getProgressNm() {
        return BidCode.progressNm(pbancSttsCd);
    }

    /** 아직 나라장터에 제출하지 않았다(참여 확정·제안 작성 중) */
    public boolean isBeforeSubmit() {
        return ST_CONFIRMED.equals(pbancSttsCd) || ST_WRITING.equals(pbancSttsCd);
    }

    public boolean isSubmitted() {
        return ST_SUBMITTED.equals(pbancSttsCd);
    }

    public boolean isResulted() {
        return ST_RESULT.equals(pbancSttsCd);
    }

    public boolean isFailed() {
        return ST_FAILED.equals(pbancSttsCd);
    }

    /** 상태 알약 색. 공고 화면의 진행상태와 같은 색이다 */
    public String getSttsPill() {
        return BidCode.progressPillClass(pbancSttsCd);
    }

    /** 예: "기술 90 : 가격 10". 공고에 배점이 없으면 null */
    public String getScoringText() {
        if (techScorScr == null) {
            return null;
        }
        double prc = prcScorScr != null ? prcScorScr : 100 - techScorScr;
        return "기술 " + StatsFormat.score(techScorScr) + " : 가격 " + StatsFormat.score(prc);
    }
}
