package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고 상세 제안팀 칩 한 사람 (11/06). 이름·팀과 팀장 직급 여부.
 * 이름이 proposal 패키지의 ProposalMemberDto 와 겹치면 MyBatis 별칭이 충돌해 앱이 뜨지 않는다.
 */
@Getter
@Setter
public class NoticeMemberDTO {

    private String userNm;

    /** 팀이 없는 계정이면 null */
    private String teamNm;

    /** 직급이 팀장(JG020)이면 true. 화면에서 이름에 바탕색을 칠한다 */
    private boolean leader;
}
