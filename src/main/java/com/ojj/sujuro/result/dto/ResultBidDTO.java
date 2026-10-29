package com.ojj.sujuro.result.dto;

import java.util.Date;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/** 평가·결과 입력의 사업건 한 줄 (선택 목록과 머리 줄). 참여 확정 뒤의 공고만 온다 */
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

    /** 선택 목록에 보일 이름. 예: "통합 보안관제 체계 구축 · 결과 등록" */
    public String getLabel() {
        return pbancNm + " · " + (pbancSttsNm == null ? "-" : pbancSttsNm);
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

    /** 상태 알약 색 */
    public String getSttsPill() {
        if (isResulted()) {
            return "success";
        }
        if (isSubmitted()) {
            return "accent";
        }
        return isFailed() ? "danger" : "neutral";
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
