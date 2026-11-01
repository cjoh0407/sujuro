package com.ojj.sujuro.proposal.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 조견표 한 줄 = 평가 항목 (TB_EVL_ARTCL). (FR-071)
 * 경영지원실이 평가 항목과 배점을 넣고, 제안팀이 항목마다 해당 내용이 있는 제안서 쪽번호(예: 12-15)를 적는다.
 * 같은 표를 결과 분석(평가표 업로드, FR-078)도 쓴다.
 */
@Getter
@Setter
public class EvlArtclDto {

    private Long evlArtclSn;
    private Long pbancSn;
    private String evlArtclNm;
    /** 배점 */
    private BigDecimal evlScorScr;
    /** 제안서 쪽번호. 예: 12-15, 3, 7-9 */
    private String pageNo;
    private int sortSeq;

    /** 쪽번호를 적었는지 */
    public boolean isPaged() {
        return pageNo != null && !pageNo.trim().isEmpty();
    }

    /** 배점 표시: 소수점 아래 0은 뗀다 (15.000 → 15, 7.500 → 7.5) */
    public String getScoreLabel() {
        return evlScorScr == null ? "-" : evlScorScr.stripTrailingZeros().toPlainString();
    }
}
