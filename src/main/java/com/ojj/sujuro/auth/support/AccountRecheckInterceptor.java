package com.ojj.sujuro.auth.support;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.AuthUser;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.mapper.AuthMapper;
import java.util.Objects;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인한 뒤에 경영지원실이 계정을 바꾸면 바로 반영한다. (FR-005 ~ FR-008)
 *
 * 로그인할 때 읽은 권한·소속·계정 상태는 세션에 그대로 남는다. 그래서 요청마다 DB 의 값과 비교해서
 * 비활성·잠금이 됐거나, 권한·소속이 바뀌었거나, 비밀번호가 초기화됐으면 로그아웃시키고 로그인 화면으로 보낸다.
 * (퇴사 처리한 직원이 열려 있던 세션으로 계속 접속하거나, 팀장에서 내려간 직원이 팀장 기능을 계속 쓰는 일을 막는다)
 * 적용 범위(제외할 주소)는 common/config/WebMvcConfig 에서 정한다.
 */
@Component
@RequiredArgsConstructor
public class AccountRecheckInterceptor implements HandlerInterceptor {

    private final AuthMapper authMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return true;
        }
        LoginUser user = (LoginUser) authentication.getPrincipal();
        String reason = reasonToLogout(user, authMapper.findByUserId(user.getEmpNo()));
        if (reason == null) {
            return true;
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        // 로그아웃 설정(SecurityConfig)과 같이 세션 쿠키도 지운다. 남겨 두면 다음 요청이 '세션 만료'로 처리돼 안내가 바뀐다
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        response.sendRedirect(request.getContextPath() + "/login?" + reason);
        return false;
    }

    /** 로그아웃시킬 이유(로그인 화면의 안내 키). 그대로 써도 되면 null */
    static String reasonToLogout(LoginUser user, AuthUser now) {
        if (now == null || LoginUser.STATUS_INACTIVE.equals(trim(now.getAcntSttsCd()))) {
            return "disabled";
        }
        if (LoginUser.STATUS_LOCKED.equals(trim(now.getAcntSttsCd()))) {
            return "locked";
        }
        boolean roleChanged = Role.fromAuthorityCode(now.getAuthrtCd()) != user.getRole();
        boolean teamChanged = !Objects.equals(trim(now.getTeamNm()), trim(user.getTeam()));
        // 경영지원실이 비밀번호를 초기화하면 상태가 '초기 비밀번호'로 돌아간다. 새 비밀번호로 다시 로그인하게 한다
        boolean passwordReset = LoginUser.STATUS_INITIAL.equals(trim(now.getAcntSttsCd())) && !user.isInitialPassword();
        if (roleChanged || teamChanged || passwordReset) {
            return "changed";
        }
        return null;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
