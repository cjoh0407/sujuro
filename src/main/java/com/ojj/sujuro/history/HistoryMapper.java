package com.ojj.sujuro.history;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 공고 처리 이력 SQL. 쿼리는 src/main/resources/mapper/history/HistoryMapper.xml 에 있다. */
@Mapper
public interface HistoryMapper {

    int insert(@Param("pbancSn") Long pbancSn,
               @Param("prcsSeCd") String prcsSeCd,
               @Param("prcrId") String prcrId,
               @Param("trgtTeamSn") Long trgtTeamSn,
               @Param("prcsRsn") String prcsRsn);

    /** 오래된 것부터 */
    List<HistoryDto> findByPbanc(@Param("pbancSn") Long pbancSn);
}
