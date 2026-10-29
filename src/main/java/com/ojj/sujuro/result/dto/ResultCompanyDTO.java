package com.ojj.sujuro.result.dto;

import com.ojj.sujuro.result.support.StatsFormat;

import lombok.Getter;
import lombok.Setter;

/** 개찰 목록의 업체 한 곳 (TB_CO_EVL_RSLT 화면용). 결과를 등록할 때 나라장터 개찰 결과에서 받아 둔 값이다 */
@Getter
@Setter
public class ResultCompanyDTO {

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

    public boolean isOurs() {
        return "Y".equals(ourCoYn);
    }

    public boolean isWinner() {
        return "Y".equals(scsbdYn);
    }

    /** 107-87-28988 */
    public String getBizNoText() {
        return bizNo != null && bizNo.length() == 10
                ? bizNo.substring(0, 3) + "-" + bizNo.substring(3, 5) + "-" + bizNo.substring(5) : bizNo;
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
