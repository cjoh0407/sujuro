package com.ojj.sujuro.user.dto;

import java.util.Date;

/** 직원 목록·수정 화면에 보여 줄 사용자 한 명의 정보 (TB_USER + 팀명 + 공통코드 이름). */
public class UserDto {

    private String userId;      // 사번 = 로그인 ID
    private String userNm;      // 이름
    private Long teamSn;        // 소속 팀 번호
    private String teamNm;      // 소속 팀 이름
    private String jbgdCd;      // 직급코드
    private String jbgdNm;      // 직급 이름
    private String authrtCd;    // 권한코드
    private String authrtNm;    // 권한 이름
    private String acntSttsCd;  // 계정상태코드
    private String acntSttsNm;  // 계정상태 이름
    private String telno;       // 전화번호
    private String emlAddr;     // 이메일
    private Date regDt;         // 등록일시

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserNm() { return userNm; }
    public void setUserNm(String userNm) { this.userNm = userNm; }
    public Long getTeamSn() { return teamSn; }
    public void setTeamSn(Long teamSn) { this.teamSn = teamSn; }
    public String getTeamNm() { return teamNm; }
    public void setTeamNm(String teamNm) { this.teamNm = teamNm; }
    public String getJbgdCd() { return jbgdCd; }
    public void setJbgdCd(String jbgdCd) { this.jbgdCd = jbgdCd; }
    public String getJbgdNm() { return jbgdNm; }
    public void setJbgdNm(String jbgdNm) { this.jbgdNm = jbgdNm; }
    public String getAuthrtCd() { return authrtCd; }
    public void setAuthrtCd(String authrtCd) { this.authrtCd = authrtCd; }
    public String getAuthrtNm() { return authrtNm; }
    public void setAuthrtNm(String authrtNm) { this.authrtNm = authrtNm; }
    public String getAcntSttsCd() { return acntSttsCd; }
    public void setAcntSttsCd(String acntSttsCd) { this.acntSttsCd = acntSttsCd; }
    public String getAcntSttsNm() { return acntSttsNm; }
    public void setAcntSttsNm(String acntSttsNm) { this.acntSttsNm = acntSttsNm; }
    public String getTelno() { return telno; }
    public void setTelno(String telno) { this.telno = telno; }
    public String getEmlAddr() { return emlAddr; }
    public void setEmlAddr(String emlAddr) { this.emlAddr = emlAddr; }
    public Date getRegDt() { return regDt; }
    public void setRegDt(Date regDt) { this.regDt = regDt; }

    /** 화면에서 상태 색을 고르는 데 쓴다. */
    public boolean isActive() { return "AS030".equals(trim(acntSttsCd)); }
    public boolean isInitial() { return "AS020".equals(trim(acntSttsCd)); }
    public boolean isInactive() { return "AS050".equals(trim(acntSttsCd)); }

    private static String trim(String s) { return s == null ? null : s.trim(); }
}
