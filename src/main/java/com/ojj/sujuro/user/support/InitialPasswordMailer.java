package com.ojj.sujuro.user.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ojj.sujuro.notification.support.NotificationMailSender;

/**
 * 초기 비밀번호 안내 메일 (FR-004 직원 추가, FR-008 비밀번호 초기화).
 *
 * 화면에서 바로 보낸다. 알림 테이블(TB_NOTI)을 거쳐 나중에 보내면 비밀번호가 DB에 글자 그대로 남기 때문이다.
 * 보냈으면 true. 메일 설정이 없거나 보내지 못했으면 false 이고, 그때는 화면에 비밀번호를 한 번 보여 줘서
 * 경영지원실이 직접 전달하게 한다. (메일 서버 제한 시간 5초: application.properties)
 */
@Component
public class InitialPasswordMailer {

    static final String SUBJECT = "[수주로] 초기 비밀번호 안내";

    private final NotificationMailSender mailSender;
    private final String baseUrl;

    public InitialPasswordMailer(NotificationMailSender mailSender,
                                 @Value("${app.base-url:}") String baseUrl) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    /**
     * @param newUser true 면 신규 등록, false 면 비밀번호 초기화 안내 문구를 쓴다
     */
    public boolean send(String userNm, String userId, String email, String password, boolean newUser) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return mailSender.send(email.trim(), SUBJECT, body(userNm, userId, password, newUser));
    }

    String body(String userNm, String userId, String password, boolean newUser) {
        StringBuilder b = new StringBuilder();
        b.append(userNm).append(" 님, 안녕하세요.\n\n");
        b.append(newUser ? "수주로 계정이 만들어졌습니다. 아래 초기 비밀번호로 로그인해 주세요.\n\n"
                         : "경영지원실에서 수주로 비밀번호를 초기화했습니다. 아래 초기 비밀번호로 로그인해 주세요.\n\n");
        b.append("  로그인 ID(사번): ").append(userId).append('\n');
        b.append("  초기 비밀번호: ").append(password).append("\n\n");
        b.append("처음 로그인하면 비밀번호를 새로 바꿔야 합니다.\n");
        if (!baseUrl.isEmpty()) {
            b.append("로그인 주소: ").append(baseUrl).append("/login\n");
        }
        b.append("\n본인이 요청한 일이 아니라면 경영지원실에 알려 주세요.\n");
        b.append("이 메일은 보내기 전용입니다.\n");
        return b.toString();
    }

    /** 화면 안내용으로 이메일 앞부분을 가린다. hong@company.com → h***@company.com */
    public static String mask(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
