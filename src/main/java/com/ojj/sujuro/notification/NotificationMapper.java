package com.ojj.sujuro.notification;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 알림 SQL. 쿼리는 src/main/resources/mapper/notification/NotificationMapper.xml 에 있다. */
@Mapper
public interface NotificationMapper {

    int insert(@Param("receiverId") String receiverId,
               @Param("typeCd") String typeCd,
               @Param("pbancSn") Long pbancSn,
               @Param("content") String content);

    /** 권한코드가 같은 사용자(비활성 제외) */
    List<String> findUserIdsByAuthority(@Param("authrtCd") String authrtCd);

    /** 그 팀의 팀장(비활성 제외) */
    List<String> findLeaderIds(@Param("teamSn") Long teamSn);

    /** 그 사업건의 제안팀원(비활성 제외) */
    List<String> findProposalMemberIds(@Param("pbancSn") Long pbancSn);
}
