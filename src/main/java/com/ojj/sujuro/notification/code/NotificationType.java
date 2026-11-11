package com.ojj.sujuro.notification.code;

/**
 * 알림 유형 코드 (공통코드 NOTIT). 알림을 보낼 때 문자열을 직접 쓰지 말고 이 값을 쓴다.
 * 계정 요청·발급(NT080, NT090)은 외부 인력용이라 쓰지 않는다.
 */
public final class NotificationType {

    public static final String BID_REGISTERED = "NT010";         // 본공고 등록
    public static final String BID_CHANGED = "NT020";            // 공고 변경
    public static final String ASSIGNED = "NT030";               // 배정
    public static final String PARTICIPATION_DECIDED = "NT040";  // 참여 결정
    public static final String DEADLINE_NEAR = "NT050";          // 마감 임박
    public static final String WITHDRAWN = "NT060";              // 중도 철회
    public static final String SUBMITTED = "NT070";              // 제출 완료
    public static final String EVL_TABLE_REQUEST = "NT110";      // 평가표 업로드 요청
    public static final String BID_STAGE_CHANGED = "NT150";      // 공고 단계 전환 (발주계획→사전규격, →입찰공고). 영업부에 화면+메일 (FR-027)
    public static final String SCHEDULE_CHANGED = "NT160";       // 일정 등록·변경·삭제. 그 사업건의 제안팀원 전원에게 화면 알림만 (메일 없음) (FR-034)
    public static final String PROPOSAL_TEAM_ADDED = "NT180";    // 제안팀 추가. 제안팀에 새로 들어간 그 사람에게 화면 알림 (메일 없음)
    public static final String COLLECT_RESULT = "NT170";         // 공고 수집 결과. 평일 17시 자동 수집 요약을 영업부에 화면 알림 (FR-025, bid 의 BidCode.NOTI_COLLECT_RESULT 와 같은 값)

    private NotificationType() {
    }
}
