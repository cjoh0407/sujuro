package com.ojj.sujuro.proposal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.proposal.dto.QnaBidDto;
import com.ojj.sujuro.proposal.dto.QnaDto;

/** 질의응답 SQL. 쿼리는 src/main/resources/mapper/proposal/QnaMapper.xml 에 있다. */
@Mapper
public interface QnaMapper {

    /** 사업건의 Q&A 전체 (예상·발표 모두, 관련 요구사항 포함). 화면에서 탭별로 나눈다. */
    List<QnaDto> findByBid(@Param("pbancSn") Long pbancSn);

    QnaDto findOne(@Param("qstnAnsSn") Long qstnAnsSn);

    int insert(QnaDto qna);

    /** 질문·답변·관련 요구사항만 바꾼다. 구분과 작성자는 그대로 둔다. */
    int update(QnaDto qna);

    int delete(@Param("qstnAnsSn") Long qstnAnsSn);

    /** 이 요구사항이 이 사업건 요구사항인지 (관련 요구사항 확인용) */
    int countRqt(@Param("pbancSn") Long pbancSn, @Param("rqtMttrSn") Long rqtMttrSn);

    /** 이전 사업 Q&A 보기: 이 사용자가 제안팀이었고 Q&A가 있는 다른 사업건 (최근 사업건 먼저) (FR-076-1) */
    List<QnaBidDto> findPastBids(@Param("userId") String userId, @Param("pbancSn") Long pbancSn);
}
