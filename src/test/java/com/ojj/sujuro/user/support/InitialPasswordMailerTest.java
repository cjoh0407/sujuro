package com.ojj.sujuro.user.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.ojj.sujuro.notification.support.NotificationMailSender;

/** 초기 비밀번호 안내 메일: 받는 주소, 본문, 이메일 가리기 */
class InitialPasswordMailerTest {

    private final NotificationMailSender sender = mock(NotificationMailSender.class);
    private final InitialPasswordMailer mailer = new InitialPasswordMailer(sender, "http://3.35.131.20/");

    @Test
    void 직원_이메일로_사번과_초기_비밀번호와_로그인_주소를_보낸다() {
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);

        assertTrue(mailer.send("홍길동", "2024-0001", " hong@company.com ", "Tmp!abcd1", true));

        verify(sender).send(eq("hong@company.com"), eq(InitialPasswordMailer.SUBJECT), contains("Tmp!abcd1"));
        String body = mailer.body("홍길동", "2024-0001", "Tmp!abcd1", true);
        assertTrue(body.contains("2024-0001"));
        assertTrue(body.contains("http://3.35.131.20/login"));
        assertTrue(body.contains("계정이 만들어졌습니다"));
        assertTrue(mailer.body("홍길동", "2024-0001", "x", false).contains("초기화했습니다"));
    }

    @Test
    void 메일을_못_보내거나_이메일이_없으면_false() {
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(false);
        assertFalse(mailer.send("홍길동", "2024-0001", "hong@company.com", "x", false));

        assertFalse(mailer.send("홍길동", "2024-0001", null, "x", false));
        assertFalse(mailer.send("홍길동", "2024-0001", "  ", "x", false));
        verify(sender, never()).send(eq("  "), anyString(), anyString());
    }

    @Test
    void 주소가_비어_있으면_본문에_로그인_주소를_넣지_않는다() {
        InitialPasswordMailer noUrl = new InitialPasswordMailer(sender, "");
        assertFalse(noUrl.body("홍길동", "2024-0001", "x", true).contains("로그인 주소"));
    }

    @Test
    void 이메일_앞부분을_가린다() {
        assertEquals("h***@company.com", InitialPasswordMailer.mask("hong@company.com"));
        assertEquals("***", InitialPasswordMailer.mask("nope"));
        assertEquals("", InitialPasswordMailer.mask(null));
    }
}
