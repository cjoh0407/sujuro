package com.ojj.sujuro.notification.dto;

/** 내일 시작하는 사업건 일정 한 건 (일정 D-1 알림 대상) */
public class ScheduleTarget {

    private Long schdlSn;
    private Long pbancSn;
    private String pbancNm;
    private String title;
    /** 화면에 보이는 형식: 2026-11-05 또는 시간이 있으면 2026-11-05 14:00 */
    private String whenText;

    public Long getSchdlSn() { return schdlSn; }
    public void setSchdlSn(Long schdlSn) { this.schdlSn = schdlSn; }
    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }
    public String getPbancNm() { return pbancNm; }
    public void setPbancNm(String pbancNm) { this.pbancNm = pbancNm; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getWhenText() { return whenText; }
    public void setWhenText(String whenText) { this.whenText = whenText; }
}
