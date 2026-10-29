package com.ojj.sujuro.user.support;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 비밀번호 규칙 (FR-002-1). 새 비밀번호는 8자 이상이며 영문·숫자·특수문자를 모두 섞어야 한다.
 * 규칙을 어기면 어떤 조건이 부족한지 모두 알려 주기 위해 안내 문구 목록을 돌려준다.
 * (BCrypt는 72바이트까지만 읽으므로 64자까지만 받는다.)
 *
 * 초기 비밀번호(FR-004, FR-008)는 {@link #generateInitial()}로 만든다.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    /** 초기 비밀번호 길이. 전달할 때 읽기 쉽도록 헷갈리는 글자(0 O 1 l I)는 뺀다. */
    private static final int INITIAL_LENGTH = 12;
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%^*";
    private static final SecureRandom RANDOM = new SecureRandom();

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

    /** 규칙을 모두 만족하는 임의의 초기 비밀번호를 만든다. (영문·숫자·특수문자 각 1자 이상) */
    public static String generateInitial() {
        List<Character> chars = new ArrayList<>();
        chars.add(pick(LETTERS));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIALS));
        String all = LETTERS + DIGITS + SPECIALS;
        while (chars.size() < INITIAL_LENGTH) {
            chars.add(pick(all));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
