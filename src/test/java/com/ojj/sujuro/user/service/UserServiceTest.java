package com.ojj.sujuro.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.user.dto.CodeOption;
import com.ojj.sujuro.user.dto.UserDto;
import com.ojj.sujuro.user.dto.UserForm;
import com.ojj.sujuro.user.dto.UserProjectDto;
import com.ojj.sujuro.user.mapper.UserMapper;

/** 사용자 관리: 이메일 중복, 팀 이동·비활성 때 진행 중 사업 정리, 하나뿐인 팀장 보호 */
class UserServiceTest {

    private UserMapper mapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMapper.class);
        service = new UserService(mapper, mock(PasswordEncoder.class));
        when(mapper.findDeptCd(1L)).thenReturn("DP030");
        when(mapper.findDeptCd(2L)).thenReturn("DP030");
        CodeOption member = new CodeOption();
        member.setCode("JG010");
        CodeOption leader = new CodeOption();
        leader.setCode("JG020");
        when(mapper.findRanks()).thenReturn(Arrays.asList(member, leader));
    }

    private UserDto user(String id, Long teamSn, String authrtCd) {
        UserDto u = new UserDto();
        u.setUserId(id);
        u.setUserNm("홍길동");
        u.setTeamSn(teamSn);
        u.setTeamNm("공공사업1Div");
        u.setAuthrtCd(authrtCd);
        u.setAcntSttsCd("AS030");
        return u;
    }

    private UserForm form(Long teamSn, String jbgdCd, String email) {
        UserForm f = new UserForm();
        f.setUserNm("홍길동");
        f.setTeamSn(teamSn);
        f.setJbgdCd(jbgdCd);
        f.setEmlAddr(email);
        return f;
    }

    private UserProjectDto project(long sn, long teamSn, int tocCnt) {
        UserProjectDto p = new UserProjectDto();
        p.setPbancSn(sn);
        p.setPbancNm("A사업");
        p.setSprvsnTeamSn(teamSn);
        p.setTocCnt(tocCnt);
        return p;
    }

    @Test
    void 다른_직원이_쓰는_이메일은_막는다() {
        when(mapper.findById("2024-0001")).thenReturn(user("2024-0001", 1L, "AU040"));
        when(mapper.countByEmail("a@b.co", "2024-0001")).thenReturn(1);

        List<String> errors = service.validateEdit("2024-0001", form(1L, "JG010", "a@b.co"), "ADMIN");

        assertTrue(errors.contains("이미 다른 직원이 쓰는 이메일입니다."));
    }

    @Test
    void 진행_중_사업의_하나뿐인_팀장은_다른_팀으로_옮길_수_없다() {
        when(mapper.findById("L1")).thenReturn(user("L1", 1L, "AU030"));
        when(mapper.countActiveLedProjects(1L)).thenReturn(2);
        when(mapper.countOtherLeaders(1L, "L1")).thenReturn(0);

        List<String> errors = service.validateEdit("L1", form(2L, "JG020", "l1@b.co"), "ADMIN");

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("하나뿐인 팀장"));
    }

    @Test
    void 같은_팀에_다른_팀장이_있으면_옮길_수_있다() {
        when(mapper.findById("L1")).thenReturn(user("L1", 1L, "AU030"));
        when(mapper.countActiveLedProjects(1L)).thenReturn(2);
        when(mapper.countOtherLeaders(1L, "L1")).thenReturn(1);

        assertTrue(service.validateEdit("L1", form(2L, "JG020", "l1@b.co"), "ADMIN").isEmpty());
    }

    @Test
    void 하나뿐인_팀장은_비활성으로_바꿀_수_없다() {
        when(mapper.findById("L1")).thenReturn(user("L1", 1L, "AU030"));
        when(mapper.countActiveLedProjects(1L)).thenReturn(1);
        when(mapper.countOtherLeaders(1L, "L1")).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.deactivate("L1", "ADMIN", true));
        verify(mapper, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void 같이_빼기로_비활성하면_명단에서_빼고_목차_담당자를_비운다() {
        when(mapper.findById("M1")).thenReturn(user("M1", 1L, "AU040"));
        when(mapper.findActiveProposals("M1")).thenReturn(Arrays.asList(project(10L, 1L, 2), project(11L, 2L, 0)));

        int removed = service.deactivate("M1", "ADMIN", true);

        assertEquals(2, removed);
        verify(mapper).updateStatus("M1", "AS050");
        verify(mapper).clearActiveTocOwner("M1");
        verify(mapper).deleteActiveMemberships("M1");
    }

    @Test
    void 그대로_두기로_비활성하면_명단은_그대로다() {
        when(mapper.findById("M1")).thenReturn(user("M1", 1L, "AU040"));

        service.deactivate("M1", "ADMIN", false);

        verify(mapper).updateStatus("M1", "AS050");
        verify(mapper, never()).deleteActiveMemberships(anyString());
        verify(mapper, never()).clearActiveTocOwner(anyString());
    }

    @Test
    void 팀을_옮기며_같이_빼기를_고르면_명단에서_뺀다_팀이_같으면_빼지_않는다() {
        when(mapper.findById("M1")).thenReturn(user("M1", 1L, "AU040"));
        when(mapper.findActiveProposals("M1")).thenReturn(Collections.singletonList(project(10L, 1L, 1)));

        assertEquals(0, service.update("M1", form(1L, "JG010", "m1@b.co"), true)); // 팀 그대로
        verify(mapper, never()).deleteActiveMemberships(anyString());

        assertEquals(1, service.update("M1", form(2L, "JG010", "m1@b.co"), true)); // 다른 팀으로
        verify(mapper).deleteActiveMemberships("M1");
    }
}
