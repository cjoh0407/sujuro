package com.ojj.sujuro.reference.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.reference.dto.RefFileDto;
import com.ojj.sujuro.reference.mapper.RefFileMapper;

/** 참고자료: 구분 판정, 거르기, 팀 격리(다른 팀 자료는 열리지 않는다), 삭제 권한 */
class RefFileServiceTest {

    private RefFileMapper mapper;
    private RefFileService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RefFileMapper.class);
        service = new RefFileService(mapper, mock(FileStorage.class));
    }

    private static RefFileDto file(long sn, long teamSn, String se, String name, String memo, String rgtr) {
        RefFileDto f = new RefFileDto();
        f.setRefFileSn(sn);
        f.setTeamSn(teamSn);
        f.setRefSeCd(se);
        f.setOrgnlFileNm(name);
        f.setMemoCn(memo);
        f.setRgtrId(rgtr);
        return f;
    }

    @Test
    void 확장자로_폰트_이미지_문서_기타를_나눈다() {
        assertEquals(RefFileDto.SE_FONT, RefFileService.categoryOf("ttf"));
        assertEquals(RefFileDto.SE_FONT, RefFileService.categoryOf("WOFF2"));
        assertEquals(RefFileDto.SE_IMAGE, RefFileService.categoryOf("png"));
        assertEquals(RefFileDto.SE_IMAGE, RefFileService.categoryOf("svg"));
        assertEquals(RefFileDto.SE_DOC, RefFileService.categoryOf("pdf"));
        assertEquals(RefFileDto.SE_DOC, RefFileService.categoryOf("hwpx"));
        assertEquals(RefFileDto.SE_ETC, RefFileService.categoryOf("zip"));
        assertEquals(RefFileDto.SE_ETC, RefFileService.categoryOf(""));
        assertEquals(RefFileDto.SE_ETC, RefFileService.categoryOf(null));
    }

    @Test
    void 그림으로_바로_여는_것은_svg를_뺀_이미지_확장자만() {
        assertTrue(RefFileService.isPreviewExt("png"));
        assertTrue(RefFileService.isPreviewExt("JPG"));
        assertFalse(RefFileService.isPreviewExt("svg"));
        assertFalse(RefFileService.isPreviewExt("pdf"));
        assertFalse(RefFileService.isPreviewExt(null));
    }

    @Test
    void 구분과_검색어로_거르고_건수를_센다() {
        List<RefFileDto> all = Arrays.asList(
                file(3, 1, RefFileDto.SE_FONT, "Pretendard.ttf", "본문용", "u1"),
                file(2, 1, RefFileDto.SE_IMAGE, "로고.PNG", null, "u2"),
                file(1, 1, RefFileDto.SE_IMAGE, "배경.jpg", "표지 배경", "u1"));

        assertEquals(3, RefFileService.filter(all, "", "").size());
        assertEquals(2, RefFileService.filter(all, RefFileDto.SE_IMAGE, null).size());
        assertEquals(1, RefFileService.filter(all, null, "pretend").size());     // 대소문자 무시
        assertEquals(1, RefFileService.filter(all, null, "표지").size());         // 설명도 검색
        assertEquals(0, RefFileService.filter(all, RefFileDto.SE_DOC, null).size());

        Map<String, Integer> c = RefFileService.counts(all);
        assertEquals(Integer.valueOf(1), c.get(RefFileDto.SE_FONT));
        assertEquals(Integer.valueOf(2), c.get(RefFileDto.SE_IMAGE));
        assertEquals(Integer.valueOf(0), c.get(RefFileDto.SE_DOC));
        assertEquals(Integer.valueOf(0), c.get(RefFileDto.SE_ETC));
    }

    @Test
    void 다른_팀의_자료는_없는_것처럼_열리지_않는다() {
        when(mapper.findOne(10L)).thenReturn(file(10, 2, RefFileDto.SE_DOC, "타팀.pdf", null, "u9"));

        assertEquals("자료를 찾을 수 없습니다.", assertThrows(BusinessException.class, () -> service.open(1L, 10L)).getMessage());
        assertEquals(Long.valueOf(10), service.open(2L, 10L).getRefFileSn());
        // 없는 번호·삭제된 자료도 같은 안내
        assertThrows(BusinessException.class, () -> service.open(1L, 99L));
        assertThrows(BusinessException.class, () -> service.open(1L, null));
    }

    @Test
    void 삭제는_올린_사람과_팀장만_할_수_있다() {
        RefFileDto f = file(5, 1, RefFileDto.SE_ETC, "자료.zip", null, "u1");
        assertTrue(RefFileService.canDelete(f, "u1", false));   // 본인
        assertTrue(RefFileService.canDelete(f, "u2", true));    // 팀장
        assertFalse(RefFileService.canDelete(f, "u2", false));  // 같은 팀 다른 팀원
        assertFalse(RefFileService.canDelete(null, "u1", true));
    }

    @Test
    void 삭제는_내_팀_자료만_되고_권한이_없으면_막는다() {
        when(mapper.findOne(5L)).thenReturn(file(5, 1, RefFileDto.SE_ETC, "자료.zip", null, "u1"));

        assertThrows(BusinessException.class, () -> service.delete(1L, 5L, "u2", false));
        verify(mapper, never()).markDeleted(5L, "u2");
        assertThrows(BusinessException.class, () -> service.delete(2L, 5L, "u1", true));   // 다른 팀 팀장도 안 된다
        service.delete(1L, 5L, "u1", false);
        verify(mapper).markDeleted(5L, "u1");
    }

    @Test
    void 소속_팀이_없으면_접근_거부() {
        when(mapper.findTeamSn("E1")).thenReturn(null);
        LoginUser u = new LoginUser("E1", "홍", "사원", "공공사업1Div", Role.MEMBER, LoginUser.STATUS_ACTIVE, "x");
        assertThrows(AccessDeniedException.class, () -> service.requireTeam(u));
        assertThrows(AccessDeniedException.class, () -> service.requireTeam(null));

        when(mapper.findTeamSn("E2")).thenReturn(7L);
        assertEquals(Long.valueOf(7), service.requireTeam(new LoginUser("E2", "김", "팀장", "공공사업1Div", Role.LEADER, LoginUser.STATUS_ACTIVE, "x")));
        when(mapper.findByTeam(7L)).thenReturn(Collections.<RefFileDto>emptyList());
        assertTrue(service.all(7L).isEmpty());
    }

    @Test
    void 페이지_나누기는_열_개씩_자르고_잘못된_번호는_맞춘다() {
        List<Integer> nums = new java.util.ArrayList<>();
        for (int i = 1; i <= 23; i++) {
            nums.add(i);
        }
        assertEquals(3, RefFileService.totalPages(23));
        assertEquals(1, RefFileService.totalPages(0));
        assertEquals(1, RefFileService.totalPages(10));
        assertEquals(2, RefFileService.totalPages(11));
        assertEquals(10, RefFileService.pageOf(nums, 1).size());
        assertEquals(Integer.valueOf(11), RefFileService.pageOf(nums, 2).get(0));
        assertEquals(3, RefFileService.pageOf(nums, 3).size());
        // 범위를 벗어난 번호는 가까운 쪽 끝 페이지
        assertEquals(RefFileService.pageOf(nums, 3), RefFileService.pageOf(nums, 99));
        assertEquals(RefFileService.pageOf(nums, 1), RefFileService.pageOf(nums, -5));
        assertTrue(RefFileService.pageOf(Collections.<Integer>emptyList(), 1).isEmpty());
    }
}
