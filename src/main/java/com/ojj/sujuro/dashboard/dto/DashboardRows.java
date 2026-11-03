package com.ojj.sujuro.dashboard.dto;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import lombok.Getter;
import lombok.Setter;

/** 대시보드 조회 결과 모양들. 화면(templates/dashboard/index.html)이 그대로 쓴다. */
public final class DashboardRows {

    private static final DateTimeFormatter DDLN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DashboardRows() {
    }

    // ===================================================================================
    // 영업부
    // ===================================================================================

    /** 공고(사업건) 한 줄 */
    @Getter
    @Setter
    public static class Bid {
        private Long pbancSn;
        private String pbancNm;
        private String statusCd;
        private String statusNm;
        private String stepNm;     // 공고 단계 (발주계획·사전규격·입찰공고 ...)
        private String returnNm;   // 불참·중도 철회로 영업부에 돌아온 건의 구분 (없으면 null)
        private String teamNm;     // 주관팀
        private String ddlnNm;     // 마감 이름: 사전규격은 '의견등록 마감', 그 밖에는 '제출 마감'. 마감일이 없으면 null
        private String ddlnText;   // 그 마감 일시 (2026-10-29 17:00)
        private Integer dday;      // 마감까지 남은 일수. 마감일이 없으면 null

        public String getDdayLabel() { return DashboardRows.ddayLabel(dday); }
        public String getStatusPill() { return DashboardRows.statusPill(statusCd); }
        /** 동그라미·칩에 쓰는 짧은 표시 (D-2, D-day, 지남) */
        public String getDdayChip() { return DashboardRows.ddayChip(dday); }
        /** 마감이 가까울수록 진해지는 색 이름 (danger 3일 이내, warning 7일 이내, accent 그 밖) */
        public String getTone() { return DashboardRows.ddayTone(dday); }
        /** 제출 마감 11/05 (목) 10:00 */
        public String getDdlnLine() {
            return ddlnText == null ? "마감일 미정" : (ddlnNm == null ? "마감" : ddlnNm) + " " + DashboardRows.shortDate(ddlnText);
        }
        /** 곧 마감 타일의 마감 일시 (11/05 (목) 10:00). 사전규격은 앞에 '의견'을 붙인다 */
        public String getDdlnShort() {
            if (ddlnText == null) {
                return "마감일 미정";
            }
            return (ddlnNm != null && ddlnNm.startsWith("의견") ? "의견 " : "") + DashboardRows.shortDate(ddlnText);
        }

        /** 곧 마감 타일의 둘째 줄: 배정 완료 · 공공사업1Div / 신규 · 미배정 / 신규 · 불참 반환 */
        public String getTileSub() {
            String st = statusNm == null ? "" : statusNm;
            if (teamNm != null) {
                return st + " · " + teamNm;
            }
            if ("PS010".equals(statusCd)) {
                return st + " · " + (returnNm != null ? returnNm + " 반환" : "미배정");
            }
            return st;
        }
    }

    /** 영업부 숫자 카드 */
    @Getter
    @Setter
    public static class SalesCounts {
        private int assignWait;    // 배정 대기 (신규 공고: 발주계획·사전규격·입찰공고)
        private int awaitingTeam;  // 팀장 응답 대기 (배정 완료)
        private int dueSoon;       // 배정 대기 중 제출 마감이 3일 이내인 것
        private int returned;      // 반환 건
        private int tracking;      // 입찰공고 대기 (발주계획·사전규격 추적 중)
    }

    /** 팀별 현황 한 줄 */
    @Getter
    @Setter
    public static class TeamStat {
        private Long teamSn;     // 팀 색(캘린더와 같은 색)을 고르는 데 쓴다
        private String teamNm;
        private int assigned;    // 배정 완료 (팀장 결정 대기)
        private int inProgress;  // 참여 확정·제안 작성 중
        private int submitted;   // 제출 완료
        private int resulted;    // 결과 등록
    }

