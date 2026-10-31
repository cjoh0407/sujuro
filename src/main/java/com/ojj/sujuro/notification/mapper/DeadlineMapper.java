package com.ojj.sujuro.notification.mapper;

import com.ojj.sujuro.notification.dto.DeadlineTarget;
import com.ojj.sujuro.notification.dto.PendingMail;
import com.ojj.sujuro.notification.dto.ScheduleTarget;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 마감 임박 알림 SQL. 쿼리는 mapper/notification/DeadlineMapper.xml */
@Mapper
public interface DeadlineMapper {

    /** 입찰 마감이 오늘로부터 daysLeft일 뒤이고 제안 진행 중(참여 확정·제안 작성 중)인 사업건 */
    List<DeadlineTarget> findTargets(@Param("daysLeft") int daysLeft);

    /** 이미 같은 표시([D-1])로 보낸 알림이 있으면 1 이상 */
    int countSent(@Param("receiverId") String receiverId, @Param("pbancSn") Long pbancSn,
                  @Param("typeCd") String typeCd, @Param("tag") String tag);

    /** 시작이 오늘로부터 daysLeft일 뒤인 사업건 일정(직접 등록한 것만. 공고 마감 자동 일정은 위 마감 알림이 따로 보낸다) */
    List<ScheduleTarget> findScheduleTargets(@Param("daysLeft") int daysLeft);

    /** 같은 내용의 알림을 최근 2일 안에 이미 보냈으면 1 이상 (일정은 공고가 없어 내용으로 중복을 막는다) */
    int countSentByContent(@Param("receiverId") String receiverId, @Param("typeCd") String typeCd,
                           @Param("content") String content);

    /** 메일을 아직 못 보낸 마감 임박 알림 (최근 2일 것만 다시 시도한다) */
    List<PendingMail> findPendingMails(@Param("typeCd") String typeCd, @Param("tag") String tag);

    /** 메일 보냄으로 표시 */
    int markMailSent(@Param("notiSn") Long notiSn);
}
