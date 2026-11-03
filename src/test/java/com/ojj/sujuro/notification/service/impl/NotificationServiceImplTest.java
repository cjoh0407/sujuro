package com.ojj.sujuro.notification.service.impl;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.mapper.NotificationMapper;
import com.ojj.sujuro.notification.support.NotificationPublisher;

/** 행동한 본인에게는 알림을 보내지 않는다. 나라장터에서 일어난 일과 스케줄러 알림은 예외 */
class NotificationServiceImplTest {

    private NotificationMapper mapper;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(NotificationMapper.class);
        service = new NotificationServiceImpl(mapper, mock(NotificationPublisher.class));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String empNo) {
        LoginUser u = new LoginUser(empNo, empNo, "팀장", "공공사업1Div", Role.LEADER, LoginUser.STATUS_ACTIVE, "x");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test
    void 일정을_바꾼_팀장_본인은_빠지고_다른_제안팀원은_받는다() {
        loginAs("L1");

        service.send(Arrays.asList("L1", "M1"), NotificationType.SCHEDULE_CHANGED, 10L, "일정이 변경되었습니다");

        verify(mapper, never()).insert(eq("L1"), anyString(), eq(10L), anyString());
        verify(mapper).insert("M1", NotificationType.SCHEDULE_CHANGED, 10L, "일정이 변경되었습니다");
    }

    @Test
    void 나라장터에서_일어난_일은_지금_수집을_누른_본인도_받는다() {
        loginAs("S1");

        service.send(Arrays.asList("S1", "S2"), NotificationType.BID_STAGE_CHANGED, 10L, "단계가 바뀌었습니다");

        verify(mapper).insert("S1", NotificationType.BID_STAGE_CHANGED, 10L, "단계가 바뀌었습니다");
        verify(mapper).insert("S2", NotificationType.BID_STAGE_CHANGED, 10L, "단계가 바뀌었습니다");
    }

    @Test
    void 로그인_사용자가_없는_스케줄러_알림은_모두에게_간다() {
        service.send(Arrays.asList("L1", "M1"), NotificationType.SCHEDULE_CHANGED, 10L, "x");

        verify(mapper).insert("L1", NotificationType.SCHEDULE_CHANGED, 10L, "x");
        verify(mapper).insert("M1", NotificationType.SCHEDULE_CHANGED, 10L, "x");
    }
}
