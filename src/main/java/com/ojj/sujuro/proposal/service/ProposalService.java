package com.ojj.sujuro.proposal.service;

import java.util.List;

import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.ProposalMemberDto;
import com.ojj.sujuro.proposal.dto.ProposalTeamDto;

/** 사업건 선택과 접근 확인, 제안팀 구성, 쪽수 제한 (FR-011, FR-016-1, FR-043~044, FR-055) */
public interface ProposalService {

    /** 내가 제안팀원인 사업건 목록 */
    List<ProposalBidDto> myBids(String userId);

    /** 제안팀원인 사업건만 열 수 있다. 아니면 업무 예외 (권한 검사는 AccessChecker가 먼저 한다) */
    ProposalBidDto openBid(Long pbancSn, String userId);

    /** 목차 담당자로 고를 수 있는 제안팀원 (FR-049) */
    List<ProposalMemberDto> members(Long pbancSn);

    /* ---------- 제안팀 구성 (FR-043~044). 권한(주관팀 팀장)은 컨트롤러가 AccessChecker로 먼저 확인한다. ---------- */

    /** 인원 찾기 팝업이 한 번에 가져오는 인원 수 (스크롤하면 다음 묶음) */
    int CANDIDATE_PAGE = 20;

    /**
     * 제안팀에 추가할 수 있는 직원 (개발부 팀장·팀원, 다른 팀 인원 포함)을 팀·직급·이름으로 거른다.
     * offset부터 CANDIDATE_PAGE + 1명을 돌려준다. 하나 더 가져온 것은 다음 묶음이 있는지 보려는 것이다.
     */
    List<ProposalMemberDto> addableUsers(Long pbancSn, Long teamSn, String jbgdNm, String name, int offset);

    /** 인원 찾기 팝업의 팀 선택지 (개발부 팀 전체) */
    List<ProposalTeamDto> devTeams();

    /** 인원 찾기 팝업의 직급 선택지 */
    List<String> rankNames();

    /** 참여팀으로 고를 수 있는 팀 (주관팀 제외, 지금 참여팀 여부 포함) */
    List<ProposalTeamDto> teamOptions(Long pbancSn);

    /**
     * 제안팀에 인원을 추가한다. 주관팀 소속이면 주관팀(PT010), 다른 팀이면 참여팀(PT020)으로 넣고,
     * 그 팀이 아직 참여팀이 아니면 참여팀으로 함께 지정한다. 추가한 사람 이름을 돌려준다.
     */
    String addMember(Long pbancSn, String userId);

    /**
     * 인원 추가 팝업에서 체크한 여러 명을 한 번에 추가한다 (FR-043 v4.0). 한 명이라도 넣을 수 없으면 아무도 추가하지 않는다.
     * 추가한 사람 이름 목록을 돌려준다.
     */
    java.util.List<String> addMembers(Long pbancSn, java.util.List<String> userIds);

    /**
     * 제안팀에서 인원을 뺀다. 주관팀 팀장은 뺄 수 없고, 담당 목차가 있으면 담당자를 먼저 바꿔야 한다.
     * 뺀 사람 이름을 돌려준다.
     */
    String removeMember(Long pbancSn, String userId);

    /**
     * 참여팀을 고른 팀들로 맞춘다(체크한 팀만 남는다). 그 팀 소속 참여팀원이 남아 있는 팀은 뺄 수 없다.
     */
    void saveTeams(Long pbancSn, List<Long> teamSns);

    /** 제안서 전체 페이지 제한을 바꾼다. null이면 제한 없음 */
    void changePageLimit(Long pbancSn, Integer limit);
}
