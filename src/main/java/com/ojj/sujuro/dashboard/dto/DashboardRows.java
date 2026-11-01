package com.ojj.sujuro.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

/** 대시보드 조회 결과 모양들. 화면(templates/dashboard/index.html)이 그대로 쓴다. */
public final class DashboardRows {

    private DashboardRows() {
    }

    /** 공고(사업건) 한 줄 */
    @Getter
    @Setter
    public static class Bid {
        private Long pbancSn;
        private String pbancNm;
        private String statusCd;
        private String statusNm;
        private String returnNm;   // 불참·중도 철회로 영업부에 돌아온 건의 구분 (없으면 null)
        private String teamNm;     // 주관팀
        private String ddlnText;   // 입찰 마감 (2026-10-29 17:00)
        private Integer dday;      // 마감까지 남은 일수. 마감일이 없으면 null

        public String getDdayLabel() { return DashboardRows.ddayLabel(dday); }
        public String getStatusPill() { return DashboardRows.statusPill(statusCd); }
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
        private String tocStatusNm;
        private Integer fileCnt;
        private String ddlnText;
        private Integer dday;

        public String getDdayLabel() { return DashboardRows.ddayLabel(dday); }
    }

    /** 제출서류 준비 현황 한 줄 (경영지원실) */
    @Getter
    @Setter
    public static class DocRow {
        private Long pbancSn;
        private String pbancNm;
        private String teamNm;
        private int docTotal;
        private int docReady;
        private int missingRequired;
        private boolean registered; // 나라장터 등록(제출) 완료

        /** 서류가 있고 필수 서류가 모두 준비됐으며 아직 등록 전이면 등록 대기 */
        public boolean isWaitingRegistration() {
            return !registered && docTotal > 0 && missingRequired == 0;
        }

        public String getStateLabel() {
            return registered ? "등록 완료" : (isWaitingRegistration() ? "등록 대기" : "작성 중");
        }

        public String getStatePill() {
            return registered ? "success" : (isWaitingRegistration() ? "warning" : "neutral");
        }
    }

    /** 팀별 현황 한 줄 */
    @Getter
    @Setter
    public static class TeamStat {
        private String teamNm;
        private int assigned;    // 배정 완료 (팀장 결정 대기)
        private int inProgress;  // 참여 확정·제안 작성 중
        private int submitted;   // 제출 완료
        private int resulted;    // 결과 등록
    }

    /** 영업부 숫자 카드 */
    @Getter
    @Setter
    public static class SalesCounts {
        private int assignWait;    // 배정 대기 (신규 입찰공고)
        private int awaitingTeam;  // 팀장 응답 대기 (배정 완료)
        private int returned;      // 반환 건
        private int tracking;      // 입찰공고 대기 (발주계획·사전규격 추적 중)
    }

    /** 팀장 숫자 카드 */
    @Getter
    @Setter
    public static class LeaderCounts {
        private int decisionWait; // 참여 결정 대기
        private int inProgress;   // 진행 중 사업
        private int unmapped;     // 목차에 연결되지 않은 요구사항
        private int dueSoon;      // 마감 7일 이내
    }

    public static String ddayLabel(Integer dday) {
        if (dday == null) {
            return "-";
        }
        if (dday < 0) {
            return "마감 지남";
        }
        return dday == 0 ? "오늘 마감" : "D-" + dday;
    }

    /** 공고 상태(PBSTS) → 배지 색 이름 (common.css 의 .pill) */
    static String statusPill(String statusCd) {
        if (statusCd == null) {
            return "neutral";
        }
        switch (statusCd) {
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
