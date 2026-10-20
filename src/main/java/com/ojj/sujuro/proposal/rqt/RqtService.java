package com.ojj.sujuro.proposal.rqt;

import java.util.List;

/** 요구사항 등록·수정·삭제와 현황 계산 (FR-050, FR-052~054) */
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
}
