package com.ojj.sujuro.history.dto;

import java.util.Date;

/** 공고 처리 이력 한 줄 (공고 상세 화면의 "배정·결정 이력"에 쓴다). */
public class HistoryDto {

    private Long prcsHstrySn;
    private String prcsSeCd;     // 처리구분코드
    private String prcsSeNm;     // 처리구분 이름
    private Long trgtTeamSn;
    private String trgtTeamNm;   // 배정·결정 대상 팀
    private String prcsRsn;      // 처리사유
    private String prcrId;
    private String prcrNm;       // 처리자 이름
    private Date prcsDt;

    public Long getPrcsHstrySn() { return prcsHstrySn; }
    public void setPrcsHstrySn(Long prcsHstrySn) { this.prcsHstrySn = prcsHstrySn; }
    public String getPrcsSeCd() { return prcsSeCd; }
    public void setPrcsSeCd(String prcsSeCd) { this.prcsSeCd = prcsSeCd; }
    public String getPrcsSeNm() { return prcsSeNm; }
    public void setPrcsSeNm(String prcsSeNm) { this.prcsSeNm = prcsSeNm; }
    public Long getTrgtTeamSn() { return trgtTeamSn; }
    public void setTrgtTeamSn(Long trgtTeamSn) { this.trgtTeamSn = trgtTeamSn; }
    public String getTrgtTeamNm() { return trgtTeamNm; }
    public void setTrgtTeamNm(String trgtTeamNm) { this.trgtTeamNm = trgtTeamNm; }
    public String getPrcsRsn() { return prcsRsn; }
    public void setPrcsRsn(String prcsRsn) { this.prcsRsn = prcsRsn; }
    public String getPrcrId() { return prcrId; }
    public void setPrcrId(String prcrId) { this.prcrId = prcrId; }
    public String getPrcrNm() { return prcrNm; }
    public void setPrcrNm(String prcrNm) { this.prcrNm = prcrNm; }
    public Date getPrcsDt() { return prcsDt; }
    public void setPrcsDt(Date prcsDt) { this.prcsDt = prcsDt; }
}
