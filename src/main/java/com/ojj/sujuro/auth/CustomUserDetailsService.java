package com.ojj.sujuro.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 로그인할 때 Spring Security가 부르는 서비스. 사번으로 DB(TB_USER)를 조회해 {@link LoginUser}를 만든다. (FR-001)
 * 비밀번호 비교는 Spring Security가 BCrypt로 하므로 여기서는 하지 않는다.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AuthMapper authMapper;

    public CustomUserDetailsService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUser row = authMapper.findByUserId(username);
        if (row == null) {
            throw new UsernameNotFoundException("등록되지 않은 사번입니다.");
        }
        Role role = Role.fromAuthorityCode(row.getAuthrtCd());
        boolean external = Role.isExternalCode(row.getAuthrtCd()) || "Y".equals(row.getOtsdMnpwYn());
        return new LoginUser(row.getUserId(), row.getUserNm(), row.getJbgdNm(), row.getTeamNm(),
                role, external, row.getAcntSttsCd(), row.getEnpswd());
    }
}