    // ===================================================================================
    // 경영지원실
    // ===================================================================================

    /** 제출서류 준비 현황 한 줄 (경영지원실) */
    @Getter
    @Setter
    public static class DocRow {
        private Long pbancSn;
        private String pbancNm;
        private String teamNm;
        private Integer dday;       // 제출 마감까지 남은 일수
        private int docTotal;
        private int docReady;
        private int missingRequired;
        private boolean registered; // 나라장터 등록(제출) 완료

        /** 서류가 있고 필수 서류가 모두 준비됐으며 아직 등록 전이면 등록 대기 */
        public boolean isWaitingRegistration() {
            return !registered && docTotal > 0 && missingRequired == 0;
        }

        /** 제출 마감이 3일 이내인데 서류가 덜 된 건 (경영지원실 '제출 마감 임박') */
        public boolean isDueSoonUnready() {
            return !registered && !isWaitingRegistration() && dday != null && dday >= 0 && dday <= 3;
        }

        public String getStateLabel() {
            return registered ? "등록 완료" : (isWaitingRegistration() ? "등록 대기" : "작성 중");
        }

        public String getStatePill() {
            return registered ? "success" : (isWaitingRegistration() ? "warning" : "neutral");
        }

        /** 서류 준비율(%) - 목록의 작은 막대 길이 */
        public int getReadyPercent() {
            return docTotal <= 0 ? 0 : (int) Math.round(docReady * 100.0 / docTotal);
        }
    }

    /** 결과 가져오기 대기: 건수와 가장 오래된 건의 개찰 후 경과 일수 */
    @Getter
    @Setter
    public static class ResultWait {
        private int cnt;
        private Integer maxDays;
    }

    /** 연도별 성과 한 줄. 연도는 제출 마감(없으면 개찰) 기준 */
    @Getter
    @Setter
    public static class YearStat {
        private int yearNo;
        private int submitted;  // 제출 완료 이후(제출 완료·결과 등록·유찰)
        private int won;        // 수주 BR010
        private int lost;       // 탈락 BR020
        private int failed;     // 유찰 PS070
        private long wonAmt;    // 수주 낙찰금액 합계(원)

        /** 수주 ÷ (수주+탈락). 유찰·불참은 뺀다. 결과가 없으면 null */
        public Integer getRate() {
            int decided = won + lost;
            return decided == 0 ? null : (int) Math.round(won * 100.0 / decided);
        }
    }

    /** 주의가 필요한 항목 한 줄 (경영지원실) */
    @Getter
    @Setter
    public static class MgmtAlert {
        public static final String DOC_MISSING = "DOC_MISSING";   // 필수 서류 누락
        public static final String PAST_DUE = "PAST_DUE";         // 제출 마감 지남 · 서류 미준비
        public static final String RESULT_WAIT = "RESULT_WAIT";   // 결과 미등록

        private String kind;
        private Long pbancSn;
        private String pbancNm;
        private String teamNm;
        private int missing;        // 누락된 필수 서류 수
        private Integer dday;       // 제출 마감까지 남은 일수 (서류 관련)
        private Integer passedDays; // 개찰 후 경과 일수 (결과 관련)

        public String getTitle() {
            if (PAST_DUE.equals(kind)) {
                return "마감 지남";
            }
            if (RESULT_WAIT.equals(kind)) {
                return "결과 미등록";
            }
            return missing > 0 ? "서류 " + missing + "건 누락" : "서류 미등록";
        }

        /** 오른쪽 끝 표시: D-2 / D+1 / +3일 */
        public String getRight() {
            if (RESULT_WAIT.equals(kind)) {
                return "+" + (passedDays == null ? 0 : passedDays) + "일";
            }
            if (dday == null) {
                return "-";
            }
            return dday < 0 ? "D+" + (-dday) : (dday == 0 ? "D-day" : "D-" + dday);
        }

