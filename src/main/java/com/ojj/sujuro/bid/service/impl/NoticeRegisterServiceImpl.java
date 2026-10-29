package com.ojj.sujuro.bid.service.impl;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterResultDTO;
import com.ojj.sujuro.bid.dto.ProcessHistoryDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeAttachmentService;
import com.ojj.sujuro.bid.service.NoticeRegisterService;
import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 공고 최초 등록 (FR-021, 022).
 *
 * 등록 한 번에 네 표가 함께 바뀌므로 한 트랜잭션으로 묶는다.
 *   TB_BID_PBANC          현재값
 *   TB_PBANC_CLCT_HSTRY   API 응답 스냅샷 (원문 포함)
 *   TB_PBANC_PRCS_HSTRY   사람이 한 일 (공고 등록)
 *   TB_ATCH_FILE          나라장터 첨부 (사전규격 규격서, 입찰공고 첨부). 발주계획은 첨부를 주지 않는다
 */
@Service
public class NoticeRegisterServiceImpl implements NoticeRegisterService {

    /** 사전규격등록번호 형식: R + 연도 2자리 + BD + 순번 8자리 (예: R26BD00283794) */
    private static final Pattern PRE_SPEC_NO = Pattern.compile("^R\\d{2}BD\\d{8}$");

    /** 발주계획번호 형식: R + 연도 2자리 + DD + 순번 8자리 (예: R26DD20907423) */
    private static final Pattern ORDER_PLAN_NO = Pattern.compile("^R[0-9]{2}DD[0-9]{8}$");

    /** 입찰공고번호 형식: R + 연도 2자리 + BK + 순번 8자리 (예: R26BK01790125). 차수는 따로 온다. */
    private static final Pattern BID_NO = Pattern.compile("^R[0-9]{2}BK[0-9]{8}$");

    private final BidNoticeMapper bidNoticeMapper;
    private final G2bApiClient g2bApiClient;
    private final NoticeAttachmentService attachmentService;

    public NoticeRegisterServiceImpl(BidNoticeMapper bidNoticeMapper, G2bApiClient g2bApiClient,
                                     NoticeAttachmentService attachmentService) {
        this.bidNoticeMapper = bidNoticeMapper;
        this.g2bApiClient = g2bApiClient;
        this.attachmentService = attachmentService;
    }

    /**
     * 번호 형식의 가운데 두 글자(DD·BD·BK)가 곧 단계라서 사람이 단계를 고를 필요가 없다.
     * 단계별 메서드를 같은 객체 안에서 부르므로 그쪽의 @Transactional 은 따로 걸리지 않고,
     * 이 메서드의 트랜잭션 하나로 묶인다.
     */
    @Override
    @Transactional
    public NoticeRegisterResultDTO register(String rawNo, String userId) {
        String no = normalize(rawNo);
        if (ORDER_PLAN_NO.matcher(no).matches()) {
            return registerByOrderPlanNo(no, userId);
        }
        if (PRE_SPEC_NO.matcher(no).matches()) {
            return registerByPreSpecNo(no, userId);
        }
        if (BID_NO.matcher(no).matches()) {
            return registerByBidNo(no, userId);
        }
        throw new BusinessException("공고번호 형식을 알 수 없습니다. 발주계획번호(R26DD…), 사전규격등록번호(R26BD…), "
                + "입찰공고번호(R26BK…) 중 하나를 13글자로 넣으세요.");
    }

