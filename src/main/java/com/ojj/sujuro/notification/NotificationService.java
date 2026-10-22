package com.ojj.sujuro.notification;

import java.util.Collection;

import com.ojj.sujuro.auth.Role;

/**
 * 알림 보내기 (FR-030 ~ FR-034). 다른 기능에서 "누구에게 무슨 일이 있었는지" 알릴 때 이 서비스만 부르면 된다.
 *
 * 사용 예 (배정 처리 서비스 안에서):
 *   notificationService.sendToTeamLeaders(teamSn, NotificationType.ASSIGNED, pbancSn,
 *           "'" + bidName + "' 공고가 " + teamName + "에 배정되었습니다.");
 *
 * - 알림은 호출한 쪽의 트랜잭션에 함께 묶인다. 처리가 실패해 되돌려지면 알림도 함께 사라진다.
 * - 같은 사람이 여러 번 걸려도 한 번만 보낸다. 비활성 계정에는 보내지 않는다.
 * - 알림 내용(content)은 사용자에게 그대로 보이므로 "무엇을 해야 하는지" 알 수 있게 쓴다.
 * - 화면에서 알림을 보여 주고 읽음 처리하는 기능은 알림 화면(10/23)에서 만든다.
 */
public interface NotificationService {

    /** 한 사람에게 보낸다. pbancSn은 관련 공고(없으면 null) */
    void send(String receiverId, String typeCd, Long pbancSn, String content);

    /** 여러 사람에게 보낸다. */
    void send(Collection<String> receiverIds, String typeCd, Long pbancSn, String content);

    /** 해당 권한을 가진 모든 사용자에게 보낸다. 예: 영업부 전원에게 "본공고 등록" */
    void sendToRole(Role role, String typeCd, Long pbancSn, String content);

    /** 그 팀의 팀장에게 보낸다. 예: 공고를 개발1팀에 배정했을 때 */
    void sendToTeamLeaders(Long teamSn, String typeCd, Long pbancSn, String content);

    /** 그 사업건의 제안팀원 전원에게 보낸다. 예: 나라장터 등록 완료 */
    void sendToProposalTeam(Long pbancSn, String typeCd, String content);
}
