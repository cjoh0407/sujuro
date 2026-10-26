package com.ojj.sujuro.proposal.toc;

import java.util.List;
import java.util.Map;

/**
 * 사업건 목차 (FR-046~049, FR-056, FR-063).
 * 목차 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 저장된 양식은 바뀌지 않는다.
 * 제안서와 제안요약서 목차는 tocSeCd(TocDto.SE_PROPOSAL / SE_SUMMARY)로 나눠 따로 관리한다.
 * 목차는 큰 목차 > 세부 목차 2단계이고, 담당자·쪽수·요구사항은 세부 목차에만 둔다. (화면 설계 v3.4)
 */
public interface TocService {

    /** 사업건의 목차 전체 (큰 목차 아래에 세부 목차 순서) */
    List<TocDto> list(Long pbancSn, String tocSeCd);

    /** 세부 목차 쪽수 합계 */
    int totalPages(List<TocDto> tocs);

    /** 큰 목차별 쪽수 합계. key=큰 목차 일련번호 */
    Map<Long, Integer> groupPages(List<TocDto> tocs);

    /** 큰 목차별 연결된 요구사항 수. key=큰 목차 일련번호 */
    Map<Long, Integer> groupRequirements(List<TocDto> tocs);

    /** 담당자가 아직 없는 세부 목차 수 */
    int unassignedCount(List<TocDto> tocs);

    /* ---------- 목차 양식 (FR-046~048) ---------- */

    /** 이 사용자가 불러올 수 있는 양식: 기본 양식(대법원·한국고용정보원) + 본인이 저장한 커스텀 양식 */
    List<TocFormDto> forms(String userId);

    /**
     * 고른 양식으로 이 사업건 목차를 만든다. 이미 목차가 있으면 지우고 바꾼다("다른 양식으로 바꾸기").
     * 바꿀 때 담당자 지정은 사라지고 연결된 요구사항은 모두 미매핑이 된다. 올린 파일이 있으면 바꿀 수 없다.
     * 만든 목차 개수를 돌려준다.
     */
    int importForm(Long pbancSn, String tocSeCd, Long tocFormSn, String userId);

    /** 지금 사업건 목차를 커스텀 양식으로 저장한다. 같은 이름(기본 양식·본인 양식 중)이 있으면 예외 (FR-048) */
    void saveAsForm(Long pbancSn, String tocSeCd, String formName, String userId);

    /* ---------- 목차 편집 (FR-047, FR-056) ---------- */

    /** 목차를 추가한다. upTocSn이 null이면 큰 목차, 값이 있으면 그 큰 목차 아래 세부 목차 */
    void add(Long pbancSn, String tocSeCd, Long upTocSn, String name, Integer pages);

    /** 목차 이름과 예상 쪽수를 고친다. 큰 목차는 쪽수를 두지 않는다. */
    void edit(Long pbancSn, Long tocSn, String name, Integer pages);

    /**
     * 목차를 지운다. 큰 목차는 세부 목차까지 함께 지운다. 올린 파일이 있으면 막는다. (NFR-009)
     * 연결된 요구사항은 미매핑으로 돌아가며, 그 건수를 돌려준다. (FR-056)
     */
    int delete(Long pbancSn, Long tocSn);

    /**
     * 세부 목차의 담당자를 지정한다. picId가 비어 있으면 지정을 해제한다. (FR-049)
     * 담당자는 이 사업건 제안팀원(주관팀·참여팀) 중에서만 고를 수 있다.
     * 연결된 요구사항의 담당자는 목차 담당자를 그대로 따르므로 따로 바꾸지 않는다. (FR-051)
     */
    void assign(Long pbancSn, Long tocSn, String picId);
}
