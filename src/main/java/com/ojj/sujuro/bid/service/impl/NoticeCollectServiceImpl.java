package com.ojj.sujuro.bid.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.bid.BidCode;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.CollectLogDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterDTO;
import com.ojj.sujuro.bid.dto.PreSpecLinkDTO;
import com.ojj.sujuro.bid.dto.TrackingNoticeDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeCollectService;
import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 공고 수집 (FR-025, FR-025-1).
 *
 * 발주계획 → 사전규격 전환은 번호로 이을 수 없다. 두 API 어디에도 상대 번호가 없기 때문이다.
 * 그래서 기간 안에 새로 올라온 사전규격을 모두 받아, 사전규격을 기다리는 발주계획과 값으로 맞춘다.
 *
 *   키   실수요기관명(rlDminsttNm) = 발주계획 발주기관명  +  정규화한 사업명이 같다
 *   실측 정답 61쌍에서 재현율 100%. 같은 키로 겹치는 발주계획은 4.7% 였다. (2026-10-26)
 *
 * 겹치는 경우(같은 기관이 같은 사업명·같은 예산으로 권역을 쪼개 발주한 경우 등)는 API 가
 * 구별할 값을 주지 않아 자동으로 가릴 수 없다. 그때는 잇지 않고 수집로그에 남겨 사람이 보게 한다.
 *
 * 공고 하나를 잇는 일(update + 수집이력 + 수집로그)은 공고마다 따로 커밋한다.
 * 한 건이 실패해도 앞서 이은 공고까지 되돌리지 않기 위해서다.
 */
@Service
public class NoticeCollectServiceImpl implements NoticeCollectService {

    private static final Logger log = LoggerFactory.getLogger(NoticeCollectServiceImpl.class);

    /** 사전규격 목록 조회 기간 형식. 발주계획 서비스(8자리)와 달리 12자리다. */
    private static final DateTimeFormatter API_DT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 사전규격 목록을 한 번에 받는 건수 */
    private static final int PAGE_SIZE = 100;

    /** 한 번 수집에서 넘길 최대 페이지. API 가 이상한 totalCount 를 줄 때 끝없이 돌지 않게 막는다. */
    private static final int MAX_PAGES = 50;

    private final BidNoticeMapper bidNoticeMapper;
    private final G2bApiClient g2bApiClient;
    private final TransactionTemplate tx;
    private final int lookbackDays;

    public NoticeCollectServiceImpl(BidNoticeMapper bidNoticeMapper,
                                    G2bApiClient g2bApiClient,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${g2b.collect.pre-spec-lookback-days}") int lookbackDays) {
        this.bidNoticeMapper = bidNoticeMapper;
        this.g2bApiClient = g2bApiClient;
        this.tx = new TransactionTemplate(transactionManager);
        this.lookbackDays = lookbackDays;
    }

    /** 평일 오후 5시 자동 수집 (g2b.collect.cron) */
    @Scheduled(cron = "${g2b.collect.cron}")
    public void collectAuto() {
        int linked = collectPreSpec(BidCode.EXCN_AUTO, null);
        log.info("자동 수집 완료: 사전규격 전환 {}건", linked);
    }

    @Override
    public int collectPreSpec(String excnSeCd, String exctrId) {
        List<TrackingNoticeDTO> waiting = new ArrayList<>(
                bidNoticeMapper.findTrackingNotices(BidCode.STEP_ORDER_PLAN, BidCode.TRACK_WAITING));
        if (waiting.isEmpty()) {
            // 기다리는 발주계획이 없으면 API 를 부를 이유가 없다.
            writeLog(null, excnSeCd, exctrId, BidCode.CLCT_NO_CHANGE, null);
            return 0;
        }

        List<JsonNode> specs;
        try {
            specs = fetchRecentPreSpecs();
        } catch (BusinessException e) {
            log.warn("사전규격 목록 수집 실패: {}", e.getMessage());
            writeLog(null, excnSeCd, exctrId, BidCode.CLCT_FAILED, e.getMessage());
            return 0;
        }

        int linked = 0;
        for (JsonNode spec : specs) {
            TrackingNoticeDTO target = pickOne(waiting, spec, excnSeCd, exctrId);
            if (target == null) {
                continue;
            }
            String specNo = G2bResponse.textOf(spec, "bfSpecRgstNo");
            if (bidNoticeMapper.countByBfSpecRgstNo(specNo) > 0) {
                // 같은 사업을 영업부가 사전규격번호로도 따로 등록해 둔 경우. 이으면 UNIQUE 에 걸린다.
                writeLog(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_NO_CHANGE,
                        "사전규격 " + specNo + " 이 이미 따로 등록돼 있어 잇지 않았습니다. 같은 사업인지 확인이 필요합니다.");
                waiting.remove(target);
                continue;
            }
            if (link(target, spec, excnSeCd, exctrId)) {
                linked++;
            }
            // 이은 발주계획은 다음 사전규격의 후보에서 뺀다.
            waiting.remove(target);
        }

        writeLog(null, excnSeCd, exctrId,
                linked > 0 ? BidCode.CLCT_TO_PRE_SPEC : BidCode.CLCT_NO_CHANGE, null);
        return linked;
    }

