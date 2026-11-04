package com.ojj.sujuro.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ExpiredSessionStrategyTest {

    private String redirectOf(String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new ExpiredSessionStrategy().onInvalidSessionDetected(req, res);
        return res.getRedirectedUrl();
    }

    @Test
    void 처음_들어오는_주소의_오래된_쿠키는_안내_없이_로그인_화면으로() throws Exception {
        assertEquals("/login", redirectOf("/"));
        assertEquals("/login", redirectOf("/login"));
    }

    @Test
    void 일하던_화면에서_세션이_끝나면_만료_안내를_붙인다() throws Exception {
        assertEquals("/login?expired", redirectOf("/dashboard"));
        assertEquals("/login?expired", redirectOf("/notices/12"));
    }
}
