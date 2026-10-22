package com.ojj.sujuro.bid;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공고 조회 (FR-017 ~ 020) */
@Service
@Transactional(readOnly = true)
public class BidNoticeService {

    private final BidNoticeMapper bidNoticeMapper;

    public BidNoticeService(BidNoticeMapper bidNoticeMapper) {
        this.bidNoticeMapper = bidNoticeMapper;
    }

    /**
     * 공고 목록 한 페이지를 읽는다.
     *
     * 건수를 먼저 세어 페이지 수를 구하고, 요청한 페이지가 마지막 페이지를 넘으면 당긴 뒤 조회한다.
     * (탭을 바꿔 건수가 줄었는데 page 가 그대로 남아 있으면 빈 목록이 보이기 때문이다.)
     */
    public BidNoticePage findPage(BidNoticeSearch search) {
        search.normalize();

        int total = bidNoticeMapper.countNotices(search);
        int totalPages = Math.max(1, (total + BidNoticeSearch.PAGE_SIZE - 1) / BidNoticeSearch.PAGE_SIZE);
        search.clampPage(totalPages);

        List<BidNotice> rows = total == 0 ? List.of() : bidNoticeMapper.findNotices(search);
        return new BidNoticePage(rows, total, search.getPage(), totalPages);
    }

    /** 목록 위 탭. 상태별 건수로, 건수가 0인 상태도 들어 있다. */
    public List<StatusCount> findStatusCounts() {
        return bidNoticeMapper.findStatusCounts();
    }
}
