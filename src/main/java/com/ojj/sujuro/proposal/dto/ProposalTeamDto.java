package com.ojj.sujuro.proposal.dto;

import lombok.Getter;
import lombok.Setter;

/** 참여팀으로 고를 수 있는 개발부 팀 (TB_TEAM + TB_PTCP_TEAM). 제안팀 구성 화면의 참여팀 선택에 쓴다. (FR-044) */
@Getter
@Setter
public class ProposalTeamDto {

    private Long teamSn;
    private String teamNm;
    /** 이 사업건의 참여팀으로 지정돼 있으면 Y (조회 때 계산) */
    private String ptcpYn;
    /** 이 팀 소속으로 제안팀에 들어 있는 인원 수 (조회 때 계산) */
    private int memberCnt;

    /** 이 사업건의 참여팀인지 */
    public boolean isPtcp() { return "Y".equals(ptcpYn); }
}
