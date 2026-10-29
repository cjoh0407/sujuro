package com.ojj.sujuro.result.dto;

import lombok.Getter;
import lombok.Setter;

/** 사업별 결과 분석의 장단점 입력 (FR-081, 담당 팀 팀장). 빈 칸은 지운다 */
@Getter
@Setter
public class StatsAnalysisForm {

    private String strptCn;
    private String sbptCsCn;
    private String splmntCn;
}
