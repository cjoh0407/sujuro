package com.ojj.sujuro.schedule;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.user.CodeOption;

/** 일정 SQL. 쿼리는 mapper/schedule/ScheduleMapper.xml (테이블 TB_SCHDL, TB_BID_PBANC, TB_SBMSN_DCMNT, TB_TEAM) */
@Mapper
public interface ScheduleMapper {

    /** 로그인한 사용자의 소속 팀 번호 (없으면 null) */
    Long findMyTeamSn(@Param("userId") String userId);

    /** 개발팀 목록 (code=팀 번호, name=팀 이름) */
    List<CodeOption> findDevTeams();

    /** 일정 구분 목록 (공통코드 SCHSE) */
    List<CodeOption> findKinds();

    /** 공고에서 가져온 일정: 제출 마감, 발표, 서류 마감. teamSn 이 null 이면 모든 팀 */
    List<ScheduleEvent> findAutoEvents(@Param("teamSn") Long teamSn, @Param("from") String from, @Param("to") String to);

    /** 팀장이 만든 내부 일정. teamSn 이 null 이면 모든 팀 */
    List<ScheduleEvent> findInternalEvents(@Param("teamSn") Long teamSn, @Param("from") String from, @Param("to") String to);

    /** 내 팀의 내부 일정 한 건 (남의 팀이면 null) */
    ScheduleEvent findInternalOne(@Param("schdlSn") Long schdlSn, @Param("teamSn") Long teamSn);

    int insert(@Param("teamSn") Long teamSn, @Param("kindCd") String kindCd, @Param("title") String title,
               @Param("date") String date, @Param("userId") String userId);

    int update(@Param("schdlSn") Long schdlSn, @Param("teamSn") Long teamSn, @Param("kindCd") String kindCd,
               @Param("title") String title, @Param("date") String date);

    int delete(@Param("schdlSn") Long schdlSn, @Param("teamSn") Long teamSn);
}
