package com.ojj.sujuro.proposal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.dto.TocFormDto;

/** 목차 SQL. 쿼리는 src/main/resources/mapper/proposal/TocMapper.xml 에 있다. */
@Mapper
public interface TocMapper {

    /** 사업건의 목차 전체 (부모 바로 아래에 자식, 같은 부모 안에서는 순서대로). tocSeCd로 제안서·제안요약서를 나눈다. */
    List<TocDto> findByBid(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd);

    /* ---------- 목차 양식 (FR-046~048) ---------- */

    /** 이 팀이 저장한 양식 중 사용 중인 것 (해당 구분만) */
    List<TocFormDto> findForms(@Param("teamSn") Long teamSn, @Param("tocSeCd") String tocSeCd);

    TocFormDto findForm(@Param("tocFormSn") Long tocFormSn);

    /** 목차 양식의 항목 (부모 바로 아래에 자식 순서) */
    List<TocDto> findFormItems(@Param("tocFormSn") Long tocFormSn);

    /** 이 팀 양식 중 같은 이름이 몇 개인지 */
    int countFormName(@Param("teamSn") Long teamSn, @Param("tocFormNm") String tocFormNm);

    /** 양식을 만든다. 만들어진 양식일련번호가 form.tocFormSn에 채워진다. */
    int insertForm(TocFormDto form);

    /** 양식 항목을 넣는다 (공고일련번호는 비우고 목차양식일련번호를 채운다). 만들어진 번호가 item.tocSn에 채워진다. */
    int insertFormItem(TocDto item);

    /* ---------- 사업건 목차 ---------- */

    /** 목차 한 건 (하위 목차 수·연결된 요구사항 수·파일 수 포함) */
    TocDto findOne(@Param("tocSn") Long tocSn);

    /** 이 사업건에 해당 구분의 목차가 몇 개 있는지 */
    int countByBid(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd);

    /** 이 사업건 해당 구분 목차에 올라간 파일 수 (양식 바꾸기 전에 확인) */
    int countFilesByBid(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd);

    /** 같은 상위 목차 안에서 다음 순서 번호. 최상위는 upTocSn을 null로 넘긴다. */
    int nextSortSeq(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd, @Param("upTocSn") Long upTocSn);

    /** 같은 상위 목차 안에서 afterSeq보다 뒤에 있는 목차의 순서를 하나씩 민다 ("+ 같은 단계" 끼워 넣기) */
    int shiftSortSeq(@Param("pbancSn") Long pbancSn, @Param("tocSeCd") String tocSeCd,
                     @Param("upTocSn") Long upTocSn, @Param("afterSeq") int afterSeq);

    /** 사업건 목차를 추가한다. 만들어진 목차일련번호가 toc.tocSn에 채워진다. tocSeCd는 반드시 채워서 넘긴다. */
    int insert(TocDto toc);

    /** 번호와 이름을 바꾼다. (번호는 사용자가 입력, 자동 번호 없음) */
    int updateLabel(@Param("tocSn") Long tocSn, @Param("tocNo") String tocNo, @Param("tocNm") String tocNm);

    int updatePages(@Param("tocSn") Long tocSn, @Param("expcPageCnt") Integer expcPageCnt);

    /** 목차 담당자를 바꾼다. picId가 null이면 미지정 (FR-049) */
    int updatePic(@Param("tocSn") Long tocSn, @Param("picId") String picId);

    /** 목차 진행 상태를 바꾼다. TS020 작성 중 / TS030 검토 완료 (FR-066) */
    int updateProgress(@Param("tocSn") Long tocSn, @Param("prgrsSttsCd") String prgrsSttsCd);

    /** 최하위 목차가 하위를 갖게 되면 담당자·쪽수를 비운다. */
    int clearLeafInfo(@Param("tocSn") Long tocSn);

    /** 담당자로 지정할 수 있는 제안팀원(주관팀·참여팀, 비활성 제외)이면 1, 아니면 0 */
    int countAssignableMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    int delete(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 요구사항 수 */
    int countRequirements(@Param("tocSn") Long tocSn);

    /** 이 목차에 올라간 파일 수 (삭제(숨김)한 파일 포함. 파일은 지우지 않으므로 있으면 목차를 못 지운다, NFR-009) */
    int countFiles(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 요구사항을 미매핑으로 돌린다. (FR-056) */
    int clearRequirements(@Param("tocSn") Long tocSn);

    /** 이 목차에 연결된 질문답변의 관련 목차를 비운다. */
    int clearQuestions(@Param("tocSn") Long tocSn);

    /** 이 목차를 조견표(평가 항목별 목차·쪽)에서 뺀다. (FR-071) */
    int clearEvaluationMappings(@Param("tocSn") Long tocSn);
}
