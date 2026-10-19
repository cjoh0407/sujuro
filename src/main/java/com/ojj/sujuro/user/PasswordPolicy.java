package com.ojj.sujuro.user;

import java.util.ArrayList;
import java.util.List;

/**
 * 비밀번호 규칙 (FR-002-1). 새 비밀번호는 8자 이상이며 영문·숫자·특수문자를 모두 섞어야 한다.
 * 규칙을 어기면 어떤 조건이 부족한지 모두 알려 주기 위해 안내 문구 목록을 돌려준다.
 * (BCrypt는 72바이트까지만 읽으므로 64자까지만 받는다.)
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    /** 규칙에 맞지 않는 항목의 안내 문구. 모두 맞으면 빈 목록. */
    public static List<String> check(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            errors.add(MIN_LENGTH + "자 이상이어야 합니다.");
        }
        if (password != null && password.length() > MAX_LENGTH) {
            errors.add(MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (password == null || !password.matches(".*[A-Za-z].*")) {
            errors.add("영문을 포함해야 합니다.");
        }
        if (password == null || !password.matches(".*[0-9].*")) {
            errors.add("숫자를 포함해야 합니다.");
        }
        if (password == null || !password.matches(".*[!-/:-@\\[-`{-~].*")) {
            errors.add("특수문자(! @ # $ % 등)를 포함해야 합니다.");
        }
        return errors;
    }
}
