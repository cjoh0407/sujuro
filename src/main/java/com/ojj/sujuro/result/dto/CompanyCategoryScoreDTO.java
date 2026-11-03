package com.ojj.sujuro.result.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 업체 한 곳 × 평가 항목(대분류) 하나의 점수 (StatsMapper.companyArticleScores, 11/03).
 * 점수는 세부 항목마다 위원 점수를 평균한 뒤 대분류로 더한 값이다. 평가표 엑셀을 올린 업체만 온다.
 */
@Getter
@Setter
public class CompanyCategoryScoreDTO {

    private Long coEvlRsltSn;
    private String coNm;
    private String ourCoYn;
    private Integer opengRnk;
    private String catNm;
    /** 평가표 순서 */
    private int ord;
    private double maxSum;
    private double scoreSum;

    public boolean isOurs() {
        return "Y".equals(ourCoYn);
    }
}
