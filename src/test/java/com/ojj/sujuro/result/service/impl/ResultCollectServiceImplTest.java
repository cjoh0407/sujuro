package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.result.dto.BidRsltDTO;
import com.ojj.sujuro.result.dto.CoEvlRsltDTO;
import com.ojj.sujuro.result.dto.ResultOutcomeDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;
import com.ojj.sujuro.result.mapper.ResultMapper;

/**
 * 결과 등록: 최종 낙찰자가 나오면 개찰 목록의 우리 회사(사업자번호) 순위·점수로 수주/탈락을 등록한다.
 * 응답은 2026-10-29 에 받은 실제 나라장터 응답(R26BK01541118 고용노동 데이터분석 서비스 개선 사업)에서 쓰는 칸만 남긴 것이다.
 * 우리 회사는 실제 회사가 아니어서, 시험마다 참가 업체 하나를 우리 회사로 가정한다.
 */
class ResultCollectServiceImplTest {

    private static final String OPENING = "["
            + "{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"opengRank\":\"1\",\"prcbdrBizno\":\"1078728988\","
            + "\"prcbdrNm\":\"엑사아이엔티 주식회사\",\"bidprcAmt\":\"260000000\","
            + "\"bidPrceEvlVal\":\"10\",\"techEvlVal\":\"90\",\"totalEvlAmtVal\":\"100\",\"techEvlNaturVal\":\"85.284\"},"
            + "{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"opengRank\":\"2\",\"prcbdrBizno\":\"3148187147\","
            + "\"prcbdrNm\":\"지엔소프트(주)\",\"bidprcAmt\":\"260200000\","
            + "\"bidPrceEvlVal\":\"9.9923\",\"techEvlVal\":\"87\",\"totalEvlAmtVal\":\"96.9923\",\"techEvlNaturVal\":\"84.564\"}]";

    private static final String AWARD = "[{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\","
            + "\"bidwinnrBizno\":\"1078728988\",\"bidwinnrNm\":\"엑사아이엔티 주식회사\",\"sucsfbidAmt\":\"260000000\"}]";

    /** 입찰공고 (같은 공고, 같은 날 실제 응답): 기술 90 : 가격 10 */
    private static final String BID = "[{\"bidNtceNo\":\"R26BK01541118\",\"bidNtceOrd\":\"000\","
            + "\"techAbltEvlRt\":\"90\",\"bidPrceEvlRt\":\"10\"}]";

    private final ObjectMapper json = new ObjectMapper();

