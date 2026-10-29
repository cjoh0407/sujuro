package com.ojj.sujuro.notification.dto;

/** 마감이 다가온 사업건 한 건 (마감 임박 알림 대상) */
public class DeadlineTarget {

    private Long pbancSn;
    private String pbancNm;
    /** 입찰 마감 일시 (화면에 보이는 형식: 2026-10-29 17:00) */
    private String ddlnText;

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }
    public String getPbancNm() { return pbancNm; }
    public void setPbancNm(String pbancNm) { this.pbancNm = pbancNm; }
    public String getDdlnText() { return ddlnText; }
    public void setDdlnText(String ddlnText) { this.ddlnText = ddlnText; }
}
