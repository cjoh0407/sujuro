package com.ojj.sujuro.bid.service;

import java.util.List;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.bid.dto.CodeOptionDTO;
import com.ojj.sujuro.bid.dto.TeamOptionDTO;

/**
 * 담당 팀 배정과 참여 결정 (FR-024, 029, 030, 037~041). 둘 다 공고 상세 한 곳에서 한다.
 *
 *   신규 ──[영업부 배정]──▶ 배정 완료 ──[팀장 참여]──▶ 참여 확정 · 제안 작성 중 (결정한 팀장이 제안팀 주관팀으로 들어간다)
 *    ▲  │                      │                              │
 *    │  └─[영업부 불참 종결]─▶ 불참 (끝)                       │
 *    ├──────[팀장 불참: 사유 필수, 담당 팀 비우고 '불참 반환' 표시]  │
 *    └──────[팀장 중도 철회: 사유 필수, 제안팀을 풀고 '중도 철회 반환' 표시]
 *
 * 배정·결정마다 공고처리이력(HistoryService)을 남기고 알림(NotificationService)을 보낸다.
 * 이력·알림은 처리와 같은 트랜잭션이라, 처리가 실패하면 함께 없어진다.
 * 업무 규칙(상태·단계·사유)에 어긋나면 BusinessException 을 던진다. 화면은 그 문구를 그대로 보여 준다.
 */
public interface NoticeTeamService {

    /** 배정할 수 있는 팀 (개발부). 고르는 칸에 팀장 이름을 같이 보여 준다. */
    List<TeamOptionDTO> findTeamOptions();

    /** 참여 형태 (공통코드 PTFRM: 주관 / 컨소시엄 참여 / 하도급) */
    List<CodeOptionDTO> findPtcpShpOptions();

    /**
     * 영업부가 주관 팀을 배정한다 (FR-037). 이미 팀이 있거나 팀에서 돌아온 공고면 재배정이다 (FR-040).
     * 배정된 팀의 팀장에게 알린다 (FR-029). 결과 문구를 돌려준다.
     *
     * @param ptcpShpCd 참여 형태. 비우면 미지정
     */
    String assign(Long pbancSn, Long teamSn, String ptcpShpCd, String userId);

    /** 영업부가 다른 팀에도 맡기지 않고 불참으로 끝낸다. 신규 공고만, 사유 필수 */
    String close(Long pbancSn, String reason, String userId);

    /** 이 사용자가 이 공고에 배정된 팀의 팀장인지. 참여 결정 입력은 이 사람에게만 보인다 (FR-039). */
    boolean isAssignedLeader(Long pbancSn, LoginUser user);

    /**
     * 배정된 팀의 팀장이 참여·불참을 정한다 (FR-038). 불참이면 사유가 필수이고 공고가 영업부로 돌아간다 (FR-040).
     * 영업부에 알린다 (FR-030). 배정된 팀의 팀장이 아니면 AccessDeniedException(403).
     */
    String decide(Long pbancSn, boolean join, String reason, LoginUser user);

    /**
     * 참여 확정 뒤 배정된 팀의 팀장이 사유를 넣어 중도 철회한다 (FR-042). 참여 확정·제안 작성 중만 된다.
     * 공고는 영업부로 넘어가고(신규 · 중도 철회 반환), 제안팀원·참여팀·목차 담당자 지정이 풀린다.
     * 목차·요구사항·올린 파일은 남는다. 영업부에 알린다. 배정된 팀의 팀장이 아니면 AccessDeniedException(403).
     */
    String withdraw(Long pbancSn, String reason, LoginUser user);
}
