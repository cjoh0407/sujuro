package com.ojj.sujuro.auth.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.AuthUser;
import com.ojj.sujuro.auth.dto.LoginUser;

/** 로그인 뒤 계정이 바뀌면 로그아웃시키는 기준 (FR-005~008) */
class AccountRecheckInterceptorTest {

    private LoginUser session(Role role, String team, String status) {
        return new LoginUser("2024-0001", "홍길동", "사원", team, role, status, "x");
    }

    private AuthUser now(String authrtCd, String team, String status) throws Exception {
        AuthUser u = new AuthUser();
        set(u, "userId", "2024-0001");
        set(u, "authrtCd", authrtCd);
        set(u, "teamNm", team);
        set(u, "acntSttsCd", status);
        return u;
    }

    private static void set(Object o, String name, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }

    @Test
    void 바뀐_게_없으면_그대로_쓴다() throws Exception {
        assertNull(AccountRecheckInterceptor.reasonToLogout(session(Role.MEMBER, "공공사업1Div", LoginUser.STATUS_ACTIVE),
                now("AU040", "공공사업1Div", LoginUser.STATUS_ACTIVE)));
    }

    @Test
    void 비활성이나_잠금이면_로그아웃() throws Exception {
        LoginUser s = session(Role.MEMBER, "공공사업1Div", LoginUser.STATUS_ACTIVE);
        assertEquals("disabled", AccountRecheckInterceptor.reasonToLogout(s, now("AU040", "공공사업1Div", LoginUser.STATUS_INACTIVE)));
        assertEquals("locked", AccountRecheckInterceptor.reasonToLogout(s, now("AU040", "공공사업1Div", LoginUser.STATUS_LOCKED)));
        assertEquals("disabled", AccountRecheckInterceptor.reasonToLogout(s, null));
    }

    @Test
    void 권한_소속이_바뀌거나_비밀번호가_초기화되면_로그아웃() throws Exception {
        LoginUser s = session(Role.MEMBER, "공공사업1Div", LoginUser.STATUS_ACTIVE);
        assertEquals("changed", AccountRecheckInterceptor.reasonToLogout(s, now("AU030", "공공사업1Div", LoginUser.STATUS_ACTIVE)));
        assertEquals("changed", AccountRecheckInterceptor.reasonToLogout(s, now("AU040", "전략사업1Div", LoginUser.STATUS_ACTIVE)));
        assertEquals("changed", AccountRecheckInterceptor.reasonToLogout(s, now("AU040", "공공사업1Div", LoginUser.STATUS_INITIAL)));
    }

    @Test
    void 처음부터_초기_비밀번호로_로그인한_사람은_그대로_둔다() throws Exception {
        LoginUser s = session(Role.MEMBER, "공공사업1Div", LoginUser.STATUS_INITIAL);
        assertNull(AccountRecheckInterceptor.reasonToLogout(s, now("AU040", "공공사업1Div", LoginUser.STATUS_INITIAL)));
    }
}
