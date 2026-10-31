package com.ojj.sujuro.notification.service.impl;

import com.ojj.sujuro.notification.mapper.NotificationMapper;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.notification.support.NotificationPublisher;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.code.Role;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationPublisher publisher;

    @Override
    @Transactional
    public void send(String receiverId, String typeCd, Long pbancSn, String content) {
        if (receiverId == null) {
            return;
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
