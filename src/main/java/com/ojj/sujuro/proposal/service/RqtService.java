package com.ojj.sujuro.proposal.service;

import java.util.List;

import com.ojj.sujuro.proposal.dto.RqtDto;

/** 요구사항 등록·수정·삭제, 목차 매핑과 현황 계산 (FR-050~054, FR-016-1) */
public interface RqtService {

    String FILTER_ALL = "all";
    String FILTER_UNMAPPED = "unmapped";
    String FILTER_MINE = "mine";

    List<RqtDto> list(Long pbancSn);

    /** 필터: 전체 / 미매핑 / 내 담당(연결된 목차의 담당자가 나) (FR-054) */
    List<RqtDto> filter(List<RqtDto> all, String filter, String userId);

    int unmappedCount(List<RqtDto> all);

    /** 매핑률(%). 요구사항이 없으면 0 (FR-053) */
    int progress(List<RqtDto> all);

    /** 수정 화면에 채울 요구사항 한 건. 이 사업건 것이 아니면 예외 */
    RqtDto findForEdit(Long pbancSn, Long rqtMttrSn);

    void register(Long pbancSn, RqtDto in, String userId);

    void edit(Long pbancSn, Long rqtMttrSn, RqtDto in);

    void delete(Long pbancSn, Long rqtMttrSn);

    /**
     * 요구사항 등록·수정·삭제·매핑을 할 수 있는지. 이 사업건 주관팀 팀장(lead)이거나,
     * 이 사업건 목차를 하나 이상 맡은 담당자면 된다. (FR-016-1)
     */
    boolean canEdit(Long pbancSn, String userId, boolean lead);

    /** canEdit이 아니면 업무 예외 */
    void requireEditor(Long pbancSn, String userId, boolean lead);

    /**
     * 요구사항을 제안서 최하위 목차에 연결한다. tocSn이 null이면 미매핑으로 돌린다. (FR-051)
     * 요구사항 하나는 최하위 목차 하나에만 연결되고(비기능 요구사항 포함), 담당자를 지정한 최하위 목차에만 연결할 수 있다. (FR-049, 요구사항 v3.2)
     * 연결된 목차의 담당자가 요구사항 담당자가 된다.
     */
    void map(Long pbancSn, Long rqtMttrSn, Long tocSn);
}
