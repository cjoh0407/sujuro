package com.ojj.sujuro.bid.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.dto.BidLinkDTO;
import com.ojj.sujuro.bid.dto.CollectResultDTO;
import com.ojj.sujuro.bid.dto.TrackingNoticeDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeAttachmentService;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.result.service.ResultCollectService;

/**
 * 수집 알림. 정정·취소(FR-028, NT020)는 영업부 전원과 배정된 팀의 팀장이, 자동 수집 결과(FR-025, NT170)는 영업부가 받는다.
 * 공고는 테스트 데이터(db/03_test_data.sql 의 R26BK00938420)이고, 나라장터 응답은 정정 확인에 쓰는 칸만 지어서 넣었다.
 */
@ExtendWith(OutputCaptureExtension.class)
class NoticeCollectServiceImplTest {

    private static final long PBANC_SN = 9L;
    private static final String BID_NO = "R26BK00938420";
    private static final String NAME = "통합 보안관제 체계 구축";

    private final ObjectMapper json = new ObjectMapper();

    private BidNoticeMapper mapper;
    private G2bApiClient api;
    private NotificationService notifications;
    private NoticeCollectServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(BidNoticeMapper.class);
        api = mock(G2bApiClient.class);
        notifications = mock(NotificationService.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new NoticeCollectServiceImpl(mapper, api, mock(NoticeAttachmentService.class),
                mock(ResultCollectService.class), txManager, notifications, 3, 5);

        when(mapper.findBidStageNotices(BidCode.STEP_BID_NOTICE)).thenReturn(List.of(stored()));
        when(mapper.updateBidCorrection(any())).thenReturn(1);
    }

    @Test
    void 정정이면_영업부와_배정된_팀_팀장에게_바뀐_마감일을_알린다() throws Exception {
        bidLookup("{\"bidNtceNo\":\"" + BID_NO + "\",\"bidNtceOrd\":\"001\",\"ntceKindNm\":\"변경공고\","
                + "\"bidNtceNm\":\"" + NAME + "\",\"bidClseDt\":\"2026-11-07 10:00:00\",\"asignBdgtAmt\":\"1600000000\"}");
        when(mapper.findSprvsnTeamSn(PBANC_SN)).thenReturn(5L);

        CollectResultDTO result = service.collect(BidCode.EXCN_AUTO, null);

        String expected = "'" + NAME + "' 입찰공고가 정정되었습니다 (차수 0 → 1). 제출 마감 2026-11-05 10:00 → 2026-11-07 10:00";
        assertEquals(1, result.getCorrected());
        verify(notifications).sendToRole(Role.SALES, NotificationType.BID_CHANGED, PBANC_SN, expected);
        verify(notifications).sendToTeamLeaders(5L, NotificationType.BID_CHANGED, PBANC_SN, expected);
    }

