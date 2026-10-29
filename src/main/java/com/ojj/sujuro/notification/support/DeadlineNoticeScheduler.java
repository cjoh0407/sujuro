package com.ojj.sujuro.notification.support;

import com.ojj.sujuro.notification.service.DeadlineNoticeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감 임박 알림 실행 시각 (FR-031, NFR-017). 스케줄러 사용 설정은 AsyncSchedulingConfig 에 있다.
 *
 * - 알림 만들기: 매일 오전 9시 (notice.deadline.cron 으로 바꿀 수 있다)
 * - 메일 보내기·재시도: 10분마다 (notice.mail.cron)
 * - 서버가 시작될 때도 한 번 실행한다. 꺼져 있던 동안 놓친 알림을 채우기 위해서다. 중복은 생기지 않는다.
 * 실행 중 오류가 나도 스케줄러가 멈추지 않도록 여기서 잡는다.
 */
@Component
public class DeadlineNoticeScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineNoticeScheduler.class);

    private final DeadlineNoticeService service;

    public DeadlineNoticeScheduler(DeadlineNoticeService service) {
        this.service = service;
    }

    @Scheduled(cron = "${notice.deadline.cron:0 0 9 * * *}")
    public void createNotices() {
        try {
            service.createNotices();
        } catch (RuntimeException e) {
            log.error("마감 임박 알림 만들기 실패", e);
        }
    }

    @Scheduled(cron = "${notice.mail.cron:0 */10 * * * *}")
    public void sendMails() {
        try {
            service.sendPendingMails();
        } catch (RuntimeException e) {
            log.error("마감 임박 메일 발송 실패", e);
        }
    }

    /** 서버 시작 직후 한 번: 놓친 알림 채우기 */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        createNotices();
        sendMails();
    }
}
