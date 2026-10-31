package com.ojj.sujuro.schedule.dto;

/** 캘린더에 표시되는 일정 한 건. 공고에서 가져온 자동 일정(id 없음)과 팀장이 만든 내부 일정(id 있음)이 있다. */
public class ScheduleEvent {

    private Long id;           // 내부 일정만 있다. 자동 일정은 null
    private String eventDate;  // 2026-10-29
    private String timeText;   // 17:00. 시각이 없으면 null
    private String title;
    private String kindCd;     // 공통코드 SCHSE (SC010 제출 마감, SC020 내부 일정. SC030~SC050은 쓰지 않는다)
    private String teamNm;
    private Long pbancSn;
    private String pbancNm;    // 사업건 이름
    private Long teamSn;       // 일정을 맡은 팀 (자동 일정은 주관팀)
    private boolean editable;  // 보는 사람이 고칠 수 있는 내부 일정인가 (주관팀 팀장 본인 팀 것)

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

    public String getPbancNm() { return pbancNm; }
    public void setPbancNm(String v) { this.pbancNm = v; }
    public Long getTeamSn() { return teamSn; }
    public void setTeamSn(Long v) { this.teamSn = v; }
    public boolean isEditable() { return editable; }
    public void setEditable(boolean v) { this.editable = v; }

    /** 팀 색 번호 1~6 (common.css 의 .ev.tm1 ~ .tm6). 팀이 없으면 0 */
    public int getTeamNo() {
        return teamNo(teamSn);
    }

    public static int teamNo(Long teamSn) {
        return teamSn == null ? 0 : (int) (Math.abs(teamSn) % 6) + 1;
    }

    /** 공고에서 자동으로 들어온 일정인가 (수정·삭제 불가) */
    public boolean isAuto() {
        return id == null;
    }

    /** 범례 색 번호 1~5 (SC010→1, SC020→2 ...). common.css 의 .ev.k1 ~ .k5 */
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
