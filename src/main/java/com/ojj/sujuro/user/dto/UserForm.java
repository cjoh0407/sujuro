package com.ojj.sujuro.user.dto;

import lombok.Getter;
import lombok.Setter;

/** 직원 추가·수정 화면에서 입력받는 값. */
@Getter
@Setter
public class UserForm {

    private String userId;   // 사번 (추가할 때만 입력)
    private String userNm;   // 이름
    private Long teamSn;     // 소속 팀
    private String jbgdCd;   // 직급
    private String telno;    // 전화번호 (선택)
    private String emlAddr;  // 이메일 (필수)

}
