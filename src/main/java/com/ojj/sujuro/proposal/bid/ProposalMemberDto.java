package com.ojj.sujuro.proposal.bid;

/** 사업건 제안팀원 (TB_PRP_TMMBR + TB_USER). 목차 담당자 후보로 쓴다. (FR-043, FR-049) */
public class ProposalMemberDto {

    private String userId;
    private String userNm;
    private String teamNm;
    private String ptcpSeCd;
    private String authrtCd;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserNm() { return userNm; }
    public void setUserNm(String userNm) { this.userNm = userNm; }

    public String getTeamNm() { return teamNm; }
    public void setTeamNm(String teamNm) { this.teamNm = teamNm; }

    /** 참여구분코드 (공통코드 PTCSE: 주관팀/참여팀) */
    public String getPtcpSeCd() { return ptcpSeCd; }
    public void setPtcpSeCd(String ptcpSeCd) { this.ptcpSeCd = ptcpSeCd == null ? null : ptcpSeCd.trim(); }

    /** 권한코드 (공통코드 AUTHS) */
    public String getAuthrtCd() { return authrtCd; }
    public void setAuthrtCd(String authrtCd) { this.authrtCd = authrtCd == null ? null : authrtCd.trim(); }

    /** 팀장인지 (제안팀 구성 표시용) */
    public boolean isLeader() {
        return com.ojj.sujuro.auth.Role.CD_LEADER.equals(authrtCd);
    }

    /** 담당자 선택 목록에 보여 줄 이름 */
    public String getLabel() {
        return teamNm == null ? userNm : userNm + " (" + teamNm + ")";
    }
}
