package com.ojj.sujuro.auth;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패 횟수 관리 (NFR-003). 비밀번호를 5번 연속 틀리면 계정을 잠그고, 로그인에 성공하면 횟수를 0으로 되돌린다.
 * 잠금 해제는 경영지원실이 사용자 관리에서 한다. (FR-008)
 */
@Service
public class LoginAttemptService {

    /** 이 횟수만큼 틀리면 잠금 */
    public static final int MAX_FAIL = 5;

    private final AuthMapper authMapper;

    public LoginAttemptService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String userId = event.getAuthentication().getName();
        authMapper.increaseFailCount(userId);
        authMapper.lockIfExceeded(userId, MAX_FAIL);
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        authMapper.resetFailCount(event.getAuthentication().getName());
    }
}
