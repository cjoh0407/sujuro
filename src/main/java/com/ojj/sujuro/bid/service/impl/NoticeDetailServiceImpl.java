package com.ojj.sujuro.bid.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.api.G2bSnapshot;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.InfoItemDTO;
import com.ojj.sujuro.bid.dto.NoticeDetailDTO;
import com.ojj.sujuro.bid.dto.StageChangeDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeAttachmentService;
import com.ojj.sujuro.bid.service.NoticeDetailService;

/**
 * 공고 상세 (FR-035, 035-1, 036).
 *
 * 공고 행 하나에 수집이력을 붙여 읽는다. 같은 사업은 한 행이라(FR-025-1) 앞 단계의 값은 행에 남아 있지 않고
 * 수집이력에만 있다. 그래서 단계 이력과 변경 비교는 수집이력으로 만든다.
 */
@Service
@Transactional(readOnly = true)
public class NoticeDetailServiceImpl implements NoticeDetailService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BidNoticeMapper bidNoticeMapper;
    private final NoticeAttachmentService attachmentService;

    public NoticeDetailServiceImpl(BidNoticeMapper bidNoticeMapper, NoticeAttachmentService attachmentService) {
        this.bidNoticeMapper = bidNoticeMapper;
        this.attachmentService = attachmentService;
    }

    @Override
    public NoticeDetailDTO findDetail(Long pbancSn) {
        NoticeDetailDTO detail = bidNoticeMapper.findDetail(pbancSn);
        if (detail == null) {
            return null;
        }
        List<CollectHistoryDTO> histories = bidNoticeMapper.findCollectHistories(pbancSn);
        detail.setStageHistories(histories);
        detail.setStageChange(compareLastTwo(histories));
        detail.setAtchFiles(attachmentService.findFiles(pbancSn));

        JsonNode plan = latestItemOf(histories, BidCode.STEP_ORDER_PLAN);
        JsonNode spec = latestItemOf(histories, BidCode.STEP_PRE_SPEC);
        JsonNode bid = latestItemOf(histories, BidCode.STEP_BID_NOTICE);
        detail.setBizInfos(bizInfosOf(detail, plan, spec, bid));
        detail.setEvlRatioText(evlRatioOf(bid));
        return detail;
    }

    /**
     * 사업 내용. 세 단계 API 가 각자 다른 항목을 주므로 단계별 마지막 원문에서 모은다.
     * 발주계획·사전규격 원문은 그 단계로 등록했을 때만 있다. 입찰공고로 바로 등록한 공고는
     * 발주 시기·납품 기한 같은 앞 단계 항목이 빠진다.
     *
     * 사업 목적·과업 범위 같은 서술 필드는 세 API 어디에도 없다 (2026-10-27 실호출 확인).
     * 발주계획의 규격내용·비고내용(specCntnts·rmrkCntnts)은 물품·공사용이라 용역 235건에서 모두 비어 있었다 (10/28).
     * 그 내용은 첨부된 과업지시서·제안요청서에 있다.
     * SW사업 여부는 10/28 사용자 요청으로 뺐다. 평가 방식의 "(SW사업)" 과 기술:가격 90:10 으로 드러난다.
     * 기관 공고번호(refNo)도 10/28 뺐다. 기관 내부 문서번호·"1" 같은 값이 섞여 오고 업무에 쓰지 않는다.
     */
    private static List<InfoItemDTO> bizInfosOf(NoticeDetailDTO detail, JsonNode plan, JsonNode spec, JsonNode bid) {
        List<InfoItemDTO> infos = new ArrayList<>();
        // 공공조달분류. 대분류 끝에 공백이 붙어 오기도 한다. 예) "연구조사서비스 › 시장 및 여론조사"
        add(infos, "사업 분야", joinNonNull(" › ", text(bid, "pubPrcrmntLrgClsfcNm"), text(bid, "pubPrcrmntMidClsfcNm")));
        add(infos, "사업 구분", text(plan, "bsnsTyNm"));                      // 신규(단기), 계속 …
        add(infos, "사업 기간", detail.getBizPrdCn());
        add(infos, "납품 기한", dateOf(text(spec, "dlvrTmlmtDt")));           // "2027-03-31 00:00:00"
        String presmpt = NoticeDetailDTO.wonOf(G2bResponse.amountOf(bid, "presmptPrce"));
        add(infos, "추정가격", presmpt == null ? null : presmpt + " (부가세 별도)");
        add(infos, "계약 방법", firstNonNull(text(bid, "cntrctCnclsMthdNm"), text(plan, "cntrctMthdNm")));
        add(infos, "공동수급", jointOf(text(bid, "cmmnSpldmdMethdNm")));
        String orderYear = text(plan, "orderYear");
        String orderMnth = text(plan, "orderMnth");
        add(infos, "발주 시기", orderYear == null || orderMnth == null ? null : orderYear + "년 " + orderMnth + "월");
        add(infos, "조달 방식", text(plan, "prcrmntMethd"));                   // 자체조달 / 조달청 위탁
        add(infos, "발주 부서", text(plan, "deptNm"));
        add(infos, "담당자", firstNonNull(
                contactOf(text(bid, "ntceInsttOfclNm"), text(bid, "ntceInsttOfclTelNo")),
                contactOf(text(spec, "ofclNm"), text(spec, "ofclTelNo")),
                contactOf(text(plan, "ofclNm"), text(plan, "telNo"))));
        return infos;
    }

    /**
     * 기술:가격 평가 비율 (입찰공고 techAbltEvlRt / bidPrceEvlRt).
     * 협상에 의한 계약에만 값이 있다. 실측 156건에서 80:20 이 77건, SW사업은 거의 90:10 이었다.
     */
    private static String evlRatioOf(JsonNode bid) {
        String tech = text(bid, "techAbltEvlRt");
        String price = text(bid, "bidPrceEvlRt");
        if (tech == null) {
            return null;
        }
        return "기술 " + tech + " : 가격 " + (price == null ? "0" : price);
    }

    /** 이 단계 수집 중 가장 최근 원문의 공고 한 건. 없거나 못 읽으면 null */
    private static JsonNode latestItemOf(List<CollectHistoryDTO> histories, String stepCd) {
        for (int i = histories.size() - 1; i >= 0; i--) {
            CollectHistoryDTO history = histories.get(i);
            if (BidCode.is(history.getPbancStepCd(), stepCd)) {
                JsonNode item = G2bSnapshot.itemOf(history.getApiRspnsCn());
                if (item != null) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * 공동수급방식. API 값 앞의 "(전자)" "(수기)" "(없음)" 은 접수 방법이라 떼어 낸다.
     * 컨소시엄으로 들어갈 수 있는지가 참여 형태(FR-024)를 정할 때 필요해서 보여 준다.
     *   "(없음)공동수급불허" → "불허 (단독 참여만)",  "(전자)공동이행" → "허용 · 공동이행"
     */
    private static String jointOf(String value) {
        if (value == null) {
            return null;
        }
        String method = value.replaceFirst("^\\([^)]*\\)", "").trim();
        return method.contains("불허") ? "불허 (단독 참여만)" : "허용 · " + method;
    }

    private static String contactOf(String name, String telNo) {
        if (name == null) {
            return null;
        }
        return telNo == null ? name : name + " · " + telNo;
    }

    /** "2027-03-31 00:00:00" → "2027-03-31" */
    private static String dateOf(String dateTime) {
        return dateTime == null || dateTime.length() < 10 ? dateTime : dateTime.substring(0, 10);
    }

    private static String text(JsonNode item, String field) {
        return G2bResponse.textOf(item, field);
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String joinNonNull(String delimiter, String... values) {
        String joined = Arrays.stream(values).filter(Objects::nonNull).collect(Collectors.joining(delimiter));
        return joined.isEmpty() ? null : joined;
    }

    private static void add(List<InfoItemDTO> infos, String label, String value) {
        if (value != null && !value.isBlank()) {
            infos.add(new InfoItemDTO(label, value));
        }
    }

    /**
     * 마지막 수집과 그 직전 수집을 나란히 놓는다 (FR-035-1).
     * 수집이력은 등록할 때 한 번, 그 뒤로는 단계가 바뀌거나 정정이 날 때만 쌓이므로
     * 마지막 두 건이 곧 "이전 단계(또는 이전 차수)와 지금"이다. 개찰·계약 수집은 빼고 센다.
     *
     * @return 수집이 한 번뿐이거나 비교할 값이 하나도 없으면 null
     */
    private StageChangeDTO compareLastTwo(List<CollectHistoryDTO> allHistories) {
        // 개찰·계약 수집은 다른 API 원문이라 공고 값이 없다. 입찰공고까지의 수집끼리만 비교한다.
        List<CollectHistoryDTO> histories = allHistories.stream()
                .filter(h -> !BidCode.is(h.getPbancStepCd(), BidCode.STEP_OPENING)
                          && !BidCode.is(h.getPbancStepCd(), BidCode.STEP_CONTRACT))
                .collect(Collectors.toList());
        if (histories.size() < 2) {
            return null;
        }
        CollectHistoryDTO before = histories.get(histories.size() - 2);
        CollectHistoryDTO after = histories.get(histories.size() - 1);

        StageChangeDTO change = new StageChangeDTO();
        change.setBeforeLabel(labelOf(before));
        change.setAfterLabel(labelOf(after));
        change.setBeforeClctDt(before.getClctDt());
        change.setAfterClctDt(after.getClctDt());

        List<StageChangeDTO.Item> items = change.getItems();
        items.add(new StageChangeDTO.Item("공고명", bizNmOf(before), bizNmOf(after)));
        items.add(new StageChangeDTO.Item("공고종류", before.getPbancTypeNm(), after.getPbancTypeNm()));
        items.add(new StageChangeDTO.Item("예산",
                NoticeDetailDTO.wonOf(before.getAltmntBgtAmt()), NoticeDetailDTO.wonOf(after.getAltmntBgtAmt())));
        items.add(new StageChangeDTO.Item("입찰 마감", dateTimeOf(before.getBidDdlnDt()), dateTimeOf(after.getBidDdlnDt())));
        items.add(new StageChangeDTO.Item("개찰일시", dateTimeOf(before.getOpengDt()), dateTimeOf(after.getOpengDt())));
        // 양쪽 다 값이 없는 항목은 비교할 것이 없다. (예: 발주계획 → 사전규격은 마감일이 둘 다 없다)
        items.removeIf(item -> item.getBefore() == null && item.getAfter() == null);
        return items.isEmpty() ? null : change;
    }

    /** 예: "사전규격 R26BD00011088", 입찰공고면 차수까지 "입찰공고 R26BK00946730 · 1차" */
    private static String labelOf(CollectHistoryDTO history) {
        String label = history.getPbancStepNm() + " " + history.getPbancNo();
        if (BidCode.is(history.getPbancStepCd(), BidCode.STEP_BID_NOTICE) && history.getBidPbancCycl() != null) {
            label += " · " + history.getBidPbancCycl() + "차";
        }
        return label;
    }

    /** 이력 컬럼에 공고명이 없어 원문에서 꺼낸다. 원문이 없으면 null */
    private static String bizNmOf(CollectHistoryDTO history) {
        return G2bSnapshot.bizNmOf(G2bSnapshot.itemOf(history.getApiRspnsCn()));
    }

    private static String dateTimeOf(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
    }
}
