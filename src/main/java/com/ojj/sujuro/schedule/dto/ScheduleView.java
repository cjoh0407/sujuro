package com.ojj.sujuro.schedule.dto;

import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.user.dto.CodeOption;

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
    private List<CodeOption> projects = new ArrayList<>();      // 내가 제안팀원인 사업건 (팀장·팀원, 2개 이상일 때 사업별 보기에 쓴다)
    private Long selectedPbancSn;                               // null 이면 모든 사업
    private List<CodeOption> editProjects = new ArrayList<>();  // 팀장이 일정을 등록할 수 있는 사업건
    private boolean canEdit;                                    // 팀장만 내부 일정을 만든다 (FR-093)
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
    /** 팀 코드(문자열)로 팀 색 번호 1~6을 구한다. 화면의 팀 범례용 */
    public int teamNoOf(String teamCode) {
        try {
            return ScheduleEvent.teamNo(Long.valueOf(teamCode));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    public List<CodeOption> getProjects() { return projects; }
    public void setProjects(List<CodeOption> v) { this.projects = v; }
    public Long getSelectedPbancSn() { return selectedPbancSn; }
    public void setSelectedPbancSn(Long v) { this.selectedPbancSn = v; }
    public List<CodeOption> getEditProjects() { return editProjects; }
    public void setEditProjects(List<CodeOption> v) { this.editProjects = v; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean v) { this.canEdit = v; }
    public ScheduleEvent getEditing() { return editing; }
    public void setEditing(ScheduleEvent v) { this.editing = v; }
}
