package com.ojj.sujuro.notification.web;

import com.ojj.sujuro.notification.mapper.NotificationInboxMapper;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ojj.sujuro.auth.dto.LoginUser;

/** 모든 화면의 종 아이콘에 안 읽은 알림 건수를 보여 주기 위해 unreadCount 를 모델에 넣는다. (FR-032) */
@ControllerAdvice
public class NotificationBadgeAdvice {

    private final NotificationInboxMapper mapper;

    public NotificationBadgeAdvice(NotificationInboxMapper mapper) {
        this.mapper = mapper;
    }

    @ModelAttribute("unreadCount")
    public int unreadCount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) {
            return 0;
        }
        try {
            return mapper.countUnread(((LoginUser) auth.getPrincipal()).getEmpNo());
        } catch (RuntimeException e) {
            return 0; // 배지 때문에 화면 전체가 깨지지 않게 한다.
        }
    }
}
