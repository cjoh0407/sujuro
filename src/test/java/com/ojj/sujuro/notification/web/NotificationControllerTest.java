package com.ojj.sujuro.notification.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.notification.dto.NotificationDto;
import com.ojj.sujuro.notification.mapper.NotificationInboxMapper;
import com.ojj.sujuro.notification.support.NotificationLinkResolver;

/** 알림 팝업: 20건씩 불러오기, 내 알림만 읽음 처리, 갈 화면 주소 */
class NotificationControllerTest {

    private NotificationInboxMapper mapper;
    private NotificationLinkResolver links;
    private NotificationController controller;
    private final LoginUser me = new LoginUser("M1", "M1", "사원", "공공사업1Div", Role.MEMBER, LoginUser.STATUS_ACTIVE, "x");

    @BeforeEach
    void setUp() {
        mapper = mock(NotificationInboxMapper.class);
        links = mock(NotificationLinkResolver.class);
        controller = new NotificationController(mapper, links);
    }

    private static List<NotificationDto> rows(int n) {
        List<NotificationDto> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            NotificationDto d = new NotificationDto();
            d.setNotiSn((long) i);
            d.setContent("알림 " + i);
            d.setPrslYn(i % 2 == 0 ? "Y" : "N");
            list.add(d);
        }
        return list;
    }

    @Test
    @SuppressWarnings("unchecked")
    void 스무_건씩_불러오고_더_있는지_알려_준다() {
        when(mapper.findPage("M1", 0, 21)).thenReturn(rows(21));
        when(mapper.findPage("M1", 20, 21)).thenReturn(rows(3));

        Map<String, Object> first = controller.list(me, 0, 20);
        assertEquals(20, ((List<?>) first.get("items")).size());
        assertEquals(true, first.get("hasMore"));

        Map<String, Object> next = controller.list(me, 20, 20);
        assertEquals(3, ((List<?>) next.get("items")).size());
        assertEquals(false, next.get("hasMore"));
        assertEquals(true, ((List<Map<String, Object>>) next.get("items")).get(0).get("read"));
    }

    @Test
    void 한_번에_50건보다_많이_달라고_해도_50건까지만() {
        when(mapper.findPage("M1", 0, 51)).thenReturn(rows(0));
        controller.list(me, -5, 1000);
        verify(mapper).findPage("M1", 0, 51);
    }

    @Test
    void 남의_알림은_읽음_처리하지_않는다() {
        when(mapper.findMineOne("M1", 99L)).thenReturn(null);

        Map<String, Object> body = controller.openJson(me, 99L, new MockHttpServletRequest());

        assertNull(body.get("link"));
        verify(mapper, never()).markRead(anyString(), anyLong());
    }

    @Test
    void 읽음_처리하고_시작_경로를_붙인_주소를_돌려준다() {
        NotificationDto n = new NotificationDto();
        n.setNotiSn(5L);
        when(mapper.findMineOne("M1", 5L)).thenReturn(n);
        when(links.linkOf(any(), any())).thenReturn("/proposal?bid=10");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContextPath("/app");

        Map<String, Object> body = controller.openJson(me, 5L, req);

        verify(mapper).markRead("M1", 5L);
        assertEquals("/app/proposal?bid=10", body.get("link"));
        // 스크립트 없이 폼으로 보낸 경우: 갈 곳이 없으면 대시보드로
        when(links.linkOf(any(), any())).thenReturn(null);
        assertEquals("redirect:/dashboard", controller.open(me, 5L));
    }
}
