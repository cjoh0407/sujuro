package com.ojj.sujuro.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 임박 알림 (FR-031, NFR-017)
 *
 * - 입찰 마감 D-3, D-1에 그 사업건의 제안팀 전원에게 화면 알림을 보낸다. D-1에는 메일도 보낸다.
 * - 대상은 참여 확정·제안 작성 중인 사업건이다. 제출 완료 이후와 불참은 보내지 않는다.
 * - 같은 사람에게 같은 사업건·같은 D-day 알림은 한 번만 만든다. (내용 맨 앞의 [D-3] 표시로 확인)
 *   그래서 서버가 꺼져 있다 켜져 다시 실행해도 중복되지 않고, 놓친 알림은 채워진다.
 * - 메일은 알림과 따로 보낸다. 실패하면 "메일 안 보냄"으로 남아 다음 실행 때 다시 시도한다. (최근 2일 것만)
 */
@Service
public class DeadlineNoticeService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineNoticeService.class);

    /** 알림을 보낼 남은 일수. 메일은 이 중 MAIL_DAY에만 보낸다. */
    public static final int[] NOTICE_DAYS = {3, 1};
    public static final int MAIL_DAY = 1;

    private final DeadlineMapper deadlineMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;
    private final NotificationMailSender mailSender;

    public DeadlineNoticeService(DeadlineMapper deadlineMapper, NotificationMapper notificationMapper,
                                 NotificationService notificationService, NotificationMailSender mailSender) {
        this.deadlineMapper = deadlineMapper;
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
        this.mailSender = mailSender;
    }

    /** D-3, D-1 알림을 만든다. 새로 만든 알림 수를 돌려준다. */
    @Transactional
    public int createNotices() {
        int created = 0;
        for (int days : NOTICE_DAYS) {
            created += createNotices(days);
        }
        return created;
    }

    int createNotices(int daysLeft) {
        String tag = tag(daysLeft);
        int created = 0;
        List<DeadlineTarget> targets = deadlineMapper.findTargets(daysLeft);
        for (DeadlineTarget t : targets) {
            String content = tag + " '" + t.getPbancNm() + "' 입찰 마감이 " + daysLeft + "일 남았습니다. (마감 " + t.getDdlnText() + ")";
            for (String receiverId : notificationMapper.findProposalMemberIds(t.getPbancSn())) {
                if (deadlineMapper.countSent(receiverId, t.getPbancSn(), NotificationType.DEADLINE_NEAR, tag) > 0) {
                    continue;
                }
                notificationService.send(receiverId, NotificationType.DEADLINE_NEAR, t.getPbancSn(), content);
                created++;
            }
        }
        if (created > 0) {
            log.info("마감 임박 알림 {}건 생성 (D-{})", created, daysLeft);
        }
        return created;
    }

    /** 아직 메일을 못 보낸 D-1 알림을 메일로 보낸다. 보낸 건수를 돌려준다. */
    public int sendPendingMails() {
        int sent = 0;
        for (PendingMail m : deadlineMapper.findPendingMails(NotificationType.DEADLINE_NEAR, tag(MAIL_DAY))) {
            if (mailSender.send(m.getEmlAddr(), "[수주로] 입찰 마감 임박 알림", m.getContent())) {
                deadlineMapper.markMailSent(m.getNotiSn());
                sent++;
            }
        }
        if (sent > 0) {
            log.info("마감 임박 메일 {}건 발송", sent);
        }
        return sent;
    }

    /** 알림 내용 맨 앞에 붙는 표시. 예: [D-3] */
    static String tag(int daysLeft) {
        return "[D-" + daysLeft + "]";
    }
}
