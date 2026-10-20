package com.ojj.sujuro.proposal.bid;

/** 제안팀이 볼 수 있는 사업건(입찰공고) 요약. 목차·요구사항 화면의 사업건 선택에 쓴다. */
public class ProposalBidDto {

    private Long pbancSn;
    private String bidPbancNo;
    private String pbancNm;
    private String pbancSttsCd;
    private String pbancSttsNm;
    private Integer lmtPageCnt;

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    public String getBidPbancNo() { return bidPbancNo; }
    public void setBidPbancNo(String bidPbancNo) { this.bidPbancNo = bidPbancNo; }

    public String getPbancNm() { return pbancNm; }
    public void setPbancNm(String pbancNm) { this.pbancNm = pbancNm; }

    public String getPbancSttsCd() { return pbancSttsCd; }
    public void setPbancSttsCd(String pbancSttsCd) { this.pbancSttsCd = pbancSttsCd; }

    /** 공고상태 이름 (공통코드 PBSTS) */
    public String getPbancSttsNm() { return pbancSttsNm; }
    public void setPbancSttsNm(String pbancSttsNm) { this.pbancSttsNm = pbancSttsNm; }

    /** 제안요청서의 전체 페이지 제한. 정해지지 않았으면 null (FR-055) */
    public Integer getLmtPageCnt() { return lmtPageCnt; }
    public void setLmtPageCnt(Integer lmtPageCnt) { this.lmtPageCnt = lmtPageCnt; }

    /** 선택 목록에 보여 줄 이름. 공고명이 아직 없으면 공고번호를 쓴다. */
    public String getLabel() {
        return pbancNm != null && !pbancNm.isEmpty() ? pbancNm : bidPbancNo;
    }
}
