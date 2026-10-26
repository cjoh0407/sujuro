package com.ojj.sujuro.proposal.bid;

/** 제안팀이 볼 수 있는 사업건(입찰공고) 요약. 목차·요구사항 화면의 사업건 선택과 권한 판단에 쓴다. */
public class ProposalBidDto {

    private Long pbancSn;
    private String bidPbancNo;
    private String pbancNm;
    private String instNm;
    private String pbancSttsCd;
    private String pbancSttsNm;
    private Integer lmtPageCnt;
    private Long sprvsnTeamSn;
    private String sprvsnTeamNm;
    private String ptcpTeamNms;

    /** 로그인한 사용자의 소속 팀 (조회 때 채움) */
    private Long myTeamSn;
    /** 로그인한 사용자의 이 사업건 참여구분 PT010 주관팀 / PT020 참여팀 / PT040 수행인력. 제안팀이 아니면 null */
    private String myPtcpSeCd;

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    public String getBidPbancNo() { return bidPbancNo; }
    public void setBidPbancNo(String bidPbancNo) { this.bidPbancNo = bidPbancNo; }

    public String getPbancNm() { return pbancNm; }
    public void setPbancNm(String pbancNm) { this.pbancNm = pbancNm; }

    /** 수요기관(없으면 공고기관) 이름 */
    public String getInstNm() { return instNm; }
    public void setInstNm(String instNm) { this.instNm = instNm; }

    public String getPbancSttsCd() { return pbancSttsCd; }
    public void setPbancSttsCd(String pbancSttsCd) { this.pbancSttsCd = pbancSttsCd == null ? null : pbancSttsCd.trim(); }

    /** 공고상태 이름 (공통코드 PBSTS) */
    public String getPbancSttsNm() { return pbancSttsNm; }
    public void setPbancSttsNm(String pbancSttsNm) { this.pbancSttsNm = pbancSttsNm; }

    /** 제안요청서의 전체 페이지 제한. 정해지지 않았으면 null (FR-055) */
    public Integer getLmtPageCnt() { return lmtPageCnt; }
    public void setLmtPageCnt(Integer lmtPageCnt) { this.lmtPageCnt = lmtPageCnt; }

    /** 주관팀 (사업을 개발하는 팀, 사업건마다 하나) */
    public Long getSprvsnTeamSn() { return sprvsnTeamSn; }
    public void setSprvsnTeamSn(Long sprvsnTeamSn) { this.sprvsnTeamSn = sprvsnTeamSn; }

    public String getSprvsnTeamNm() { return sprvsnTeamNm; }
    public void setSprvsnTeamNm(String sprvsnTeamNm) { this.sprvsnTeamNm = sprvsnTeamNm; }

    /** 참여팀 이름들 (쉼표로 이어 붙임). 없으면 null */
    public String getPtcpTeamNms() { return ptcpTeamNms; }
    public void setPtcpTeamNms(String ptcpTeamNms) { this.ptcpTeamNms = ptcpTeamNms; }

    public Long getMyTeamSn() { return myTeamSn; }
    public void setMyTeamSn(Long myTeamSn) { this.myTeamSn = myTeamSn; }

    public String getMyPtcpSeCd() { return myPtcpSeCd; }
    public void setMyPtcpSeCd(String myPtcpSeCd) { this.myPtcpSeCd = myPtcpSeCd == null ? null : myPtcpSeCd.trim(); }

    /** 선택 목록에 보여 줄 이름. 공고명이 아직 없으면 공고번호를 쓴다. */
    public String getLabel() {
        return pbancNm != null && !pbancNm.isEmpty() ? pbancNm : bidPbancNo;
    }

    /** 로그인한 사용자가 이 사업건 주관팀 소속인지 */
    public boolean isMySupervisingTeam() {
        return sprvsnTeamSn != null && sprvsnTeamSn.equals(myTeamSn);
    }

    /** 로그인한 사용자가 제안팀(주관팀·참여팀 인원)인지. 수행인력(PT040)은 제안서 작성에 들어가지 않는다. */
    public boolean isProposalWriter() {
        return "PT010".equals(myPtcpSeCd) || "PT020".equals(myPtcpSeCd);
    }
}
