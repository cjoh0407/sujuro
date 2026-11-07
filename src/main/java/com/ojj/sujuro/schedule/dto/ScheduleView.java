package com.ojj.sujuro.schedule.dto;

import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.user.dto.CodeOption;
import lombok.Getter;
import lombok.Setter;

/** 일정 화면에 필요한 값 (월 캘린더) */
@Getter
@Setter
public class ScheduleView {

    /** 캘린더 칸 하나. day 가 0 이면 앞쪽 빈 칸 */
    @Getter
    public static class Cell {
        private final int day;
        private final boolean today;
        private final List<ScheduleEvent> events;

        public Cell(int day, boolean today, List<ScheduleEvent> events) {
            this.day = day;
            this.today = today;
            this.events = events;
        }

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
    private List<CodeOption> projects = new ArrayList<>();      // 내가 제안팀원인 사업건 전체 (지난 사업 포함. 일정을 보여 줄지·고른 사업이 맞는지 판단)
    private List<CodeOption> pickProjects = new ArrayList<>();  // 사업 고르기 드롭다운: 진행 중인 사업만 (+ 지난 사업이 골라져 있으면 그 하나)
    private Long selectedPbancSn;                               // null 이면 모든 사업
    private List<CodeOption> editProjects = new ArrayList<>();  // 팀장이 일정을 등록할 수 있는 사업건
    private boolean canEdit;                                    // 팀장만 내부 일정을 만든다 (FR-093)
    private ScheduleEvent editing;                              // 수정 중인 내부 일정
    private String today;                                       // 오늘 날짜(2026-11-02). 날짜 칸에서 이전 날짜를 못 고르게 한다
    private List<CodeOption> legendTeams = new ArrayList<>();   // 이번 달 캘린더에 나온 팀 (팀 색 범례, FR-094)

    /** 사업 고르기 드롭다운의 지금 고른 사업명 (마우스를 올리면 보이는 전체 이름). 안 골랐으면 '전체 사업' */
    public String getPickTitle() {
        if (selectedPbancSn != null) {
            for (CodeOption p : pickProjects) {
                if (p.getCode().equals(String.valueOf(selectedPbancSn))) {
                    return p.getName();
                }
            }
        }
        return "전체 사업";
    }

    /** 팀 코드(문자열)로 팀 색 번호 1~6을 구한다. 화면의 팀 범례용 */
    public int teamNoOf(String teamCode) {
        try {
            return ScheduleEvent.teamNo(Long.valueOf(teamCode));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