        /** 빨강(위험) 또는 갈색(주의) */
        public String getTone() {
            if (PAST_DUE.equals(kind)) {
                return "danger";
            }
            if (DOC_MISSING.equals(kind) && dday != null && dday <= 3) {
                return "danger";
            }
            return "warning";
        }

        public String getHref() {
            return (RESULT_WAIT.equals(kind) ? "/results?bid=" : "/docs?bid=") + pbancSn;
        }
    }

    /** 올해 수주한 사업 한 줄 (경영지원실) */
    @Getter
    @Setter
    public static class Win {
        private Long pbancSn;
        private String pbancNm;
        private String orderInstNm;  // 발주기관
        private String dmndInstNm;   // 수요기관
        private String teamNm;       // 담당(주관)팀
        private String bizTypeNm;    // SI / 유지보수
        private long scsbdAmt;       // 낙찰금액(원)
        private long budgetAmt;      // 배정예산(원)
        private int rivalCnt;        // 개찰에 참가한 업체 수(우리 포함). 데이터가 없으면 0
        private Integer evlRank;     // 평가순위

        public String getAmtText() { return DashboardRows.eok(scsbdAmt); }
        public String getBudgetText() { return DashboardRows.eok(budgetAmt); }
        /** 낙찰률 = 낙찰금액 ÷ 배정예산. 예산이 없으면 null */
        public Double getRate() { return budgetAmt <= 0 ? null : scsbdAmt * 100.0 / budgetAmt; }
        public String getRateText() { Double r = getRate(); return r == null ? "-" : String.format(Locale.ROOT, "%.1f%%", r); }
        public String getRivalText() {
            if (rivalCnt <= 0) {
                return "-";
            }
            return rivalCnt + "개사" + (evlRank == null ? "" : " · " + evlRank + "위");
        }
        public String getTypePill() { return "SI".equals(bizTypeNm) ? "accent" : "neutral"; }
    }

    // ===================================================================================
    // 팀장 · 팀원
    // ===================================================================================

    /** 팀장 숫자 카드 */
    @Getter
    @Setter
    public static class LeaderCounts {
        private int decisionWait; // 참여 결정 대기
        private int inProgress;   // 진행 중 사업
        private int unmapped;     // 목차에 연결되지 않은 요구사항
        private int dueSoon;      // 마감 7일 이내
    }

    /** 작은 원 하나: 가운데 글자(2/9)와 채워진 비율 */
    @Getter
    public static class Ring {
        private final String text;
        private final int percent;
        private final boolean empty;

        public Ring(int done, int total) {
            this.empty = total <= 0;
            this.text = total <= 0 ? "-" : done + "/" + total;
            this.percent = total <= 0 ? 0 : Math.min(100, (int) Math.round(done * 100.0 / total));
        }
    }

    /** 팀장 '사업별 제안서 진행 현황' 한 줄 */
    @Getter
    @Setter
    public static class LeaderProject {
        private Long pbancSn;
        private String pbancNm;
        private String statusCd;
        private String statusNm;
        private int leadYn;          // 1이면 내가 주관팀 제안팀원, 0이면 참여팀
        private String ddlnText;
        private Integer dday;
        private int tocTotal;        // 하위 목차가 없는 목차 수
        private int tocDone;         // 그중 검토 완료
        private int rqtTotal;
        private int rqtMapped;
        private int docTotal;
        private int docReady;
        private int finalConfirmed;  // 최종본 확정(통합본 LAST_YN='Y') 여부 1/0

        public boolean isLead() { return leadYn == 1; }
        public String getRoleLabel() { return leadYn == 1 ? "주관팀" : "참여팀"; }
        public boolean isFinalConfirmed() { return finalConfirmed == 1; }
        public int getUnmapped() { return rqtTotal - rqtMapped; }
        public Ring getTocRing() { return new Ring(tocDone, tocTotal); }
        public Ring getRqtRing() { return new Ring(rqtMapped, rqtTotal); }
        public Ring getDocRing() { return new Ring(docReady, docTotal); }
        public String getDdayChip() { return DashboardRows.ddayChip(dday); }
        public String getTone() { return DashboardRows.ddayTone(dday); }
        public String getStatePill() { return DashboardRows.statusPill(statusCd); }
        public String getDdlnShort() { return ddlnText == null ? "마감일 미정" : DashboardRows.shortDate(ddlnText); }
    }

