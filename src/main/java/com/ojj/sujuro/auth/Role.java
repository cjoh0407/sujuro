package com.ojj.sujuro.auth;

/**
 * 사용자 권한 (FR-007). 직급·소속 부서에 따라 자동으로 정해지며 따로 지정하지 않는다.
 *
 * 외부 인력은 별도 권한이 아니라 팀원(MEMBER) + EXTERNAL 권한(LoginUser.EXTERNAL_AUTHORITY)으로 구분한다.
 * DB에 저장하는 권한 코드값이 다르면 이 파일만 고친다.
 */
public enum Role {

    MGMT("경영지원실"),
    SALES("영업부"),
    LEADER("팀장"),
    MEMBER("팀원");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Spring Security의 권한 이름 (예: ROLE_MGMT). hasRole('MGMT')로 검사한다. */
    public String getAuthority() {
        return "ROLE_" + name();
    }

    /** 소속 팀과 직급으로 권한을 정한다. (FR-007) */
    public static Role resolve(String team, String rank) {
        if ("영업부".equals(team)) {
            return SALES;
        }
        if ("경영지원실".equals(team)) {
            return MGMT;
        }
        if ("팀장".equals(rank)) {
            return LEADER;
        }
        return MEMBER;
    }
}
