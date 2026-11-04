package com.ojj.sujuro.common.config;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.web.session.InvalidSessionStrategy;
import org.springframework.security.web.session.SimpleRedirectInvalidSessionStrategy;

/**
 * 서버가 모르는 세션 쿠키(JSESSIONID)가 왔을 때 로그인 화면으로 보낸다.
 *
 * 일하던 화면에서 세션이 끝났으면 "오래 사용하지 않아 로그아웃되었습니다" 안내(/login?expired)를 붙인다.
 * 하지만 처음 들어오는 주소(/ 또는 /login)에서 만난 오래된 쿠키는 서버를 다시 띄웠거나 배포한 뒤 남은 것일 뿐이라
 * 안내 없이 로그인 화면만 보여 준다. 두 경우 모두 오래된 쿠키는 지워서 다음 요청부터는 정상으로 처리한다.
 */
public class ExpiredSessionStrategy implements InvalidSessionStrategy {

    private final InvalidSessionStrategy withNotice = new SimpleRedirectInvalidSessionStrategy("/login?expired");
    private final InvalidSessionStrategy plain = new SimpleRedirectInvalidSessionStrategy("/login");

    @Override
    public void onInvalidSessionDetected(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (isEntryPath(request)) {
            plain.onInvalidSessionDetected(request, response);
        } else {
            withNotice.onInvalidSessionDetected(request, response);
        }
    }

    /** 처음 들어오는 주소인가: 루트 또는 로그인 화면 */
    static boolean isEntryPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return path.isEmpty() || "/".equals(path) || "/login".equals(path);
    }
}
