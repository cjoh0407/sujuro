package com.ojj.sujuro.bid.dto;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공고 목록 한 페이지의 조회 결과. 목록과 함께 화면에 필요한 페이지 정보를 담는다.
 * 페이지 번호는 1부터 센다.
 */
@Getter
@RequiredArgsConstructor
public class BidNoticePageDTO {

    private final List<BidNoticeDTO> rows;

    /** 검색 조건에 맞는 전체 건수 */
    private final int total;

    /** 지금 보고 있는 페이지 */
    private final int page;

    /** 전체 페이지 수. 건수가 0이어도 1이다. */
    private final int totalPages;

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public boolean isHasPrev() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < totalPages;
    }

    /** 목록의 번호 칸 (11/06). 전체 누적 순번이다. 1페이지 1~10, 2페이지 11~20. index 는 이 페이지에서 0부터 센다 */
    public int rowNo(int index) {
        return (page - 1) * BidNoticeSearchDTO.PAGE_SIZE + index + 1;
    }
}
