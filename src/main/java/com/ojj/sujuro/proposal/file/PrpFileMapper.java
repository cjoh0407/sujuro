package com.ojj.sujuro.proposal.file;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 제안서 파일 SQL. 쿼리는 src/main/resources/mapper/proposal/PrpFileMapper.xml 에 있다. */
@Mapper
public interface PrpFileMapper {

    /** 사업건의 해당 구분 파일 중 삭제되지 않은 것 (목차별은 목차·최신 버전 순, 통합본은 최신 차수 순) */
    List<PrpFileDto> findByBid(@Param("pbancSn") Long pbancSn, @Param("fileSeCd") String fileSeCd);

    /** 사업건의 해당 구분 파일 중 삭제(숨김)된 것. 최근 삭제 먼저 */
    List<PrpFileDto> findDeleted(@Param("pbancSn") Long pbancSn, @Param("fileSeCd") String fileSeCd);

    /** 사업건의 공고상태코드 (제출 후에는 삭제할 수 없다) */
    String findBidStatus(@Param("pbancSn") Long pbancSn);

    /** 삭제(숨김) 처리. 원본과 이력은 남긴다 (NFR-009) */
    int markDeleted(@Param("fileSn") Long fileSn, @Param("userId") String userId, @Param("reason") String reason);

    PrpFileDto findOne(@Param("fileSn") Long fileSn);

    /** 이 목차에 다음으로 붙일 버전 번호. 삭제한 파일도 세므로 번호가 되돌아가지 않는다. */
    int nextTocVersion(@Param("pbancSn") Long pbancSn, @Param("fileSeCd") String fileSeCd, @Param("tocSn") Long tocSn);

    /** 다음 통합본 차수 */
    int nextIntgCycl(@Param("pbancSn") Long pbancSn);

    /** 파일 정보를 넣는다. 만들어진 번호가 file.fileSn에 채워진다. */
    int insert(PrpFileDto file);

    /** 이 사업건 통합본의 최종본 표시를 모두 지운다. */
    int clearFinal(@Param("pbancSn") Long pbancSn);

    /** 이 통합본을 최종본으로 표시한다. (FR-064) */
    int markFinal(@Param("fileSn") Long fileSn, @Param("userId") String userId);
}
