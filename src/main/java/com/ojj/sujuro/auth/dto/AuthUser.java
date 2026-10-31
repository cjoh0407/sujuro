package com.ojj.sujuro.auth.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 로그인 조회 결과 (AuthMapper.findByUserId). DB 값을 그대로 담는 그릇이다.
 * 화면과 서비스에서는 이 객체가 아니라 {@link LoginUser}를 쓴다.
 */
@Getter
@Setter
public class AuthUser {

    private String userId;
    private String userNm;
    /** 직급명 (공통코드 JBGDS) */
    private String jbgdNm;
    /** 팀명 */
    private String teamNm;
    /** 권한코드 (공통코드 AUTHS) */
    private String authrtCd;
    private String enpswd;
    /** 계정상태코드 (공통코드 ACSTS) */
    private String acntSttsCd;
}
