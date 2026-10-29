package com.ojj.sujuro.proposal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.proposal.dto.RqtDto;
import com.ojj.sujuro.proposal.dto.TocDto;


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

    /** 요구사항을 목차에 연결한다. tocSn이 null이면 미매핑으로 돌린다. (FR-051) */
    int updateToc(@Param("rqtMttrSn") Long rqtMttrSn, @Param("tocSn") Long tocSn);

    /** 연결 대상 목차 확인용: 공고·상위목차·목차구분·담당자만 채운다. 없으면 null */
    TocDto findMapTarget(@Param("tocSn") Long tocSn);

    /** 이 사업건 목차(제안서·제안요약서) 중 이 사용자가 담당한 목차 수 (1 이상이면 목차 담당자) */
    int countOwnedTocs(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);
}
