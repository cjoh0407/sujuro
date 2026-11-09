package com.ojj.sujuro.proposal.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 제안요청서 PDF 요구사항 추출 결과 (FR-050-1). 추출 → 미리보기(세션에 보관) → 사람이 확인 → 등록 순서로 쓴다.
 * 총괄표의 개수와 실제로 뽑은 개수가 다르면 warnings에 담아 경고만 보여 주고 등록은 막지 않는다.
 */
@Getter
@Setter
public class RqtExtractDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long pbancSn;
    /** 올린 PDF 파일 이름 (화면 표시용, 파일은 저장하지 않는다) */
    private String fileNm;
    /** 뽑은 요구사항 (목록표 순서) */
    private List<RqtExtractItemDto> items = new ArrayList<>();
    /** 총괄표의 ID 접두어별 요구사항 수 (예: SFR → 7). 총괄표를 못 찾으면 비어 있다 */
    private Map<String, Integer> expected = new LinkedHashMap<>();
    /** 총괄표의 ID 접두어별 구분명 (예: SFR → 기능 요구사항, COR → 제약사항). 추출 결과를 이 구분별로 묶어 보여 준다 (11/09) */
    private Map<String, String> groupNames = new LinkedHashMap<>();
    /** 총괄표에 적힌 합계. 못 찾으면 null */
    private Integer declaredTotal;
    /** 개수가 맞지 않는 등 확인이 필요한 내용 */
    private List<String> warnings = new ArrayList<>();

    /** 총괄표 접두어별 개수의 합 */
    public int getExpectedSum() {
        int sum = 0;
        for (Integer n : expected.values()) {
            sum += n;
        }
        return sum;
    }

    public int getFoundCount() {
        return items.size();
    }

    /** 새로 등록될 수 있는 수 (이미 등록된 ID 제외) */
    public int getNewCount() {
        int n = 0;
        for (RqtExtractItemDto i : items) {
            if (!i.isExists()) {
                n++;
            }
        }
        return n;
    }

    private static String prefixOf(RqtExtractItemDto i) {
        String id = i.getRqtMttrNo();
        int k = id == null ? -1 : id.indexOf('-');
        return k < 0 ? (id == null ? "" : id) : id.substring(0, k);
    }

    /** 추출 결과 표: idx번째 줄 위에 구분 제목 줄을 넣을지 (첫 줄이거나 접두어가 앞 줄과 다를 때) */
    public boolean isGroupStart(int idx) {
        return idx == 0 || !prefixOf(items.get(idx)).equals(prefixOf(items.get(idx - 1)));
    }

    /** 구분 제목. 총괄표에서 구분명을 읽었으면 "기능 요구사항", 못 읽었으면 접두어(SFR) */
    public String groupTitle(int idx) {
        String p = prefixOf(items.get(idx));
        return groupNames == null ? p : groupNames.getOrDefault(p, p);
    }

    /** 구분 제목 옆 작은 글자: 구분명을 읽었으면 "SFR · 7개", 아니면 "7개" */
    public String groupSub(int idx) {
        String p = prefixOf(items.get(idx));
        int n = 0;
        for (int k = idx; k < items.size() && prefixOf(items.get(k)).equals(p); k++) {
            n++;
        }
        return (groupNames != null && groupNames.containsKey(p) ? p + " · " : "") + n + "개";
    }

    /** 접두어별로 뽑은 개수 (총괄표와 비교용) */
    public Map<String, Integer> getFound() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (RqtExtractItemDto i : items) {
            String p = i.getRqtMttrNo().substring(0, i.getRqtMttrNo().indexOf('-'));
            m.merge(p, 1, Integer::sum);
        }
        return m;
    }
}
