package com.ojj.sujuro.bid.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.dto.CollectResultDTO;
import com.ojj.sujuro.bid.service.NoticeAttachmentService;
import com.ojj.sujuro.bid.service.NoticeCollectService;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.result.service.ResultCollectService;

/**
 * 가상 공고 하나를 발주계획 → 사전규격 → 입찰공고 → 개찰 → 계약으로 수집이 한 단계씩 넘기는지 본다 (11/03).
 *
 * 나라장터 대신 가짜 응답(G2bApiClient 목)을 주고, DB 는 로컬 Oracle 을 그대로 쓴다. 단계 전환 SQL 의 조건까지 실제로 돈다.
 * 테스트가 끝나면 모두 되돌린다(@Transactional). 첨부·결과 등록·알림은 목이라 나라장터·메일에 나가지 않는다.
 * DB 가 있어야 해서 평소 mvn test 에서는 건너뛴다. 돌릴 때: mvn -o test -Dsujuro.db-test=true -Dtest=NoticeStageFlowDbTest
 *
 * 공고·번호·기관은 모두 가상이다. 번호는 실제 나라장터 번호와 겹치지 않게 9999 로 시작한다.
 */
@SpringBootTest(properties = {
        "g2b.collect.cron=-", "notice.deadline.cron=-", "notice.mail.cron=-" })
@Transactional
@EnabledIfSystemProperty(named = "sujuro.db-test", matches = "true")
class NoticeStageFlowDbTest {

    private static final String PLAN_NO = "R26DD99990001";
    private static final String SPEC_NO = "R26BD99990001";
    private static final String BID_NO = "R26BK99990001";
    private static final String BIZ_NM = "(가상) 단계 전환 시험 사업";
    private static final String INST_NM = "(가상) 시험기관";
    private static final DateTimeFormatter API_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private NoticeCollectService collectService;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private G2bApiClient api;
    @MockBean private NoticeAttachmentService attachments;
    @MockBean private ResultCollectService results;
    @MockBean private NotificationService notifications;

