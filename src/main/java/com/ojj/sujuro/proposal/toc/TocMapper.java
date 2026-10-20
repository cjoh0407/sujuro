package com.ojj.sujuro.proposal.toc;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 목차 SQL. 쿼리는 src/main/resources/mapper/proposal/TocMapper.xml 에 있다. */
@Mapper
public interface TocMapper {

    /** 사업건의 목차 전체 (같은 상위 목차 안에서 순서대로) */
    List<TocDto> findByBid(@Param("pbancSn") Long pbancSn);

    /** 회사 공통 목차 양식 (공고일련번호가 NULL인 목차) */
    List<TocDto> findCommon();

    TocDto findOne(@Param("tocSn") Long tocSn);

    /** 이 사업건에 목차가 몇 개 있는지 */
    int countByBid(@Param("pbancSn") Long pbancSn);

    /** 같은 상위 목차 안에서 다음 순서 번호. 최상위는 upTocSn을 null로 넘긴다. */
    int nextSortSeq(@Param("pbancSn") Long pbancSn, @Param("upTocSn") Long upTocSn);

    /** 목차를 추가한다. 만들어진 목차일련번호가 toc.tocSn에 채워진다. */
    int insert(TocDto toc);

    int update(@Param("tocSn") Long tocSn, @Param("tocNm") String tocNm, @Param("expcPageCnt") Integer expcPageCnt);

    int updatePages(@Param("tocSn") Long tocSn, @Param("expcPageCnt") Integer expcPageCnt);

    int updateTocNo(@Param("tocSn") Long tocSn, @Param("tocNo") String tocNo);

    int delete(@Param("tocSn") Long tocSn);

    /** 이 목차에 달린 하위 목차 수 */
    int countChildren(@Param("tocSn") Long tocSn);

    /** 이 목차에 올라간 제안서 파일 수 (파일은 지우지 않으므로 있으면 목차를 못 지운다, NFR-009) */
    int countFiles(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 요구사항을 미매핑으로 돌린다. (FR-056) */
    int clearRequirements(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 질문답변의 관련 목차를 비운다. */
    int clearQuestions(@Param("tocSn") Long tocSn);
}
