package com.ojj.sujuro.bid.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 공고 목록의 번호 칸(전체 누적 순번)과 주관팀 필터 값 (11/06) */
class BidNoticePagingTest {

    @Test
    void 번호는_페이지를_이어서_센다() {
        assertEquals(1, new BidNoticePageDTO(List.of(), 35, 1, 4).rowNo(0));
        assertEquals(10, new BidNoticePageDTO(List.of(), 35, 1, 4).rowNo(9));
        assertEquals(11, new BidNoticePageDTO(List.of(), 35, 2, 4).rowNo(0));
        assertEquals(20, new BidNoticePageDTO(List.of(), 35, 2, 4).rowNo(9));
        assertEquals(35, new BidNoticePageDTO(List.of(), 35, 4, 4).rowNo(4));
    }

    @Test
    void 주관팀_필터는_배정과_미배정만_남기고_나머지는_전체다() {
        assertEquals("assigned", normalized("assigned"));
        assertEquals("unassigned", normalized("unassigned"));
        assertNull(normalized(""));
        assertNull(normalized(null));
        assertNull(normalized("' OR 1=1"));
    }

    private static String normalized(String assign) {
        BidNoticeSearchDTO search = new BidNoticeSearchDTO();
        search.setAssign(assign);
        search.normalize();
        return search.getAssign();
    }
}
