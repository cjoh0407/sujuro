package com.ojj.sujuro.reference.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.reference.dto.RefFileDto;

/** 참고자료 SQL. 쿼리는 src/main/resources/mapper/reference/RefFileMapper.xml 에 있다. */
@Mapper
public interface RefFileMapper {

    /** 사용자의 소속 팀 (없으면 null) */
    Long findTeamSn(@Param("userId") String userId);

    /** 팀의 참고자료 중 삭제되지 않은 것. 최근에 올린 순 */
    List<RefFileDto> findByTeam(@Param("teamSn") Long teamSn);

    /** 삭제되지 않은 자료 한 건 (팀 확인은 서비스에서 한다) */
    RefFileDto findOne(@Param("refFileSn") Long refFileSn);

    int insert(RefFileDto file);

    /** 삭제(숨김) 처리. 파일과 올린 기록은 남긴다 */
    int markDeleted(@Param("refFileSn") Long refFileSn, @Param("userId") String userId);
}