    @Test
    void 취소공고이고_배정된_팀이_없으면_영업부만_받는다() throws Exception {
        bidLookup("{\"bidNtceNo\":\"" + BID_NO + "\",\"bidNtceOrd\":\"001\",\"ntceKindNm\":\"취소공고\","
                + "\"bidNtceNm\":\"" + NAME + "\"}");
        when(mapper.findSprvsnTeamSn(PBANC_SN)).thenReturn(null);

        CollectResultDTO result = service.collect(BidCode.EXCN_AUTO, null);

        assertEquals(1, result.getCanceled());
        verify(notifications).sendToRole(Role.SALES, NotificationType.BID_CHANGED, PBANC_SN,
                "'" + NAME + "' 입찰공고가 취소되었습니다 (차수 0 → 1). 공고 상세에서 확인해 주세요.");
        verify(notifications, never()).sendToTeamLeaders(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    void 차수가_같아도_예산과_공고명이_바뀌면_둘_다_적는다() {
        BidLinkDTO after = new BidLinkDTO();
        after.setBidPbancCycl(0);
        after.setPbancNm(NAME + " (2차)");
        after.setAltmntBgtAmt(new BigDecimal("1500000000"));

        assertEquals("'" + NAME + " (2차)' 입찰공고가 정정되었습니다. 예산 1,600,000,000원 → 1,500,000,000원 · 공고명 변경 (이전: " + NAME + ")",
                NoticeCollectServiceImpl.correctionContent(stored(), after));
    }

    @Test
    void 자동_수집이_끝나면_결과_요약을_영업부에_알린다() throws Exception {
        bidLookup("{\"bidNtceNo\":\"" + BID_NO + "\",\"bidNtceOrd\":\"001\",\"ntceKindNm\":\"변경공고\","
                + "\"bidNtceNm\":\"" + NAME + "\",\"bidClseDt\":\"2026-11-07 10:00:00\"}");

        service.collectAuto();

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendToRole(eq(Role.SALES), eq(BidCode.NOTI_COLLECT_RESULT), isNull(), content.capture());
        assertTrue(content.getValue().startsWith("[자동 수집] "), content.getValue());
        assertTrue(content.getValue().endsWith(" · 정정 1건"), content.getValue());
    }

    @Test
    void 바뀐_공고가_없어도_남기고_확인_실패는_뒤에_붙인다() {
        CollectResultDTO result = new CollectResultDTO();
        result.addFailure("사전규격 전환", "나라장터가 응답하지 않았습니다");

        assertEquals("[자동 수집] 2026-11-02 17:00 · 바뀐 공고가 없습니다 / 확인 실패: 사전규격 전환: 나라장터가 응답하지 않았습니다",
                NoticeCollectServiceImpl.autoCollectContent(LocalDateTime.of(2026, 11, 2, 17, 0), result));
    }

    @Test
    void 정정_확인은_우리_공고를_번호로_묻고_기간_목록은_받지_않는다() throws Exception {
        bidLookup("{\"bidNtceNo\":\"" + BID_NO + "\",\"bidNtceOrd\":\"001\",\"ntceKindNm\":\"변경공고\",\"bidNtceNm\":\"" + NAME + "\"}");

        CollectResultDTO result = service.collect(BidCode.EXCN_MANUAL, "2023-0067");

        assertEquals(1, result.getCorrected(), "0차와 1차가 함께 와도 1차로 정정을 찾습니다");
        verify(api).findBidNotice(BID_NO);
        verify(api, never()).findBidNoticesBetween(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void 추적_공고가_기준보다_많으면_기간_목록으로_받고_번호로_묻지_않는다() throws Exception {
        List<TrackingNoticeDTO> many = targets(NoticeCollectServiceImpl.CORRECTION_BY_NUMBER_MAX + 1);
        when(mapper.findBidStageNotices(BidCode.STEP_BID_NOTICE)).thenReturn(many);
        String no = many.get(0).getBidPbancNo();
        when(api.findBidNoticesBetween(eq(G2bApiClient.BID_BY_REG_DT), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(cycle(no, "001"));
        when(api.findBidNoticesBetween(eq(G2bApiClient.BID_BY_CHG_DT), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(response("[]"));

        CollectResultDTO result = service.collect(BidCode.EXCN_MANUAL, "2023-0067");

        assertEquals(1, result.getCorrected());
        verify(api, never()).findBidNotice(anyString());
    }

    @Test
    void 번호_조회가_한_번_실패해도_다음_공고는_계속_확인하고_실패를_알린다() throws Exception {
        List<TrackingNoticeDTO> three = targets(3);
        when(mapper.findBidStageNotices(BidCode.STEP_BID_NOTICE)).thenReturn(three);
        when(api.findBidNotice(three.get(0).getBidPbancNo())).thenThrow(new BusinessException("나라장터 응답 없음"));
        when(api.findBidNotice(three.get(1).getBidPbancNo())).thenReturn(cycle(three.get(1).getBidPbancNo(), "001"));
        when(api.findBidNotice(three.get(2).getBidPbancNo())).thenReturn(cycle(three.get(2).getBidPbancNo(), "000"));

        CollectResultDTO result = service.collect(BidCode.EXCN_MANUAL, "2023-0067");

        assertEquals(1, result.getCorrected(), "실패한 다음 공고의 정정을 찾습니다");
        verify(api).findBidNotice(three.get(2).getBidPbancNo());
        assertEquals(List.of("정정 확인: 입찰공고 1건의 정정을 확인하지 못했습니다: 나라장터 응답 없음"), result.getFailures());
    }

    @Test
    void 번호_조회가_연달아_두_번_실패하면_멈추고_남은_공고는_묻지_않는다() throws Exception {
        List<TrackingNoticeDTO> four = targets(4);
        when(mapper.findBidStageNotices(BidCode.STEP_BID_NOTICE)).thenReturn(four);
        when(api.findBidNotice(four.get(0).getBidPbancNo())).thenReturn(cycle(four.get(0).getBidPbancNo(), "001"));
        when(api.findBidNotice(four.get(1).getBidPbancNo())).thenThrow(new BusinessException("나라장터 응답 없음"));
        when(api.findBidNotice(four.get(2).getBidPbancNo())).thenThrow(new BusinessException("나라장터 응답 없음"));

        CollectResultDTO result = service.collect(BidCode.EXCN_MANUAL, "2023-0067");

        assertEquals(1, result.getCorrected(), "멈추기 전에 찾은 정정은 반영합니다");
        verify(api, never()).findBidNotice(four.get(3).getBidPbancNo());
        assertEquals(List.of("정정 확인: 나라장터가 연달아 응답하지 않아 정정 확인을 멈췄습니다 (확인 못 한 공고 3건): 나라장터 응답 없음"),
                result.getFailures());
    }

    @Test
    void 단계마다_확인한_방식과_건수와_걸린_시간을_로그에_남긴다(CapturedOutput output) throws Exception {
        bidLookup("{\"bidNtceNo\":\"" + BID_NO + "\",\"bidNtceOrd\":\"001\",\"ntceKindNm\":\"변경공고\",\"bidNtceNm\":\"" + NAME + "\"}");

        service.collect(BidCode.EXCN_MANUAL, "2023-0067");

        String logs = output.getAll();
        assertTrue(logs.contains("[수집] 1. 사전규격 전환 · 확인할 공고 없음 · "), "부르지 않은 단계도 남깁니다");
        assertTrue(logs.matches("(?s).*\\[수집\\] 3\\. 정정 확인 · 번호 조회 1건 · \\d+\\.\\d초.*"), logs);
        assertTrue(logs.contains("[수집] 7. 빠진 첨부 · "), "마지막 단계까지 남깁니다");
        assertTrue(logs.matches("(?s).*\\[수집\\] 수동 수집 끝 · 전체 \\d+\\.\\d초 · .*정정 1건.*"), logs);
    }

    /** DB 에 저장된 입찰공고: 0차, 일반공고, 제출 마감 2026-11-05 10:00, 예산 16억 */
    private static TrackingNoticeDTO stored() {
        TrackingNoticeDTO t = new TrackingNoticeDTO();
        t.setPbancSn(PBANC_SN);
        t.setBidPbancNo(BID_NO);
        t.setBidPbancCycl(0);
        t.setPbancStepCd(BidCode.STEP_BID_NOTICE);
        t.setPbancTypeCd(BidCode.TYPE_NORMAL);
        t.setPbancNm(NAME);
        t.setAltmntBgtAmt(new BigDecimal("1600000000"));
        t.setBidDdlnDt(LocalDateTime.of(2026, 11, 5, 10, 0));
        return t;
    }

    /** 입찰공고 번호 조회: 0차(처음 등록)와 정정된 차수가 함께 온다. 차수가 가장 큰 건을 써야 한다 */
    private void bidLookup(String item) throws Exception {
        when(api.findBidNotice(BID_NO)).thenReturn(response("[{\"bidNtceNo\":\"" + BID_NO + "\",\"bidNtceOrd\":\"000\","
                + "\"ntceKindNm\":\"등록공고\",\"bidNtceNm\":\"" + NAME + "\"}," + item + "]"));
    }

    /** 정정 확인 대상 입찰공고 n건. 번호는 R26BK999902xx (가상) */
    private List<TrackingNoticeDTO> targets(int n) {
        List<TrackingNoticeDTO> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            TrackingNoticeDTO t = stored();
            t.setPbancSn(1000L + i);
            t.setBidPbancNo(String.format("R26BK999902%02d", i));
            list.add(t);
        }
        return list;
    }

    /** 번호 조회 결과 한 건. 0차는 저장값과 같은 등록공고(바뀐 것 없음), 그 뒤 차수는 변경공고(정정) */
    private G2bResponse cycle(String no, String ord) throws Exception {
        String kind = "000".equals(ord) ? "등록공고" : "변경공고";
        return response("[{\"bidNtceNo\":\"" + no + "\",\"bidNtceOrd\":\"" + ord + "\",\"ntceKindNm\":\"" + kind + "\",\"bidNtceNm\":\"" + NAME + "\"}]");
    }

    private G2bResponse response(String items) throws Exception {
        JsonNode node = json.readTree(items);
        return new G2bResponse(node, items, node.size());
    }
}
