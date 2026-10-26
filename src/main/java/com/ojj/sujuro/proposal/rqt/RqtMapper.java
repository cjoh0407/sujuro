package com.ojj.sujuro.proposal.rqt;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 요구사항 SQL. 쿼리는 src/main/resources/mapper/proposal/RqtMapper.xml 에 있다. */
@Mapper
public interface RqtMapper {

    /** 사업건의 요구사항 전체 (연결된 목차와 담당자 포함) */
    List<RqtDto> findByBid(@Param("pbancSn") Long pbancSn);

    RqtDto findOne(@Param("rqtMttrSn") Long rqtMttrSn);

    /** 같은 사업건에 같은 요구사항번호가 몇 개 있는지 */
    int countByNo(@Param("pbancSn") Long pbancSn, @Param("rqtMttrNo") String rqtMttrNo);

    int insert(RqtDto rqt);

    /** 번호와 연결 목차는 그대로 두고 내용만 바꾼다. */
    int update(RqtDto rqt);

    int delete(@Param("rqtMttrSn") Long rqtMttrSn);
}
