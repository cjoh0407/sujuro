package com.ojj.sujuro.history.service;

import com.ojj.sujuro.history.dto.HistoryDto;

import java.util.List;

/**
 * 공고 처리 이력 (NFR-008). 배정, 참여 결정, 제안팀 변경, 나라장터 등록 완료, 결과 등록처럼
 * "누가·언제·무엇을 했는지"를 남기고 보여 준다.
 *
 * 사용 예 (참여 결정 처리 서비스 안에서):
 *   historyService.record(pbancSn, ProcessType.PARTICIPATION_DECIDED, me.getEmpNo(), teamSn, "인력 확보 가능");
 *
 * - 이력은 호출한 쪽의 트랜잭션에 함께 묶인다. 처리가 실패해 되돌려지면 이력도 함께 사라진다.
 * - 이력은 쌓기만 하고 고치거나 지우지 않는다.
 */
public interface HistoryService {

    /**
     * 이력을 한 줄 남긴다.
     * @param processTypeCd 처리구분({@link ProcessType})
     * @param processorId   처리한 사람의 사번
     * @param targetTeamSn  배정·결정 대상 팀(없으면 null)
     * @param reason        처리 사유(불참·철회는 필수, 나머지는 null 가능)
     */
    void record(Long pbancSn, String processTypeCd, String processorId, Long targetTeamSn, String reason);

    /** 대상 팀과 사유가 없는 처리 (예: 나라장터 등록) */
    void record(Long pbancSn, String processTypeCd, String processorId);

    /** 그 공고의 이력 목록(오래된 순) */
    List<HistoryDto> findByPbanc(Long pbancSn);
}
