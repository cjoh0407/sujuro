package com.ojj.sujuro.user;

/** 선택 목록(직급, 계정 상태, 팀)에 쓰는 값 하나. 팀은 code에 팀 번호, extra에 부서코드가 들어간다. */
public class CodeOption {

    private String code;
    private String name;
    private String extra;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
}
