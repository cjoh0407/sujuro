package com.ojj.sujuro.result.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.result.code.ResultCode;
import com.ojj.sujuro.result.dto.BidRsltDTO;
import com.ojj.sujuro.result.dto.CoEvlRsltDTO;
import com.ojj.sujuro.result.dto.ResultOutcomeDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;
import com.ojj.sujuro.result.mapper.ResultMapper;
import com.ojj.sujuro.result.service.ResultCollectService;
import com.ojj.sujuro.result.support.StatsFormat;

/**
 * 결과 등록. 규칙은 인터페이스(ResultCollectService)에 있다.
 *
 * 쓰는 API (2026-10-29 R26BK01541118 로 확인)
 *   낙찰된 목록  getScsbidListSttusServc         bidwinnrBizno(최종 낙찰자), sucsfbidAmt(낙찰금액)
 *   개찰완료 목록 getOpengResultListInfoOpengCompt  업체마다 opengRank·prcbdrBizno·bidprcAmt·techEvlVal·bidPrceEvlVal·totalEvlAmtVal
 *                                                 ·techEvlNaturVal
 *   입찰공고     getBidPblancListInfoServc         techAbltEvlRt·bidPrceEvlRt (공고에 배점이 비어 있을 때만)
 * 기술평가점수(techEvlVal)는 차등점수제를 적용한 점수, 기술평가원점수(techEvlNaturVal)는 적용 전 점수다. 둘 다 저장한다.
 */
@Service
public class ResultCollectServiceImpl implements ResultCollectService {

    private static final Logger log = LoggerFactory.getLogger(ResultCollectServiceImpl.class);

    /** 점수 컬럼이 NUMBER(10,3) 이다. API 는 9.9923 처럼 넷째 자리까지 준다 */
    private static final int SCORE_SCALE = 3;

    private final ResultMapper resultMapper;
    private final G2bApiClient g2bApiClient;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;
    /** 우리 회사 사업자번호들 (10자리). 쉼표로 여러 개를 줄 수 있고, 개찰 목록에 그중 하나라도 있으면 우리 회사다 (11/10) */
    private final Set<String> ourBizNos;

    public ResultCollectServiceImpl(ResultMapper resultMapper,
                                    G2bApiClient g2bApiClient,
                                    NotificationService notificationService,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${company.biz-no:}") String ourBizNos) {
        this.resultMapper = resultMapper;
        this.g2bApiClient = g2bApiClient;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(transactionManager);
        this.ourBizNos = parseBizNos(ourBizNos);
    }

    /** "107-87-28988, 220-86-57703" → {1078728988, 2208657703}. 10자리가 아니거나 0000000000 이면 뺀다 */
    static Set<String> parseBizNos(String text) {
        Set<String> nos = new LinkedHashSet<>();
        if (text == null) {
            return nos;
        }
        for (String part : text.split(",")) {
            String d = digits(part);
            if (d != null && d.length() == 10 && !d.equals("0000000000")) {
                nos.add(d);
            }
        }
        return nos;
    }

    @Override
    public boolean isReady() {
        return !ourBizNos.isEmpty();
    }

    @Override
    public List<ResultTargetDTO> findTargets() {
        return resultMapper.findResultTargets();
    }

