package com.ojj.sujuro.schedule;

import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.user.CodeOption;

/** 일정 화면에 필요한 값 (월 캘린더) */
public class ScheduleView {

    /** 캘린더 칸 하나. day 가 0 이면 앞쪽 빈 칸 */
    public static class Cell {
        private final int day;
        private final boolean today;
        private final List<ScheduleEvent> events;

        public Cell(int day, boolean today, List<ScheduleEvent> events) {
            this.day = day;
            this.today = today;
            this.events = events;
        }

        public int getDay() { return day; }
        public boolean isToday() { return today; }
        public List<ScheduleEvent> getEvents() { return events; }
        public boolean isBlank() { return day == 0; }
    }

    private String ym;          // 2026-10
    private String label;       // 2026년 10월
    private String prevYm;
    private String nextYm;
    private String scopeText;   // 지금 보는 범위 설명
    private final List<Cell> cells = new ArrayList<>();
    private List<CodeOption> kinds = new ArrayList<>();         // 범례
    private List<CodeOption> teams = new ArrayList<>();         // 팀 탭 (영업부·경영지원실만)
    private Long selectedTeamSn;                                // null 이면 전체
    private boolean canEdit;                                    // 팀장만 내부 일정을 만든다 (FR-093)
    private List<CodeOption> editableKinds = new ArrayList<>(); // 내부 일정으로 만들 수 있는 구분
    private ScheduleEvent editing;                              // 수정 중인 내부 일정

    public String getYm() { return ym; }
    public void setYm(String v) { this.ym = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public String getPrevYm() { return prevYm; }
    public void setPrevYm(String v) { this.prevYm = v; }
    public String getNextYm() { return nextYm; }
    public void setNextYm(String v) { this.nextYm = v; }
    public String getScopeText() { return scopeText; }
    public void setScopeText(String v) { this.scopeText = v; }
    public List<Cell> getCells() { return cells; }
    public List<CodeOption> getKinds() { return kinds; }
    public void setKinds(List<CodeOption> v) { this.kinds = v; }
    public List<CodeOption> getTeams() { return teams; }
    public void setTeams(List<CodeOption> v) { this.teams = v; }
    public Long getSelectedTeamSn() { return selectedTeamSn; }
    public void setSelectedTeamSn(Long v) { this.selectedTeamSn = v; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean v) { this.canEdit = v; }
    public List<CodeOption> getEditableKinds() { return editableKinds; }
    public void setEditableKinds(List<CodeOption> v) { this.editableKinds = v; }
    public ScheduleEvent getEditing() { return editing; }
    public void setEditing(ScheduleEvent v) { this.editing = v; }
}
