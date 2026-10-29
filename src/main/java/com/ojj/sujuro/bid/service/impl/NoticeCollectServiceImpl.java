package com.ojj.sujuro.bid.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.dto.AtchFileDTO;
import com.ojj.sujuro.bid.dto.BidLinkDTO;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.CollectLogDTO;
import com.ojj.sujuro.bid.dto.CollectResultDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterDTO;
import com.ojj.sujuro.bid.dto.PreSpecLinkDTO;
import com.ojj.sujuro.bid.dto.TrackingNoticeDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeAttachmentService;
import com.ojj.sujuro.bid.service.NoticeCollectService;
import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 공고 수집 (FR-025, FR-025-1, FR-028). 단계별 확인 순서는 인터페이스(NoticeCollectService)에 있다.
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
 * 첨부처럼 나라장터를 여러 번 부르는 일은 트랜잭션을 열기 전에 끝내 둔다.
 */
@Service
public class NoticeCollectServiceImpl implements NoticeCollectService {

    private static final Logger log = LoggerFactory.getLogger(NoticeCollectServiceImpl.class);

    /** 목록 조회 기간 형식. 발주계획 서비스(8자리)와 달리 사전규격·입찰공고는 12자리다. */
    private static final DateTimeFormatter API_DT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 목록을 한 번에 받는 건수 */
    private static final int PAGE_SIZE = 100;

    /** 한 번 수집에서 넘길 최대 페이지. API 가 이상한 totalCount 를 줄 때 끝없이 돌지 않게 막는다. */
    private static final int MAX_PAGES = 50;

    /** 개찰결과 API 의 진행구분명 (progrsDivCdNm). 실측으로 이 두 값만 왔다. (2026-10-28) */
    private static final String OPENG_DONE = "개찰완료";
    private static final String OPENG_FAILED = "유찰";

    private final BidNoticeMapper bidNoticeMapper;
    private final G2bApiClient g2bApiClient;
    private final NoticeAttachmentService attachmentService;
    private final TransactionTemplate tx;
    private final int lookbackDays;
    private final int manualCooldownMinutes;

    /** 수집은 한 번에 하나만 돈다. 오후 5시 자동 수집 중에 [지금 수집] 을 눌러도 겹치지 않게 한다. */
    private final ReentrantLock running = new ReentrantLock();

    public NoticeCollectServiceImpl(BidNoticeMapper bidNoticeMapper,
                                    G2bApiClient g2bApiClient,
                                    NoticeAttachmentService attachmentService,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${g2b.collect.lookback-days}") int lookbackDays,
                                    @Value("${g2b.collect.manual-cooldown-minutes}") int manualCooldownMinutes) {
        this.bidNoticeMapper = bidNoticeMapper;
        this.g2bApiClient = g2bApiClient;
        this.attachmentService = attachmentService;
        this.tx = new TransactionTemplate(transactionManager);
        this.lookbackDays = lookbackDays;
        this.manualCooldownMinutes = manualCooldownMinutes;
    }

    /** 평일 오후 5시 자동 수집 (g2b.collect.cron) */
    @Scheduled(cron = "${g2b.collect.cron}")
    public void collectAuto() {
        try {
            log.info("자동 수집 완료: {}", collect(BidCode.EXCN_AUTO, null).getSummary());
        } catch (BusinessException e) {
            log.warn("자동 수집을 건너뜁니다: {}", e.getMessage());
        }
    }

    @Override
    public CollectResultDTO collectNow(String exctrId) {
        if (bidNoticeMapper.countRecentRuns(BidCode.EXCN_MANUAL, manualCooldownMinutes) > 0) {
            throw new BusinessException("방금 지금 수집을 했습니다. " + manualCooldownMinutes + "분 뒤에 다시 누르세요.");
        }
        return collect(BidCode.EXCN_MANUAL, exctrId);
    }

    /**
     * 사전규격 전환을 먼저 한다. 방금 사전규격으로 올라간 공고도 같은 수집에서 입찰공고까지 이어질 수 있다.
     * 같은 이유로 개찰 → 계약도 앞 단계 뒤에 둔다.
     */
    @Override
    public CollectResultDTO collect(String excnSeCd, String exctrId) {
        if (!running.tryLock()) {
            throw new BusinessException("수집이 이미 진행 중입니다. 끝난 뒤 공고 목록에서 확인하세요.");
        }
        try {
            CollectResultDTO result = new CollectResultDTO();
            result.setToPreSpec(collectPreSpec(excnSeCd, exctrId, result));
            result.setToBidNotice(collectBidNotice(excnSeCd, exctrId, result));
            collectCorrections(excnSeCd, exctrId, result);
            collectOpenings(excnSeCd, exctrId, result);
            collectContracts(excnSeCd, exctrId, result);
            fillMissingFiles(excnSeCd, exctrId, result);
            return result;
        } finally {
            running.unlock();
        }
    }

