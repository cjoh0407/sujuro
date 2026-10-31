package com.ojj.sujuro.proposal.service;

import java.util.List;

import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.dto.TocFormDto;

/**
 * 사업건 목차 (FR-046~049, FR-056, FR-063, 요구사항 v3.2).
 * 목차 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 저장된 양식은 바뀌지 않는다.
 * 제안서와 제안요약서 목차는 tocSeCd(TocDto.SE_PROPOSAL / SE_SUMMARY)로 나눠 따로 관리하고, 단계 규칙은 같다.
 * 제안서 목차를 고치면(추가·이름·쪽수·삭제) 현재 양식(TB_BID_PBANC.TOC_FORM_SN)에도 바로 반영해, 양식을 바꿨다 돌아와도 남는다.
 * 주관팀 양식은 제안서 목차에만 쓴다. 제안요약서는 항상 같은 기본 목차(SUMMARY_DEFAULT)로 시작해 사업건마다 고친다.
 * 목차 단계에는 제한이 없고 번호는 사용자가 입력한다. 담당자·쪽수·요구사항은 최하위 목차에만 둔다.
 * 누가 할 수 있는지(주관팀·제안팀·주관팀 팀장)는 컨트롤러에서 AccessChecker로 먼저 확인한다.
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

    /**
     * 제안요약서 기본 목차 (멘토 검토, 2026-10-31). 각 줄의 첫 칸이 1단계, 나머지가 그 하위 목차다.
     * 로마자 1단계는 거의 바뀌지 않고, 하위 목차는 사업마다 조금씩 고친다.
     * "Action n." 옆에는 사업마다 전략명을 적는다 (예: Action 1. 공공기관 UI/UX 가이드라인 준수).
     */
    String[][] SUMMARY_DEFAULT = {
        { "Ⅰ. 사업의 이해", "제안사 소개", "제안사 사업 수행실적", "사업의 의미", "사업의 범위" },
        { "Ⅱ. 사업 추진 전략", "Action 1.", "Action 2.", "Action 3.", "Action 4." },
        { "Ⅲ. 완성도 제고 방안", "보안 관리 방안", "품질 보증 방안", "사업 추진 일정" },
    };

    /**
     * 이 사업건 제안요약서 목차를 기본 목차로 만든다.
     * reset이 false면 목차가 없을 때만 만들고(기본 목차로 시작), true면 있던 목차를 지우고 기본 목차로 되돌린다.
     * 되돌리면 담당자 지정도 사라진다. 요약서 파일을 올린 적이 있으면(삭제 기록 포함) 되돌릴 수 없다.
     */
    void applySummaryDefault(Long pbancSn, boolean reset);

    /* ---------- 목차 양식 (FR-046~048): 제안서 목차만 ---------- */

    /**
     * 이 사업건 제안서 목차가 지금 쓰는 양식 (TB_BID_PBANC.TOC_FORM_SN). 마지막으로 불러오거나 저장한 양식이다.
     * 직접 만든 목차면 null. 그 뒤 삭제한 양식이면 isInUse()가 false다. 화면에서 모든 제안팀원에게 보여 준다.
     */
    TocFormDto currentForm(Long pbancSn);

    /** 이 주관팀이 저장한 양식 (해당 구분) */
    List<TocFormDto> forms(Long teamSn, String tocSeCd);

    /**
     * 고른 양식으로 이 사업건 목차를 만들고, 그 양식을 이 사업건의 현재 양식으로 기록한다. 이미 목차가 있으면 지우고 바꾼다("다른 양식으로 바꾸기").
     * 바꿀 때 담당자 지정은 사라지고 연결된 요구사항은 모두 미매핑이 된다. 올린 파일이 있으면 바꿀 수 없다.
     * 양식은 이 사업건 주관팀(teamSn)이 저장한 것만 쓸 수 있다. 만든 목차 개수를 돌려준다.
     */
    int importForm(Long pbancSn, String tocSeCd, Long tocFormSn, Long teamSn);

    /**
     * 지금 사업건 목차를 주관팀 양식으로 저장하고, 저장한 양식을 이 사업건의 현재 양식으로 기록한다 (FR-048).
     * 같은 팀에 같은 이름의 양식이 있으면 그 양식을 지금 목차로 덮어쓰고 true를, 새로 만들었으면 false를 돌려준다.
     * 같은 이름이 다른 구분(제안서/제안요약서) 양식이면 덮어쓰지 않고 막는다.
     */
    boolean saveAsForm(Long pbancSn, String tocSeCd, String formName, Long teamSn, String userId);

    /** 주관팀 양식의 이름을 바꾼다. 같은 팀에 같은 이름이 있으면 막는다. 바뀐 이름을 돌려준다. (FR-048) */
    String renameForm(Long tocFormSn, String newName, Long teamSn);

    /** 주관팀 양식을 삭제(숨김)한다. 그 양식으로 이미 만든 사업건 목차는 그대로다. 삭제한 양식 이름을 돌려준다. (FR-048) */
    String deleteForm(Long tocFormSn, Long teamSn);

    /** 이 사업건 목차에 이미 쓴 외부 회사명 (담당자 자동완성 후보, FR-043-1) */
    List<String> coNames(Long pbancSn);

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
     * 담당자는 이 사업건 제안팀원(주관팀·참여팀) 중에서 고르거나, 외부 회사명(coName)을 적는다(둘 중 하나, FR-043-1).
     * 외부 회사명은 이 사업건에 이미 쓴 이름과 공백·대소문자·(주) 표기만 다르면 기존 이름으로 맞춘다.
     * 연결된 요구사항의 담당자는 목차 담당자를 그대로 따르므로 따로 바꾸지 않는다. (FR-051)
     * 새 담당자에게는 지정 알림, 이전 담당자에게는 해제 알림을 보낸다(NT100). 처리한 본인(actorId)에게는 보내지 않는다.
     */
    void assign(Long pbancSn, Long tocSn, String picId, String coName, String actorId);
}
