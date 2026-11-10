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

    public boolean isOurs() {
        return "Y".equals(ourCoYn);
    }

    public boolean isUploaded() {
        return "Y".equals(evlTblUpldYn);
    }

    /**
     * 평가표 업체 고르기 목록의 이름. 예: "1위 엑사아이엔티 주식회사 (자사) · 평가표 있음"
     * 우리 쪽 행에 붙는 이름은 사업의 참여 형태로 정해져 화면이 넘긴다: ${c.optionLabel(b.oursTag)} (ResultBidDTO.getOursTag)
     */
    public String optionLabel(String oursTag) {
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
