package com.ojj.sujuro.bid;

/**
 * 공고(TB_BID_PBANC)가 쓰는 공통코드 값. 코드값이 바뀌면 이 파일만 고친다.
 * 코드 이름(화면에 보여 줄 글자)은 TB_COM_CD 를 조인해서 받아 오고, 여기서는 분기에 쓰는 값만 둔다.
 *
 * 컬럼은 CHAR(5) 라서 값을 비교할 때 뒤에 공백이 붙어 올 수 있다. {@link #is(String, String)} 를 쓴다.
 */
public final class BidCode {

    private BidCode() {
    }

    /**
     * 공고단계 (PBSTP). 10/23 회의에서 정한 라이프사이클이다.
     *
     *   발주계획 ──┐
     *   사전규격 ──┴→ 입찰공고 → 개찰 → 계약
     *
     * 발주계획과 사전규격은 서로를 가리키는 필드가 API 에 없어 직접 이어지지 않는다.
     * 둘 다 응답의 bidNtceNoList 로 입찰공고에서 합류한다. (2026-10-26 실호출 확인)
     * 회의의 "요청접수"는 조달요청 서비스에 용역 오퍼레이션이 없어 뺐다.
     */
    public static final String STEP_ORDER_PLAN = "PP010"; // 발주계획
    public static final String STEP_PRE_SPEC = "PP020";   // 사전규격
    public static final String STEP_BID_NOTICE = "PP030"; // 입찰공고
    public static final String STEP_OPENING = "PP040";    // 개찰
    public static final String STEP_CONTRACT = "PP050";   // 계약

    /** 추적상태 (TRSTS) - 발주계획·사전규격으로 등록해 입찰공고를 기다리는 공고가 쓴다 */
    public static final String TRACK_WAITING = "TR010"; // 입찰공고 대기
    public static final String TRACK_LINKED = "TR020";  // 연결됨
    public static final String TRACK_CLOSED = "TR030";  // 추적 종료

    /** 공고상태 (PBSTS) */
    public static final String STTS_NEW = "PS010";       // 신규
    public static final String STTS_ASSIGNED = "PS020";  // 배정 완료
    public static final String STTS_JOINED = "PS030";    // 참여 확정
    public static final String STTS_WRITING = "PS040";   // 제안 작성 중
    public static final String STTS_SUBMITTED = "PS050"; // 제출 완료
    public static final String STTS_RESULT = "PS060";    // 결과 등록
    public static final String STTS_DECLINED = "PS090";  // 불참

    /** 반환구분 (RTRSE) - 팀에서 영업부로 돌아온 공고만 값이 있다 */
    public static final String RTRN_DECLINE = "RT010";  // 불참
    public static final String RTRN_WITHDRAW = "RT020"; // 중도 철회

    /** 처리구분 (PRCSE) - 공고처리이력에 남기는 사람의 행동 */
    public static final String PRCS_REGIST = "PC010";    // 관심 공고 등록
    public static final String PRCS_CORRECT = "PC020";   // 공고 정정
    public static final String PRCS_ASSIGN = "PC030";    // 배정
    public static final String PRCS_REASSIGN = "PC040";  // 재배정
    public static final String PRCS_DECIDE = "PC050";    // 참여 결정
    public static final String PRCS_DECLINE = "PC060";   // 불참 처리
    public static final String PRCS_WITHDRAW = "PC070";  // 중도 철회

    /**
     * 공고상태에 맞는 pill 색 이름. common.css 의 {@code .pill.<색>} 에 쓴다.
     * UI 목업 v3.2 의 ST_PILL 과 같은 값이라, 목업과 화면 색이 어긋나지 않는다.
     */
    public static String sttsPillClass(String sttsCd) {
        String cd = trim(sttsCd);
        if (STTS_NEW.equals(cd)) {
            return "info";
        }
        if (STTS_ASSIGNED.equals(cd)) {
            return "warning";
        }
        if (STTS_JOINED.equals(cd) || STTS_SUBMITTED.equals(cd)) {
            return "success";
        }
        if (STTS_WRITING.equals(cd)) {
            return "accent";
        }
        return "neutral"; // 결과 등록, 불참, 그 밖의 값
    }

    /** CHAR 컬럼이라 뒤에 공백이 붙어 올 수 있어 잘라서 비교한다. */
    public static boolean is(String code, String expected) {
        return expected != null && expected.equals(trim(code));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
