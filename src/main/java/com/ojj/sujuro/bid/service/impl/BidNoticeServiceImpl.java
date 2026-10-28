package com.ojj.sujuro.bid.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.bid.dto.BidNoticeDTO;
import com.ojj.sujuro.bid.dto.BidNoticePageDTO;
import com.ojj.sujuro.bid.dto.CollectRunDTO;
import com.ojj.sujuro.bid.dto.BidNoticeSearchDTO;
import com.ojj.sujuro.bid.dto.StatusCountDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.BidNoticeService;

/** 공고 조회 (FR-017 ~ 020) */
@Service
@Transactional(readOnly = true)
public class BidNoticeServiceImpl implements BidNoticeService {

    private final BidNoticeMapper bidNoticeMapper;

    public BidNoticeServiceImpl(BidNoticeMapper bidNoticeMapper) {
        this.bidNoticeMapper = bidNoticeMapper;
    }

    /**
     * 건수를 먼저 세어 페이지 수를 구하고, 요청한 페이지가 마지막 페이지를 넘으면 당긴 뒤 조회한다.
     * (탭을 바꿔 건수가 줄었는데 page 가 그대로 남아 있으면 빈 목록이 보이기 때문이다.)
     */
    @Override
    public BidNoticePageDTO findPage(BidNoticeSearchDTO search) {
        search.normalize();

        int total = bidNoticeMapper.countNotices(search);
        int totalPages = Math.max(1,
                (total + BidNoticeSearchDTO.PAGE_SIZE - 1) / BidNoticeSearchDTO.PAGE_SIZE);
        search.clampPage(totalPages);

        List<BidNoticeDTO> rows = total == 0 ? List.of() : bidNoticeMapper.findNotices(search);
        return new BidNoticePageDTO(rows, total, search.getPage(), totalPages);
    }

    @Override
    public List<StatusCountDTO> findStatusCounts() {
        return bidNoticeMapper.findStatusCounts();
    }

    @Override
    public CollectRunDTO findLastRun() {
        return bidNoticeMapper.findLastRun();
    }
}
