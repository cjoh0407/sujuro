package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 업체 한 곳의 평가 항목 하나: 위원 점수 평균 (ResultMapper.findCompanyArticleScores, 업체별 평가표 비교) */
@Getter
@Setter
public class CompanyArticleScoreDTO {

    private Long coEvlRsltSn;
    private Long evlArtclSn;
    private String evlArtclNm;
    /** 배점 */
    private BigDecimal evlScorScr;
    /** 위원 점수 평균 */
    private Double avgScr;
}
