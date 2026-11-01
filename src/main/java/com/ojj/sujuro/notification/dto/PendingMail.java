package com.ojj.sujuro.notification.dto;

import lombok.Getter;
import lombok.Setter;

/** 아직 메일을 보내지 못한 알림 한 건 */
@Getter
@Setter
public class PendingMail {

    private Long notiSn;
    private String emlAddr;
    private String content;
}
