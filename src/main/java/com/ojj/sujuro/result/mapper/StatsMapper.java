package com.ojj.sujuro.result.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationScoreDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsArticleDTO;
import com.ojj.sujuro.result.dto.StatsFileDTO;
import com.ojj.sujuro.result.dto.StatsHistoryDTO;
import com.ojj.sujuro.result.dto.StatsRateDTO;
import com.ojj.sujuro.result.dto.StatsTallyDTO;
import com.ojj.sujuro.result.dto.StatsTeamDTO;

/** 통계·사업별 결과 분석 SQL. 쿼리와 집계 기준은 src/main/resources/mapper/result/StatsMapper.xml 에 있다. */
@Mapper
public interface StatsMapper {

    /** 사용 중인 개발팀 (팀 탭) */
    List<StatsTeamDTO> findDevTeams();

    /** 이 사용자가 속한 개발팀. 개발팀이 아니면 null */
    Long findDevTeamSnOf(@Param("userId") String userId);

    /** 참여 공고가 있는 연도 (오래된 순, 입찰 마감일 기준) */
    List<String> findYears(@Param("teamSns") List<Long> teamSns);

    /** 연도·팀별 참여·결과·수주·유찰·수주 금액·평균 기술점수 */
    List<StatsTallyDTO> tallyByYearTeam(@Param("teamSns") List<Long> teamSns);

    /** 참여 형태별 수주율. 공통코드(PTFRM) 순서대로, 참여 형태가 비어 있는 공고는 '미지정' 줄로 맨 뒤에 붙인다 */
    List<StatsRateDTO> ptcpShpRates(@Param("teamSn") Long teamSn, @Param("year") String year);

    /**
     * 평가 항목(대분류)별 득점률. 팀·연도로 묶거나(통계), 공고 하나만 본다(사업별 결과 분석).
     * 비운 조건은 걸지 않는다. 공고 하나를 볼 때는 평가표 순서, 아니면 득점률이 낮은 순이다.
     */
    List<StatsArticleDTO> articleRates(@Param("teamSn") Long teamSn, @Param("year") String year,
                                       @Param("pbancSn") Long pbancSn);

    /** 결과가 등록된 이 팀의 사업 (연도 상관없이 마감일 최근 순) */
    List<StatsHistoryDTO> history(@Param("teamSn") Long teamSn);

    /** 결과가 등록된 개발팀 사업 하나. 아니면 null */
    StatsArchiveDTO findArchive(@Param("pbancSn") Long pbancSn);

    /** 팀장이 확정한 최종 제안서. 없으면 null */
    StatsFileDTO findFinalFile(@Param("pbancSn") Long pbancSn);

    /** 마지막으로 올린 평가표 엑셀. 없으면 null */
    EvaluationFileDTO findEvaluationFile(@Param("pbancSn") Long pbancSn);

    /** 개찰 목록에 우리 회사 행이 있는지 (0 또는 1) */
    int countOurCompany(@Param("pbancSn") Long pbancSn);

    /** 저장된 평가 항목과 우리 회사의 위원별 점수 (항목 순서 → 위원 번호 순) */
    List<EvaluationScoreDTO> findEvaluationScores(@Param("pbancSn") Long pbancSn);
}
