package com.ojj.sujuro.user.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/** 초기 비밀번호: 메일을 보냈으면 화면에 비밀번호를 보여 주지 않고, 못 보냈을 때만 보여 준다 */
class UserControllerTest {

    @Test
    void 메일을_보냈으면_비밀번호를_화면에_보여_주지_않는다() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        UserController.notifyPassword(redirect, true, "홍길동 님을 등록했습니다.", "2024-0001", "hong@company.com", "Tmp!abcd1");

        Map<String, ?> flash = redirect.getFlashAttributes();
        assertNull(flash.get("issued"));
        String message = (String) flash.get("message");
        assertTrue(message.contains("h***@company.com"));
        assertFalse(message.contains("Tmp!abcd1"));
    }

    @Test
    void 메일을_못_보냈으면_비밀번호를_한_번_보여_준다() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        UserController.notifyPassword(redirect, false, "홍길동 님의 비밀번호를 초기화했습니다.", "2024-0001", "hong@company.com", "Tmp!abcd1");

        @SuppressWarnings("unchecked")
        Map<String, String> issued = (Map<String, String>) redirect.getFlashAttributes().get("issued");
        assertEquals("Tmp!abcd1", issued.get("password"));
        assertEquals("2024-0001", issued.get("empNo"));
        assertNull(redirect.getFlashAttributes().get("message"));
    }
}
