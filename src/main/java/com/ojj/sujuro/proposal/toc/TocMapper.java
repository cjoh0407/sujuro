package com.ojj.sujuro.proposal.toc;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 목차 SQL. 쿼리는 src/main/resources/mapper/proposal/TocMapper.xml 에 있다. */
@Mapper
public interface TocMapper {

    /** 사업건의 목차 전체 (같은 상위 목차 안에서 순서대로). tocSeCd로 제안서·제안요약서를 나눈다. */
    List<TocDto> findByBid(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd);

    /* ---------- 목차 양식 (FR-046~048) ---------- */

    /** 불러올 수 있는 목차 양식: 사용 중인 기본 양식 + 이 사용자가 저장한 커스텀 양식 (기본 양식 먼저) */
    List<TocFormDto> findForms(@Param("userId") String userId);

    TocFormDto findForm(@Param("tocFormSn") Long tocFormSn);

    /** 목차 양식의 항목 (같은 상위 목차 안에서 순서대로) */
    List<TocDto> findFormItems(@Param("tocFormSn") Long tocFormSn);

    /** 이 사용자가 볼 수 있는 양식(기본 + 본인 커스텀) 중 같은 이름이 몇 개인지 */
    int countFormName(@Param("tocFormNm") String tocFormNm, @Param("userId") String userId);

    /** 커스텀 양식을 만든다. 만들어진 양식일련번호가 form.tocFormSn에 채워진다. */
    int insertForm(TocFormDto form);

    /** 양식 항목을 넣는다 (공고일련번호는 비우고 목차양식일련번호를 채운다). 만들어진 번호가 item.tocSn에 채워진다. */
    int insertFormItem(TocDto item);

    /* ---------- 사업건 목차 ---------- */

    TocDto findOne(@Param("tocSn") Long tocSn);

    /** 이 사업건에 해당 구분의 목차가 몇 개 있는지 */
    int countByBid(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd);

    /** 이 사업건 해당 구분 목차에 올라간 파일 수 (양식 바꾸기 전에 확인) */
    int countFilesByBid(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd);

    /** 같은 상위 목차 안에서 다음 순서 번호. 최상위는 upTocSn을 null로 넘긴다. */
    int nextSortSeq(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd, @Param("upTocSn") Long upTocSn);

    /** 사업건 목차를 추가한다. 만들어진 목차일련번호가 toc.tocSn에 채워진다. tocSeCd는 반드시 채워서 넘긴다. */
    int insert(TocDto toc);

    int update(@Param("tocSn") Long tocSn, @Param("tocNm") String tocNm, @Param("expcPageCnt") Integer expcPageCnt);

    int updatePages(@Param("tocSn") Long tocSn, @Param("expcPageCnt") Integer expcPageCnt);

    int updateTocNo(@Param("tocSn") Long tocSn, @Param("tocNo") String tocNo);

    /** 목차 담당자를 바꾼다. picId가 null이면 미지정 (FR-049) */
    int updatePic(@Param("tocSn") Long tocSn, @Param("picId") String picId);

    /** 담당자로 지정할 수 있는 제안팀원(주관팀·참여팀, 비활성 제외)이면 1, 아니면 0 */
    int countAssignableMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    int delete(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 요구사항 수 */
    int countRequirements(@Param("tocSn") Long tocSn);

    /** 이 목차에 올라간 파일 수 (파일은 지우지 않으므로 있으면 목차를 못 지운다, NFR-009) */
    int countFiles(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 요구사항을 미매핑으로 돌린다. (FR-056) */
    int clearRequirements(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 질문답변의 관련 목차를 비운다. */
    int clearQuestions(@Param("tocSn") Long tocSn);

    /** 이 목차를 조견표(평가 항목별 목차·쪽)에서 뺀다. (FR-071) */
    int clearEvaluationMappings(@Param("tocSn") Long tocSn);
}
