package com.ojj.sujuro.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정 (NFR-001, 002, 003, 006).
 *
 * - 비밀번호는 BCrypt로 단방향 암호화한다. (NFR-002)
 * - CSRF 보호는 기본값(켜짐)을 유지한다. 화면의 form은 th:action을 쓰면 토큰이 자동으로 들어간다. (NFR-006)
 * - 메뉴를 숨기는 것만으로는 권한을 막았다고 보지 않는다. 아래 URL 규칙으로 서버에서 다시 검사한다. (NFR-001)
 *   사업건 단위 접근(제안팀 소속 여부)은 URL만으로 알 수 없으므로 별도 검사(AccessChecker)로 처리한다.
 * - 세션 30분 만료는 application.properties의 server.servlet.session.timeout 으로 설정한다. (NFR-003)
 *
 * 권한 값은 {@link com.ojj.sujuro.auth.Role}, 외부 인력은 EXTERNAL 권한으로 구분한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests(auth -> auth
                // 로그인 없이 볼 수 있는 곳
                .antMatchers("/css/**", "/js/**", "/lib/**", "/images/**", "/favicon.ico").permitAll()
                .antMatchers("/login", "/error", "/error/**").permitAll()

                // 메뉴별 접근 규칙 (HTML 목업 v3.2의 메뉴 노출 기준과 같다)
                .antMatchers("/notices/**").access("hasAnyRole('SALES','LEADER','MGMT') and !hasAuthority('EXTERNAL')")
                .antMatchers("/proposal/**", "/files/**", "/qa/**").hasAnyRole("LEADER", "MEMBER")
                .antMatchers("/docs/**").hasAnyRole("MGMT", "LEADER", "MEMBER")
                .antMatchers("/results/**").hasRole("MGMT")
                .antMatchers("/schedule/**").access("isAuthenticated() and !hasAuthority('EXTERNAL')")
                .antMatchers("/stats/**").access("hasAnyRole('MGMT','LEADER') and !hasAuthority('EXTERNAL')")
                .antMatchers("/users/**").access("hasRole('MGMT') and !hasAuthority('EXTERNAL')")

                // 그 밖의 화면(대시보드, 알림, 내 정보 등)은 로그인한 사용자 모두
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("empNo")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session
                .invalidSessionUrl("/login?expired"));

        return http.build();
    }
}
