package com.ojj.sujuro.auth;

/**
 * 사용자 권한 (FR-007). 직급·소속 부서에 따라 자동으로 정해지며 따로 지정하지 않는다.
 *
 * 외부 인력은 별도 권한이 아니라 팀원(MEMBER) + EXTERNAL 권한(LoginUser.EXTERNAL_AUTHORITY)으로 구분한다.
 * 로그인할 때는 DB의 권한코드(TB_USER.AUTHRT_CD, 공통코드 AUTHS)를 읽어 변환한다. 코드값이 바뀌면 이 파일만 고친다.
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

    /** 공통코드 AUTHS의 권한코드 */
    public static final String CD_SALES = "AU010";
    public static final String CD_MGMT = "AU020";
    public static final String CD_LEADER = "AU030";
    public static final String CD_MEMBER = "AU040";
    public static final String CD_EXTERNAL = "AU050";

    /** DB의 권한코드를 권한으로 바꾼다. 외부(AU050)는 팀원 권한에 EXTERNAL이 더해진다. 모르는 코드는 가장 낮은 권한(팀원) */
    public static Role fromAuthorityCode(String code) {
        if (code == null) {
            return MEMBER;
        }
        switch (code.trim()) {
            case CD_SALES:
                return SALES;
            case CD_MGMT:
                return MGMT;
            case CD_LEADER:
                return LEADER;
            default:
                return MEMBER;
        }
    }

    public static boolean isExternalCode(String code) {
        return code != null && CD_EXTERNAL.equals(code.trim());
    }

    /** 소속 팀과 직급으로 권한을 정한다. (FR-007) 사용자 등록 화면에서 권한코드를 정할 때 쓴다. */
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
