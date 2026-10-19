package com.ojj.sujuro.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 로그인한 사용자 정보. 화면과 서비스에서는 이 객체로 "지금 누가 로그인했는지" 확인한다.
 *
 *   컨트롤러 : public String list(@AuthenticationPrincipal LoginUser user)
 *   화면     : sec:authentication="principal.name", ${#authentication.principal.team}
 *
 * DB 조회로 바뀌어도 이 클래스는 그대로 쓴다. (직원 테이블 → LoginUser 변환만 바뀐다.)
 */
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    /** 외부 인력 계정에 붙는 권한. 메뉴·URL 제한에 쓴다. (FR-008-1, NFR-004-1) */
    public static final String EXTERNAL_AUTHORITY = "EXTERNAL";

    public static final String STATUS_INITIAL = "초기 비밀번호";
    public static final String STATUS_ACTIVE = "활성";
    public static final String STATUS_INACTIVE = "비활성";
    public static final String STATUS_LOCKED = "잠금";

    private final String empNo;
    private final String name;
    private final String rank;
    private final String team;
    private final Role role;
    private final boolean external;
    private final String status;
    private final String password;

    public LoginUser(String empNo, String name, String rank, String team,
                     Role role, boolean external, String status, String password) {
        this.empNo = empNo;
        this.name = name;
        this.rank = rank;
        this.team = team;
        this.role = role;
        this.external = external;
        this.status = status;
        this.password = password;
    }

    public String getEmpNo() {
        return empNo;
    }

    public String getName() {
        return name;
    }

    public String getRank() {
        return rank;
    }

    /** 소속 팀. 외부 인력은 소속 회사명이 들어간다. */
    public String getTeam() {
        return team;
    }

    public Role getRole() {
        return role;
    }

    public boolean isExternal() {
        return external;
    }

    public String getStatus() {
        return status;
    }

    /** 화면에 보여 줄 권한 이름. 외부 인력은 "외부 인력"으로 보여 준다. */
    public String getRoleLabel() {
        return external ? "외부 인력" : role.getLabel();
    }

    /** 계정 상태가 '초기 비밀번호'이면 비밀번호를 바꾸기 전까지 다른 메뉴에 들어갈 수 없다. (FR-002) */
    public boolean isInitialPassword() {
        return STATUS_INITIAL.equals(status);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.getAuthority()));
        if (external) {
            authorities.add(new SimpleGrantedAuthority(EXTERNAL_AUTHORITY));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /** 로그인 ID는 사번이다. */
    @Override
    public String getUsername() {
        return empNo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !STATUS_LOCKED.equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !STATUS_INACTIVE.equals(status);
    }
}
