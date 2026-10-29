package com.ojj.sujuro.proposal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.proposal.dto.QnaDto;

/** 질의응답 SQL. 쿼리는 src/main/resources/mapper/proposal/QnaMapper.xml 에 있다. */
@Mapper
public interface QnaMapper {

    /** 사업건의 Q&A 전체 (예상·발표 모두). 화면에서 탭별로 나눈다. */
    List<QnaDto> findByBid(@Param("pbancSn") Long pbancSn);

    QnaDto findOne(@Param("qstnAnsSn") Long qstnAnsSn);

    int insert(QnaDto qna);

    /** 질문·답변·답변 담당자·관련 목차·일치 여부만 바꾼다. 구분과 작성자는 그대로 둔다. */
    int update(QnaDto qna);

    int delete(@Param("qstnAnsSn") Long qstnAnsSn);

    /** 이 사용자가 이 사업건 제안팀원인지 (답변 담당자 확인용) */
    int countMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    /** 이 목차가 이 사업건 목차인지 (관련 목차 확인용) */
    int countToc(@Param("pbancSn") Long pbancSn, @Param("tocSn") Long tocSn);
}
