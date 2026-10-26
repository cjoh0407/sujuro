package com.ojj.sujuro.bid.service.impl;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.bid.BidCode;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterResultDTO;
import com.ojj.sujuro.bid.dto.ProcessHistoryDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeRegisterService;
import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 공고 최초 등록 (FR-021, 022).
 *
 * 등록 한 번에 세 표가 함께 바뀌므로 한 트랜잭션으로 묶는다.
 *   TB_BID_PBANC          현재값
 *   TB_PBANC_CLCT_HSTRY   API 응답 스냅샷 (원문 포함)
 *   TB_PBANC_PRCS_HSTRY   사람이 한 일 (관심 공고 등록)
 */
@Service
public class NoticeRegisterServiceImpl implements NoticeRegisterService {

    /** 사전규격등록번호 형식: R + 연도 2자리 + BD + 순번 8자리 (예: R26BD00283794) */
    private static final Pattern PRE_SPEC_NO = Pattern.compile("^R\\d{2}BD\\d{8}$");

    private final BidNoticeMapper bidNoticeMapper;
    private final G2bApiClient g2bApiClient;

    public NoticeRegisterServiceImpl(BidNoticeMapper bidNoticeMapper, G2bApiClient g2bApiClient) {
        this.bidNoticeMapper = bidNoticeMapper;
        this.g2bApiClient = g2bApiClient;
    }

    /**
     * 등록 시점에 이미 본공고가 올라와 있을 수 있다(응답의 bidNtceNoList). 그 경우에도 여기서
     * 본공고로 바꾸지는 않고 사전규격으로 넣은 뒤 추적 상태를 "본공고 대기"로 둔다.
     * 본공고 연결은 수집(하루 1회 · [지금 불러오기])이 맡는다.
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
        JsonNode item = res.first();
        if (item == null) {
            throw new BusinessException(
                    "나라장터에서 이 사전규격등록번호를 찾을 수 없습니다. 번호를 다시 확인하세요. (" + no + ")");
        }

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

        bidNoticeMapper.insertNotice(regist);
        Long pbancSn = regist.getPbancSn();

        CollectHistoryDTO history = new CollectHistoryDTO();
        history.setPbancSn(pbancSn);
        history.setClctSeq(1);
        history.setPbancStepCd(BidCode.STEP_PRE_SPEC);
        history.setPbancNo(no);
        history.setAltmntBgtAmt(regist.getAltmntBgtAmt());
        history.setApiRspnsCn(res.getRawJson());
        bidNoticeMapper.insertCollectHistory(history);

        bidNoticeMapper.insertProcessHistory(
                new ProcessHistoryDTO(pbancSn, BidCode.PRCS_REGIST, "사전규격등록번호 " + no, userId));

        // 이미 본공고가 올라와 있으면 화면에서 그렇게 안내한다.
        String linkedBidNo = firstOf(G2bResponse.textOf(item, "bidNtceNoList"));
        return new NoticeRegisterResultDTO(pbancSn, regist.getPbancNm(), no, linkedBidNo);
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

    /** bidNtceNoList 는 "번호1,번호2" 형태로 올 수 있다. 첫 번호만 본다. */
    private static String firstOf(String csv) {
        if (csv == null) {
            return null;
        }
        String first = csv.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }
}
