package com.ojj.sujuro.bid.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 공고수집이력에 넣어 둔 API 원문(TB_PBANC_CLCT_HSTRY.API_RSPNS_CN)을 다시 읽는다.
 * 공고명·사업 내용처럼 이력 컬럼에 따로 두지 않은 값을 공고 상세에서 보여 줄 때 쓴다.
 * 첨부파일은 원문이 아니라 TB_ATCH_FILE 에서 읽는다 (NoticeAttachmentService).
 *
 * 원문은 두 모양으로 들어 있다.
 *   등록 때 : 응답 전체 {"response":{"body":{"items":[ … ]}}}
 *   수집 때 : 공고 한 건 { "bidNtceNo": … }
 * 어느 쪽이든 공고 한 건(item)을 꺼내 준다. 못 읽으면 null 이고, 그때 화면은 그 값만 비워 둔다.
 */
public final class G2bSnapshot {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private G2bSnapshot() {
    }

    /**
     * 원문에서 공고 한 건을 꺼낸다. 입찰공고를 번호로 조회한 응답은 차수별로 여러 건이라
     * 마지막 차수를 고른다. 발주계획·사전규격 응답에는 차수가 없으므로 첫 건이다.
     */
    public static JsonNode itemOf(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(rawJson);
        } catch (Exception e) {
            return null;
        }
        JsonNode items = root.path("response").path("body").path("items");
        if (items.isMissingNode()) {
            return root.isObject() ? root : null;
        }
        G2bResponse res = new G2bResponse(items, rawJson, 0);
        JsonNode latest = res.latestOrd();
        return latest != null ? latest : res.first();
    }

    /** 사업명. API 마다 이름 필드가 다르다 (입찰공고 bidNtceNm / 사전규격 prdctClsfcNoNm / 발주계획 bizNm). */
    public static String bizNmOf(JsonNode item) {
        String nm = G2bResponse.textOf(item, "bidNtceNm");
        if (nm == null) {
            nm = G2bResponse.textOf(item, "prdctClsfcNoNm");
        }
        if (nm == null) {
            nm = G2bResponse.textOf(item, "bizNm");
        }
        return nm;
    }
}
