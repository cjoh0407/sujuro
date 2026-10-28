package com.ojj.sujuro.bid.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.bid.dto.AtchFileDTO;

/**
 * 나라장터 공고 첨부 (FR-036). 등록하거나 단계가 바뀔 때 첨부 목록을 받아 TB_ATCH_FILE 에 넣고, 공고 상세가 읽는다.
 *
 * 받는 일(read…)과 넣는 일(save)을 나눈 이유: 받는 쪽은 나라장터를 여러 번 부른다(e발주 첨부 API, 파일마다
 * 헤더 읽기). 수집은 공고 하나씩 짧게 커밋하므로 DB 트랜잭션을 연 채로 네트워크를 기다리지 않게,
 * 먼저 받아 두고 트랜잭션 안에서는 넣기만 한다.
 *
 * 받는 쪽은 실패해도 예외를 던지지 않는다. 첨부가 안 와도 등록·전환은 그대로 진행해야 하기 때문이다.
 */
public interface NoticeAttachmentService {

    /**
     * 입찰공고 첨부. 입찰공고 응답의 첨부 칸(ntceSpecFileNm1~10)과 e발주 첨부 API 를 합친다.
     * 조달청 위탁 공고는 제안요청서가 e발주 쪽에만 있다. 제안요청서는 FL010, 나머지는 FL090 이다.
     *
     * @param bidItem 입찰공고 API 응답 한 건 (마지막 차수)
     */
    List<AtchFileDTO> readBidFiles(JsonNode bidItem);

    /**
     * 사전규격 규격서 (specDocFileUrl1~5, FL080). 파일명이 API 에 없어 내려받기 응답 헤더에서 읽는다.
     * 실측 21건 중 17건에 제안요청서(초안)가 있었다.
     *
     * @param specItem 사전규격 API 응답 한 건
     */
    List<AtchFileDTO> readPreSpecFiles(JsonNode specItem);

    /**
     * 받은 첨부를 공고에 넣는다. 호출한 쪽의 트랜잭션에 묶인다.
     *
     * @param rgtrId 등록자. 수집처럼 사람이 없으면 null 이고, 그때는 공고 등록자로 넣는다
     */
    void save(Long pbancSn, String rgtrId, List<AtchFileDTO> files);

    /** 공고 상세용. 입찰공고 첨부 먼저, 그다음 사전규격 규격서 */
    List<AtchFileDTO> findFiles(Long pbancSn);
}