    /**
     * 이 사전규격과 같은 사업인 발주계획을 하나 고른다. 하나로 정해지지 않으면 null.
     * 후보가 여럿이면 예산이 같은 것으로 좁힌다. 예산은 실측 92% 만 같아서 처음부터 조건으로 걸지 않는다.
     */
    private TrackingNoticeDTO pickOne(List<TrackingNoticeDTO> waiting, JsonNode spec,
                                      String excnSeCd, String exctrId) {
        String specInst = G2bResponse.textOf(spec, "rlDminsttNm");
        String specNm = G2bResponse.textOf(spec, "prdctClsfcNoNm");

        List<TrackingNoticeDTO> candidates = waiting.stream()
                .filter(w -> BidCode.sameBizCase(w.getOrderInstNm(), w.getPbancNm(), specInst, specNm))
                .collect(Collectors.toList());
        if (candidates.size() <= 1) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        BigDecimal specAmt = G2bResponse.amountOf(spec, "asignBdgtAmt");
        List<TrackingNoticeDTO> sameAmt = candidates.stream()
                .filter(w -> specAmt != null && w.getAltmntBgtAmt() != null
                        && specAmt.compareTo(w.getAltmntBgtAmt()) == 0)
                .collect(Collectors.toList());
        if (sameAmt.size() == 1) {
            return sameAmt.get(0);
        }

        String specNo = G2bResponse.textOf(spec, "bfSpecRgstNo");
        String others = candidates.stream().map(TrackingNoticeDTO::getOrderPlanNo)
                .collect(Collectors.joining(", "));
        for (TrackingNoticeDTO c : candidates) {
            writeLog(c.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_NO_CHANGE,
                    "사전규격 " + specNo + " 과 같은 사업으로 보이는 발주계획이 " + candidates.size()
                            + "건(" + others + ")이라 자동으로 잇지 않았습니다. 영업부 확인이 필요합니다.");
        }
        return null;
    }

    /** 발주계획 행을 사전규격 단계로 올리고 수집이력·수집로그를 남긴다. 한 공고씩 따로 커밋한다. */
    private boolean link(TrackingNoticeDTO target, JsonNode spec, String excnSeCd, String exctrId) {
        String specNo = G2bResponse.textOf(spec, "bfSpecRgstNo");
        try {
            Boolean done = tx.execute(status -> {
                PreSpecLinkDTO param = new PreSpecLinkDTO();
                param.setPbancSn(target.getPbancSn());
                param.setBfSpecRgstNo(specNo);
                param.setPbancNm(G2bResponse.textOf(spec, "prdctClsfcNoNm"));
                param.setDmndInstNm(G2bResponse.textOf(spec, "rlDminsttNm"));
                param.setSwBizYn(yn(G2bResponse.textOf(spec, "swBizObjYn")));
                param.setAltmntBgtAmt(G2bResponse.amountOf(spec, "asignBdgtAmt"));
                param.setOpnnDdlnDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(spec, "opninRgstClseDt")));

                // 다른 수집이 먼저 이었으면 0행이다. 그때는 아무것도 남기지 않는다.
                if (bidNoticeMapper.updateToPreSpec(param) == 0) {
                    return false;
                }

                CollectHistoryDTO history = new CollectHistoryDTO();
                history.setPbancSn(target.getPbancSn());
                history.setClctSeq(bidNoticeMapper.nextCollectSeq(target.getPbancSn()));
                history.setPbancStepCd(BidCode.STEP_PRE_SPEC);
                history.setPbancNo(specNo);
                history.setAltmntBgtAmt(param.getAltmntBgtAmt());
                // 목록 응답 전체가 아니라 이 사전규격 한 건만 남긴다.
                history.setApiRspnsCn(spec.toString());
                bidNoticeMapper.insertCollectHistory(history);

                bidNoticeMapper.insertCollectLog(
                        new CollectLogDTO(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_TO_PRE_SPEC, null));
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                log.info("사전규격 전환: 발주계획 {} -> 사전규격 {}", target.getOrderPlanNo(), specNo);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("사전규격 전환 실패: 발주계획 {} -> 사전규격 {}", target.getOrderPlanNo(), specNo, e);
            writeLog(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_FAILED,
                    "사전규격 " + specNo + " 으로 전환하지 못했습니다: " + e.getMessage());
            return false;
        }
    }

    /**
     * 최근 기간에 등록된 사전규격을 모두 받는다.
     * 기간은 금요일 수집 뒤 월요일까지 비는 사흘을 덮도록 잡는다. 겹쳐 받아도 같은 전이는 두 번 일어나지 않는다.
     */
    private List<JsonNode> fetchRecentPreSpecs() {
        LocalDateTime now = LocalDateTime.now();
        String bgn = now.minusDays(lookbackDays).format(API_DT);
        String end = now.format(API_DT);

        List<JsonNode> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            G2bResponse res = g2bApiClient.findPreSpecsRegistered(bgn, end, PAGE_SIZE, page);
            List<JsonNode> items = res.all();
            all.addAll(items);
            if (items.isEmpty() || all.size() >= res.getTotalCount()) {
                break;
            }
        }
        return all;
    }

    private void writeLog(Long pbancSn, String excnSeCd, String exctrId, String rsltCd, String msg) {
        bidNoticeMapper.insertCollectLog(new CollectLogDTO(pbancSn, excnSeCd, exctrId, rsltCd, cut(msg)));
    }

    /** 오류메시지 컬럼이 4000자다. */
    private static String cut(String msg) {
        return msg == null || msg.length() <= 4000 ? msg : msg.substring(0, 4000);
    }

    /** Y/N 이 아닌 값이 오면 넣지 않는다. CHECK 제약에 걸리기 때문이다. */
    private static String yn(String value) {
        return "Y".equals(value) || "N".equals(value) ? value : null;
    }
}
