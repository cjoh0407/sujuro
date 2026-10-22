package com.ojj.sujuro.bid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고를 새로 넣을 때 쓰는 값 묶음 (BidNoticeMapper.insertNotice).
 * 사전규격으로 등록할 때와 본공고로 등록할 때가 채우는 칸이 다르다. 안 채운 칸은 null 로 들어간다.
 *
 * insert 가 끝나면 pbancSn 에 새로 생긴 공고일련번호가 담긴다.
 */
@Getter
@Setter
public class NoticeRegist {

    /** 나라장터 응답의 날짜 형식. 예: 2026-10-19 23:59:00 */
    private static final DateTimeFormatter API_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long pbancSn;

    // 사전규격·본공고 공통
    private String pbancStepCd;
    private String trckSttsCd;
    private String pbancNm;
    private String orderInstNm;
    private String dmndInstNm;
    private String swBizYn;
    private BigDecimal altmntBgtAmt;
    private String pbancSttsCd = BidCode.STTS_NEW;
    private String rgtrId;

    // 사전규격으로 등록할 때
    private String bfSpecRgstNo;
    private LocalDateTime opnnDdlnDt;

    // 본공고로 등록할 때
    private String bidPbancNo;
    private Integer bidPbancCycl;
    private String ntceKindNm;
    private LocalDateTime bidDdlnDt;
    private LocalDateTime opengDt;
    private String evlMthdNm;
    private String pbancDtlUrl;

    /** "2026-10-19 23:59:00" 을 날짜로 바꾼다. 형식이 다르거나 비었으면 null */
    public static LocalDateTime parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), API_DATE);
        } catch (Exception e) {
            return null;
        }
    }

}
