package com.ojj.sujuro.bid;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고 목록의 검색 조건 (FR-017 ~ 020). 화면의 쿼리스트링을 그대로 받는다.
 * 예: /notices?sttsCd=PS020&amp;searchBy=org&amp;keyword=행정안전부&amp;sort=recent&amp;page=2
 *
 * 정렬·검색 대상은 {@link #normalize()} 에서 허용된 값만 남긴다.
 * SQL 에서는 전부 #{} 로 넘기므로 값이 쿼리에 직접 끼어들지 않는다.
 */
@Getter
@Setter
public class BidNoticeSearch {

    /** 한 페이지에 보여 줄 건수. UI 목업 v3.2 와 같게 10건으로 고정한다. */
    public static final int PAGE_SIZE = 10;

    /** 검색 대상 */
    public static final String BY_NAME = "name"; // 공고명
    public static final String BY_NO = "no";     // 공고번호 (입찰공고번호 + 사전규격번호)
    public static final String BY_ORG = "org";   // 발주기관

    /** 정렬 */
    public static final String SORT_DEADLINE = "deadline"; // 마감일 빠른 순
    public static final String SORT_RECENT = "recent";     // 최신 순 (등록 역순)

    /** 공고상태코드. 비어 있으면 전체 탭 */
    private String sttsCd;
    private String searchBy = BY_NAME;
    private String keyword;
    private String sort = SORT_DEADLINE;
    private int page = 1;

    /** 허용되지 않는 값이 들어오면 기본값으로 되돌린다. 컨트롤러가 조회 전에 한 번 부른다. */
    public void normalize() {
        if (isBlank(sttsCd)) {
            sttsCd = null;
        }
        if (isBlank(keyword)) {
            keyword = null;
        } else {
            keyword = keyword.trim();
        }
        if (!BY_NO.equals(searchBy) && !BY_ORG.equals(searchBy)) {
            searchBy = BY_NAME;
        }
        if (!SORT_RECENT.equals(sort)) {
            sort = SORT_DEADLINE;
        }
        if (page < 1) {
            page = 1;
        }
    }

    /** 조회할 페이지가 마지막 페이지를 넘으면 마지막 페이지로 당긴다. */
    public void clampPage(int totalPages) {
        if (page > totalPages) {
            page = Math.max(1, totalPages);
        }
    }

    /** 건너뛸 행 수. SQL 의 OFFSET 에 쓴다. */
    public int getOffset() {
        return (page - 1) * PAGE_SIZE;
    }

    public int getSize() {
        return PAGE_SIZE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
