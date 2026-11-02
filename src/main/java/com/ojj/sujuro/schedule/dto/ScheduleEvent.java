package com.ojj.sujuro.schedule.dto;

import lombok.Getter;
import lombok.Setter;

/** 캘린더에 표시되는 일정 한 건. 공고에서 가져온 자동 일정(id 없음)과 팀장이 만든 내부 일정(id 있음)이 있다. */
@Getter
@Setter
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
    private boolean editable;  // 보는 사람이 눌러서 관리(수정·삭제)할 수 있는 내부 일정인가 (등록한 팀의 팀장)
    private boolean changeable; // 그중 내용까지 고칠 수 있는가. 지난 일정이거나 사업이 끝났으면 false (삭제만 된다)
    private String lockReason;  // changeable 이 false 인 이유 (화면 안내)

    /** 팀 색 번호 1~6 (common.css 의 .ev.tm1 ~ .tm6). 팀이 없으면 0 */
    public int getTeamNo() {
        return teamNo(teamSn);
    }

    public static int teamNo(Long teamSn) {
        return teamSn == null ? 0 : (int) (Math.abs(teamSn) % 6) + 1;
    }

    /**
     * 캘린더에 보이는 글자. 제출 마감(자동)은 "[마감] 10:00 사업명", 직접 등록한 일정은 "14:00 일정명" (FR-094).
     * 팀 이름은 화면에서 영업부·경영지원실에게만 앞에 붙인다 (FR-091).
     */
    public String getLabel() {
        String time = timeText == null ? "" : timeText + " ";
        if (isAuto()) {
            return "[마감] " + time + (pbancNm != null ? pbancNm : title);
        }
        return time + title;
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
