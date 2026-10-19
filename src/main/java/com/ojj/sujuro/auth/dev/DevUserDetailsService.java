package com.ojj.sujuro.auth.dev;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;

/**
 * ★ 임시 파일 — DB 로그인(직원 테이블 조회)을 만들면 이 파일을 통째로 삭제한다. ★
 *
 * 직원 테이블이 아직 없을 때 공통 레이아웃·권한별 메뉴를 확인하려고 만든 개발용 계정이다.
 * 진짜 UserDetailsService가 생기면 빈이 두 개가 되어 로그인이 깨지므로 반드시 지운다.
 * (local 프로필에서만 동작하며, 배포용 프로필에서는 만들어지지 않는다.)
 *
 *   사번        이름     소속          권한
 *   2023-0067   박서연   영업부        SALES
 *   2024-0031   김가희   개발1팀(팀장)  LEADER
 *   2024-0102   오창준   개발1팀       MEMBER
 *   2022-0009   최민정   경영지원실     MGMT
 *   EXT-001     서진우   ㈜가온시스템   MEMBER + EXTERNAL
 *   비밀번호는 모두 Demo!2026a
 */
@Service
@Profile("local")
public class DevUserDetailsService implements UserDetailsService {

    private static final String DEV_PASSWORD = "Demo!2026a";

    private final Map<String, LoginUser> users = new LinkedHashMap<>();

    public DevUserDetailsService(PasswordEncoder passwordEncoder) {
        String encoded = passwordEncoder.encode(DEV_PASSWORD);
        add(encoded, "2023-0067", "박서연", "대리", "영업부", false);
        add(encoded, "2024-0031", "김가희", "팀장", "개발1팀", false);
        add(encoded, "2024-0102", "오창준", "대리", "개발1팀", false);
        add(encoded, "2022-0009", "최민정", "과장", "경영지원실", false);
        add(encoded, "EXT-001", "서진우", "외부", "㈜가온시스템", true);
    }

    private void add(String encoded, String empNo, String name, String rank, String team, boolean external) {
        users.put(empNo, new LoginUser(empNo, name, rank, team, Role.resolve(team, rank),
                external, LoginUser.STATUS_ACTIVE, encoded));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LoginUser user = users.get(username);
        if (user == null) {
            throw new UsernameNotFoundException("등록되지 않은 사번입니다.");
        }
        return user;
    }
}
