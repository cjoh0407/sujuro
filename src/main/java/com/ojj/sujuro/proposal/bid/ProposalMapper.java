package com.ojj.sujuro.proposal.bid;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 제안 업무 공통 SQL (사업건 목록, 제안팀원 확인). 쿼리는 src/main/resources/mapper/proposal/ProposalMapper.xml 에 있다. */
@Mapper
public interface ProposalMapper {

    /** 내가 제안팀원으로 들어가 있는 사업건 (FR-011) */
    List<ProposalBidDto> findMyBids(@Param("userId") String userId);

    /** 사업건 한 건 (제안팀원 여부와 관계없이) */
    ProposalBidDto findBid(@Param("pbancSn") Long pbancSn);

    /** 사용자가 이 사업건의 제안팀원이면 1, 아니면 0 */
    int countMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    /** 제안서 전체 페이지 제한을 바꾼다. (FR-055) */
    int updatePageLimit(@Param("pbancSn") Long pbancSn, @Param("lmtPageCnt") Integer lmtPageCnt);
}
