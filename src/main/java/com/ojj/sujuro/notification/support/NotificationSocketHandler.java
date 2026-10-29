package com.ojj.sujuro.notification.support;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 실시간 알림 연결 관리 (FR-034).
 *
 * 로그인한 사용자가 화면을 열면 브라우저가 /ws/notifications 로 연결하고, 여기에 사번별로 모아 둔다.
 * 알림이 저장되면 {@link NotificationPublisher}가 {@link #pushTo}로 그 사람의 모든 연결(탭)에 보낸다.
 * 연결은 서버가 보내기만 하고, 브라우저가 보내는 메시지는 무시한다.
 *
 * 연결할 때 로그인 세션으로 사용자를 알아낸다. 로그인하지 않은 연결은 SecurityConfig가 막는다.
 */
@Component
public class NotificationSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationSocketHandler.class);

    /** 사번 → 그 사람의 연결들 (탭을 여러 개 열 수 있다) */
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = userIdOf(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        remove(session);
    }

    /** 그 사용자의 모든 연결에 보낸다. 연결이 없거나 보내다 실패해도 호출한 쪽에는 영향이 없다. */
    public void pushTo(String userId, String json) {
        Set<WebSocketSession> targets = sessions.get(userId);
        if (targets == null) {
            return;
        }
        for (WebSocketSession s : targets) {
            if (!s.isOpen()) {
                remove(s);
                continue;
            }
            try {
                synchronized (s) { // WebSocketSession은 동시에 여러 스레드가 보내면 안 된다.
                    s.sendMessage(new TextMessage(json));
                }
            } catch (IOException | RuntimeException e) {
                log.warn("실시간 알림 전송 실패 userId={} : {}", userId, e.getMessage());
                remove(s);
            }
        }
    }

    /** 지금 연결된 사용자 수 (확인용) */
    public int connectedUserCount() {
        return sessions.size();
    }

    private void remove(WebSocketSession session) {
        String userId = userIdOf(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(userId, set);
            }
        }
    }

    private static String userIdOf(WebSocketSession session) {
        Principal p = session.getPrincipal();
        return p == null ? null : p.getName();
    }
}
