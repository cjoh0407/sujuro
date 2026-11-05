package com.ojj.sujuro.result.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.result.dto.CompanyCategoryScoreDTO;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationScoreDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsArticleDTO;
import com.ojj.sujuro.result.dto.ResultBidDTO;
import com.ojj.sujuro.result.dto.StatsHistoryDTO;
import com.ojj.sujuro.result.dto.StatsTeamDTO;

/** 사업 이력 관리·사업별 결과 분석 SQL. 쿼리와 조회 기준은 src/main/resources/mapper/result/StatsMapper.xml 에 있다. */
@Mapper
public interface StatsMapper {

    /** 사용 중인 개발팀 (팀 드롭다운) */
    List<StatsTeamDTO> findDevTeams();

    /** 이 사용자가 속한 개발팀. 개발팀이 아니면 null */
    Long findDevTeamSnOf(@Param("userId") String userId);

    /** 참여 공고가 있는 연도 (오래된 순, 입찰 마감일 기준) */
    List<String> findYears(@Param("teamSns") List<Long> teamSns);

    /**
     * 사업별 결과 분석의 평가 항목(대분류)별 득점률. 평가표 순서다.
     * 우리 회사 평가표 엑셀이 올라온 공고만 센다 (11/03). 올라오기 전에는 빈 목록이다.
     */
    List<StatsArticleDTO> articleRates(@Param("pbancSn") Long pbancSn);

    /** 사업별 결과 분석의 업체 × 평가 항목(대분류) 점수 (11/03). 평가표 엑셀을 올린 업체만, 우리 회사 먼저 */
    List<CompanyCategoryScoreDTO> companyArticleScores(@Param("pbancSn") Long pbancSn);

    /** 이 팀의 진행 중 사업: 고른 연도의 참여 확정·제안 작성 중·제출 완료 (마감일이 없으면 올해, 결과를 입력할 제출 완료가 먼저) */
    List<ResultBidDTO> inProgress(@Param("teamSn") Long teamSn, @Param("year") String year);

    /** 이 팀의 지난 사업: 고른 연도의 결과 등록·유찰 (마감일 최근 순) */
    List<StatsHistoryDTO> history(@Param("teamSn") Long teamSn, @Param("year") String year);

    /** 결과가 등록된 개발팀 사업 하나. 아니면 null */
    StatsArchiveDTO findArchive(@Param("pbancSn") Long pbancSn);

    /** 참여 확정 뒤의 개발팀 사업 하나의 공고 기본 정보 (결과는 읽지 않는다). 아니면 null */
    StatsArchiveDTO findPendingBid(@Param("pbancSn") Long pbancSn);

    /** 마지막으로 올린 평가표 엑셀. 없으면 null */
    EvaluationFileDTO findEvaluationFile(@Param("pbancSn") Long pbancSn);

    /** 개찰 목록에 우리 회사 행이 있는지 (0 또는 1) */
    int countOurCompany(@Param("pbancSn") Long pbancSn);

    /** 저장된 평가 항목과 우리 회사의 위원별 점수 (항목 순서 → 위원 번호 순) */
    List<EvaluationScoreDTO> findEvaluationScores(@Param("pbancSn") Long pbancSn);
}
