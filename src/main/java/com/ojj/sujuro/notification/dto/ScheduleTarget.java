package com.ojj.sujuro.notification.dto;

import lombok.Getter;
import lombok.Setter;

/** 내일 시작하는 사업건 일정 한 건 (일정 D-1 알림 대상) */
@Getter
@Setter
public class ScheduleTarget {

    private Long schdlSn;
    private Long pbancSn;
    private String pbancNm;
    private String title;
    /** 화면에 보이는 형식: 2026-11-05 또는 시간이 있으면 2026-11-05 14:00 */
    private String whenText;
}
