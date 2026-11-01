package com.ojj.sujuro.proposal.dto;

import lombok.Getter;
import lombok.Setter;

/** 사업건 제안팀원 (TB_PRP_TMMBR + TB_USER), 또는 제안팀에 추가할 수 있는 직원. 목차 담당자 후보로도 쓴다. (FR-043~044, FR-049) */
@Getter
@Setter
public class ProposalMemberDto {

    private String userId;
    private String userNm;
    private String teamNm;
    /** 참여구분코드 (공통코드 PTCSE: 주관팀/참여팀) */
    private String ptcpSeCd;
    /** 권한코드 (공통코드 AUTHS) */
    private String authrtCd;
    private Long teamSn;
    /** 계정상태코드 (추가할 때 비활성 계정인지 확인) */
    private String acntSttsCd;
    /** 소속 팀의 부서코드 (DP030 개발부만 제안팀에 들어갈 수 있다) */
    private String deptCd;
    /** 직급명 (공통코드 JBGDS). 인원 추가 팝업의 직급 필터·표시용, 추가 후보 조회에서만 채운다 (FR-043) */
    private String jbgdNm;

    public void setPtcpSeCd(String ptcpSeCd) { this.ptcpSeCd = ptcpSeCd == null ? null : ptcpSeCd.trim(); }

    public void setAuthrtCd(String authrtCd) { this.authrtCd = authrtCd == null ? null : authrtCd.trim(); }

    public void setAcntSttsCd(String acntSttsCd) { this.acntSttsCd = acntSttsCd == null ? null : acntSttsCd.trim(); }

    public void setDeptCd(String deptCd) { this.deptCd = deptCd == null ? null : deptCd.trim(); }

    /** 주관팀 인원(PT010)인지 */
    public boolean isLeadTeam() {
        return "PT010".equals(ptcpSeCd);
    }

    /** 제안팀에서 뺄 수 있는지. 주관팀 팀장은 빼지 않는다. */
    public boolean isRemovable() {
        return !(isLeadTeam() && isLeader());
    }

    /** 팀장인지 (제안팀 구성 표시용) */
    public boolean isLeader() {
        return com.ojj.sujuro.auth.code.Role.CD_LEADER.equals(authrtCd);
    }

    /** 담당자 선택 목록에 보여 줄 이름 */
    public String getLabel() {
        return teamNm == null ? userNm : userNm + " (" + teamNm + ")";
    }
}
