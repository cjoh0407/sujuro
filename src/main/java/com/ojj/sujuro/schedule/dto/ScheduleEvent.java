package com.ojj.sujuro.schedule.dto;

/** 캘린더에 표시되는 일정 한 건. 공고에서 가져온 자동 일정(id 없음)과 팀장이 만든 내부 일정(id 있음)이 있다. */
public class ScheduleEvent {

    private Long id;           // 내부 일정만 있다. 자동 일정은 null
    private String eventDate;  // 2026-10-29
    private String timeText;   // 17:00. 시각이 없으면 null
    private String title;
    private String kindCd;     // 공통코드 SCHSE (SC010 제출 마감 ... SC050 서류 마감)
    private String teamNm;
    private Long pbancSn;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getEventDate() { return eventDate; }
    public void setEventDate(String v) { this.eventDate = v; }
    public String getTimeText() { return timeText; }
    public void setTimeText(String v) { this.timeText = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getKindCd() { return kindCd; }
    public void setKindCd(String v) { this.kindCd = v; }
    public String getTeamNm() { return teamNm; }
    public void setTeamNm(String v) { this.teamNm = v; }
    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long v) { this.pbancSn = v; }

    /** 공고에서 자동으로 들어온 일정인가 (수정·삭제 불가) */
    public boolean isAuto() {
        return id == null;
    }

    /** 범례 색 번호 1~5 (SC010→1 ... SC050→5). common.css 의 .ev.k1 ~ .k5 */
    public int getKindNo() {
        return kindNo(kindCd);
    }

    public static int kindNo(String kindCd) {
        if (kindCd == null || kindCd.length() < 4) {
            return 3;
        }
        int n = kindCd.charAt(3) - '0';
        return n >= 1 && n <= 5 ? n : 3;
    }
}
