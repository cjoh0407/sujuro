package com.ojj.sujuro.notification.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 메일 발송 (FR-031). 메일 서버 설정(spring.mail.*)이 없으면 보내지 않고 false를 돌려준다.
 * 실패해도 예외를 던지지 않는다. 보내지 못한 알림은 "메일 안 보냄(N)"으로 남아 다음 실행 때 다시 시도된다. (NFR-017)
 *
 * 설정 예 (application-local.properties):
 *   spring.mail.host=smtp.example.com
 *   spring.mail.username=...
 *   spring.mail.password=...
 *   notice.mail.from=noreply@example.com
 */
@Component
@Slf4j
public class NotificationMailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public NotificationMailSender(ObjectProvider<JavaMailSender> mailSender,
                                  @Value("${notice.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** 보냈으면 true. 메일 설정이 없거나 보내지 못했으면 false */
    public boolean send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("메일 서버 설정(spring.mail.host)이 없어 메일을 보내지 않습니다. to={}", to);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (from != null && !from.isEmpty()) {
                msg.setFrom(from);
            }
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            return true;
        } catch (RuntimeException e) {
            log.warn("메일 발송 실패 to={} : {}", to, e.getMessage());
            return false;
        }
    }
}
