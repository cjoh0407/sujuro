package com.ojj.sujuro.user;

/** 직원 추가·수정 화면에서 입력받는 값. */
public class UserForm {

    private String userId;   // 사번 (추가할 때만 입력)
    private String userNm;   // 이름
    private Long teamSn;     // 소속 팀
    private String jbgdCd;   // 직급
    private String telno;    // 전화번호 (선택)
    private String emlAddr;  // 이메일 (선택)

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserNm() { return userNm; }
    public void setUserNm(String userNm) { this.userNm = userNm; }
    public Long getTeamSn() { return teamSn; }
    public void setTeamSn(Long teamSn) { this.teamSn = teamSn; }
    public String getJbgdCd() { return jbgdCd; }
    public void setJbgdCd(String jbgdCd) { this.jbgdCd = jbgdCd; }
    public String getTelno() { return telno; }
    public void setTelno(String telno) { this.telno = telno; }
    public String getEmlAddr() { return emlAddr; }
    public void setEmlAddr(String emlAddr) { this.emlAddr = emlAddr; }
}
