package com.ojj.sujuro.notification.dto;

import lombok.Getter;
import lombok.Setter;

/** 마감이 다가온 사업건 한 건 (마감 임박 알림 대상) */
@Getter
@Setter
public class DeadlineTarget {

    private Long pbancSn;
    private String pbancNm;
    /** 입찰 마감 일시 (화면에 보이는 형식: 2026-10-29 17:00) */
    private String ddlnText;
}
