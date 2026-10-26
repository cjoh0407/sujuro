package com.ojj.sujuro.proposal.bid;

import java.util.List;

/** 사업건 선택과 접근 확인, 쪽수 제한 (FR-011, FR-016-1, FR-055) */
public interface ProposalService {

    /** 내가 제안팀원인 사업건 목록 */
    List<ProposalBidDto> myBids(String userId);

    /** 제안팀원인 사업건만 열 수 있다. 아니면 업무 예외 */
    ProposalBidDto openBid(Long pbancSn, String userId);

    /** 목차 담당자로 고를 수 있는 제안팀원 (FR-049) */
    List<ProposalMemberDto> members(Long pbancSn);

    /** 목차 편집·담당자 지정은 팀장만 할 수 있다. 팀원이면 업무 예외 */
    void requireLeader(boolean leader);

    /** 제안서 전체 페이지 제한을 바꾼다. null이면 제한 없음 */
    void changePageLimit(Long pbancSn, Integer limit);
}