    /** 팀장 '처리할 일' 한 줄 */
    @Getter
    @Setter
    public static class TodoItem {
        public static final String DECISION = "DECISION";  // 참여 결정
        public static final String FINAL = "FINAL";        // 최종본 확정
        public static final String DOCS = "DOCS";          // 제출서류
        public static final String RQT = "RQT";            // 요구사항 연결
        public static final String RESULT = "RESULT";      // 결과 입력

        private String kind;
        private Long pbancSn;
        private String pbancNm;
        private String sub;        // 둘째 줄 설명
        private String ddlnText;   // 제출 마감 일시 (2026-11-05 10:00). 결과 입력은 null
        private Integer dday;      // 제출 마감까지 남은 일수. 결과 입력은 null

        public String getTypeLabel() {
            switch (kind == null ? "" : kind) {
                case DECISION: return "참여 결정";
                case FINAL: return "최종본 확정";
                case DOCS: return "제출서류";
                case RQT: return "요구사항 연결";
                case RESULT: return "결과 입력";
                default: return "";
            }
        }

        /** 참여 결정과 결과 입력은 파랑, 나머지는 회색 */
        public String getTypePill() { return DECISION.equals(kind) || RESULT.equals(kind) ? "accent" : "neutral"; }

        public String getActionLabel() {
            switch (kind == null ? "" : kind) {
                case DECISION: return "공고 상세 보기";
                case FINAL: return "파일 확인";
                case DOCS: return "서류 확인";
                case RQT: return "연결하기";
                case RESULT: return "점수 입력";
                default: return "열기";
            }
        }

        /** 참여 결정은 공고 상세에서 한다. 나머지는 해당 작업 화면 */
        public String getHref() {
            switch (kind == null ? "" : kind) {
                case DECISION: return "/notices/" + pbancSn;
                case FINAL: return "/files?bid=" + pbancSn;
                case DOCS: return "/docs?bid=" + pbancSn;
                case RQT: return "/proposal?bid=" + pbancSn + "&view=unmapped";
                case RESULT: return "/results?bid=" + pbancSn;
                default: return "/proposal?bid=" + pbancSn;
            }
        }

        public String getDdayChip() { return dday == null ? null : DashboardRows.ddayChip(dday); }
        public String getTone() { return DashboardRows.ddayTone(dday); }
    }

    /** 팀원 '사업별 제안서 진행 현황' 한 줄 */
    @Getter
    @Setter
    public static class MemberProject {
        private Long pbancSn;
        private String pbancNm;
        private String statusCd;
        private String statusNm;
        private String ddlnText;
        private Integer dday;
        private int myTocTotal;    // 내가 맡은 목차
        private int myTocDone;     // 그중 검토 완료
        private int myFileTocs;    // 그중 파일을 올린 목차
        private int allTocTotal;   // 사업 전체 목차(하위 없는 것)
        private int allTocDone;    // 그중 검토 완료

        public Ring getMyDoneRing() { return new Ring(myTocDone, myTocTotal); }
        public Ring getMyFileRing() { return new Ring(myFileTocs, myTocTotal); }
        public Ring getAllRing() { return new Ring(allTocDone, allTocTotal); }
        public String getDdayChip() { return DashboardRows.ddayChip(dday); }
        public String getTone() { return DashboardRows.ddayTone(dday); }
        public String getStatePill() { return DashboardRows.statusPill(statusCd); }
        public String getDdlnShort() { return ddlnText == null ? "마감일 미정" : DashboardRows.shortDate(ddlnText); }
    }

