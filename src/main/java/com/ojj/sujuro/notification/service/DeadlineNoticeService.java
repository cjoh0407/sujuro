package com.ojj.sujuro.notification.service;

import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.dto.DeadlineTarget;
import com.ojj.sujuro.notification.dto.PendingMail;
import com.ojj.sujuro.notification.dto.ScheduleTarget;
import com.ojj.sujuro.notification.mapper.DeadlineMapper;
import com.ojj.sujuro.notification.mapper.NotificationMapper;
import com.ojj.sujuro.notification.support.NotificationMailSender;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 임박 알림 (FR-031, NFR-017)
 *
 * - 입찰 마감 하루 전(D-1)에 그 사업건의 제안팀 전원에게 화면 알림과 메일을 보낸다.
 * - 팀장이 사업건에 등록한 일정은 하루 전(D-1)에 그 사업건의 제안팀원 전원에게 화면 알림과 메일을 보낸다.
 * - 공고 단계 전환(NT150) 알림의 메일도 여기서 보낸다. (알림은 수집기가 만든다)
 * - 대상은 참여 확정·제안 작성 중인 사업건이다. 제출 완료 이후와 불참은 보내지 않는다.
 * - 같은 사람에게 같은 사업건·같은 D-day 알림은 한 번만 만든다. (내용 맨 앞의 [D-1] 표시로 확인)
 *   그래서 서버가 꺼져 있다 켜져 다시 실행해도 중복되지 않고, 놓친 알림은 채워진다.
 * - 메일은 알림과 따로 보낸다. 실패하면 "메일 안 보냄"으로 남아 다음 실행 때 다시 시도한다. (최근 2일 것만)
 */
@Service
public class DeadlineNoticeService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineNoticeService.class);

    /** 알림을 보낼 남은 일수. 마감 하루 전(D-1) 하나만이고, 메일도 같은 날 보낸다. */
    public static final int[] NOTICE_DAYS = {1};
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

    /** D-1 알림을 만든다. 새로 만든 알림 수를 돌려준다. */
    @Transactional
    public int createNotices() {
        int created = 0;
        for (int days : NOTICE_DAYS) {
            created += createNotices(days);
        }
        created += createScheduleNotices();
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

    /**
     * 아직 메일을 못 보낸 알림을 메일로 보낸다. 보낸 건수를 돌려준다.
     * 입찰 마감 D-1, 팀 일정 D-1, 공고 단계 전환(NT150)이 대상이다. 화면 알림은 이미 갔고, 메일만 따로 재시도한다.
     */
    public int sendPendingMails() {
        int sent = 0;
        sent += sendPending(NotificationType.DEADLINE_NEAR, tag(MAIL_DAY), "[수주로] 입찰 마감 임박 알림");
        sent += sendPending(NotificationType.DEADLINE_NEAR, SCHEDULE_TAG, "[수주로] 내일 일정 알림");
        sent += sendPending(NotificationType.BID_STAGE_CHANGED, "", "[수주로] 공고 단계 전환 알림");
        return sent;
    }

    private int sendPending(String typeCd, String tag, String subject) {
        int sent = 0;
        for (PendingMail m : deadlineMapper.findPendingMails(typeCd, tag)) {
            if (mailSender.send(m.getEmlAddr(), subject, m.getContent())) {
                deadlineMapper.markMailSent(m.getNotiSn());
                sent++;
            }
        }
        if (sent > 0) {
            log.info("{} 메일 {}건 발송", subject, sent);
        }
        return sent;
    }

    /** 일정 D-1 알림 맨 앞의 표시. 입찰 마감 알림([D-1])과 섞이지 않게 따로 둔다. */
    static final String SCHEDULE_TAG = "[일정 D-1]";

    /** 내일 시작하는 일정을 그 사업건의 제안팀원 전체에게 알린다. 새로 만든 알림 수를 돌려준다. (메일은 sendPendingMails 가 보낸다) */
    @Transactional
    public int createScheduleNotices() {
        int created = 0;
        for (ScheduleTarget t : deadlineMapper.findScheduleTargets(MAIL_DAY)) {
            String content = SCHEDULE_TAG + " 내일 '" + t.getTitle() + "' 일정이 있습니다. (" + t.getWhenText() + ") · " + t.getPbancNm();
            for (String receiverId : notificationMapper.findProposalMemberIds(t.getPbancSn())) {
                if (deadlineMapper.countSentByContent(receiverId, NotificationType.DEADLINE_NEAR, content) > 0) {
                    continue;
                }
                notificationService.send(receiverId, NotificationType.DEADLINE_NEAR, t.getPbancSn(), content);
                created++;
            }
        }
        if (created > 0) {
            log.info("일정 D-1 알림 {}건 생성", created);
        }
        return created;
    }

    /** 알림 내용 맨 앞에 붙는 표시. 예: [D-1] */
    static String tag(int daysLeft) {
        return "[D-" + daysLeft + "]";
    }
}
