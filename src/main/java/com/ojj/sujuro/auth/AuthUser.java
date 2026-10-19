package com.ojj.sujuro.auth;

/**
 * 로그인 조회 결과 (AuthMapper.findByUserId). DB 값을 그대로 담는 그릇이다.
 * 화면과 서비스에서는 이 객체가 아니라 {@link LoginUser}를 쓴다.
 */
public class AuthUser {

    private String userId;
    private String userNm;
    private String jbgdNm;
    private String teamNm;
    private String authrtCd;
    private String enpswd;
    private String acntSttsCd;
    private String otsdMnpwYn;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserNm() { return userNm; }
    public void setUserNm(String userNm) { this.userNm = userNm; }

    /** 직급명 (공통코드 JBGDS) */
    public String getJbgdNm() { return jbgdNm; }
    public void setJbgdNm(String jbgdNm) { this.jbgdNm = jbgdNm; }

    /** 팀명. 외부 인력은 회사명 */
    public String getTeamNm() { return teamNm; }
    public void setTeamNm(String teamNm) { this.teamNm = teamNm; }

    /** 권한코드 (공통코드 AUTHS) */
    public String getAuthrtCd() { return authrtCd; }
    public void setAuthrtCd(String authrtCd) { this.authrtCd = authrtCd; }

    public String getEnpswd() { return enpswd; }
    public void setEnpswd(String enpswd) { this.enpswd = enpswd; }

    /** 계정상태코드 (공통코드 ACSTS) */
    public String getAcntSttsCd() { return acntSttsCd; }
    public void setAcntSttsCd(String acntSttsCd) { this.acntSttsCd = acntSttsCd; }

    public String getOtsdMnpwYn() { return otsdMnpwYn; }
    public void setOtsdMnpwYn(String otsdMnpwYn) { this.otsdMnpwYn = otsdMnpwYn; }
}
