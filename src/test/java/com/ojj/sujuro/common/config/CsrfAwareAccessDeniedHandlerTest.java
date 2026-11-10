package com.ojj.sujuro.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/** CSRF 토큰이 무효인 요청은 "접근 권한이 없습니다" 대신 로그인·대시보드로 보낸다 (11/10) */
class CsrfAwareAccessDeniedHandlerTest {

    private final CsrfAwareAccessDeniedHandler handler = new CsrfAwareAccessDeniedHandler();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        return req;
    }

    private static void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "2024-0301", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_LEADER"))));
    }

    @Test
    void 세션이_없는데_토큰이_무효면_로그인_화면으로_만료_안내() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.handle(post("/login"), res, new MissingCsrfTokenException("x"));
        assertEquals("/login?expired", res.getRedirectedUrl());
    }

    @Test
    void 이미_로그인된_채로_로그인_화면에서_다시_로그인하면_대시보드로() throws Exception {
        login();
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.handle(post("/login"), res, new MissingCsrfTokenException("x"));
        assertEquals("/dashboard", res.getRedirectedUrl());
    }

    @Test
    void 진짜_권한_없음은_그대로_403() throws Exception {
        login();
        MockHttpServletResponse res = new MockHttpServletResponse();
        handler.handle(post("/users"), res, new AccessDeniedException("no"));
        assertEquals(403, res.getStatus());
    }
}
