package com.ojj.sujuro.proposal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.ProposalMemberDto;
import com.ojj.sujuro.proposal.dto.ProposalTeamDto;

/** 제안 업무 공통 SQL (사업건 목록, 제안팀원 확인, 제안팀 구성). 쿼리는 src/main/resources/mapper/proposal/ProposalMapper.xml 에 있다. */
@Mapper
public interface ProposalMapper {

    /** 내가 제안팀원으로 들어가 있는 사업건 (FR-011) */
    List<ProposalBidDto> findMyBids(@Param("userId") String userId);

    /** 사업건 한 건 (제안팀원 여부와 관계없이) */
    ProposalBidDto findBid(@Param("pbancSn") Long pbancSn);

    /** 사용자가 이 사업건의 제안팀원이면 1, 아니면 0 */
    int countMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    /** 목차 담당자로 고를 수 있는 제안팀원 (주관팀·참여팀, 비활성 계정 제외) (FR-049) */
    List<ProposalMemberDto> findMembers(@Param("pbancSn") Long pbancSn);

    /* ---------- 제안팀 구성 (FR-043~044) ---------- */

    /** 제안팀에 추가할 수 있는 직원: 개발부 팀장·팀원, 비활성 아님, 아직 이 사업건 제안팀이 아닌 사람 */
    List<ProposalMemberDto> findAddableUsers(@Param("pbancSn") Long pbancSn);

    /** 직원 한 명 (소속 팀·부서·권한·계정상태). 없으면 null */
    ProposalMemberDto findUser(@Param("userId") String userId);

    /** 제안팀원 한 명 (이 사업건에 없으면 null) */
    ProposalMemberDto findMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    int insertMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId, @Param("ptcpSeCd") String ptcpSeCd);

    int deleteMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    /** 이 사람이 이 사업건에서 담당자로 지정된 목차 수 (제안서·제안요약서) */
    int countOwnedTocs(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    /** 참여팀으로 고를 수 있는 개발부 팀 (주관팀 제외). 참여팀 여부와 그 팀 소속 제안팀원 수를 함께 */
    List<ProposalTeamDto> findTeamOptions(@Param("pbancSn") Long pbancSn);

    /** 이 팀이 이 사업건의 참여팀이면 1, 아니면 0 */
    int countPtcpTeam(@Param("pbancSn") Long pbancSn, @Param("teamSn") Long teamSn);

    int insertPtcpTeam(@Param("pbancSn") Long pbancSn, @Param("teamSn") Long teamSn);

    int deletePtcpTeam(@Param("pbancSn") Long pbancSn, @Param("teamSn") Long teamSn);

    /** 제안서 전체 페이지 제한을 바꾼다. (FR-055) */
    int updatePageLimit(@Param("pbancSn") Long pbancSn, @Param("lmtPageCnt") Integer lmtPageCnt);
}
