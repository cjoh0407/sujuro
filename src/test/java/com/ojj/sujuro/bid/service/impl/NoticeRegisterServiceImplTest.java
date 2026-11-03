package com.ojj.sujuro.bid.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.dto.NoticeDetailDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeAttachmentService;
import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 잘못 등록한 공고 삭제. 팀에 한 번도 맡기지 않은 신규 공고만 지우고, 딸린 기록을 먼저 지운 뒤 공고를 지운다.
 * 공고는 테스트 데이터(db/03_test_data.sql 의 R26BK00938420)의 번호·이름만 빌렸다.
 */
class NoticeRegisterServiceImplTest {

    private static final long PBANC_SN = 9L;
    private static final String SALES = "S0001";

    private BidNoticeMapper mapper;
    private NoticeRegisterServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(BidNoticeMapper.class);
        service = new NoticeRegisterServiceImpl(mapper, mock(G2bApiClient.class), mock(NoticeAttachmentService.class));
    }

    @Test
    void 팀에_맡긴_적_없는_신규_공고는_딸린_기록과_함께_지운다() {
        when(mapper.findDetail(PBANC_SN)).thenReturn(notice(BidCode.STTS_NEW, null, null));
        when(mapper.deleteNotice(PBANC_SN, BidCode.STTS_NEW)).thenReturn(1);

        assertEquals("'통합 보안관제 체계 구축' 공고를 삭제했습니다.", service.delete(PBANC_SN, SALES));

        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteNoticeNotifications(PBANC_SN);
        order.verify(mapper).deleteNoticeFiles(PBANC_SN);
        order.verify(mapper).deleteCollectLogs(PBANC_SN);
        order.verify(mapper).deleteCollectHistories(PBANC_SN);
        order.verify(mapper).deleteProcessHistories(PBANC_SN);
        order.verify(mapper).deleteNotice(PBANC_SN, BidCode.STTS_NEW);
    }

    @Test
    void 신규가_아니거나_팀에서_돌아왔거나_배정_이력이_있으면_지우지_않는다() {
        assertRefused(notice(BidCode.STTS_ASSIGNED, 5L, null), 0);
        assertRefused(notice(BidCode.STTS_DECLINED, null, null), 0);
        assertRefused(notice(BidCode.STTS_NEW, null, "불참"), 0);   // 팀장이 불참해 돌아온 공고
        assertRefused(notice(BidCode.STTS_NEW, null, null), 1);     // 반환구분은 없지만 배정했던 이력이 있다
    }

    @Test
    void 다른_자료가_공고를_가리키면_안내_문구로_바꾼다() {
        when(mapper.findDetail(PBANC_SN)).thenReturn(notice(BidCode.STTS_NEW, null, null));
        when(mapper.deleteNotice(PBANC_SN, BidCode.STTS_NEW))
                .thenThrow(new DataIntegrityViolationException("ORA-02292"));

        assertTrue(message(() -> service.delete(PBANC_SN, SALES)).contains("연결된 다른 자료가 있어"));
    }

    @Test
    void 그사이_배정되어_공고가_지워지지_않으면_실패로_알린다() {
        when(mapper.findDetail(PBANC_SN)).thenReturn(notice(BidCode.STTS_NEW, null, null));
        when(mapper.deleteNotice(PBANC_SN, BidCode.STTS_NEW)).thenReturn(0);

        // 예외가 나야 트랜잭션이 앞의 삭제까지 되돌린다
        assertTrue(message(() -> service.delete(PBANC_SN, SALES)).contains("그사이 공고 상태가 바뀌었습니다"));
    }

    @Test
    void 없는_공고는_지우지_않는다() {
        assertTrue(message(() -> service.delete(PBANC_SN, SALES)).contains("공고를 찾을 수 없습니다"));
        verify(mapper, never()).deleteNotice(anyLong(), anyString());
    }

    private void assertRefused(NoticeDetailDTO notice, int assignHistories) {
        setUp();
        when(mapper.findDetail(PBANC_SN)).thenReturn(notice);
        when(mapper.countAssignHistories(eq(PBANC_SN), anyString(), anyString())).thenReturn(assignHistories);

        assertTrue(message(() -> service.delete(PBANC_SN, SALES)).contains("배정 전 공고만 삭제할 수 있습니다"));
        verify(mapper, never()).deleteNoticeNotifications(anyLong());
        verify(mapper, never()).deleteNotice(anyLong(), anyString());
    }

    private static NoticeDetailDTO notice(String sttsCd, Long teamSn, String rtrnSeNm) {
        NoticeDetailDTO n = new NoticeDetailDTO();
        n.setPbancSn(PBANC_SN);
        n.setPbancNo("R26BK00938420");
        n.setPbancNm("통합 보안관제 체계 구축 ");
        n.setPbancSttsCd(sttsCd);
        n.setSprvsnTeamSn(teamSn);
        n.setRtrnSeNm(rtrnSeNm);
        return n;
    }

    private static String message(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(BusinessException.class, call).getMessage();
    }
}
