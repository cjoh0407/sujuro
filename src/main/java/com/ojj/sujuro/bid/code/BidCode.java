package com.ojj.sujuro.bid.code;

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
     * 공고단계 (PBSTP)
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

    /**
     * 공고유형 (PBTYP) - API ntceKindNm 을 코드로 옮긴 값. 입찰공고 단계부터 값이 있다.
     * 실측 300건에서 네 가지만 나왔다: 등록공고 246 / 재공고 28 / 취소공고 16 / 변경공고 10. (2026-10-26)
     */
    public static final String TYPE_NORMAL = "PY010";   // 일반공고 (API 는 '등록공고'라고 한다)
    public static final String TYPE_CHANGED = "PY020";  // 변경공고
    public static final String TYPE_RE = "PY030";       // 재공고
    public static final String TYPE_CANCELED = "PY040"; // 취소공고

    /** 공고상태 (PBSTS) */
    public static final String STTS_NEW = "PS010";       // 신규
    public static final String STTS_ASSIGNED = "PS020";  // 배정 완료
    public static final String STTS_JOINED = "PS030";    // 참여 확정
    public static final String STTS_WRITING = "PS040";   // 제안 작성 중
    public static final String STTS_SUBMITTED = "PS050"; // 제출 완료
    public static final String STTS_RESULT = "PS060";    // 결과 등록
    public static final String STTS_FAILED_BID = "PS070"; // 유찰 - 개찰했으나 입찰이 성립하지 않았다
    public static final String STTS_DECLINED = "PS090";  // 불참

    /** 반환구분 (RTRSE) - 팀에서 영업부로 돌아온 공고만 값이 있다 */
    public static final String RTRN_DECLINE = "RT010";  // 불참
    public static final String RTRN_WITHDRAW = "RT020"; // 중도 철회

    /** 참여형태 (PTFRM) - 팀 배정 때 영업부가 고른다. 고르는 칸은 공통코드에서 읽는다. */
    public static final String GROUP_PTCP_SHP = "PTFRM";

    /** 부서 (DEPTS) - 공고를 배정받는 팀은 개발부 팀이다 */
    public static final String DEPT_DEV = "DP030";

    /** 참여구분 (PTCSE) - 참여를 결정한 팀장이 주관팀(PT010)으로 제안팀에 처음 들어간다 */
    public static final String PTCP_LEAD = "PT010";

    /** 실행구분 (EXCSE) - 수집을 누가 돌렸는지 */
    public static final String EXCN_AUTO = "EX010";   // 자동 (스케줄러, 평일 오후 5시)
    public static final String EXCN_MANUAL = "EX020"; // 수동 ([지금 수집])

    /** 수집결과 (CLRST) - 수집로그에 남기는 결과 */
    public static final String CLCT_NO_CHANGE = "CL010";     // 변경 없음
    public static final String CLCT_TO_BID_NOTICE = "CL020"; // 입찰공고 전환
    public static final String CLCT_CORRECTED = "CL030";     // 정정 반영
    public static final String CLCT_CANCELED = "CL040";      // 취소 공고
    public static final String CLCT_OPENING = "CL050";       // 개찰 결과 (유찰 포함)
    public static final String CLCT_TO_PRE_SPEC = "CL060";   // 사전규격 전환
    public static final String CLCT_TO_CONTRACT = "CL070";   // 계약 전환
    public static final String CLCT_FAILED = "CL090";      // 실패

    /** 처리구분 (PRCSE) - 공고처리이력에 남기는 사람의 행동 */
    public static final String PRCS_REGIST = "PC010";    // 공고 등록
    public static final String PRCS_CORRECT = "PC020";   // 공고 정정
    public static final String PRCS_ASSIGN = "PC030";    // 배정
    public static final String PRCS_REASSIGN = "PC040";  // 재배정
    public static final String PRCS_DECIDE = "PC050";    // 참여 결정
    public static final String PRCS_DECLINE = "PC060";   // 불참 처리
    public static final String PRCS_WITHDRAW = "PC070";  // 중도 철회

    /**
     * 파일구분 (FILSE) - 나라장터에서 받아 TB_ATCH_FILE 에 넣는 공고 첨부. 제안팀이 올리는 파일(FL020~070)과 같은 표를 쓴다.
     * 입찰공고 첨부 중 제안요청서만 FL010 이고 공고서·과업지시서 같은 나머지는 FL090 이다.
     */
    public static final String FILE_RFP = "FL010";       // RFP (입찰공고의 제안요청서)
    public static final String FILE_PRE_SPEC = "FL080";  // 사전규격 규격서 (제안요청서 초안 포함)
    public static final String FILE_BID_ATCH = "FL090";  // 입찰공고 첨부 (제안요청서 말고 나머지)

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
        if (STTS_FAILED_BID.equals(cd)) {
            // 유찰은 입찰이 없어진 것이라 눈에 걸려야 한다. 재공고를 기다릴지 판단해야 하기 때문이다.
            return "danger";
        }
        if (STTS_WRITING.equals(cd)) {
            return "accent";
        }
        return "neutral"; // 결과 등록, 불참, 그 밖의 값
    }

    /**
     * API 의 공고종류명(ntceKindNm)을 공고유형코드로 옮긴다.
     * 02_code_data.sql 의 PBTYP 설명과 같은 규칙이니, 규칙을 바꾸면 양쪽을 함께 고친다.
     *
     * 앞글자만 보는 이유: 예전 테스트 데이터에 '등록'처럼 '공고'가 빠진 값이 있어서다.
     * 모르는 값이면 null 을 돌려준다. 코드가 비면 화면에 '-' 로 나오고, 원문은 수집이력에 남는다.
     */
    public static String pbancTypeCdOf(String ntceKindNm) {
        String nm = trim(ntceKindNm);
        if (nm == null || nm.isEmpty()) {
            return null;
        }
        if (nm.startsWith("등록")) {
            return TYPE_NORMAL;
        }
        if (nm.startsWith("변경")) {
            return TYPE_CHANGED;
        }
        if (nm.startsWith("재공고")) {
            return TYPE_RE;
        }
        if (nm.startsWith("취소")) {
            return TYPE_CANCELED;
        }
        return null;
    }

    /**
     * 공고유형 pill 색 이름. common.css 의 {@code .pill.<색>} 에 쓴다.
     * 취소는 입찰이 없어진 것이라 눈에 걸려야 하므로 danger, 변경은 다시 확인해야 하므로 warning 을 쓴다.
     */
    public static String pbancTypePillClass(String pbancTypeCd) {
        String cd = trim(pbancTypeCd);
        if (TYPE_CANCELED.equals(cd)) {
            return "danger";
        }
        if (TYPE_CHANGED.equals(cd)) {
            return "warning";
        }
        if (TYPE_RE.equals(cd)) {
            return "info";
        }
        return "neutral"; // 일반공고, 그리고 아직 입찰공고가 아니라 유형이 없는 공고
    }

    /**
     * 사업명을 비교용으로 다듬는다. 발주계획과 사전규격이 같은 사업을 서로 다르게 적는 패턴을 지운다.
     *
     * 발주계획과 사전규격은 서로를 가리키는 번호가 없어서, 같은 사업인지를 값으로 맞춰 봐야 한다.
     * 실측한 61쌍(통합공개 API 로 정답을 아는 쌍)에서 그냥 비교하면 92%, 이 정규화를 거치면 100% 붙었다.
     * 어긋나던 패턴은 네 가지였다. (2026-10-26)
     *   괄호   발주 "…역량강화사업(2026-2030, 995만불) PMC 용역" / 사전 "…역량강화사업 PMC 용역"
     *   괄호   발주 "…실시설계용역(장기계속)"                  / 사전 "…실시설계용역"
     *   대괄호 사전 "[긴급] (가칭)여주특수학교…"                / 발주 "(가칭)여주특수학교…"
     *   공백   사전 "폐기물 처리 용역"                         / 발주 "폐기물 처리용역"
     *
     * 유사도(Levenshtein 등)는 쓰지 않는다. 정규화만으로 전부 붙는데, 유사도를 쓰면 다른 사업을
     * 잘못 붙일 위험만 생긴다. 비교할 때는 기관명도 반드시 함께 봐야 한다.
     */
    public static String normalizeBizNm(String bizNm) {
        if (bizNm == null) {
            return null;
        }
        return bizNm.replaceAll("\\([^)]*\\)", "")
                    .replaceAll("\\[[^\\]]*\\]", "")
                    .replaceAll("\\s", "");
    }

    /**
     * 발주계획과 사전규격이 같은 사업인지 본다. 기관명은 완전일치, 사업명은 정규화 후 완전일치다.
     *
     * 사전규격 쪽 기관은 실수요기관명(rlDminsttNm)을 넘겨야 한다. 발주기관명(orderInsttNm)은
     * 조달청 위탁 건에서 ''조달청 서울지방조달청''으로 와서 발주계획의 기관명과 달라 92%만 맞는다.
     *
     * 이 조건을 만족하는 발주계획이 하나면 그 건으로 확정해도 된다. 둘 이상이면 사람이 골라야 한다.
     * 같은 기관이 같은 사업명·같은 예산으로 권역을 쪼개 발주하는 경우가 있고(실측 4.7%),
     * API 가 구별할 필드를 주지 않아 어떤 방법으로도 자동으로는 가릴 수 없다.
     */
    public static boolean sameBizCase(String orderInstNm, String orderBizNm,
                                      String specInstNm, String specBizNm) {
        if (orderInstNm == null || specInstNm == null) {
            return false;
        }
        if (!orderInstNm.trim().equals(specInstNm.trim())) {
            return false;
        }
        String a = normalizeBizNm(orderBizNm);
        String b = normalizeBizNm(specBizNm);
        return a != null && !a.isEmpty() && a.equals(b);
    }

    /**
     * 공고단계 pill 색 이름. 입찰공고가 나기 전(발주계획·사전규격)은 회색으로 두고,
     * 참여 결정을 할 수 있는 입찰공고부터 색을 준다.
     */
    public static String stepPillClass(String pbancStepCd) {
        String cd = trim(pbancStepCd);
        if (STEP_BID_NOTICE.equals(cd)) {
            return "accent";
        }
        if (STEP_OPENING.equals(cd)) {
            return "info";
        }
        if (STEP_CONTRACT.equals(cd)) {
            return "success";
        }
        return "neutral"; // 발주계획, 사전규격 - 아직 입찰공고를 기다리는 단계
    }

    /** CHAR 컬럼이라 뒤에 공백이 붙어 올 수 있어 잘라서 비교한다. */
    public static boolean is(String code, String expected) {
        return expected != null && expected.equals(trim(code));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
