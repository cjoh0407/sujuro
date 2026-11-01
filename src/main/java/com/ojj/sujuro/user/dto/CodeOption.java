package com.ojj.sujuro.user.dto;

import lombok.Getter;
import lombok.Setter;

/** 선택 목록(직급, 계정 상태, 팀)에 쓰는 값 하나. 팀은 code에 팀 번호, extra에 부서코드가 들어간다. */
@Getter
@Setter
public class CodeOption {

    private String code;
    private String name;
    private String extra;
}
