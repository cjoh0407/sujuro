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
import com.ojj.sujuro.bid.dto.BidLinkDTO;
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

    /**
     * 평일 오후 5시 자동 수집 (g2b.collect.cron).
     * 사전규격 전환을 먼저 한다. 방금 사전규격으로 올라간 공고도 같은 수집에서 입찰공고까지 이어질 수 있다.
     */
    @Scheduled(cron = "${g2b.collect.cron}")
    public void collectAuto() {
        int toPreSpec = collectPreSpec(BidCode.EXCN_AUTO, null);
        int toBid = collectBidNotice(BidCode.EXCN_AUTO, null);
        log.info("자동 수집 완료: 사전규격 전환 {}건, 입찰공고 전환 {}건", toPreSpec, toBid);
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
     * 입찰공고를 기다리는 공고(발주계획·사전규격)에 입찰공고가 났는지 보고, 났으면 입찰공고 단계로 올린다.
     *
     * 사전규격 전환과 달리 번호로 바로 이어진다. 통합공개 API 에 사전규격번호(없으면 발주계획번호)로 물으면
     * 입찰공고가 난 사업만 행이 온다(안 났으면 0건). 그 행의 입찰공고번호로 입찰공고 내용을 받아 채운다.
     *
     * API 가 응답하지 않으면 그 자리에서 멈춘다. 나머지 공고도 같은 API 를 불러야 하므로
     * 공고마다 실패를 쌓지 않고 실패 한 줄만 남긴다.
     */
    @Override
    public int collectBidNotice(String excnSeCd, String exctrId) {
        List<TrackingNoticeDTO> waiting = bidNoticeMapper.findBidWaitingNotices(BidCode.TRACK_WAITING);
        if (waiting.isEmpty()) {
            writeLog(null, excnSeCd, exctrId, BidCode.CLCT_NO_CHANGE, null);
            return 0;
        }

        int linked = 0;
        for (TrackingNoticeDTO target : waiting) {
            G2bResponse process;
            G2bResponse bidRes;
            String bidNo;
            try {
                process = findProcess(target);
                bidNo = earliestBidNo(process);
                if (bidNo == null) {
                    continue; // 아직 입찰공고가 나지 않았다
                }
                if (bidNoticeMapper.countByBidPbancNo(bidNo) > 0) {
                    // 같은 입찰공고를 영업부가 따로 등록해 둔 경우. 이으면 UNIQUE 에 걸린다.
                    writeLog(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_NO_CHANGE,
                            "입찰공고 " + bidNo + " 이 이미 따로 등록돼 있어 잇지 않았습니다. 같은 사업인지 확인이 필요합니다.");
                    continue;
                }
                bidRes = g2bApiClient.findBidNotice(bidNo);
            } catch (BusinessException e) {
                log.warn("입찰공고 전환 확인 실패: {}", e.getMessage());
                writeLog(null, excnSeCd, exctrId, BidCode.CLCT_FAILED, e.getMessage());
                return linked;
            }

            // 통합공개에는 났는데 입찰공고 서비스에는 아직 안 올라온 경우가 있을 수 있다. 다음 수집에서 다시 본다.
            JsonNode item = bidRes.latestOrd() != null ? bidRes.latestOrd() : bidRes.first();
            if (item == null) {
                continue;
            }
            if (linkBid(target, bidNo, item, excnSeCd, exctrId)) {
                linked++;
            }
        }

        writeLog(null, excnSeCd, exctrId,
                linked > 0 ? BidCode.CLCT_TO_BID_NOTICE : BidCode.CLCT_NO_CHANGE, null);
        return linked;
    }

    /** 사전규격번호가 있으면 그것으로, 없으면 발주계획번호로 통합공개 API 에 묻는다. */
    private G2bResponse findProcess(TrackingNoticeDTO target) {
        if (target.getBfSpecRgstNo() != null) {
            return g2bApiClient.findProcessByPreSpecNo(target.getBfSpecRgstNo());
        }
        return g2bApiClient.findProcessByOrderNo(target.getOrderPlanNo());
    }

    /**
     * 통합공개 응답에서 입찰공고번호를 고른다. 차수마다 한 행씩 와서 같은 번호가 여러 번 나온다.
     * 서로 다른 번호가 섞여 있으면 가장 작은(먼저 난) 번호를 고른다. 뒤의 번호는 유찰 뒤의 재공고일 수 있는데,
     * 재공고는 기존 사업을 잇지 않고 새 사업건으로 등록하기로 했다 (FR-024-1).
     */
    private static String earliestBidNo(G2bResponse process) {
        return process.all().stream()
                .map(row -> G2bResponse.textOf(row, "bidNtceNo"))
                .filter(no -> no != null)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    /** 발주계획·사전규격 행을 입찰공고 단계로 올리고 수집이력·수집로그를 남긴다. 한 공고씩 따로 커밋한다. */
    private boolean linkBid(TrackingNoticeDTO target, String bidNo, JsonNode item,
                            String excnSeCd, String exctrId) {
        try {
            Boolean done = tx.execute(status -> {
                BidLinkDTO param = new BidLinkDTO();
                param.setPbancSn(target.getPbancSn());
                param.setBidPbancNo(bidNo);
                param.setBidPbancCycl(G2bResponse.intOf(item, "bidNtceOrd", 0));
                param.setBfSpecRgstNo(fillableSpecNo(target, item));
                param.setOrderPlanNo(fillableOrderPlanNo(target, item));
                param.setPbancTypeCd(BidCode.pbancTypeCdOf(G2bResponse.textOf(item, "ntceKindNm")));
                param.setPbancNm(G2bResponse.textOf(item, "bidNtceNm"));
                param.setOrderInstNm(G2bResponse.textOf(item, "ntceInsttNm"));
                param.setDmndInstNm(G2bResponse.textOf(item, "dminsttNm"));
                param.setAltmntBgtAmt(G2bResponse.amountOf(item, "asignBdgtAmt"));
                param.setBidDdlnDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(item, "bidClseDt")));
                param.setOpengDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(item, "opengDt")));
                param.setEvlMthdNm(G2bResponse.textOf(item, "sucsfbidMthdNm"));
                param.setPbancDtlUrl(G2bResponse.textOf(item, "bidNtceDtlUrl"));

                // 다른 수집이 먼저 이었으면 0행이다. 그때는 아무것도 남기지 않는다.
                if (bidNoticeMapper.updateToBidNotice(param) == 0) {
                    return false;
                }

                CollectHistoryDTO history = new CollectHistoryDTO();
                history.setPbancSn(target.getPbancSn());
                history.setClctSeq(bidNoticeMapper.nextCollectSeq(target.getPbancSn()));
                history.setPbancStepCd(BidCode.STEP_BID_NOTICE);
                history.setPbancNo(bidNo);
                history.setBidPbancCycl(param.getBidPbancCycl());
                history.setPbancTypeCd(param.getPbancTypeCd());
                history.setAltmntBgtAmt(param.getAltmntBgtAmt());
                history.setBidDdlnDt(param.getBidDdlnDt());
                history.setOpengDt(param.getOpengDt());
                history.setApiRspnsCn(item.toString());
                bidNoticeMapper.insertCollectHistory(history);

                bidNoticeMapper.insertCollectLog(
                        new CollectLogDTO(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_TO_BID_NOTICE, null));
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                log.info("입찰공고 전환: {} -> 입찰공고 {}", noOf(target), bidNo);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("입찰공고 전환 실패: {} -> 입찰공고 {}", noOf(target), bidNo, e);
            writeLog(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_FAILED,
                    "입찰공고 " + bidNo + " 으로 전환하지 못했습니다: " + e.getMessage());
            return false;
        }
    }

    /**
     * 사전규격번호가 비어 있는 행(발주계획에서 사전규격을 건너뛰었거나 매칭이 애매했던 경우)에 채울 번호.
     * 입찰공고 응답의 bfSpecRgstNo 에서 꺼낸다. 그 사전규격이 따로 등록돼 있으면 UNIQUE 에 걸리므로 채우지 않는다.
     */
    private String fillableSpecNo(TrackingNoticeDTO target, JsonNode bidItem) {
        if (target.getBfSpecRgstNo() != null) {
            return null;
        }
        String specNo = G2bResponse.textOf(bidItem, "bfSpecRgstNo");
        return specNo != null && bidNoticeMapper.countByBfSpecRgstNo(specNo) == 0 ? specNo : null;
    }

    /**
     * 발주계획번호가 비어 있는 행(사전규격으로 등록한 경우)에 채울 번호. 입찰공고 응답의 orderPlanUntyNo 에서
     * 꺼낸다(통합공개 API 의 orderPlanNo 와 같은 값). 따로 등록돼 있으면 채우지 않는다.
     */
    private String fillableOrderPlanNo(TrackingNoticeDTO target, JsonNode bidItem) {
        if (target.getOrderPlanNo() != null) {
            return null;
        }
        String planNo = G2bResponse.textOf(bidItem, "orderPlanUntyNo");
        return planNo != null && bidNoticeMapper.countByOrderPlanNo(planNo) == 0 ? planNo : null;
    }

    /** 로그에 쓸 번호. 사전규격번호가 있으면 그것, 없으면 발주계획번호 */
    private static String noOf(TrackingNoticeDTO target) {
        return target.getBfSpecRgstNo() != null ? target.getBfSpecRgstNo() : target.getOrderPlanNo();
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
