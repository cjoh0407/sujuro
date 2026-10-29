package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 저장된 평가표 한 칸: 평가 항목 하나 × 위원 한 명 (우리 회사). 점수가 없는 항목은 cmsnrNo 가 null 이다 */
@Getter
@Setter
public class EvaluationScoreDTO {

    private Long evlArtclSn;
    private String evlArtclNm;
    private BigDecimal evlScorScr;
    private Integer cmsnrNo;
    private BigDecimal evlScr;
}