    /**
     * 등록 시점에 이미 입찰공고가 올라와 있을 수 있다(응답의 bidNtceNoList). 그 경우에도 여기서
     * 입찰공고로 바꾸지는 않고 사전규격으로 넣은 뒤 추적 상태를 "입찰공고 대기"로 둔다.
     * 입찰공고 연결은 수집(collectBidNotice)이 맡는다.
     */
    @Override
    @Transactional
    public NoticeRegisterResultDTO registerByPreSpecNo(String rawNo, String userId) {
        String no = normalize(rawNo);
        if (!PRE_SPEC_NO.matcher(no).matches()) {
            throw new BusinessException(
                    "사전규격등록번호 형식이 아닙니다. R + 연도 2자리 + BD + 순번 8자리 13글자입니다. (예: R26BD00283794)");
        }
        if (bidNoticeMapper.countByBfSpecRgstNo(no) > 0) {
            throw new BusinessException("이미 등록된 사전규격입니다. 공고 목록에서 확인하세요.");
        }

        G2bResponse res = g2bApiClient.findPreSpec(no);
        JsonNode item = firstOrThrow(res, "사전규격등록번호", no);

        NoticeRegisterDTO regist = new NoticeRegisterDTO();
        regist.setBfSpecRgstNo(no);
        regist.setPbancStepCd(BidCode.STEP_PRE_SPEC);
        regist.setTrckSttsCd(BidCode.TRACK_WAITING);
        // 사전규격은 공고명이 아니라 품명이 사업명 구실을 한다.
        regist.setPbancNm(G2bResponse.textOf(item, "prdctClsfcNoNm"));
        regist.setOrderInstNm(G2bResponse.textOf(item, "orderInsttNm"));
        regist.setDmndInstNm(G2bResponse.textOf(item, "rlDminsttNm"));
        regist.setSwBizYn(yn(G2bResponse.textOf(item, "swBizObjYn")));
        regist.setAltmntBgtAmt(G2bResponse.amountOf(item, "asignBdgtAmt"));
        regist.setOpnnDdlnDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(item, "opninRgstClseDt")));
        regist.setRgtrId(userId);

        Long pbancSn = saveNew(regist, "사전규격등록번호", no, res);
        // 규격서에는 제안요청서 초안이 들어 있는 경우가 많다 (실측 21건 중 17건)
        attachmentService.save(pbancSn, userId, attachmentService.readPreSpecFiles(item));
        return new NoticeRegisterResultDTO(pbancSn, regist.getPbancNm(), "사전규격", no, linkedBidNoOf(item));
    }

    /**
     * 발주계획에는 사전규격부터 나오는 값(의견등록마감일시·SW사업여부·실수요기관)이 아직 없다.
     * 사업명·발주기관·예산만 받고, 나머지는 사전규격으로 넘어갈 때 채운다 (updateToPreSpec).
     *
     * 등록 시점에 이미 입찰공고가 나 있을 수 있다(bidNtceNoList). 그래도 발주계획으로 넣는다.
     * 사전규격번호 등록과 같은 규칙이다. 발주계획의 사업명·기관명이 나중에 사전규격을 찾는 열쇠다.
     */
    @Override
    @Transactional
    public NoticeRegisterResultDTO registerByOrderPlanNo(String rawNo, String userId) {
        String no = normalize(rawNo);
        if (!ORDER_PLAN_NO.matcher(no).matches()) {
            throw new BusinessException(
                    "발주계획번호 형식이 아닙니다. R + 연도 2자리 + DD + 순번 8자리 13글자입니다. (예: R26DD20907423)");
        }
        if (bidNoticeMapper.countByOrderPlanNo(no) > 0) {
            throw new BusinessException("이미 등록된 발주계획입니다. 공고 목록에서 확인하세요.");
        }

        G2bResponse res = g2bApiClient.findOrderPlan(no);
        JsonNode item = firstOrThrow(res, "발주계획번호", no);

        NoticeRegisterDTO regist = new NoticeRegisterDTO();
        regist.setOrderPlanNo(no);
        regist.setPbancStepCd(BidCode.STEP_ORDER_PLAN);
        regist.setTrckSttsCd(BidCode.TRACK_WAITING);
        regist.setPbancNm(G2bResponse.textOf(item, "bizNm"));
        regist.setOrderInstNm(G2bResponse.textOf(item, "orderInsttNm"));
        // 발주금액 합계. 사전규격의 배정예산금액과 실측 92% 가 같았다.
        regist.setAltmntBgtAmt(G2bResponse.amountOf(item, "sumOrderAmt"));
        regist.setRgtrId(userId);

        Long pbancSn = saveNew(regist, "발주계획번호", no, res);
        return new NoticeRegisterResultDTO(pbancSn, regist.getPbancNm(), "발주계획", no, linkedBidNoOf(item));
    }

    /**
     * 입찰공고는 정정이 나면 같은 번호에 차수만 올라가 여러 건이 온다. 현재값은 마지막 차수에서 꺼낸다.
     * 공고종류(등록·변경·재·취소공고)도 마지막 차수 기준이다.
     */
    @Override
    @Transactional
    public NoticeRegisterResultDTO registerByBidNo(String rawNo, String userId) {
        String no = normalize(rawNo);
        if (!BID_NO.matcher(no).matches()) {
            throw new BusinessException(
                    "입찰공고번호 형식이 아닙니다. R + 연도 2자리 + BK + 순번 8자리 13글자입니다. (예: R26BK01790125)");
        }
        if (bidNoticeMapper.countByBidPbancNo(no) > 0) {
            throw new BusinessException("이미 등록된 입찰공고입니다. 공고 목록에서 확인하세요.");
        }

        G2bResponse res = g2bApiClient.findBidNotice(no);
        JsonNode first = firstOrThrow(res, "입찰공고번호", no);
        JsonNode latest = res.latestOrd();
        JsonNode item = latest != null ? latest : first;

        // 입찰공고 응답에 같은 사업의 사전규격번호·발주계획번호가 들어 있다.
        String specNo = G2bResponse.textOf(item, "bfSpecRgstNo");
        String planNo = G2bResponse.textOf(item, "orderPlanUntyNo");
        rejectIfSameBizRegistered(specNo, planNo);

        NoticeRegisterDTO regist = new NoticeRegisterDTO();
        regist.setBidPbancNo(no);
        // 앞 단계 번호도 같은 행에 넣어 둔다. 나중에 그 번호로 따로 등록하려 하면 중복으로 막힌다.
        regist.setBfSpecRgstNo(specNo);
        regist.setOrderPlanNo(planNo);
        regist.setBidPbancCycl(G2bResponse.intOf(item, "bidNtceOrd", 0));
        regist.setPbancStepCd(BidCode.STEP_BID_NOTICE);
        // 추적상태(trckSttsCd)는 비워 둔다. 발주계획·사전규격으로 등록해 입찰공고를 기다리는 공고만 쓴다.
        regist.setPbancTypeCd(BidCode.pbancTypeCdOf(G2bResponse.textOf(item, "ntceKindNm")));
        regist.setPbancNm(G2bResponse.textOf(item, "bidNtceNm"));
        regist.setOrderInstNm(G2bResponse.textOf(item, "ntceInsttNm"));
        regist.setDmndInstNm(G2bResponse.textOf(item, "dminsttNm"));
        regist.setAltmntBgtAmt(G2bResponse.amountOf(item, "asignBdgtAmt"));
        // 마감일이 빈 값으로 오는 공고가 있다(실측). 그때는 비워 둔다.
        regist.setBidDdlnDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(item, "bidClseDt")));
        regist.setOpengDt(NoticeRegisterDTO.parseDate(G2bResponse.textOf(item, "opengDt")));
        regist.setEvlMthdNm(G2bResponse.textOf(item, "sucsfbidMthdNm"));
        regist.setPbancDtlUrl(G2bResponse.textOf(item, "bidNtceDtlUrl"));
        regist.setRgtrId(userId);

        Long pbancSn = saveNew(regist, "입찰공고번호", no, res);
        attachmentService.save(pbancSn, userId, attachmentService.readBidFiles(item));
        return new NoticeRegisterResultDTO(pbancSn, regist.getPbancNm(), "입찰공고", no, null);
    }

    /**
     * 이 입찰공고가 이미 등록한 발주계획·사전규격과 같은 사업이면 막는다. 같은 사업은 한 행으로 둔다.
     * 두 번호는 입찰공고 API 응답(bfSpecRgstNo, orderPlanUntyNo)에서 꺼낸다. 통합공개 API 와 실측
     * 59건이 모두 같아서(2026-10-27) 따로 묻지 않는다.
     */
    private void rejectIfSameBizRegistered(String specNo, String planNo) {
        if (specNo != null && bidNoticeMapper.countByBfSpecRgstNo(specNo) > 0) {
            throw new BusinessException("이 입찰공고는 이미 등록된 사전규격(" + specNo + ")과 같은 사업입니다. "
                    + "같은 사업은 한 줄로 관리하므로 새로 등록하지 않습니다.");
        }
        if (planNo != null && bidNoticeMapper.countByOrderPlanNo(planNo) > 0) {
            throw new BusinessException("이 입찰공고는 이미 등록된 발주계획(" + planNo + ")과 같은 사업입니다. "
                    + "같은 사업은 한 줄로 관리하므로 새로 등록하지 않습니다.");
        }
    }

    /**
     * 새 공고를 세 표에 함께 넣는다. 어떤 번호로 등록하든 절차는 같다.
     *   공고 → 수집이력 1번(API 응답 원문) → 처리이력(공고 등록)
     * 단계는 regist 에 채워 둔 값을 수집이력에도 그대로 쓴다.
     *
     * @param regist API 응답을 옮겨 둔 공고. 단계(pbancStepCd)·등록자(rgtrId)가 채워져 있어야 한다
     * @param noNm   번호 이름. 처리이력에 "발주계획번호 R26DD…" 처럼 남긴다
     * @param no     정규화한 번호
     * @param res    수집이력에 원문을 남길 API 응답
     * @return 새 공고일련번호
     */
    private Long saveNew(NoticeRegisterDTO regist, String noNm, String no, G2bResponse res) {
        bidNoticeMapper.insertNotice(regist);
        Long pbancSn = regist.getPbancSn();

        CollectHistoryDTO history = new CollectHistoryDTO();
        history.setPbancSn(pbancSn);
        history.setClctSeq(1);
        history.setPbancStepCd(regist.getPbancStepCd());
        history.setPbancNo(no);
        // 나중에 정정·전환이 나면 상세의 '변경 내용' 이 이 값들을 이전 값으로 보여 준다.
        history.setBidPbancCycl(regist.getBidPbancCycl());
        history.setPbancTypeCd(regist.getPbancTypeCd());
        history.setAltmntBgtAmt(regist.getAltmntBgtAmt());
        history.setBidDdlnDt(regist.getBidDdlnDt());
        history.setOpengDt(regist.getOpengDt());
        history.setApiRspnsCn(res.getRawJson());
        bidNoticeMapper.insertCollectHistory(history);

        bidNoticeMapper.insertProcessHistory(
                new ProcessHistoryDTO(pbancSn, BidCode.PRCS_REGIST, noNm + " " + no, regist.getRgtrId()));
        return pbancSn;
    }

    /** 응답의 첫 건. 없으면 사람이 번호를 잘못 넣은 것이므로 다시 확인하라고 알린다. */
    private static JsonNode firstOrThrow(G2bResponse res, String noNm, String no) {
        JsonNode item = res.first();
        if (item == null) {
            throw new BusinessException(
                    "나라장터에서 이 " + noNm + "를 찾을 수 없습니다. 번호를 다시 확인하세요. (" + no + ")");
        }
        return item;
    }

    /**
     * 등록 시점에 이미 나 있는 입찰공고번호. 없으면 null. 화면에서 "이미 나 있습니다"로 안내한다.
     * 발주계획은 16자리, 사전규격은 13자리로 오므로 13자리로 맞춘다.
     */
    private static String linkedBidNoOf(JsonNode item) {
        return bidNoOf(firstOf(G2bResponse.textOf(item, "bidNtceNoList")));
    }

    /** 앞뒤 공백을 없애고 대문자로 맞춘다. 사람이 소문자로 넣는 경우가 많다. */
    private static String normalize(String raw) {
        if (raw == null) {
            throw new BusinessException("공고번호를 입력하세요.");
        }
        String no = raw.trim().toUpperCase().replace(" ", "");
        if (no.isEmpty()) {
            throw new BusinessException("공고번호를 입력하세요.");
        }
        return no;
    }

    /** Y/N 이 아닌 값이 오면 넣지 않는다. CHECK 제약에 걸리기 때문이다. */
    private static String yn(String value) {
        return "Y".equals(value) || "N".equals(value) ? value : null;
    }

    /**
     * 발주계획의 bidNtceNoList 는 공고번호 13자리 뒤에 차수 3자리가 붙어 16자리로 온다.
     * (예: R26BK01790068000) 사전규격 쪽은 13자리라 맞춰 두지 않으면 같은 공고로 보이지 않는다.
     */
    private static String bidNoOf(String no) {
        if (no != null && no.length() == 16 && no.startsWith("R")) {
            return no.substring(0, 13);
        }
        return no;
    }

    /** bidNtceNoList 는 "번호1,번호2" 형태로 올 수 있다. 첫 번호만 본다. */
    private static String firstOf(String csv) {
        if (csv == null) {
            return null;
        }
        String first = csv.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }
}
