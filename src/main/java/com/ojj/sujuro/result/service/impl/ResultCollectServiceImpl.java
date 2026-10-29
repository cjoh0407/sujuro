package com.ojj.sujuro.result.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final String ourBizNo;

    public ResultCollectServiceImpl(ResultMapper resultMapper,
                                    G2bApiClient g2bApiClient,
                                    NotificationService notificationService,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${company.biz-no:}") String ourBizNo) {
        this.resultMapper = resultMapper;
        this.g2bApiClient = g2bApiClient;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(transactionManager);
        this.ourBizNo = digits(ourBizNo);
    }

    @Override
    public boolean isReady() {
        return ourBizNo != null && ourBizNo.length() == 10 && !ourBizNo.equals("0000000000");
    }

    @Override
    public List<ResultTargetDTO> findTargets() {
        return resultMapper.findResultTargets();
    }

    @Override
    public ResultOutcomeDTO register(ResultTargetDTO target) {
        if (!isReady()) {
            return ResultOutcomeDTO.waiting(); // 부르는 쪽이 먼저 isReady 를 본다
        }
        String no = target.getBidPbancNo();
        JsonNode award = latest(g2bApiClient.findAward(no).all());
        String winner = digits(G2bResponse.textOf(award, "bidwinnrBizno"));
        if (winner == null) {
            return ResultOutcomeDTO.waiting();
        }

        List<JsonNode> rows = latestRound(g2bApiClient.findOpeningCompanies(no).all());
        JsonNode ours = rows.stream()
                .filter(r -> ourBizNo.equals(digits(G2bResponse.textOf(r, "prcbdrBizno"))))
                .findFirst().orElse(null);
        if (ours == null) {
            return ResultOutcomeDTO.notListed("입찰공고 " + no + " 개찰 목록에 우리 회사(사업자번호 " + ourBizNo
                    + ")가 없어 결과를 등록하지 않았습니다. 사업자번호나 제출 여부를 확인하세요.");
        }

        boolean won = ourBizNo.equals(winner);
        BidRsltDTO result = resultOf(target.getPbancSn(), ours, won,
                won ? longOf(G2bResponse.amountOf(award, "sucsfbidAmt")) : null);
        List<CoEvlRsltDTO> companies = rows.stream()
                .map(r -> companyOf(target.getPbancSn(), r, winner))
                .filter(c -> c != null)
                .collect(Collectors.toList());
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

        String text = (won ? "수주" : "탈락") + (result.getEvlRnk() == null ? "" : " · " + result.getEvlRnk() + "위");
        requestEvaluationTable(target, text);
        return ResultOutcomeDTO.registered("결과 등록: " + text);
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

    /** 경영지원실에 평가표 엑셀을 올려 달라고 알린다. 알림이 실패해도 결과 등록은 그대로 둔다 */
    private void requestEvaluationTable(ResultTargetDTO target, String text) {
        try {
            notificationService.sendToRole(Role.MGMT, NotificationType.EVL_TABLE_REQUEST, target.getPbancSn(),
                    "[" + target.getPbancNm() + "] 결과가 등록되었습니다(" + text + "). "
                            + "나라장터 기술평가점수 조회에서 평가표 엑셀을 내려받아 올리고 항목별 점수를 입력하세요.");
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

    /** 개찰 목록 한 행. 사업자번호가 10자리가 아니면 넣지 않는다(BIZ_NO CHAR(10)) */
    CoEvlRsltDTO companyOf(Long pbancSn, JsonNode row, String winner) {
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
        c.setOurCoYn(bizNo.equals(ourBizNo) ? "Y" : "N");
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
