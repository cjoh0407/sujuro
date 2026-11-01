package com.ojj.sujuro.notification.service;

import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.dto.DeadlineTarget;
import com.ojj.sujuro.notification.dto.PendingMail;
import com.ojj.sujuro.notification.dto.ScheduleTarget;
import com.ojj.sujuro.notification.mapper.DeadlineMapper;
import com.ojj.sujuro.notification.mapper.NotificationMapper;
import com.ojj.sujuro.notification.support.NotificationMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 마감 임박 알림 (FR-031, NFR-017): 대상별 알림 생성, 중복 방지, 메일 재시도 */
class DeadlineNoticeServiceTest {

    private DeadlineMapper deadlineMapper;
    private NotificationMapper notificationMapper;
    private NotificationService notificationService;
    private NotificationMailSender mailSender;
    private DeadlineNoticeService service;

    @BeforeEach
    void setUp() {
        deadlineMapper = mock(DeadlineMapper.class);
        notificationMapper = mock(NotificationMapper.class);
        notificationService = mock(NotificationService.class);
        mailSender = mock(NotificationMailSender.class);
        service = new DeadlineNoticeService(deadlineMapper, notificationMapper, notificationService, mailSender);
    }

    private DeadlineTarget target(long pbancSn, String name) {
        DeadlineTarget t = new DeadlineTarget();
        t.setPbancSn(pbancSn);
        t.setPbancNm(name);
        t.setDdlnText("2026-10-29 17:00");
        return t;
    }

    @Test
    void 제안팀원_전원에게_알림을_만든다() {
        when(deadlineMapper.findTargets(1)).thenReturn(Collections.singletonList(target(10L, "A사업")));
        when(notificationMapper.findProposalMemberIds(10L)).thenReturn(Arrays.asList("E1", "E2"));

        int created = service.createNotices();

        assertEquals(2, created);
        verify(notificationService).send(eq("E1"), eq(NotificationType.DEADLINE_NEAR), eq(10L), contains("[D-1]"));
        verify(notificationService).send(eq("E2"), eq(NotificationType.DEADLINE_NEAR), eq(10L), contains("[D-1]"));
    }

    @Test
    void 이미_보낸_사람에게는_다시_만들지_않는다() {
        when(deadlineMapper.findTargets(1)).thenReturn(Collections.singletonList(target(10L, "A사업")));
        when(notificationMapper.findProposalMemberIds(10L)).thenReturn(Arrays.asList("E1", "E2"));
        when(deadlineMapper.countSent("E1", 10L, NotificationType.DEADLINE_NEAR, "[D-1]")).thenReturn(1);

        int created = service.createNotices();

        assertEquals(1, created);
        verify(notificationService, never()).send(eq("E1"), anyString(), eq(10L), anyString());
        verify(notificationService).send(eq("E2"), eq(NotificationType.DEADLINE_NEAR), eq(10L), contains("[D-1]"));
    }

    @Test
    void 대상이_없으면_아무것도_보내지_않는다() {
        when(deadlineMapper.findTargets(1)).thenReturn(Collections.<DeadlineTarget>emptyList());

        assertEquals(0, service.createNotices());
    }

    private PendingMail mail(long notiSn, String to) {
        PendingMail m = new PendingMail();
        m.setNotiSn(notiSn);
        m.setEmlAddr(to);
        m.setContent("[D-1] 내용");
        return m;
    }

    @Test
    void 메일을_보낸_알림만_보냄으로_표시한다() {
        when(deadlineMapper.findPendingMails(NotificationType.DEADLINE_NEAR, "[D-1]"))
                .thenReturn(Arrays.asList(mail(1L, "ok@example.com"), mail(2L, "fail@example.com")));
        when(mailSender.send(eq("ok@example.com"), anyString(), anyString())).thenReturn(true);
        when(mailSender.send(eq("fail@example.com"), anyString(), anyString())).thenReturn(false);

        int sent = service.sendPendingMails();

        assertEquals(1, sent);
        verify(deadlineMapper).markMailSent(1L);
        verify(deadlineMapper, never()).markMailSent(2L);
    }

    @Test
    void 내일_일정은_그_사업건_제안팀원_전원에게_알림을_만든다() {
        ScheduleTarget t = new ScheduleTarget();
        t.setSchdlSn(5L);
        t.setPbancSn(70L);
        t.setPbancNm("A사업");
        t.setTitle("내부 검토");
        t.setWhenText("2026-11-05 14:00");
        when(deadlineMapper.findScheduleTargets(1)).thenReturn(Collections.singletonList(t));
        when(notificationMapper.findProposalMemberIds(70L)).thenReturn(Arrays.asList("E1", "E2"));

        int created = service.createScheduleNotices();

        assertEquals(2, created);
        verify(notificationService).send(eq("E1"), eq(NotificationType.DEADLINE_NEAR), eq(70L), contains("[일정 D-1]"));
        verify(notificationService).send(eq("E2"), eq(NotificationType.DEADLINE_NEAR), eq(70L), contains("내부 검토"));
    }

    @Test
    void 일정_알림도_이미_보낸_사람에게는_다시_만들지_않는다() {
        ScheduleTarget t = new ScheduleTarget();
        t.setSchdlSn(5L);
        t.setPbancSn(70L);
        t.setPbancNm("A사업");
        t.setTitle("내부 검토");
        t.setWhenText("2026-11-05");
        when(deadlineMapper.findScheduleTargets(1)).thenReturn(Collections.singletonList(t));
        when(notificationMapper.findProposalMemberIds(70L)).thenReturn(Arrays.asList("E1", "E2"));
        String content = "[일정 D-1] 내일 '내부 검토' 일정이 있습니다. (2026-11-05) · A사업";
        when(deadlineMapper.countSentByContent("E1", NotificationType.DEADLINE_NEAR, content)).thenReturn(1);

        int created = service.createScheduleNotices();

        assertEquals(1, created);
        verify(notificationService, never()).send(eq("E1"), anyString(), eq(70L), anyString());
    }

    @Test
    void 단계_전환_알림도_메일로_보낸다() {
        when(deadlineMapper.findPendingMails(NotificationType.BID_STAGE_CHANGED, ""))
                .thenReturn(Collections.singletonList(mail(9L, "sales@example.com")));
        when(mailSender.send(eq("sales@example.com"), anyString(), anyString())).thenReturn(true);

        int sent = service.sendPendingMails();

        assertEquals(1, sent);
        verify(deadlineMapper).markMailSent(9L);
    }
}
