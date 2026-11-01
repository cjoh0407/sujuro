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