    private ResultMapper mapper;
    private G2bApiClient api;
    private NotificationService notifications;
    private PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(ResultMapper.class);
        api = mock(G2bApiClient.class);
        notifications = mock(NotificationService.class);
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(api.findAward("R26BK01541118")).thenReturn(response(AWARD));
        when(api.findOpeningCompanies("R26BK01541118")).thenReturn(response(OPENING));
        when(api.findBidNotice("R26BK01541118")).thenReturn(response(BID));
        when(mapper.updateToResult(5L)).thenReturn(1);
    }

    @Test
    void 원점수를_같이_저장하고_공고에_배점이_없으면_입찰공고에서_채운다() {
        service("1078728988").register(target());

        assertEquals(new BigDecimal("85.284"), captureResult().getTechEvlOrgnlScr());
        ArgumentCaptor<CoEvlRsltDTO> co = ArgumentCaptor.forClass(CoEvlRsltDTO.class);
        verify(mapper, times(2)).mergeCoEvlRslt(co.capture());
        assertEquals(new BigDecimal("84.564"), co.getAllValues().get(1).getTechEvlOrgnlScr());
        verify(mapper).fillScoring(5L, new BigDecimal("90"), new BigDecimal("10"));
    }

    @Test
    void 공고에_배점이_있으면_입찰공고를_다시_묻지_않는다() {
        ResultTargetDTO t = target();
        t.setTechScorScr(new BigDecimal("90"));
        service("1078728988").register(t);

        verify(api, never()).findBidNotice(anyString());
        verify(mapper, never()).fillScoring(anyLong(), any(), any());
    }

    @Test
    void 낙찰자가_우리면_수주로_등록하고_개찰_업체를_모두_남긴다() {
        ResultOutcomeDTO out = service("107-87-28988").register(target());

        assertEquals(ResultOutcomeDTO.Kind.REGISTERED, out.getKind());
        assertEquals("결과 등록: 수주 · 1위", out.getMessage());

        BidRsltDTO r = captureResult();
        assertEquals("BR010", r.getBidRsltCd());
        assertEquals(1, r.getEvlRnk());
        assertEquals(new BigDecimal("90.000"), r.getTechEvlScr());
        assertEquals(new BigDecimal("10.000"), r.getPrcEvlScr());
        assertEquals(new BigDecimal("100.000"), r.getSnthsEvlScr());
        assertEquals(260_000_000L, r.getScsbdAmt());

        ArgumentCaptor<CoEvlRsltDTO> co = ArgumentCaptor.forClass(CoEvlRsltDTO.class);
        verify(mapper, times(2)).mergeCoEvlRslt(co.capture());
        assertEquals("Y", co.getAllValues().get(0).getOurCoYn());
        assertEquals("Y", co.getAllValues().get(0).getScsbdYn());
        assertEquals("N", co.getAllValues().get(1).getOurCoYn());
        assertEquals(new BigDecimal("9.992"), co.getAllValues().get(1).getPrcEvlScr()); // 넷째 자리는 반올림

        verify(notifications).sendToRole(eq(Role.MGMT), eq(NotificationType.EVL_TABLE_REQUEST), eq(5L), contains("수주 · 1위"));
    }

    @Test
    void 낙찰자가_다른_업체면_탈락이고_낙찰금액은_비운다() {
        ResultOutcomeDTO out = service("3148187147").register(target());

        assertEquals("결과 등록: 탈락 · 2위", out.getMessage());
        BidRsltDTO r = captureResult();
        assertEquals("BR020", r.getBidRsltCd());
        assertEquals(2, r.getEvlRnk());
        assertEquals(new BigDecimal("87.000"), r.getTechEvlScr());
        assertEquals(new BigDecimal("96.992"), r.getSnthsEvlScr());
        assertNull(r.getScsbdAmt());
    }

    @Test
    void 최종_낙찰자가_아직_없으면_다음_수집까지_기다린다() throws Exception {
        when(api.findAward("R26BK01541118")).thenReturn(response("[]"));
        ResultOutcomeDTO out = service("1078728988").register(target());

        assertEquals(ResultOutcomeDTO.Kind.WAITING, out.getKind());
        verify(api, never()).findOpeningCompanies(anyString());
        verify(mapper, never()).mergeBidRslt(any());
    }

    @Test
    void 개찰_목록에_우리_회사가_없으면_등록하지_않고_사람이_보게_한다() {
        ResultOutcomeDTO out = service("1234567890").register(target());

        assertEquals(ResultOutcomeDTO.Kind.NOT_LISTED, out.getKind());
        assertTrue(out.getMessage().contains("1234567890"));
        verify(mapper, never()).mergeCoEvlRslt(any());
        verify(mapper, never()).updateToResult(anyLong());
    }

    @Test
    void 그사이_상태가_바뀌었으면_되돌리고_알리지_않는다() {
        when(mapper.updateToResult(5L)).thenReturn(0);
        ResultOutcomeDTO out = service("1078728988").register(target());

        assertEquals(ResultOutcomeDTO.Kind.WAITING, out.getKind());
        verify(notifications, never()).sendToRole(any(), anyString(), anyLong(), anyString());
    }

    @Test
    void 사업자번호가_설정되지_않았으면_확인하지_않는다() {
        assertFalse(service("0000000000").isReady());
        assertFalse(service("").isReady());
        assertFalse(service("12345").isReady());
        assertTrue(service("107-87-28988").isReady());
    }

    @Test
    void 재입찰이_있으면_마지막_회차의_업체만_쓴다() throws Exception {
        List<JsonNode> rows = response("["
                + "{\"bidNtceOrd\":\"000\",\"rbidNo\":\"000\",\"prcbdrBizno\":\"1111111111\"},"
                + "{\"bidNtceOrd\":\"000\",\"rbidNo\":\"001\",\"prcbdrBizno\":\"2222222222\"},"
                + "{\"bidNtceOrd\":\"000\",\"rbidNo\":\"001\",\"prcbdrBizno\":\"3333333333\"}]").all();
        List<JsonNode> last = ResultCollectServiceImpl.latestRound(rows);
        assertEquals(2, last.size());
        assertEquals("2222222222", G2bResponse.textOf(last.get(0), "prcbdrBizno"));
    }

    private ResultCollectServiceImpl service(String bizNo) {
        return new ResultCollectServiceImpl(mapper, api, notifications, txManager, bizNo);
    }

    private BidRsltDTO captureResult() {
        ArgumentCaptor<BidRsltDTO> c = ArgumentCaptor.forClass(BidRsltDTO.class);
        verify(mapper).mergeBidRslt(c.capture());
        return c.getValue();
    }

    private static ResultTargetDTO target() {
        ResultTargetDTO t = new ResultTargetDTO();
        t.setPbancSn(5L);
        t.setBidPbancNo("R26BK01541118");
        t.setPbancNm("고용노동 데이터분석 서비스 개선 사업");
        return t;
    }

    private G2bResponse response(String items) throws Exception {
        JsonNode node = json.readTree(items);
        return new G2bResponse(node, items, node.size());
    }
}
