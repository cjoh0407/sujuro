package com.ojj.sujuro.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 계정 상태가 '초기 비밀번호'인 사용자는 비밀번호를 바꾸기 전까지 다른 화면에 들어갈 수 없다. (FR-002)
 * 주소를 직접 입력해도 비밀번호 변경 화면으로 돌려보낸다.
 * 적용 범위(제외할 주소)는 common/config/WebMvcConfig 에서 정한다.
 */
@Component
public class InitialPasswordInterceptor implements HandlerInterceptor {

    public static final String CHANGE_URL = "/password/initial";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            LoginUser user = (LoginUser) authentication.getPrincipal();
            if (user.isInitialPassword()) {
                response.sendRedirect(request.getContextPath() + CHANGE_URL);
                return false;
            }
        }
        return true;
    }
}
