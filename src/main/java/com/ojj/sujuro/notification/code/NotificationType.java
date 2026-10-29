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
    public static final String TOC_ASSIGNED = "NT100";           // 목차 담당
    public static final String EVL_TABLE_REQUEST = "NT110";      // 평가표 업로드 요청
    public static final String RQT_CHANGED = "NT120";            // 요구사항 변경 (DB 공통코드 추가 요청 중)
    public static final String PROPOSAL_TEAM_CHANGED = "NT130";  // 제안팀 변경 (DB 공통코드 추가 요청 중)

    private NotificationType() {
    }
}
