package com.ojj.sujuro.notification.mapper;

import com.ojj.sujuro.notification.dto.NotificationDto;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 알림함(조회·읽음 처리) SQL. 쿼리는 mapper/notification/NotificationInboxMapper.xml */
@Mapper
public interface NotificationInboxMapper {

    /** 내 알림 최신순. unreadOnly=true면 안 읽은 것만 */
    List<NotificationDto> findMine(@Param("userId") String userId, @Param("unreadOnly") boolean unreadOnly);

    int countUnread(@Param("userId") String userId);

    /** 내 알림 한 건 조회 (남의 알림이면 null) */
    NotificationDto findMineOne(@Param("userId") String userId, @Param("notiSn") Long notiSn);

    /** 읽음 처리. 받은 사람 본인 것만 바뀐다. */
    int markRead(@Param("userId") String userId, @Param("notiSn") Long notiSn);

    int markAllRead(@Param("userId") String userId);
}
