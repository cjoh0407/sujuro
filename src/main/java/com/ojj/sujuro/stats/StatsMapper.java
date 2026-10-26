package com.ojj.sujuro.stats;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.stats.StatsRows.Artcl;
import com.ojj.sujuro.stats.StatsRows.History;
import com.ojj.sujuro.stats.StatsRows.Tally;
import com.ojj.sujuro.user.CodeOption;

/**
 * 통계 조회 SQL (FR-083 ~ 088). 쿼리는 mapper/stats/StatsMapper.xml
 *
 * teamSn 이 null 이면 전체 팀, 값이 있으면 그 주관팀 공고만 센다.
 * 팀장은 서비스가 자기 팀 번호를 넣으므로 화면에서 받은 값을 그대로 쓰지 않는다. (NFR-001)
 */
@Mapper
public interface StatsMapper {

    Long findMyTeamSn(@Param("userId") String userId);

    /** 개발팀 목록 (code=팀 번호, name=팀 이름) */
    List<CodeOption> findDevTeams();

    /** 통계에 나올 수 있는 연도 (입찰 마감일 기준, 최신순) */
    List<String> findYears(@Param("teamSn") Long teamSn);

    /** 연도별 (label=연도) */
    List<Tally> byYear(@Param("teamSn") Long teamSn);

    /** 팀별 (label=팀 이름) */
    List<Tally> byTeam(@Param("year") String year, @Param("teamSn") Long teamSn);

    /** 사업 유형별 (label=SI/유지보수) */
    List<Tally> byBizType(@Param("year") String year, @Param("teamSn") Long teamSn);

    /** 참여 형태별 (label=주관/컨소시엄/하도급) */
    List<Tally> byShape(@Param("year") String year, @Param("teamSn") Long teamSn);

    /** 평가 항목별 평균 득점률, 낮은 항목부터 */
    List<Artcl> articles(@Param("year") String year, @Param("teamSn") Long teamSn);

    /** 과거 사업 이력 (제출 완료 이상, 최신순 200건까지) */
    List<History> history(@Param("year") String year, @Param("teamSn") Long teamSn);
}
