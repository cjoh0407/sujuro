package com.ojj.sujuro.notification.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.notification.dto.NotificationDto;

/** 알림을 눌렀을 때 갈 화면: 종류·역할별 이동, 일정은 그 달, 권한이 없어지면 null */
class NotificationLinkResolverTest {

    private AccessChecker access;
    private NotificationLinkResolver resolver;

    private final LoginUser sales = user("S1", Role.SALES);
    private final LoginUser mgmt = user("G1", Role.MGMT);
    private final LoginUser leader = user("L1", Role.LEADER);
    private final LoginUser member = user("M1", Role.MEMBER);

    @BeforeEach
    void setUp() {
        access = mock(AccessChecker.class);
        resolver = new NotificationLinkResolver(access);
        // 10번 사업: L1·M1 이 제안팀원, L1 이 주관팀 팀장
        when(access.isProposalMember(eq(10L), any(LoginUser.class)))
                .thenAnswer(i -> ((LoginUser) i.getArgument(1)).getEmpNo().matches("L1|M1"));
        when(access.isLeadTeamLeader(eq(10L), any(LoginUser.class)))
                .thenAnswer(i -> "L1".equals(((LoginUser) i.getArgument(1)).getEmpNo()));
    }

    private static LoginUser user(String id, Role role) {
        return new LoginUser(id, id, "사원", "공공사업1Div", role, LoginUser.STATUS_ACTIVE, "x");
    }

    private static NotificationDto noti(String type, Long pbancSn, String content) {
        NotificationDto n = new NotificationDto();
        n.setTypeCd(type);
        n.setPbancSn(pbancSn);
        n.setContent(content);
        return n;
    }

    @Test
    void 공고_흐름_알림은_공고_상세로_간다() {
        for (String type : new String[] {"NT020", "NT030", "NT040", "NT060", "NT150"}) {
            assertEquals("/notices/10", resolver.linkOf(noti(type, 10L, "x"), sales), type);
            assertEquals("/notices/10", resolver.linkOf(noti(type, 10L, "x"), leader), type);
        }
        // 팀장이었다가 팀원이 되면 공고 화면을 볼 수 없다
        assertNull(resolver.linkOf(noti("NT030", 10L, "x"), member));
    }

    @Test
    void 마감_임박은_제안_화면으로_간다_제안팀에서_빠지면_갈_곳이_없다() {
        assertEquals("/proposal?bid=10", resolver.linkOf(noti("NT050", 10L, "[D-1] 'A사업' 입찰 마감이 1일 남았습니다."), member));
        assertNull(resolver.linkOf(noti("NT050", 10L, "[D-1] 'A사업' 입찰 마감이 1일 남았습니다."), user("M9", Role.MEMBER)));
    }

    @Test
    void 일정_알림은_그_일정이_있는_달로_간다() {
        assertEquals("/schedule?ym=2026-12&pbanc=10",
                resolver.linkOf(noti("NT160", 10L, "[A사업] '착수 회의' 일정이 변경되었습니다. (2026-12-03 14:00)"), member));
        assertEquals("/schedule?ym=2026-11&pbanc=10",
                resolver.linkOf(noti("NT050", 10L, "[일정 D-1] 내일 '검토' 일정이 있습니다. (2026-11-12 10:00) · A사업"), leader));
        // 일정 이름에 날짜가 있어도 마지막(일정 날짜)을 쓴다
        assertEquals("2026-11", NotificationLinkResolver.monthOf("[A] '(2025-01-01) 회고' 일정이 등록되었습니다. (2026-11-20 00:00)"));
        // 날짜를 못 읽으면 이번 달(ym 없음)
        assertEquals("/schedule?pbanc=10", resolver.linkOf(noti("NT160", 10L, "일정이 바뀌었습니다"), member));
        // 제안팀에서 빠지면 캘린더에 그 일정이 안 보이므로 갈 곳이 없다
        assertNull(resolver.linkOf(noti("NT160", 10L, "(2026-12-03 14:00)"), user("M9", Role.MEMBER)));
    }

    @Test
    void 제출_완료는_제안팀원은_제출서류_영업부는_공고_상세() {
        assertEquals("/docs?bid=10", resolver.linkOf(noti("NT070", 10L, "x"), member));
        assertEquals("/notices/10", resolver.linkOf(noti("NT070", 10L, "x"), sales));
        assertNull(resolver.linkOf(noti("NT070", 10L, "x"), user("M9", Role.MEMBER)));
    }

    @Test
    void 평가표_요청은_경영지원실과_주관팀_팀장이_평가_결과_입력으로_간다() {
        assertEquals("/results?bid=10", resolver.linkOf(noti("NT110", 10L, "x"), mgmt));
        assertEquals("/results?bid=10", resolver.linkOf(noti("NT110", 10L, "x"), leader));
        assertNull(resolver.linkOf(noti("NT110", 10L, "x"), user("L9", Role.LEADER))); // 다른 팀 팀장
    }

    @Test
    void 수집_결과는_공고_목록_사업이_없는_알림은_갈_곳이_없다() {
        assertEquals("/notices", resolver.linkOf(noti("NT170", null, "[자동 수집] ..."), sales));
        assertNull(resolver.linkOf(noti("NT010", null, "[테스트]"), member));
        assertNull(resolver.linkOf(null, member));
    }
}
