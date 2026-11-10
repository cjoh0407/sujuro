package com.ojj.sujuro.proposal.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

/**
 * 요구사항 구분별 묶음 (11/09). 미매핑 요구사항 표를 "기능 요구사항 · SFR · 7건" 제목 줄로 묶어 보여 준다.
 * 구분명은 PDF 추출 때 저장한 이름(RQT_MTTR_SE_NM)을 먼저 쓰고, 없으면(수기 등록·예전 데이터) 흔한 ID 접두어 이름, 그래도 없으면 접두어.
 * 목록은 이미 접두어별로 정렬돼 있다(RqtServiceImpl.sortById). 붙어 있는 같은 접두어끼리 한 묶음.
 */
@Getter
public class RqtGroupDto {

    /** 공공 제안요청서에서 흔히 쓰는 ID 접두어 → 구분명 (저장된 구분명이 없을 때만 쓴다) */
    private static final Map<String, String> DEFAULT_NAMES = new HashMap<>();
    static {
        DEFAULT_NAMES.put("SFR", "기능 요구사항");
        DEFAULT_NAMES.put("PER", "성능 요구사항");
        DEFAULT_NAMES.put("SIR", "인터페이스 요구사항");
        DEFAULT_NAMES.put("INR", "인터페이스 요구사항");
        DEFAULT_NAMES.put("DAR", "데이터 요구사항");
        DEFAULT_NAMES.put("TER", "테스트 요구사항");
        DEFAULT_NAMES.put("SER", "보안 요구사항");
        DEFAULT_NAMES.put("QUR", "품질 요구사항");
        DEFAULT_NAMES.put("COR", "제약사항");
        DEFAULT_NAMES.put("PMR", "프로젝트관리 요구사항");
        DEFAULT_NAMES.put("PSR", "프로젝트지원 요구사항");
        DEFAULT_NAMES.put("MHR", "유지관리 인력 요구사항");
        DEFAULT_NAMES.put("ECR", "시스템 장비 구성 요구사항");
    }

    private final String prefix;
    private String title;
    /** 표에 보일 요구사항 (미매핑 표면 아직 연결 안 된 것만) */
    private final List<RqtDto> items = new ArrayList<>();
    /** 이 구분의 전체 요구사항 수 (연결된 것 포함) */
    private int total;

    private RqtGroupDto(String prefix) {
        this.prefix = prefix;
    }

    /** 제목 옆 작은 글자: "SFR · 미매핑 3 / 7건" (모두 보이면 "SFR · 7건", 제목이 접두어 그대로면 접두어 생략) */
    public String getSub() {
        String head = prefix.equals(title) || prefix.isEmpty() ? "" : prefix + " · ";
        return head + (items.size() == total ? total + "건" : "미매핑 " + items.size() + " / " + total + "건");
    }

    /** 정렬된 요구사항 목록을 붙어 있는 같은 접두어끼리 묶는다 (모두 보임) */
    public static List<RqtGroupDto> of(List<RqtDto> sorted) {
        return of(sorted, false);
    }

    /**
     * 정렬된 전체 요구사항을 구분별로 묶는다. unmappedOnly 면 표에는 미매핑만 담지만,
     * 다 연결해 비어도 구분 제목 줄은 남긴다 (11/09: 다 매핑해도 목차 부분이 사라지지 않게)
     */
    public static List<RqtGroupDto> of(List<RqtDto> sorted, boolean unmappedOnly) {
        List<RqtGroupDto> out = new ArrayList<>();
        RqtGroupDto cur = null;
        for (RqtDto q : sorted) {
            String p = q.getPrefix();
            if (cur == null || !cur.prefix.equals(p)) {
                cur = new RqtGroupDto(p);
                out.add(cur);
            }
            cur.total++;
            if (!unmappedOnly || q.isUnmapped()) {
                cur.items.add(q);
            }
            if (cur.title == null && q.getRqtSeNm() != null && !q.getRqtSeNm().trim().isEmpty()) {
                cur.title = q.getRqtSeNm().trim();
            }
        }
        for (RqtGroupDto g : out) {
            if (g.title == null) {
                g.title = DEFAULT_NAMES.getOrDefault(g.prefix, g.prefix.isEmpty() ? "기타" : g.prefix);
            }
        }
        return out;
    }
}
