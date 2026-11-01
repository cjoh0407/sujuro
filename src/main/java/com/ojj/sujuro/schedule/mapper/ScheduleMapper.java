package com.ojj.sujuro.schedule.mapper;

import com.ojj.sujuro.schedule.dto.ScheduleEvent;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.user.dto.CodeOption;

/** 일정 SQL. 쿼리는 mapper/schedule/ScheduleMapper.xml (테이블 TB_SCHDL, TB_BID_PBANC, TB_SBMSN_DCMNT, TB_TEAM) */
@Mapper
public interface ScheduleMapper {

    /** 로그인한 사용자의 소속 팀 번호 (없으면 null) */
    Long findMyTeamSn(@Param("userId") String userId);

    /** 개발팀 목록 (code=팀 번호, name=팀 이름) */
    List<CodeOption> findDevTeams();

    /** 일정 구분 목록 (공통코드 SCHSE) */
    List<CodeOption> findKinds();

    /** 내가 제안팀원으로 들어간 사업건 (일정에서 사업별로 고를 때 쓴다) */
    List<CodeOption> findMyPbancs(@Param("userId") String userId);
    /** 우리 팀이 주관하고 진행 중인 사업건 (팀장이 일정을 등록할 수 있는 사업건) */
    List<CodeOption> findEditablePbancs(@Param("teamSn") Long teamSn);
    /**
     * 공고에서 가져온 일정: 제출 마감, 발표, 서류 마감.
     * teamSn: 보는 팀(null 이면 모든 팀), memberId: 이 사람이 제안팀원인 사업건만(null 이면 제한 없음), pbancSn: 이 사업건만(null 이면 전체)
     */
    List<ScheduleEvent> findAutoEvents(@Param("teamSn") Long teamSn, @Param("memberId") String memberId,
                                       @Param("pbancSn") Long pbancSn, @Param("from") String from, @Param("to") String to);
    /** 팀장이 만든 내부 일정. 조건은 findAutoEvents 와 같다 */
    List<ScheduleEvent> findInternalEvents(@Param("teamSn") Long teamSn, @Param("memberId") String memberId,
                                           @Param("pbancSn") Long pbancSn, @Param("from") String from, @Param("to") String to);
    /** 내 팀의 내부 일정 한 건 (남의 팀이면 null) */
    ScheduleEvent findInternalOne(@Param("schdlSn") Long schdlSn, @Param("teamSn") Long teamSn);
    /** dateTime 은 "2026-11-05 14:00" 형식 */
    int insert(@Param("teamSn") Long teamSn, @Param("pbancSn") Long pbancSn, @Param("kindCd") String kindCd,
               @Param("title") String title, @Param("dateTime") String dateTime, @Param("userId") String userId);
    int update(@Param("schdlSn") Long schdlSn, @Param("teamSn") Long teamSn, @Param("pbancSn") Long pbancSn,
               @Param("title") String title, @Param("dateTime") String dateTime);
    int delete(@Param("schdlSn") Long schdlSn, @Param("teamSn") Long teamSn);
}