    /** 기본은 나라장터에 아무것도 없다. 각 단계에서 필요한 응답만 바꿔 준다 */
    @BeforeEach
    void emptyG2b() {
        when(api.findPreSpecsRegistered(anyString(), anyString(), anyInt(), anyInt())).thenReturn(empty());
        when(api.findProcessByOrderNo(anyString())).thenReturn(empty());
        when(api.findProcessByPreSpecNo(anyString())).thenReturn(empty());
        when(api.findProcessByBidNo(anyString())).thenReturn(empty());
        when(api.findBidNotice(anyString())).thenReturn(empty());
        when(api.findPreSpec(anyString())).thenReturn(empty());
        when(api.findBidNoticesBetween(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenReturn(empty());
        when(api.findOpeningResult(anyString())).thenReturn(empty());
    }

    @Test
    void 발주계획부터_계약까지_한_단계씩_넘어간다() {
        Long sn = insertOrderPlan();
        assertState(sn, BidCode.STEP_ORDER_PLAN, BidCode.TRACK_WAITING, null, null, BidCode.STTS_NEW);

        // 1. 사전규격이 올라왔다. 입찰공고는 아직이다
        when(api.findPreSpecsRegistered(anyString(), anyString(), anyInt(), anyInt())).thenReturn(of(preSpec()));
        CollectResultDTO r1 = collect();
        assertEquals(1, r1.getToPreSpec());
        assertEquals(0, r1.getToBidNotice());
        assertState(sn, BidCode.STEP_PRE_SPEC, BidCode.TRACK_WAITING, SPEC_NO, null, BidCode.STTS_NEW);
        verify(notifications).sendToRole(eq(Role.SALES), eq(NotificationType.BID_STAGE_CHANGED), eq(sn),
                contains("발주계획 단계에서 사전규격 단계로"));

        // 2. 입찰공고가 났다. 개찰일은 지났지만 개찰 결과는 아직이다
        when(api.findProcessByPreSpecNo(SPEC_NO)).thenReturn(of(item("bidNtceNo", BID_NO, "bidNtceOrd", "000")));
        when(api.findBidNotice(BID_NO)).thenReturn(of(bidNotice()));
        CollectResultDTO r2 = collect();
        assertEquals(1, r2.getToBidNotice());
        assertEquals(0, r2.getOpened());
        assertState(sn, BidCode.STEP_BID_NOTICE, BidCode.TRACK_LINKED, SPEC_NO, BID_NO, BidCode.STTS_NEW);
        verify(notifications).sendToRole(eq(Role.SALES), eq(NotificationType.BID_STAGE_CHANGED), eq(sn),
                contains("사전규격 단계에서 입찰공고 단계로"));

        // 3. 개찰 결과가 올라왔다(개찰완료). 계약은 아직이다
        when(api.findOpeningResult(BID_NO)).thenReturn(of(item("bidNtceNo", BID_NO, "bidNtceOrd", "000",
                "rbidNo", "0", "progrsDivCdNm", "개찰완료")));
        CollectResultDTO r3 = collect();
        assertEquals(1, r3.getOpened());
        assertEquals(0, r3.getFailedBid());
        assertEquals(0, r3.getContracted());
        assertState(sn, BidCode.STEP_OPENING, BidCode.TRACK_LINKED, SPEC_NO, BID_NO, BidCode.STTS_NEW);

        // 4. 계약을 맺었다 (통합공개 API 의 계약정보목록은 문자열로 온다)
        when(api.findProcessByBidNo(BID_NO)).thenReturn(of(item("bidNtceNo", BID_NO,
                "cntrctInfoList", "[1^R26TE99990001^(가상) 계약^2026-11-03]")));
        CollectResultDTO r4 = collect();
        assertEquals(1, r4.getContracted());
        assertState(sn, BidCode.STEP_CONTRACT, BidCode.TRACK_LINKED, SPEC_NO, BID_NO, BidCode.STTS_NEW);

        // 5. 한 번 더 돌려도 더 넘어가거나 이력이 늘지 않는다
        collect();
        assertState(sn, BidCode.STEP_CONTRACT, BidCode.TRACK_LINKED, SPEC_NO, BID_NO, BidCode.STTS_NEW);

        // 단계 이력(공고수집이력)은 단계마다 한 줄씩, 차례대로 남는다.
        // 등록할 때 남는 발주계획 줄은 없다. 가상 공고를 등록 화면 대신 SQL 로 바로 넣었기 때문이다
        assertEquals(List.of("PP020", "PP030", "PP040", "PP050"), jdbc.queryForList(
                "SELECT TRIM(PBANC_STEP_CD) FROM TB_PBANC_CLCT_HSTRY WHERE PBANC_SN = ? ORDER BY CLCT_SEQ", String.class, sn));
        // 수집로그도 전환마다 남는다: 사전규격 전환·입찰공고 전환·개찰·계약 전환
        assertEquals(List.of("CL060", "CL020", "CL050", "CL070"), jdbc.queryForList(
                "SELECT TRIM(CLCT_RSLT_CD) FROM TB_CLCT_LOG WHERE PBANC_SN = ? ORDER BY CLCT_LOG_SN", String.class, sn));

        // 사전규격·입찰공고에서 받은 값이 공고에 들어갔다
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT DMND_INST_NM, ALTMNT_BGT_AMT, PBANC_TYPE_CD FROM TB_BID_PBANC WHERE PBANC_SN = ?", sn);
        assertEquals(INST_NM + " 수요부서", row.get("DMND_INST_NM"));
        assertEquals(550000000L, ((Number) row.get("ALTMNT_BGT_AMT")).longValue());
        assertEquals(BidCode.TYPE_NORMAL, ((String) row.get("PBANC_TYPE_CD")).trim());
    }

    @Test
    void 유찰이면_개찰에서_멈추고_계약을_묻지_않는다() {
        Long sn = insertOrderPlan();
        when(api.findPreSpecsRegistered(anyString(), anyString(), anyInt(), anyInt())).thenReturn(of(preSpec()));
        when(api.findProcessByPreSpecNo(SPEC_NO)).thenReturn(of(item("bidNtceNo", BID_NO, "bidNtceOrd", "000")));
        when(api.findBidNotice(BID_NO)).thenReturn(of(bidNotice()));
        when(api.findOpeningResult(BID_NO)).thenReturn(of(item("bidNtceNo", BID_NO, "bidNtceOrd", "000",
                "rbidNo", "0", "progrsDivCdNm", "유찰")));
        when(api.findProcessByBidNo(BID_NO)).thenReturn(of(item("bidNtceNo", BID_NO, "cntrctInfoList", "[1^가상]")));

        // 한 번 수집에서 사전규격 → 입찰공고 → 개찰까지 이어진다 (앞 단계를 먼저 돌리기 때문)
        CollectResultDTO r = collect();
        assertEquals(1, r.getToPreSpec());
        assertEquals(1, r.getToBidNotice());
        assertEquals(1, r.getFailedBid());
        assertEquals(0, r.getContracted());
        assertState(sn, BidCode.STEP_OPENING, BidCode.TRACK_LINKED, SPEC_NO, BID_NO, BidCode.STTS_FAILED_BID);

        // 다음 수집에서도 유찰 공고는 계약 단계로 가지 않는다
        assertEquals(0, collect().getContracted());
        assertState(sn, BidCode.STEP_OPENING, BidCode.TRACK_LINKED, SPEC_NO, BID_NO, BidCode.STTS_FAILED_BID);
    }

    @Test
    void 사전규격과_발주계획의_사업이_다르면_잇지_않는다() {
        Long sn = insertOrderPlan();
        ObjectNode other = preSpec();
        other.put("prdctClsfcNoNm", "(가상) 전혀 다른 사업");
        when(api.findPreSpecsRegistered(anyString(), anyString(), anyInt(), anyInt())).thenReturn(of(other));

        assertEquals(0, collect().getToPreSpec());
        assertState(sn, BidCode.STEP_ORDER_PLAN, BidCode.TRACK_WAITING, null, null, BidCode.STTS_NEW);
    }

    // ---- 가상 데이터 ----

    /** 영업부가 발주계획번호로 등록해 둔 가상 공고 */
    private Long insertOrderPlan() {
        String salesId = jdbc.queryForObject(
                "SELECT USER_ID FROM TB_USER WHERE AUTHRT_CD = ? ORDER BY USER_ID FETCH FIRST 1 ROWS ONLY", String.class, Role.CD_SALES);
        jdbc.update("INSERT INTO TB_BID_PBANC (ORDER_PLAN_NO, PBANC_STEP_CD, TRCK_STTS_CD, PBANC_NM, ORDER_INST_NM, "
                + "ALTMNT_BGT_AMT, PBANC_STTS_CD, RGTR_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                PLAN_NO, BidCode.STEP_ORDER_PLAN, BidCode.TRACK_WAITING, BIZ_NM, INST_NM, 500000000L, BidCode.STTS_NEW, salesId);
        return jdbc.queryForObject("SELECT PBANC_SN FROM TB_BID_PBANC WHERE ORDER_PLAN_NO = ?", Long.class, PLAN_NO);
    }

    /** 같은 기관·같은 사업명의 사전규격 (예산은 발주계획과 다르게 바뀌어 왔다) */
    private static ObjectNode preSpec() {
        return item("bfSpecRgstNo", SPEC_NO, "prdctClsfcNoNm", BIZ_NM, "rlDminsttNm", INST_NM,
                "asignBdgtAmt", "520000000", "swBizObjYn", "Y",
                "opninRgstClseDt", LocalDateTime.now().minusDays(10).format(API_DT));
    }

    /** 입찰공고 0차. 마감·개찰일은 이미 지났다(개찰 결과를 묻게) */
    private static ObjectNode bidNotice() {
        return item("bidNtceNo", BID_NO, "bidNtceOrd", "000", "ntceKindNm", "등록공고", "bidNtceNm", BIZ_NM,
                "ntceInsttNm", INST_NM, "dminsttNm", INST_NM + " 수요부서", "asignBdgtAmt", "550000000",
                "bidClseDt", LocalDateTime.now().minusDays(2).format(API_DT),
                "opengDt", LocalDateTime.now().minusDays(1).format(API_DT),
                "bfSpecRgstNo", SPEC_NO, "orderPlanUntyNo", PLAN_NO);
    }

    private void assertState(Long sn, String step, String trck, String specNo, String bidNo, String stts) {
        Map<String, Object> row = jdbc.queryForMap("SELECT TRIM(PBANC_STEP_CD) STEP, TRIM(TRCK_STTS_CD) TRCK, BF_SPEC_RGST_NO, "
                + "BID_PBANC_NO, TRIM(PBANC_STTS_CD) STTS FROM TB_BID_PBANC WHERE PBANC_SN = ?", sn);
        assertEquals(step, row.get("STEP"), "단계");
        assertEquals(trck, row.get("TRCK"), "추적상태");
        if (specNo == null) {
            assertNull(row.get("BF_SPEC_RGST_NO"));
        } else {
            assertEquals(specNo, row.get("BF_SPEC_RGST_NO"));
        }
        if (bidNo == null) {
            assertNull(row.get("BID_PBANC_NO"));
        } else {
            assertEquals(bidNo, row.get("BID_PBANC_NO"));
        }
        assertEquals(stts, row.get("STTS"), "공고상태");
    }

    private CollectResultDTO collect() {
        return collectService.collect(BidCode.EXCN_AUTO, null);
    }

    private static ObjectNode item(String... kv) {
        ObjectNode n = JSON.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) {
            n.put(kv[i], kv[i + 1]);
        }
        return n;
    }

    private static G2bResponse of(ObjectNode... items) {
        ArrayNode arr = JSON.createArrayNode();
        for (ObjectNode n : items) {
            arr.add(n);
        }
        return new G2bResponse(arr, arr.toString(), items.length);
    }

    private static G2bResponse empty() {
        return of();
    }
}
