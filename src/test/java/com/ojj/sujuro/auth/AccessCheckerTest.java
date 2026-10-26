package com.ojj.sujuro.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** 제안팀 권한 판정 (양식=주관팀 인원, 목차 편집=제안팀 전원, 담당자 지정=주관팀 팀장) */
class AccessCheckerTest {

    private static final Long BID = 1L;

    private AccessMapper mapper;
    private AccessChecker checker;

    @BeforeEach
    void setUp() {
        mapper = mock(AccessMapper.class);
        checker = new AccessChecker(mapper);
    }

    private LoginUser user(String empNo, Role role) {
        return new LoginUser(empNo, empNo, "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }

    /** empNo를 해당 참여구분의 제안팀원으로 등록한다. (null이면 제안팀원이 아님) */
    private void member(String empNo, String ptcpSeCd) {
        when(mapper.countProposalMember(anyLong(), eq(empNo))).thenReturn(ptcpSeCd == null ? 0 : 1);
        when(mapper.countProposalMemberByType(anyLong(), eq(empNo), anyString()))
                .thenAnswer(inv -> inv.getArgument(2).equals(ptcpSeCd) ? 1 : 0);
    }

    @Test
    void 주관팀_인원은_양식을_다룰_수_있다() {
        member("L1", "PT010");
        assertTrue(checker.isLeadTeamMember(BID, user("L1", Role.MEMBER)));
    }

    @Test
    void 참여팀_인원은_양식을_다룰_수_없다() {
        member("J1", "PT020");
        assertFalse(checker.isLeadTeamMember(BID, user("J1", Role.MEMBER)));
        assertThrows(AccessDeniedException.class, () -> checker.requireLeadTeamMember(BID, user("J1", Role.MEMBER)));
    }

    @Test
    void 제안팀원이_아니면_모두_막힌다() {
        member("X1", null);
        LoginUser x = user("X1", Role.LEADER);
        assertFalse(checker.isProposalMember(BID, x));
        assertFalse(checker.isLeadTeamMember(BID, x));
        assertFalse(checker.isLeadTeamLeader(BID, x));
    }

    @Test
    void 목차_편집은_주관팀과_참여팀_모두_가능하다() {
        member("L1", "PT010");
        member("J1", "PT020");
        assertTrue(checker.isProposalMember(BID, user("L1", Role.MEMBER)));
        assertTrue(checker.isProposalMember(BID, user("J1", Role.MEMBER)));
    }

    @Test
    void 담당자_지정은_주관팀_팀장만_가능하다() {
        member("L1", "PT010");
        assertTrue(checker.isLeadTeamLeader(BID, user("L1", Role.LEADER)));
        assertFalse(checker.isLeadTeamLeader(BID, user("L1", Role.MEMBER)));
    }

    @Test
    void 참여팀_팀장은_담당자를_지정할_수_없다() {
        member("J1", "PT020");
        assertFalse(checker.isLeadTeamLeader(BID, user("J1", Role.LEADER)));
        assertThrows(AccessDeniedException.class, () -> checker.requireLeadTeamLeader(BID, user("J1", Role.LEADER)));
    }

    @Test
    void 사업건_번호나_사용자가_없으면_거부한다() {
        assertFalse(checker.isLeadTeamMember(null, user("L1", Role.LEADER)));
        assertFalse(checker.isLeadTeamLeader(BID, null));
    }
}
