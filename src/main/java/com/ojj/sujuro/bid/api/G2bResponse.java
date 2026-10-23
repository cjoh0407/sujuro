package com.ojj.sujuro.bid.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 나라장터 응답 한 건. items 와 응답 원문을 함께 들고 있다.
 *
 * 원문을 그대로 보관하는 이유는 나중에 "그때 API가 뭐라고 했는지"를 따질 수 있어야 하기 때문이다.
 * TB_PBANC_CLCT_HSTRY.API_RSPNS_CN 에 넣는다.
 *
 * 응답의 숫자·날짜는 모두 문자열로 오므로 꺼낼 때 바꿔 준다. 빈 문자열("")도 자주 온다.
 */
public class G2bResponse {

    private final JsonNode items;
    private final String rawJson;

    public G2bResponse(JsonNode items, String rawJson) {
        this.items = items;
        this.rawJson = rawJson;
    }

    public String getRawJson() {
        return rawJson;
    }

    /** 가져온 건이 없으면 true */
    public boolean isEmpty() {
        return first() == null;
    }

    /** 첫 번째 item. 없으면 null */
    public JsonNode first() {
        if (items == null || !items.isArray() || items.size() == 0) {
            return null;
        }
        return items.get(0);
    }

    /** 모든 item */
    public List<JsonNode> all() {
        List<JsonNode> list = new ArrayList<>();
        if (items != null && items.isArray()) {
            items.forEach(list::add);
        }
        return list;
    }

    /**
     * 차수(bidNtceOrd)가 가장 큰 item. 입찰공고를 번호로 조회하면 차수별로 여러 건이 오는데,
     * 현재값은 마지막 차수여야 한다. 첫 건을 쓰면 최초 등록 내용이 남아 정정이 반영되지 않는다.
     *
     * 2026-10-20 실측: R26BK01760594 를 번호로 조회하면 000(등록공고)과 001(변경공고) 두 건이 왔다.
     * 아직 부르는 곳이 없다. 입찰공고 등록과 수집에서 쓴다.
     */
    public JsonNode latestOrd() {
        JsonNode best = null;
        int bestOrd = -1;
        for (JsonNode item : all()) {
            int ord = intOf(item, "bidNtceOrd", -1);
            if (ord > bestOrd) {
                bestOrd = ord;
                best = item;
            }
        }
        return best;
    }

    /** 문자열 값. 비었으면 null */
    public static String textOf(JsonNode item, String field) {
        if (item == null) {
            return null;
        }
        String v = item.path(field).asText("");
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    /** 숫자 값. 비었거나 숫자가 아니면 null */
    public static BigDecimal amountOf(JsonNode item, String field) {
        String v = textOf(item, field);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "000" 처럼 0으로 채워 온 차수를 숫자로 바꾼다. */
    public static int intOf(JsonNode item, String field, int whenMissing) {
        String v = textOf(item, field);
        if (v == null) {
            return whenMissing;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return whenMissing;
        }
    }
}
