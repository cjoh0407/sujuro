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
 * DB 조회 결과(AuthUser)를 CustomUserDetailsService가 이 객체로 바꿔 준다.
 */
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    /** 계정상태코드 (공통코드 ACSTS) */
    public static final String STATUS_INITIAL = "AS020";   // 초기 비밀번호
    public static final String STATUS_ACTIVE = "AS030";    // 활성
    public static final String STATUS_LOCKED = "AS040";    // 잠금
    public static final String STATUS_INACTIVE = "AS050";  // 비활성

    private final String empNo;
    private final String name;
    private final String rank;
    private final String team;
    private final Role role;
    private final String status; // 계정상태코드
    private final String password;

    public LoginUser(String empNo, String name, String rank, String team,
                     Role role, String status, String password) {
        this.empNo = empNo;
        this.name = name;
        this.rank = rank;
        this.team = team;
        this.role = role;
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

    /** 소속 팀 */
    public String getTeam() {
        return team;
    }

    public Role getRole() {
        return role;
    }

    /** 외부 인력은 쓰지 않기로 해서 항상 false다. 이 메서드를 쓰는 코드(ProposalController)를 정리하면 함께 지운다. */
    @Deprecated
    public boolean isExternal() {
        return false;
    }

    public String getStatus() {
        return status;
    }

    /** 화면에 보여 줄 권한 이름 */
    public String getRoleLabel() {
        return role.getLabel();
    }

    /** 계정 상태가 '초기 비밀번호'이면 비밀번호를 바꾸기 전까지 다른 메뉴에 들어갈 수 없다. (FR-002) */
    public boolean isInitialPassword() {
        return STATUS_INITIAL.equals(trim(status));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.getAuthority()));
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
        return !STATUS_LOCKED.equals(trim(status));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        String s = trim(status);
        return !STATUS_INACTIVE.equals(s);
    }

    /** CHAR 컬럼이라 뒤에 공백이 붙어 올 수 있어 잘라서 비교한다. */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
