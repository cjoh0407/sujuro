package com.ojj.sujuro.notification.service.impl;

import com.ojj.sujuro.notification.mapper.NotificationMapper;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.notification.support.NotificationPublisher;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.notification.code.NotificationType;
import lombok.RequiredArgsConstructor;

/**
 * 알림 저장 + 실시간 전달. 받을 사람 목록은 부르는 쪽이 정한다.
 *
 * 행동한 본인에게는 보내지 않는다 (일정을 등록·변경·옮긴 팀장, 결과를 가져온 경영지원실 등).
 * 본인은 화면에서 이미 완료 안내를 보고, 본인 알림이 페이지 이동 직전에 와서 토스트가 번쩍이고 사라지는 문제가 있었다.
 * 행동한 사람 = 지금 요청을 보낸 로그인 사용자. 스케줄러(마감 임박·자동 수집)에는 로그인 사용자가 없어 모두에게 간다.
 * 단, 나라장터에서 일어난 일(정정·취소, 단계 전환, 수집 결과)은 [지금 수집]을 누른 사람이 한 일이 아니므로 그 사람에게도 보낸다.
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 행동한 본인에게도 보내는 알림 (나라장터에서 일어난 일, 스케줄러가 만드는 알림) */
    private static final Set<String> ALSO_TO_ACTOR = Set.of(
            NotificationType.BID_CHANGED, NotificationType.BID_STAGE_CHANGED,
            NotificationType.COLLECT_RESULT, NotificationType.DEADLINE_NEAR);

    private final NotificationMapper notificationMapper;
    private final NotificationPublisher publisher;

    @Override
    @Transactional
    public void send(String receiverId, String typeCd, Long pbancSn, String content) {
        if (receiverId == null) {
            return;
        }
        if (!ALSO_TO_ACTOR.contains(typeCd) && receiverId.equals(currentUserId())) {
            return; // 행동한 본인
        }
        notificationMapper.insert(receiverId, typeCd, pbancSn, content);
        publisher.publish(receiverId, typeCd, pbancSn, content); // 접속 중이면 바로 보여 준다. 커밋된 뒤에 나간다. (FR-034)
    }

    @Override
    @Transactional
    public void send(Collection<String> receiverIds, String typeCd, Long pbancSn, String content) {
        if (receiverIds == null) {
            return;
        }
        Set<String> unique = new LinkedHashSet<>(receiverIds);
        for (String id : unique) {
            send(id, typeCd, pbancSn, content);
        }
    }

    @Override
    @Transactional
    public void sendToRole(Role role, String typeCd, Long pbancSn, String content) {
        send(notificationMapper.findUserIdsByAuthority(codeOf(role)), typeCd, pbancSn, content);
    }

    @Override
    @Transactional
    public void sendToTeamLeaders(Long teamSn, String typeCd, Long pbancSn, String content) {
        send(notificationMapper.findLeaderIds(teamSn), typeCd, pbancSn, content);
    }

    @Override
    @Transactional
    public void sendToProposalTeam(Long pbancSn, String typeCd, String content) {
        send(notificationMapper.findProposalMemberIds(pbancSn), typeCd, pbancSn, content);
    }

    /** 지금 요청을 보낸 로그인 사용자의 사번. 스케줄러처럼 로그인 사용자가 없으면 null */
    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) auth.getPrincipal()).getEmpNo();
        }
        return null;
    }

    /** 권한 → 권한코드 (공통코드 AUTHS) */
    private static String codeOf(Role role) {
        switch (role) {
            case SALES:
                return Role.CD_SALES;
            case MGMT:
                return Role.CD_MGMT;
            case LEADER:
                return Role.CD_LEADER;
            default:
                return Role.CD_MEMBER;
        }
    }
}
