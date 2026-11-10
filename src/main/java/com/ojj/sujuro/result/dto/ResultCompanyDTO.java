package com.ojj.sujuro.result.dto;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/** 개찰 목록의 업체 한 곳 (TB_CO_EVL_RSLT 화면용). 결과를 등록할 때 나라장터 개찰 결과에서 받아 둔 값이다 */
@Getter
@Setter
public class ResultCompanyDTO {

    private Long coEvlRsltSn;
    private Integer opengRnk;
    private String bizNo;
    private String coNm;
    private Long bidAmt;
    private Double techEvlScr;
    private Double techEvlOrgnlScr;
    private Double prcEvlScr;
    private Double snthsEvlScr;
    private String ourCoYn;
    private String scsbdYn;
    /** 평가표 엑셀을 올렸는지 (업체마다 따로 올린다) */
    private String evlTblUpldYn;
    /**
     * 우리 쪽 행 옆에 붙일 이름. 보통은 자사이고, 컨소시엄 참여·하도급 사업은 우리 대신 개찰 목록에 오른
     * 주관사·원도급사 행이 우리 쪽이라 그 이름을 붙인다 (11/10, ResultEntryServiceImpl.build 가 정한다)
     */
    private String oursTag = "자사";

    public boolean isOurs() {
        return "Y".equals(ourCoYn);
    }

    public boolean isUploaded() {
        return "Y".equals(evlTblUpldYn);
    }

    /** 평가표 업체 고르기 목록의 이름. 예: "1위 엑사아이엔티 주식회사 (자사) · 평가표 있음" */
    public String getOptionLabel() {
        return (opengRnk == null ? "" : opengRnk + "위 ") + coNm + (isOurs() ? " (" + oursTag + ")" : "")
                + (isUploaded() ? " · 평가표 있음" : "");
    }

    public boolean isWinner() {
        return "Y".equals(scsbdYn);
    }

    /** 107-87-28988 */
    public String getBizNoText() {
        return StatsFormat.bizNo(bizNo);
    }

    /** 260,000,000 */
    public String getBidAmtText() {
        return bidAmt == null ? "-" : String.format("%,d", bidAmt);
    }

    public String getTechText() {
        return StatsFormat.score(techEvlScr);
    }

    public String getTechOrgnlText() {
        return StatsFormat.score(techEvlOrgnlScr);
    }

    public String getPrcText() {
        return StatsFormat.score(prcEvlScr);
    }

    public String getTotalText() {
        return StatsFormat.score(snthsEvlScr);
    }
}
