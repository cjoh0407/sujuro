package com.ojj.sujuro.bid.service;

import java.util.List;

import com.ojj.sujuro.bid.dto.BidNoticePageDTO;
import com.ojj.sujuro.bid.dto.CollectRunDTO;
import com.ojj.sujuro.bid.dto.BidNoticeSearchDTO;
import com.ojj.sujuro.bid.dto.StatusCountDTO;

/** 공고 조회 (FR-017 ~ 020) */
public interface BidNoticeService {

    /** 공고 목록 한 페이지를 읽는다. */
    BidNoticePageDTO findPage(BidNoticeSearchDTO search);

    /** 목록 위 탭. 상태별 건수로, 건수가 0인 상태도 들어 있다. */
    List<StatusCountDTO> findStatusCounts();

    /** 마지막 수집 실행. 목록 위 [지금 수집] 옆에 보여 준다. 없으면 null */
    CollectRunDTO findLastRun();
}
