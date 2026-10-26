package com.ojj.sujuro.dashboard;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.dashboard.DashboardRows.Bid;
import com.ojj.sujuro.dashboard.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.DashboardRows.LeaderCounts;
import com.ojj.sujuro.dashboard.DashboardRows.MyToc;
import com.ojj.sujuro.dashboard.DashboardRows.SalesCounts;
import com.ojj.sujuro.dashboard.DashboardRows.TeamStat;

/**
 * 대시보드 조회 SQL (FR-089, 090). 쿼리는 mapper/dashboard/DashboardMapper.xml
 *
 * 팀 범위는 요청 값이 아니라 로그인한 사용자(userId)의 소속 팀으로 SQL 안에서 정한다. (NFR-001)
 */
@Mapper
public interface DashboardMapper {

    SalesCounts salesCounts();

    /** 영업부가 처리할 공고: 배정 대기(신규 입찰공고)와 팀장 응답 대기(배정 완료) */
    List<Bid> salesTodo();

    LeaderCounts leaderCounts(@Param("userId") String userId);

    /** 내 팀이 주관팀이고 팀장이 참여 여부를 결정해야 하는 공고 */
    List<Bid> leaderDecisionWait(@Param("userId") String userId);

    /** 내 팀이 주관팀이거나 참여팀인 제안 사업 */
    List<Bid> teamProposals(@Param("userId") String userId);

    /** 내가 담당자로 지정된 목차 (진행 중인 사업건만) */
    List<MyToc> myTocs(@Param("userId") String userId);

    /** 제출서류 준비 현황 (참여 확정·제안 작성 중 사업건) */
    List<DocRow> docRows();

    /** 제출 완료 후 결과 입력을 기다리는 건수 */
    int resultWaitCount();

    /** 팀별 현황. onlyMine 이 false 면 전체 개발팀, true 면 내 팀만 */
    List<TeamStat> teamStats(@Param("userId") String userId, @Param("onlyMine") boolean onlyMine);
}