    /** 1. 발주계획 → 사전규격 */
    private int collectPreSpec(String excnSeCd, String exctrId, CollectResultDTO result) {
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
            result.addFailure("사전규격 전환", e.getMessage());
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
     * 2. 입찰공고를 기다리는 공고(발주계획·사전규격)에 입찰공고가 났는지 보고, 났으면 입찰공고 단계로 올린다.
     *
     * 사전규격 전환과 달리 번호로 바로 이어진다. 통합공개 API 에 사전규격번호(없으면 발주계획번호)로 물으면
     * 입찰공고가 난 사업만 행이 온다(안 났으면 0건). 그 행의 입찰공고번호로 입찰공고 내용을 받아 채운다.
     *
     * API 가 응답하지 않으면 그 자리에서 멈춘다. 나머지 공고도 같은 API 를 불러야 하므로
     * 공고마다 실패를 쌓지 않고 실패 한 줄만 남긴다.
     */
    private int collectBidNotice(String excnSeCd, String exctrId, CollectResultDTO result) {
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
                result.addFailure("입찰공고 전환", e.getMessage());
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
        // 첨부는 나라장터를 여러 번 부르므로 트랜잭션을 열기 전에 받아 둔다. 실패해도 빈 목록이다.
        List<AtchFileDTO> files = attachmentService.readBidFiles(item);
        try {
            Boolean done = tx.execute(status -> {
                BidLinkDTO param = bidValuesOf(item);
                param.setPbancSn(target.getPbancSn());
                param.setBidPbancNo(bidNo);
                param.setBfSpecRgstNo(fillableSpecNo(target, item));
                param.setOrderPlanNo(fillableOrderPlanNo(target, item));

                // 다른 수집이 먼저 이었으면 0행이다. 그때는 아무것도 남기지 않는다.
                if (bidNoticeMapper.updateToBidNotice(param) == 0) {
                    return false;
                }

                insertHistory(target.getPbancSn(), BidCode.STEP_BID_NOTICE, bidNo, param, item);
                attachmentService.save(target.getPbancSn(), null, files);

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
        // 규격서 파일명·크기는 파일마다 나라장터를 불러 읽으므로 트랜잭션을 열기 전에 받아 둔다.
        List<AtchFileDTO> files = attachmentService.readPreSpecFiles(spec);
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
                attachmentService.save(target.getPbancSn(), null, files);

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
     * 3. 입찰공고 정정·취소 (FR-028). 입찰공고 단계 공고의 차수나 값(마감일·예산·공고명 등)이 바뀌었으면 고친다.
     *
     * 공고마다 번호로 묻지 않고, 기간 안의 입찰공고 목록을 받아 우리 공고와 맞춘다. 등록 공고가 늘어도 호출 수가 같다.
     * 정정·취소는 새 차수로 '등록'돼 등록일시 목록에 오고, 차수 없이 값만 고친 공고는 변경일시 목록에 온다.
     * 같은 공고가 여러 번 오면 차수가 가장 큰 것을 쓴다.
     */
    private void collectCorrections(String excnSeCd, String exctrId, CollectResultDTO result) {
        List<TrackingNoticeDTO> targets = bidNoticeMapper.findBidStageNotices(BidCode.STEP_BID_NOTICE);
        if (targets.isEmpty()) {
            return;
        }
        Map<String, JsonNode> latest;
        try {
            latest = latestBidItems();
        } catch (BusinessException e) {
            log.warn("입찰공고 목록 수집 실패: {}", e.getMessage());
            writeLog(null, excnSeCd, exctrId, BidCode.CLCT_FAILED, e.getMessage());
            result.addFailure("정정 확인", e.getMessage());
            return;
        }

        int changed = 0;
        for (TrackingNoticeDTO target : targets) {
            JsonNode item = latest.get(target.getBidPbancNo());
            if (item == null || !isChanged(target, item)) {
                continue;
            }
            BidLinkDTO values = bidValuesOf(item);
            if (applyCorrection(target, item, values, excnSeCd, exctrId)) {
                changed++;
                if (BidCode.is(values.getPbancTypeCd(), BidCode.TYPE_CANCELED)) {
                    result.setCanceled(result.getCanceled() + 1);
                } else {
                    result.setCorrected(result.getCorrected() + 1);
                }
            }
        }
        writeLog(null, excnSeCd, exctrId, changed > 0 ? BidCode.CLCT_CORRECTED : BidCode.CLCT_NO_CHANGE, null);
    }

    /**
     * 나라장터 값이 저장된 값과 다른지. 차수가 올라갔으면 정정이다. 차수가 같으면 값을 하나씩 비교한다.
     * API 가 빈 값을 준 항목은 비교하지 않는다. 우리는 빈 값으로 덮지 않으므로(NVL), 비교하면 매일 바뀐 것으로 보인다.
     */
    private static boolean isChanged(TrackingNoticeDTO target, JsonNode item) {
        int cycl = G2bResponse.intOf(item, "bidNtceOrd", 0);
        int stored = target.getBidPbancCycl() == null ? 0 : target.getBidPbancCycl();
        if (cycl != stored) {
            return cycl > stored; // 목록에 옛 차수만 있으면(기간 밖에서 이미 정정 반영) 건너뛴다
        }
        BidLinkDTO now = bidValuesOf(item);
        return differs(target.getPbancTypeCd(), now.getPbancTypeCd())
            || differs(target.getPbancNm(), now.getPbancNm())
            || differs(target.getBidDdlnDt(), now.getBidDdlnDt())
            || differs(target.getOpengDt(), now.getOpengDt())
            || (now.getAltmntBgtAmt() != null
                && (target.getAltmntBgtAmt() == null || now.getAltmntBgtAmt().compareTo(target.getAltmntBgtAmt()) != 0));
    }

    private static boolean differs(Object stored, Object fromApi) {
        return fromApi != null && !fromApi.equals(stored instanceof String ? ((String) stored).trim() : stored);
    }

    /** 정정 한 건을 반영한다. 차수가 올라갔으면 새 차수의 첨부를 새 버전으로 받아 둔다. 한 공고씩 따로 커밋한다. */
    private boolean applyCorrection(TrackingNoticeDTO target, JsonNode item, BidLinkDTO values,
                                    String excnSeCd, String exctrId) {
        int stored = target.getBidPbancCycl() == null ? 0 : target.getBidPbancCycl();
        List<AtchFileDTO> files = values.getBidPbancCycl() != null && values.getBidPbancCycl() > stored
                ? attachmentService.readBidFiles(item) : List.of();
        String rsltCd = BidCode.is(values.getPbancTypeCd(), BidCode.TYPE_CANCELED)
                ? BidCode.CLCT_CANCELED : BidCode.CLCT_CORRECTED;
        try {
            Boolean done = tx.execute(status -> {
                values.setPbancSn(target.getPbancSn());
                values.setFromBidPbancCycl(stored);
                // 다른 수집이 먼저 반영했으면 0행이다.
                if (bidNoticeMapper.updateBidCorrection(values) == 0) {
                    return false;
                }
                insertHistory(target.getPbancSn(), BidCode.STEP_BID_NOTICE, target.getBidPbancNo(), values, item);
                attachmentService.save(target.getPbancSn(), null, files);
                bidNoticeMapper.insertCollectLog(new CollectLogDTO(target.getPbancSn(), excnSeCd, exctrId, rsltCd,
                        "차수 " + stored + " → " + values.getBidPbancCycl()));
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                log.info("정정 반영: 입찰공고 {} 차수 {} -> {}", target.getBidPbancNo(), stored, values.getBidPbancCycl());
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("정정 반영 실패: 입찰공고 {}", target.getBidPbancNo(), e);
            writeLog(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_FAILED,
                    "입찰공고 " + target.getBidPbancNo() + " 정정을 반영하지 못했습니다: " + e.getMessage());
            return false;
        }
    }

    /**
     * 4. 입찰공고 → 개찰 (FR-024-1). 개찰일이 지난 공고만 번호로 개찰 결과를 묻는다.
     * 유찰이면 단계는 개찰, 공고상태는 유찰이다. 취소공고는 개찰이 없어 묻지 않는다.
     * 개찰 결과는 개찰일 며칠 뒤에 올라오기도 해서, 올라올 때까지 매 수집마다 다시 묻는다.
     */
    private void collectOpenings(String excnSeCd, String exctrId, CollectResultDTO result) {
        LocalDateTime now = LocalDateTime.now();
        List<TrackingNoticeDTO> targets = bidNoticeMapper.findBidStageNotices(BidCode.STEP_BID_NOTICE).stream()
                .filter(t -> t.getOpengDt() != null && t.getOpengDt().isBefore(now))
                .filter(t -> !BidCode.is(t.getPbancTypeCd(), BidCode.TYPE_CANCELED))
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return;
        }

        int opened = 0;
        for (TrackingNoticeDTO target : targets) {
            JsonNode row;
            try {
                row = latestOpening(g2bApiClient.findOpeningResult(target.getBidPbancNo()));
            } catch (BusinessException e) {
                log.warn("개찰 결과 확인 실패: {}", e.getMessage());
                writeLog(null, excnSeCd, exctrId, BidCode.CLCT_FAILED, e.getMessage());
                result.addFailure("개찰 확인", e.getMessage());
                break;
            }
            String progrs = G2bResponse.textOf(row, "progrsDivCdNm");
            if (!OPENG_DONE.equals(progrs) && !OPENG_FAILED.equals(progrs)) {
                continue; // 아직 개찰 결과가 없다
            }
            boolean failed = OPENG_FAILED.equals(progrs);
            if (moveStep(target, BidCode.STEP_BID_NOTICE, BidCode.STEP_OPENING,
                    failed ? BidCode.STTS_FAILED_BID : null, row, BidCode.CLCT_OPENING, progrs, excnSeCd, exctrId)) {
                opened++;
                if (failed) {
                    result.setFailedBid(result.getFailedBid() + 1);
                }
            }
        }
        result.setOpened(opened);
        writeLog(null, excnSeCd, exctrId, opened > 0 ? BidCode.CLCT_OPENING : BidCode.CLCT_NO_CHANGE, null);
    }

    /**
     * 개찰결과는 차수·재입찰마다 한 행씩 온다. 차수가 가장 큰 행, 같으면 재입찰번호가 가장 큰 행을 쓴다.
     * 유찰 뒤 재입찰해서 개찰완료가 된 경우 마지막 결과가 개찰완료이기 때문이다.
     */
    private static JsonNode latestOpening(G2bResponse res) {
        return res.all().stream()
                .max(Comparator.comparingInt((JsonNode r) -> G2bResponse.intOf(r, "bidNtceOrd", 0))
                        .thenComparingInt(r -> G2bResponse.intOf(r, "rbidNo", 0)))
                .orElse(null);
    }

    /**
     * 5. 개찰 → 계약. 유찰이 아닌 개찰 공고를 통합공개 API 에 번호로 묻고, 계약정보목록이 있으면 계약 단계로 올린다.
     * 계약은 개찰 몇 주 뒤에 맺기도 해서, 맺을 때까지 매 수집마다 다시 묻는다.
     */
    private void collectContracts(String excnSeCd, String exctrId, CollectResultDTO result) {
        List<TrackingNoticeDTO> targets = bidNoticeMapper.findBidStageNotices(BidCode.STEP_OPENING).stream()
                .filter(t -> !BidCode.is(t.getPbancSttsCd(), BidCode.STTS_FAILED_BID))
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return;
        }

        int contracted = 0;
        for (TrackingNoticeDTO target : targets) {
            JsonNode row;
            try {
                row = g2bApiClient.findProcessByBidNo(target.getBidPbancNo()).first();
            } catch (BusinessException e) {
                log.warn("계약 확인 실패: {}", e.getMessage());
                writeLog(null, excnSeCd, exctrId, BidCode.CLCT_FAILED, e.getMessage());
                result.addFailure("계약 확인", e.getMessage());
                break;
            }
            if (G2bResponse.textOf(row, "cntrctInfoList") == null) {
                continue; // 아직 계약 전이다
            }
            if (moveStep(target, BidCode.STEP_OPENING, BidCode.STEP_CONTRACT, null, row,
                    BidCode.CLCT_TO_CONTRACT, null, excnSeCd, exctrId)) {
                contracted++;
            }
        }
        result.setContracted(contracted);
        writeLog(null, excnSeCd, exctrId, contracted > 0 ? BidCode.CLCT_TO_CONTRACT : BidCode.CLCT_NO_CHANGE, null);
    }

    /**
     * 6. 첨부가 하나도 없는 사전규격·입찰공고 공고의 첨부를 받는다 (FR-036).
     * 첨부 기능(10/28) 전에 등록한 공고나, 등록·전환 때 나라장터가 응답하지 않아 첨부를 못 받은 공고가 대상이다.
     * 정정·전환이 받은 첨부와 겹치지 않게 맨 마지막에 돈다. 첨부가 정말 없는 공고는 수집마다 다시 묻는다(공고당 1번).
     */
    private void fillMissingFiles(String excnSeCd, String exctrId, CollectResultDTO result) {
        List<TrackingNoticeDTO> targets =
                bidNoticeMapper.findNoticesWithoutFiles(BidCode.STEP_PRE_SPEC, BidCode.STEP_BID_NOTICE);
        int filled = 0;
        for (TrackingNoticeDTO target : targets) {
            List<AtchFileDTO> files;
            try {
                files = readFilesOf(target);
            } catch (BusinessException e) {
                log.warn("첨부 받기 실패: {}", e.getMessage());
                writeLog(null, excnSeCd, exctrId, BidCode.CLCT_FAILED, e.getMessage());
                result.addFailure("첨부 받기", e.getMessage());
                break;
            }
            if (files.isEmpty()) {
                continue; // 나라장터에도 첨부가 없다 (또는 번호가 없는 공고)
            }
            try {
                tx.executeWithoutResult(status -> attachmentService.save(target.getPbancSn(), null, files));
                log.info("첨부 받음: 공고 {} {}건", target.getPbancSn(), files.size());
                filled++;
            } catch (RuntimeException e) {
                log.warn("첨부 저장 실패: 공고 {}", target.getPbancSn(), e);
                writeLog(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_FAILED,
                        "첨부를 저장하지 못했습니다: " + e.getMessage());
            }
        }
        result.setFilesFilled(filled);
    }

    /** 입찰공고 단계면 마지막 차수의 입찰공고 첨부, 사전규격 단계면 사전규격 규격서 */
    private List<AtchFileDTO> readFilesOf(TrackingNoticeDTO target) {
        if (BidCode.is(target.getPbancStepCd(), BidCode.STEP_BID_NOTICE)) {
            G2bResponse res = g2bApiClient.findBidNotice(target.getBidPbancNo());
            JsonNode item = res.latestOrd() != null ? res.latestOrd() : res.first();
            return item == null ? List.of() : attachmentService.readBidFiles(item);
        }
        JsonNode spec = g2bApiClient.findPreSpec(target.getBfSpecRgstNo()).first();
        return spec == null ? List.of() : attachmentService.readPreSpecFiles(spec);
    }

    /** 단계만 올리고 수집이력·수집로그를 남긴다 (개찰·계약). 한 공고씩 따로 커밋한다. */
    private boolean moveStep(TrackingNoticeDTO target, String fromStepCd, String toStepCd, String sttsCd,
                             JsonNode row, String rsltCd, String msg, String excnSeCd, String exctrId) {
        try {
            Boolean done = tx.execute(status -> {
                if (bidNoticeMapper.updateStep(target.getPbancSn(), fromStepCd, toStepCd, sttsCd) == 0) {
                    return false;
                }
                insertHistory(target.getPbancSn(), toStepCd, target.getBidPbancNo(), null, row);
                bidNoticeMapper.insertCollectLog(new CollectLogDTO(target.getPbancSn(), excnSeCd, exctrId, rsltCd, msg));
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                log.info("단계 전환: 입찰공고 {} {} -> {}{}", target.getBidPbancNo(), fromStepCd, toStepCd,
                        msg == null ? "" : " (" + msg + ")");
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("단계 전환 실패: 입찰공고 {} -> {}", target.getBidPbancNo(), toStepCd, e);
            writeLog(target.getPbancSn(), excnSeCd, exctrId, BidCode.CLCT_FAILED,
                    "입찰공고 " + target.getBidPbancNo() + " 를 " + toStepCd + " 로 바꾸지 못했습니다: " + e.getMessage());
            return false;
        }
    }

    /** 입찰공고 응답 한 건을 공고 행의 값으로 옮긴다. 입찰공고 전환과 정정 반영이 같이 쓴다. */
    private static BidLinkDTO bidValuesOf(JsonNode item) {
        BidLinkDTO values = new BidLinkDTO();
        values.setBidPbancCycl(G2bResponse.intOf(item, "bidNtceOrd", 0));
        values.setPbancTypeCd(BidCode.pbancTypeCdOf(G2bResponse.textOf(item, "ntceKindNm")));
        values.setPbancNm(G2bResponse.textOf(item, "bidNtceNm"));
        values.setOrderInstNm(G2bResponse.textOf(item, "ntceInsttNm"));
        values.setDmndInstNm(G2bResponse.textOf(item, "dminsttNm"));
        values.setAltmntBgtAmt(G2bResponse.amountOf(item, "asignBdgtAmt"));
        // 마감일이 빈 값으로 오는 공고가 있다(실측). 그때는 null 이고 덮지 않는다.
        values.setBidDdlnDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(item, "bidClseDt")));
        values.setOpengDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(item, "opengDt")));
        values.setEvlMthdNm(G2bResponse.textOf(item, "sucsfbidMthdNm"));
        values.setPbancDtlUrl(G2bResponse.textOf(item, "bidNtceDtlUrl"));
        return values;
    }

    /** 수집이력 한 행. 그 시점의 API 원문을 남겨 상세의 변경 내용·단계 이력이 쓴다. values 가 없으면 단계와 번호만 남긴다. */
    private void insertHistory(Long pbancSn, String stepCd, String no, BidLinkDTO values, JsonNode raw) {
        CollectHistoryDTO history = new CollectHistoryDTO();
        history.setPbancSn(pbancSn);
        history.setClctSeq(bidNoticeMapper.nextCollectSeq(pbancSn));
        history.setPbancStepCd(stepCd);
        history.setPbancNo(no);
        if (values != null) {
            history.setBidPbancCycl(values.getBidPbancCycl());
            history.setPbancTypeCd(values.getPbancTypeCd());
            history.setAltmntBgtAmt(values.getAltmntBgtAmt());
            history.setBidDdlnDt(values.getBidDdlnDt());
            history.setOpengDt(values.getOpengDt());
        }
        history.setApiRspnsCn(raw == null ? null : raw.toString());
        bidNoticeMapper.insertCollectHistory(history);
    }

    /** 기간 안에 등록·변경된 입찰공고를 모두 받아 번호별로 차수가 가장 큰 한 건만 남긴다. */
    private Map<String, JsonNode> latestBidItems() {
        String[] range = lookbackRange();
        List<JsonNode> items = new ArrayList<>();
        items.addAll(fetchPages(page -> g2bApiClient.findBidNoticesBetween(
                G2bApiClient.BID_BY_REG_DT, range[0], range[1], PAGE_SIZE, page)));
        items.addAll(fetchPages(page -> g2bApiClient.findBidNoticesBetween(
                G2bApiClient.BID_BY_CHG_DT, range[0], range[1], PAGE_SIZE, page)));

        Map<String, JsonNode> latest = new HashMap<>();
        for (JsonNode item : items) {
            String no = G2bResponse.textOf(item, "bidNtceNo");
            if (no == null) {
                continue;
            }
            JsonNode before = latest.get(no);
            if (before == null || G2bResponse.intOf(item, "bidNtceOrd", 0) >= G2bResponse.intOf(before, "bidNtceOrd", 0)) {
                latest.put(no, item);
            }
        }
        return latest;
    }

    /**
     * 최근 기간에 등록된 사전규격을 모두 받는다.
     * 기간은 금요일 수집 뒤 월요일까지 비는 사흘을 덮도록 잡는다. 겹쳐 받아도 같은 전이는 두 번 일어나지 않는다.
     */
    private List<JsonNode> fetchRecentPreSpecs() {
        String[] range = lookbackRange();
        return fetchPages(page -> g2bApiClient.findPreSpecsRegistered(range[0], range[1], PAGE_SIZE, page));
    }

    /** 지금부터 g2b.collect.lookback-days 전까지. [시작, 끝] 12자리 */
    private String[] lookbackRange() {
        LocalDateTime now = LocalDateTime.now();
        return new String[] { now.minusDays(lookbackDays).format(API_DT), now.format(API_DT) };
    }

    /** 목록 API 를 마지막 페이지까지 넘긴다. totalCount 를 다 받았거나 빈 페이지가 오면 멈춘다. */
    private static List<JsonNode> fetchPages(IntFunction<G2bResponse> pageFetcher) {
        List<JsonNode> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            G2bResponse res = pageFetcher.apply(page);
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
