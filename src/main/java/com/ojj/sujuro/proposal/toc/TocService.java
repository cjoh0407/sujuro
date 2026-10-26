package com.ojj.sujuro.proposal.toc;

import java.util.List;

/**
 * 사업건 목차 (FR-046~049, FR-056, FR-063, 요구사항 v3.2).
 * 목차 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 저장된 양식은 바뀌지 않는다.
 * 제안서와 제안요약서 목차는 tocSeCd(TocDto.SE_PROPOSAL / SE_SUMMARY)로 나눠 따로 관리하고, 단계 규칙은 같다.
 * 목차 단계에는 제한이 없고 번호는 사용자가 입력한다. 담당자·쪽수·요구사항은 최하위 목차에만 둔다.
 * 누가 할 수 있는지(주관팀·제안팀·주관팀 팀장)는 컨트롤러에서 ProposalPerm으로 먼저 확인한다.
 */
public interface TocService {

    String MODE_CHILD = "d";   // + 하위
    String MODE_SIBLING = "s"; // + 같은 단계

    /** 사업건의 목차 전체 (부모 바로 아래에 자식 순서). 묶음 목차에는 아래 최하위 목차 수·요구사항 수·쪽수 합계를 채운다. */
    List<TocDto> list(Long pbancSn, String tocSeCd);

    /** 최하위 목차만 */
    List<TocDto> leaves(List<TocDto> tocs);

    /** 최하위 목차 쪽수 합계 */
    int totalPages(List<TocDto> tocs);

    /** 담당자가 아직 없는 최하위 목차 수 */
    int unassignedCount(List<TocDto> tocs);

    /* ---------- 목차 양식 (FR-046~048) ---------- */

    /** 이 주관팀이 저장한 양식 (해당 구분) */
    List<TocFormDto> forms(Long teamSn, String tocSeCd);

    /**
     * 고른 양식으로 이 사업건 목차를 만든다. 이미 목차가 있으면 지우고 바꾼다("다른 양식으로 바꾸기").
     * 바꿀 때 담당자 지정은 사라지고 연결된 요구사항은 모두 미매핑이 된다. 올린 파일이 있으면 바꿀 수 없다.
     * 양식은 이 사업건 주관팀(teamSn)이 저장한 것만 쓸 수 있다. 만든 목차 개수를 돌려준다.
     */
    int importForm(Long pbancSn, String tocSeCd, Long tocFormSn, Long teamSn);

    /** 지금 사업건 목차를 주관팀 양식으로 저장한다. 같은 팀에 같은 이름이 있으면 예외 (FR-048) */
    void saveAsForm(Long pbancSn, String tocSeCd, String formName, Long teamSn, String userId);

    /* ---------- 목차 편집 (FR-047, FR-056) ---------- */

    /** 1단계 목차를 맨 뒤에 추가한다. label은 번호를 포함한 이름 (예: "Ⅴ. 사업 관리") */
    void addTop(Long pbancSn, String tocSeCd, String label);

    /**
     * 기준 목차 옆에 목차를 추가한다. mode가 "d"면 기준 목차의 마지막 하위로, "s"면 기준 목차(와 그 하위) 바로 뒤 같은 단계로 넣는다.
     * 최하위 목차에 하위를 만들면 그 목차는 묶음이 되어 담당자·쪽수가 비워지고, 연결된 요구사항은 미매핑이 된다.
     * 그 목차에 올린 파일이 있으면 막는다. 미매핑된 요구사항 수를 돌려준다.
     */
    int addNear(Long pbancSn, Long baseTocSn, String mode, String label);

    /** 번호 포함 이름을 고친다. */
    void rename(Long pbancSn, Long tocSn, String label);

    /** 최하위 목차의 예상 쪽수를 고친다. */
    void changePages(Long pbancSn, Long tocSn, Integer pages);

    /**
     * 목차를 하위 목차까지 함께 지운다. 올린 파일이 있으면 막는다. (NFR-009)
     * 연결된 요구사항은 미매핑으로 돌아가며, 그 건수를 돌려준다. (FR-056)
     */
    int delete(Long pbancSn, Long tocSn);

    /**
     * 최하위 목차의 담당자를 지정한다. picId가 비어 있으면 지정을 해제한다. (FR-049)
     * 담당자는 이 사업건 제안팀원(주관팀·참여팀) 중에서만 고를 수 있다.
     * 연결된 요구사항의 담당자는 목차 담당자를 그대로 따르므로 따로 바꾸지 않는다. (FR-051)
     */
    void assign(Long pbancSn, Long tocSn, String picId);
}