    @Override
    public ResultOutcomeDTO register(ResultTargetDTO target) {
        String partner = digits(target.getPartnerBizNo());
        if (partner == null && !isReady()) {
            return ResultOutcomeDTO.waiting(); // 부르는 쪽이 먼저 isReady 를 본다
        }
        Set<String> mine = partner != null ? Collections.singleton(partner) : ourBizNos;
        String no = target.getBidPbancNo();
        JsonNode award = latest(g2bApiClient.findAward(no).all());
        String winner = digits(G2bResponse.textOf(award, "bidwinnrBizno"));

        // 개찰 업체는 낙찰자가 나오기 전에도, 우리 회사가 목록에 없어도 전부 저장한다 (11/04). 사업자번호는 그중 우리 행을 가릴 때만 쓴다
        List<JsonNode> rows = latestRound(g2bApiClient.findOpeningCompanies(no).all());
        if (partner != null && rows.isEmpty()) {
            // 참여 업체 번호는 개찰 목록의 그 업체 행에 남긴다. 개찰 전에는 남길 행이 없어 받지 않는다
            return ResultOutcomeDTO.notListed("입찰공고 " + no + " 는 아직 나라장터에 개찰 결과가 없습니다. "
                    + "개찰 결과가 나온 뒤에 함께 참여한 업체의 사업자번호를 다시 넣어 주세요.");
        }
        JsonNode ours = oursOf(rows, mine, winner);
        String oursBizNo = ours == null ? null : digits(G2bResponse.textOf(ours, "prcbdrBizno"));
        List<CoEvlRsltDTO> companies = rows.stream()
                .map(r -> companyOf(target.getPbancSn(), r, winner, oursBizNo))
                .filter(c -> c != null)
                .collect(Collectors.toList());
        if (companies.size() < rows.size()) {
            log.warn("입찰공고 {} 개찰 업체 {}곳 중 {}곳은 사업자번호가 10자리가 아니라 저장하지 않았습니다",
                    no, rows.size(), rows.size() - companies.size());
        }
        if (partner != null && ours == null) {
            saveCompanies(companies);
            return ResultOutcomeDTO.notListed("입찰공고 " + no + " 개찰 목록에 사업자번호 " + StatsFormat.bizNo(partner)
                    + " 업체가 없어 결과를 가져오지 않았습니다. 함께 참여한 주관사·원도급사의 사업자번호를 확인해 주세요.");
        }
        if (winner == null) {
            saveCompanies(companies);
            return ResultOutcomeDTO.waiting();
        }
        if (ours == null) {
            saveCompanies(companies);
            return ResultOutcomeDTO.notListed("입찰공고 " + no + " 개찰 목록에 우리 회사(사업자번호 " + bizNosText(mine)
                    + ")가 없어 결과를 등록하지 않았습니다. 사업자번호나 제출 여부를 확인해 주세요.");
        }

        boolean won = winner.equals(oursBizNo);
        // 컨소시엄 참여·하도급도 주관사·원도급사의 낙찰 금액을 그대로 수주 금액으로 쓴다 (11/10 결정: 직접 입력 없이 API 값만)
        BidRsltDTO result = resultOf(target.getPbancSn(), ours, won,
                won ? longOf(G2bResponse.amountOf(award, "sucsfbidAmt")) : null);
        JsonNode bid = target.getTechScorScr() == null ? bidNoticeOf(no) : null;

        Boolean done = tx.execute(status -> {
            if (bid != null) {
                resultMapper.fillScoring(target.getPbancSn(), G2bResponse.amountOf(bid, "techAbltEvlRt"),
                        G2bResponse.amountOf(bid, "bidPrceEvlRt"));
            }
            companies.forEach(resultMapper::mergeCoEvlRslt);
            resultMapper.mergeBidRslt(result);
            if (resultMapper.updateToResult(target.getPbancSn()) == 0) {
                status.setRollbackOnly(); // 그사이 다른 수집이 등록했거나 상태가 바뀌었다
                return false;
            }
            return true;
        });
        if (!Boolean.TRUE.equals(done)) {
            return ResultOutcomeDTO.waiting();
        }

        String text = (won ? "수주" : "실주") + (result.getEvlRnk() == null ? "" : " · " + result.getEvlRnk() + "위");
        requestEvaluationTable(target, text);
        return ResultOutcomeDTO.registered("결과 등록: " + text);
    }

    /** 결과는 아직 등록하지 않고 개찰 업체만 저장한다. 평가·결과 입력의 개찰 업체 점수에 바로 보인다 */
    private void saveCompanies(List<CoEvlRsltDTO> companies) {
        if (!companies.isEmpty()) {
            tx.executeWithoutResult(status -> companies.forEach(resultMapper::mergeCoEvlRslt));
        }
    }

    /**
     * 기술·가격 배점(비율)이 없는 공고(10/29 전에 등록)는 입찰공고를 한 번 더 물어 채운다.
     * 배점은 점수 만점을 보여 주는 데만 쓰여서, 나라장터가 응답하지 않으면 비운 채로 결과를 등록한다.
     */
    private JsonNode bidNoticeOf(String no) {
        try {
            return g2bApiClient.findBidNotice(no).latestOrd();
        } catch (BusinessException e) {
            log.warn("입찰공고 {} 배점을 받지 못했습니다: {}", no, e.getMessage());
            return null;
        }
    }

    /**
     * 경영지원실에는 평가표 엑셀을 올려 달라고, 주관 팀 팀장에게는 평가 점수·장단점을 입력하라고 알린다
     * (점수·장단점 입력은 팀장, 회의 결정 2026-11-02 반영). 알림이 실패해도 결과 등록은 그대로 둔다
     */
    private void requestEvaluationTable(ResultTargetDTO target, String text) {
        String head = "[" + target.getPbancNm() + "] 결과가 등록되었습니다(" + text + "). ";
        try {
            notificationService.sendToRole(Role.MGMT, NotificationType.EVL_TABLE_REQUEST, target.getPbancSn(),
                    head + "나라장터 기술평가점수 조회에서 평가표 엑셀을 내려받아 올려 주세요.");
            if (target.getSprvsnTeamSn() != null) {
                notificationService.sendToTeamLeaders(target.getSprvsnTeamSn(), NotificationType.EVL_TABLE_REQUEST,
                        target.getPbancSn(), head + "평가표 엑셀이 올라오면 평가·결과 입력에서 항목별 점수와 장단점을 입력해 주세요.");
            }
        } catch (RuntimeException e) {
            log.warn("평가표 업로드 요청 알림 실패: 입찰공고 {}", target.getBidPbancNo(), e);
        }
    }

