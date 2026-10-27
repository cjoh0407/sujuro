package com.ojj.sujuro.bid.service;

import com.ojj.sujuro.bid.dto.NoticeDetailDTO;

/** 공고 상세 (FR-035, 035-1, 036) */
public interface NoticeDetailService {

    /**
     * 공고 한 건을 상세 화면에 맞게 읽는다. 단계 이력, 직전 수집과의 변경 비교, 입찰공고 첨부파일까지 채운다.
     * 배정·결정 이력(FR-041)은 공통 HistoryService 가 따로 준다.
     *
     * @return 없는 공고면 null
     */
    NoticeDetailDTO findDetail(Long pbancSn);
}
