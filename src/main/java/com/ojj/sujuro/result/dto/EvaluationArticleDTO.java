package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 평가 항목 한 줄 저장용 (TB_EVL_ARTCL). 저장 뒤 evlArtclSn 이 채워진다 */
@Getter
@Setter
public class EvaluationArticleDTO {

    private Long evlArtclSn;
    private Long pbancSn;
    private String evlArtclNm;
    private BigDecimal evlScorScr;
    private int sortSeq;
}
