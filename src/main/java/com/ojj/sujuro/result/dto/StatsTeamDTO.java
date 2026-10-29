package com.ojj.sujuro.result.dto;

import lombok.Getter;
import lombok.Setter;

/** 통계를 볼 수 있는 개발팀 한 줄 (팀 탭). 통계는 개발팀 단위로만 보여 준다 (FR-016). */
@Getter
@Setter
public class StatsTeamDTO {

    private Long teamSn;
    private String teamNm;
}
