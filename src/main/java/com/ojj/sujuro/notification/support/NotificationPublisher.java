package com.ojj.sujuro.notification.support;

import com.ojj.sujuro.notification.mapper.NotificationInboxMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 저장한 알림을 접속 중인 사용자에게 바로 보낸다. (FR-034)
 *
 * 알림은 호출한 쪽의 트랜잭션에 묶여 저장되므로, 트랜잭션 안이면 "커밋된 뒤"에 보낸다.
 * 처리가 실패해 되돌려진 알림이 화면에 뜨는 일을 막기 위해서다.
 * 보내기에 실패해도 알림 저장과 호출한 쪽의 처리에는 영향이 없다. (알림은 DB에 남아 있어 목록에서 볼 수 있다.)
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final NotificationSocketHandler handler;
    private final NotificationInboxMapper inboxMapper;
    private final ObjectMapper objectMapper;

    public NotificationPublisher(NotificationSocketHandler handler, NotificationInboxMapper inboxMapper,
                                 ObjectMapper objectMapper) {
        this.handler = handler;
        this.inboxMapper = inboxMapper;
        this.objectMapper = objectMapper;
    }

    /** 저장이 끝난 알림 한 건을 보낸다. 트랜잭션이 있으면 커밋 뒤에 보낸다. */
    public void publish(String receiverId, String typeCd, Long pbancSn, String content) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push(receiverId, typeCd, pbancSn, content);
                }
            });
        } else {
            push(receiverId, typeCd, pbancSn, content);
        }
    }

    private void push(String receiverId, String typeCd, Long pbancSn, String content) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", typeCd);
            body.put("pbancSn", pbancSn);
            body.put("content", content);
            body.put("unreadCount", inboxMapper.countUnread(receiverId));
            handler.pushTo(receiverId, objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("실시간 알림 만들기 실패 receiverId={} : {}", receiverId, e.getMessage());
        }
    }
}
