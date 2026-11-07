package com.ojj.sujuro.dashboard.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.dashboard.dto.DashboardRows.Bid;
import com.ojj.sujuro.dashboard.dto.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderCounts;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MemberProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MyToc;
import com.ojj.sujuro.dashboard.dto.DashboardRows.ResultWait;
import com.ojj.sujuro.dashboard.dto.DashboardRows.SalesCounts;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TeamStat;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TodoItem;
import com.ojj.sujuro.dashboard.dto.DashboardRows.YearStat;

/**
 * 대시보드 조회 SQL (FR-089, 090). 쿼리는 mapper/dashboard/DashboardMapper.xml
 *
 * 팀 범위는 요청 값이 아니라 로그인한 사용자(userId)의 소속 팀으로 SQL 안에서 정한다. (NFR-001)
 * 목록 쿼리는 칸 안에서 스크롤하므로 전부 가져오되, 길게 늘어나지 않게 최대 30건에서 끊는다.
 */
@Mapper
public interface DashboardMapper {

    // ---- 영업부 ----

    SalesCounts salesCounts();

    /** 영업부가 처리할 공고: 배정 대기(신규 공고: 발주계획·사전규격·입찰공고, 취소·추적 종료 제외)와 팀장 응답 대기(배정 완료). 마감 빠른 순 */
    List<Bid> salesTodo();

    /** 팀별 현황. onlyMine 이 false 면 전체 개발팀, true 면 내 팀만 */
    List<TeamStat> teamStats(@Param("userId") String userId, @Param("onlyMine") boolean onlyMine);

    // ---- 경영지원실 ----

    /** 제출서류 준비 현황 (참여 확정·제안 작성 중 사업건) */
    List<DocRow> docRows();

    /** 제출 완료 후 결과 입력을 기다리는 건수와 가장 오래된 건의 개찰 후 경과 일수 */
    ResultWait resultWait();

    /** 올해를 포함한 최근 3년의 연도별 성과 (오래된 해부터) */
    List<YearStat> yearStats();

    // ---- 팀장 ----

    LeaderCounts leaderCounts(@Param("userId") String userId);

    /** 내가 제안팀원인 사업(참여 확정~제출 완료)의 목차·요구사항·서류·최종본 진행 */
    List<LeaderProject> leaderProjects(@Param("userId") String userId);

    /** 팀장이 처리할 일: 참여 결정, 최종본 확정, 제출서류, 요구사항 연결, 결과 입력. 마감 빠른 순 */
    List<TodoItem> leaderTodos(@Param("userId") String userId);

    // ---- 팀원 ----

    /** 내가 담당자로 지정된 목차 (진행 중인 사업건만) */
    List<MyToc> myTocs(@Param("userId") String userId);

    /** 내가 제안팀원인 진행 중 사업(참여 확정·제안 작성 중)별 내 목차·파일, 사업 전체 목차 진행 */
    List<MemberProject> memberProjects(@Param("userId") String userId);
}
