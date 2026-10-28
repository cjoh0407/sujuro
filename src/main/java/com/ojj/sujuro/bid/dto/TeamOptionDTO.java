package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.Setter;

/** 공고 상세의 [주관 팀] 고르는 칸 한 줄 (FR-037). 배정 알림은 팀장에게 가므로 팀장 이름을 같이 보여 준다. */
@Getter
@Setter
public class TeamOptionDTO {

    private Long teamSn;
    private String teamNm;

    /** 팀장 이름. 여럿이면 쉼표로 잇는다. 팀장 계정이 없으면 null */
    private String leaderNm;

    /** 예: "개발1팀 (팀장 김가희)", "개발3팀 (팀장 없음)" */
    public String getLabel() {
        return teamNm + (leaderNm == null ? " (팀장 없음)" : " (팀장 " + leaderNm + ")");
    }
}
