package com.ojj.sujuro.notification;

import java.util.Date;

/** 알림 목록 한 줄. 받은 사람 본인에게만 보인다. */
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

    public Long getNotiSn() { return notiSn; }
    public void setNotiSn(Long notiSn) { this.notiSn = notiSn; }
    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }
    public String getTypeCd() { return typeCd; }
    public void setTypeCd(String typeCd) { this.typeCd = typeCd; }
    public String getTypeNm() { return typeNm; }
    public void setTypeNm(String typeNm) { this.typeNm = typeNm; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPrslYn() { return prslYn; }
    public void setPrslYn(String prslYn) { this.prslYn = prslYn; }
    public Date getCrtDt() { return crtDt; }
    public void setCrtDt(Date crtDt) { this.crtDt = crtDt; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public boolean isRead() { return "Y".equals(prslYn); }
}
