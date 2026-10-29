package com.ojj.sujuro.history.code;

/** 공고 처리 구분 코드 (공통코드 PRCSE). 이력을 남길 때 문자열을 직접 쓰지 말고 이 값을 쓴다. */
public final class ProcessType {

    public static final String INTEREST_REGISTERED = "PC010"; // 관심 공고 등록
    public static final String BID_CORRECTED = "PC020";       // 공고 정정
    public static final String ASSIGNED = "PC030";            // 배정
    public static final String REASSIGNED = "PC040";          // 재배정
    public static final String PARTICIPATION_DECIDED = "PC050"; // 참여 결정
    public static final String DECLINED = "PC060";           // 불참 처리
    public static final String WITHDRAWN = "PC070";          // 중도 철회
    public static final String PROPOSAL_STARTED = "PC080";   // 제안 작성 시작
    public static final String REGISTERED_NARAJANGTER = "PC090"; // 나라장터 등록
    public static final String RESULT_REGISTERED = "PC100";  // 결과 등록

    private ProcessType() {
    }
}