    static BidRsltDTO resultOf(Long pbancSn, JsonNode ours, boolean won, Long scsbdAmt) {
        BidRsltDTO r = new BidRsltDTO();
        r.setPbancSn(pbancSn);
        r.setBidRsltCd(won ? ResultCode.WON : ResultCode.LOST);
        r.setEvlRnk(rankOf(ours));
        r.setTechEvlScr(scoreOf(ours, "techEvlVal"));
        r.setTechEvlOrgnlScr(scoreOf(ours, "techEvlNaturVal"));
        r.setPrcEvlScr(scoreOf(ours, "bidPrceEvlVal"));
        r.setSnthsEvlScr(scoreOf(ours, "totalEvlAmtVal"));
        r.setScsbdAmt(scsbdAmt != null ? scsbdAmt : (won ? longOf(G2bResponse.amountOf(ours, "bidprcAmt")) : null));
        return r;
    }

    /**
     * 개찰 목록에서 우리 쪽 행. 우리 번호(mine)인 행 중 낙찰자가 있으면 그 행, 없으면 순위가 가장 높은 행.
     * 우리 회사 번호가 둘이라 두 행이 다 올라와도 우리 행은 하나로 정한다(OUR_CO_YN = 'Y' 는 사업마다 한 행).
     */
    private static JsonNode oursOf(List<JsonNode> rows, Set<String> mine, String winner) {
        List<JsonNode> candidates = rows.stream()
                .filter(r -> mine.contains(digits(G2bResponse.textOf(r, "prcbdrBizno"))))
                .collect(Collectors.toList());
        return candidates.stream()
                .filter(r -> winner != null && winner.equals(digits(G2bResponse.textOf(r, "prcbdrBizno"))))
                .findFirst()
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingInt((JsonNode r) -> {
                            Integer rank = rankOf(r);
                            return rank == null ? Integer.MAX_VALUE : rank;
                        }))
                        .orElse(null));
    }

    /** "107-87-28988, 220-86-57703" */
    private static String bizNosText(Set<String> nos) {
        return nos.stream().map(StatsFormat::bizNo).collect(Collectors.joining(", "));
    }

    /** 개찰 목록 한 행. 사업자번호가 10자리가 아니면 넣지 않는다(BIZ_NO CHAR(10)). oursBizNo 행만 우리 쪽(OUR_CO_YN = 'Y') */
    CoEvlRsltDTO companyOf(Long pbancSn, JsonNode row, String winner, String oursBizNo) {
        String bizNo = digits(G2bResponse.textOf(row, "prcbdrBizno"));
        if (bizNo == null || bizNo.length() != 10) {
            return null;
        }
        String nm = G2bResponse.textOf(row, "prcbdrNm");
        CoEvlRsltDTO c = new CoEvlRsltDTO();
        c.setPbancSn(pbancSn);
        c.setBizNo(bizNo);
        c.setCoNm(nm == null ? "(업체명 없음)" : nm);
        c.setOpengRnk(rankOf(row));
        c.setBidAmt(longOf(G2bResponse.amountOf(row, "bidprcAmt")));
        c.setTechEvlScr(scoreOf(row, "techEvlVal"));
        c.setTechEvlOrgnlScr(scoreOf(row, "techEvlNaturVal"));
        c.setPrcEvlScr(scoreOf(row, "bidPrceEvlVal"));
        c.setSnthsEvlScr(scoreOf(row, "totalEvlAmtVal"));
        c.setOurCoYn(bizNo.equals(oursBizNo) ? "Y" : "N");
        c.setScsbdYn(bizNo.equals(winner) ? "Y" : "N");
        return c;
    }

    /** 차수·재입찰마다 행이 온다. 차수가 가장 큰, 같으면 재입찰번호가 가장 큰 행 */
    private static final Comparator<JsonNode> ROUND = Comparator
            .comparingInt((JsonNode r) -> G2bResponse.intOf(r, "bidNtceOrd", 0))
            .thenComparingInt(r -> G2bResponse.intOf(r, "rbidNo", 0));

    private static JsonNode latest(List<JsonNode> rows) {
        return rows.stream().max(ROUND).orElse(null);
    }

    /** 개찰 목록에서 마지막 차수·재입찰 회차의 행만 */
    static List<JsonNode> latestRound(List<JsonNode> rows) {
        JsonNode last = latest(rows);
        if (last == null) {
            return new ArrayList<>();
        }
        return rows.stream().filter(r -> ROUND.compare(r, last) == 0).collect(Collectors.toList());
    }

    private static Integer rankOf(JsonNode row) {
        int rank = G2bResponse.intOf(row, "opengRank", -1);
        return rank > 0 ? rank : null;
    }

    private static BigDecimal scoreOf(JsonNode row, String field) {
        BigDecimal v = G2bResponse.amountOf(row, field);
        return v == null ? null : v.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private static Long longOf(BigDecimal v) {
        return v == null ? null : v.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 숫자만 남긴다 ("107-87-28988" → "1078728988"). 비면 null */
    static String digits(String s) {
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("[^0-9]", "");
        return d.isEmpty() ? null : d;
    }
}
