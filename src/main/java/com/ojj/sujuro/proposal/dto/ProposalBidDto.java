package com.ojj.sujuro.proposal.dto;

import lombok.Getter;
import lombok.Setter;

/** 제안팀이 볼 수 있는 사업건(입찰공고) 요약. 목차·요구사항 화면의 사업건 선택과 사업명 줄·제안팀 구성 표시에 쓴다. */
@Getter
@Setter
public class ProposalBidDto {

    private Long pbancSn;
    private String bidPbancNo;
    private String pbancNm;
    /** 수요기관(없으면 공고기관) 이름 */
    private String instNm;
    private String pbancSttsCd;
    /** 공고상태 이름 (공통코드 PBSTS) */
    private String pbancSttsNm;
    /** 제안요청서의 전체 페이지 제한. 정해지지 않았으면 null (FR-055) */
    private Integer lmtPageCnt;
    /** 주관팀 (사업을 개발하는 팀, 사업건마다 하나) */
    private Long sprvsnTeamSn;
    private String sprvsnTeamNm;
    /** 참여팀 이름들 (쉼표로 이어 붙임). 없으면 null */
    private String ptcpTeamNms;

    public void setPbancSttsCd(String pbancSttsCd) { this.pbancSttsCd = pbancSttsCd == null ? null : pbancSttsCd.trim(); }

    /** 선택 목록에 보여 줄 이름. 공고명이 아직 없으면 공고번호를 쓴다. */
    public String getLabel() {
        return pbancNm != null && !pbancNm.isEmpty() ? pbancNm : bidPbancNo;
    }
}
