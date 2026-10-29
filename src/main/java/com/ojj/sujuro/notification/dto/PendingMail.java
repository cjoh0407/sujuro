package com.ojj.sujuro.notification.dto;

/** 아직 메일을 보내지 못한 알림 한 건 */
public class PendingMail {

    private Long notiSn;
    private String emlAddr;
    private String content;

    public Long getNotiSn() { return notiSn; }
    public void setNotiSn(Long notiSn) { this.notiSn = notiSn; }
    public String getEmlAddr() { return emlAddr; }
    public void setEmlAddr(String emlAddr) { this.emlAddr = emlAddr; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
