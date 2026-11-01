package com.ojj.sujuro.notification.dto;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/** 알림 목록 한 줄. 받은 사람 본인에게만 보인다. */
@Getter
@Setter
public class NotificationDto {

    private Long notiSn;
    private Long pbancSn;
    private String typeCd;
    private String typeNm;
    private String content;
    private String prslYn;
    private Date crtDt;
    /** 눌렀을 때 이동할 주소. 이동할 곳이 없거나 볼 권한이 없으면 null */
    private String link;

    public boolean isRead() { return "Y".equals(prslYn); }
}