    /** 내가 맡은 목차 한 줄 (팀원) */
    @Getter
    @Setter
    public static class MyToc {
        private Long pbancSn;
        private String pbancNm;
        private String tocNo;
        private String tocNm;
        private Integer expcPageCnt;
        private String tocStatusCd;  // 목차진행상태 TS010 미착수, TS020 작성 중, TS030 검토 완료
        private String tocStatusNm;
        private Integer fileCnt;
        private String ddlnNm;
        private String ddlnText;
        private Integer dday;

        public String getDdayLabel() { return DashboardRows.ddayLabel(dday); }
        public String getDdayChip() { return DashboardRows.ddayChip(dday); }
        public String getTone() { return DashboardRows.ddayTone(dday); }
        public boolean isDone() { return "TS030".equals(tocStatusCd == null ? null : tocStatusCd.trim()); }
        /** 미착수 회색, 작성 중 파랑, 검토 완료 초록 */
        public String getStatePill() {
            String cd = tocStatusCd == null ? "" : tocStatusCd.trim();
            return "TS030".equals(cd) ? "success" : ("TS020".equals(cd) ? "accent" : "neutral");
        }
        public String getTitle() { return ((tocNo == null ? "" : tocNo + " ") + (tocNm == null ? "" : tocNm)).trim(); }
        public String getFileLabel() { return fileCnt != null && fileCnt > 0 ? "파일 " + fileCnt + "건 올림" : "파일 없음"; }
        /** 검토 완료면 목차를 보러, 아니면 파일을 올리러 간다 */
        public String getActionLabel() { return isDone() ? "목차 보기" : "파일 올리기"; }
        public String getActionHref() { return (isDone() ? "/proposal?bid=" : "/files?bid=") + pbancSn; }
    }

    // ===================================================================================
    // 공통
    // ===================================================================================

    public static String ddayLabel(Integer dday) {
        if (dday == null) {
            return "-";
        }
        if (dday < 0) {
            return "마감 지남";
        }
        return dday == 0 ? "오늘 마감" : "D-" + dday;
    }

    /** 칩·동그라미 안에 들어가는 짧은 표시 */
    public static String ddayChip(Integer dday) {
        if (dday == null) {
            return "-";
        }
        if (dday < 0) {
            return "지남";
        }
        return dday == 0 ? "D-day" : "D-" + dday;
    }

    /** 마감까지 3일 이내 danger(빨강), 7일 이내 warning(갈색), 그 밖 accent(파랑). 마감일이 없으면 neutral */
    public static String ddayTone(Integer dday) {
        if (dday == null) {
            return "neutral";
        }
        if (dday <= 3) {
            return "danger";
        }
        return dday <= 7 ? "warning" : "accent";
    }

    /** "2026-11-05 10:00" → "11/05 (목) 10:00". 모양이 다르면 받은 글자 그대로 */
    public static String shortDate(String ddlnText) {
        if (ddlnText == null) {
            return "";
        }
        try {
            LocalDateTime t = LocalDateTime.parse(ddlnText.trim(), DDLN);
            DayOfWeek dow = t.getDayOfWeek();
            return String.format(Locale.ROOT, "%02d/%02d (%s) %02d:%02d", t.getMonthValue(), t.getDayOfMonth(),
                    dow.getDisplayName(TextStyle.SHORT, Locale.KOREAN), t.getHour(), t.getMinute());
        } catch (RuntimeException e) {
            return ddlnText;
        }
    }

    /** 원 단위 금액을 "12.4억"으로. 0 이하면 "-" */
    public static String eok(long won) {
        if (won <= 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f억", won / 100_000_000.0);
    }

    /** 공고 상태(PBSTS) → 배지 색 이름 (common.css 의 .pill) */
    static String statusPill(String statusCd) {
        if (statusCd == null) {
            return "neutral";
        }
        switch (statusCd.trim()) {
            case "PS010": return "info";
            case "PS020": return "warning";
            case "PS030":
            case "PS040": return "accent";
            case "PS050": return "success";
            case "PS090": return "danger";
            default: return "neutral";
        }
    }
}
