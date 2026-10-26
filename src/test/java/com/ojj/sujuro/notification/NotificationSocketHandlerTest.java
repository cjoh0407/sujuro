package com.ojj.sujuro.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** 실시간 알림 연결 관리 (FR-034): 사번별로 연결을 모아 그 사람에게만 보낸다. */
class NotificationSocketHandlerTest {

    private NotificationSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NotificationSocketHandler();
    }

    private WebSocketSession session(String userId) {
        WebSocketSession s = mock(WebSocketSession.class);
        Principal p = mock(Principal.class);
        when(p.getName()).thenReturn(userId);
        when(s.getPrincipal()).thenReturn(userId == null ? null : p);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    @Test
    void 연결된_사용자의_모든_탭에_보낸다() throws Exception {
        WebSocketSession tab1 = session("E1");
        WebSocketSession tab2 = session("E1");
        handler.afterConnectionEstablished(tab1);
        handler.afterConnectionEstablished(tab2);

        handler.pushTo("E1", "{\"a\":1}");

        verify(tab1).sendMessage(any(TextMessage.class));
        verify(tab2).sendMessage(any(TextMessage.class));
    }

    @Test
    void 다른_사람에게는_보내지_않는다() throws Exception {
        WebSocketSession other = session("E2");
        handler.afterConnectionEstablished(other);

        handler.pushTo("E1", "{}");

        verify(other, never()).sendMessage(any());
    }

    @Test
    void 연결이_끊기면_더_이상_보내지_않는다() throws Exception {
        WebSocketSession s = session("E1");
        handler.afterConnectionEstablished(s);
        handler.afterConnectionClosed(s, CloseStatus.NORMAL);

        handler.pushTo("E1", "{}");

        verify(s, never()).sendMessage(any());
        assertEquals(0, handler.connectedUserCount());
    }

    @Test
    void 로그인_정보가_없는_연결은_닫는다() throws Exception {
        WebSocketSession anon = session(null);
        handler.afterConnectionEstablished(anon);

        verify(anon).close(CloseStatus.POLICY_VIOLATION);
        assertEquals(0, handler.connectedUserCount());
    }

    @Test
    void 연결이_없는_사용자에게_보내도_오류가_없다() {
        handler.pushTo("NOBODY", "{}");
    }
}
