package com.ojj.sujuro.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.ojj.sujuro.notification.support.NotificationSocketHandler;
import lombok.RequiredArgsConstructor;

/**
 * 웹소켓 설정 (FR-034 실시간 알림).
 *
 * - 주소: /ws/notifications  (static/js/notification-socket.js 가 연결한다)
 * - 로그인한 사용자만 연결할 수 있다. SecurityConfig 의 anyRequest().authenticated() 가 처리한다.
 * - 연결은 같은 출처(같은 주소)에서만 허용한다. 다른 사이트에서 몰래 연결하는 것을 막는다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String NOTIFICATION_PATH = "/ws/notifications";

    private final NotificationSocketHandler notificationHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, NOTIFICATION_PATH);
    }
}
