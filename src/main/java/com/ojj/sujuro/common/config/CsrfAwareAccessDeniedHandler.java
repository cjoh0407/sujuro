package com.ojj.sujuro.common.config;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

/**
 * 403 처리 (11/10, 이세종). CSRF 토큰 오류만 따로 처리하고, 나머지(진짜 권한 없음)는 지금처럼 "접근 권한이 없습니다" 화면.
 *
 * 화면에 들어 있던 CSRF 토큰이 무효가 되면(화면을 30분 넘게 띄워 둠, 서버 재배포·재시작, 다른 탭에서 로그인,
 * 로그인 뒤 뒤로 가기로 돌아온 로그인 화면) 권한과 상관없이 POST 가 403 으로 막혀 "접근 권한이 없습니다"가 뜨던 문제를 고친다.
 *   - 이미 로그인된 세션이 있는데 로그인 화면에서 다시 로그인한 경우 → 대시보드로 바로 보낸다
 *   - 로그인된 세션이 없는 경우(세션 만료·재배포) → /login?expired ("오래 사용하지 않아 로그아웃되었습니다" 안내)
 *   - 로그인된 상태에서 다른 화면의 토큰만 어긋난 경우 → 기본 403 화면 (드문 경우라 그대로 둔다)
 */
public class CsrfAwareAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException, ServletException {
        if (e instanceof CsrfException && !response.isCommitted()) {
            String ctx = request.getContextPath();
            if (!loggedIn()) {
                response.sendRedirect(ctx + "/login?expired");
                return;
            }
            if (isLoginPost(request)) {
                response.sendRedirect(ctx + "/dashboard");
                return;
            }
        }
        fallback.handle(request, response, e);
    }

    /** 로그인된 사용자인가 (익명·없음은 아니다) */
    static boolean loggedIn() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
    }

    /** 로그인 요청(POST /login)인가 */
    static boolean isLoginPost(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        return "POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(path);
    }
}
