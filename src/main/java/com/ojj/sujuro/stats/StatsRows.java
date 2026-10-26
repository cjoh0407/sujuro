package com.ojj.sujuro.stats;

/** 통계 조회 결과 모양들. 화면(templates/stats/index.html)과 CSV 가 그대로 쓴다. */
public final class StatsRows {

    private StatsRows() {
    }

    /**
     * 집계 한 줄 (연도·팀·사업유형·참여형태 공통). label 이 줄 이름이다.
     * 수주율 = 수주 / (수주 + 탈락). 유찰 건은 분모에서 뺀다. (FR-083~085)
     */
    public static class Tally {
        private String label;
        private long joined;    // 참여: 제출 완료 이상인 공고
        private long won;       // 수주
        private long lost;      // 탈락 (유찰 제외)
        private long failed;    // 유찰 (점수·순위가 없는 결과)
        private long amount;    // 수주 금액
        private Double avgTech; // 평균 기술점수 (유찰 제외, 점수가 없으면 null)

        public String getLabel() { return label; }
        public void setLabel(String v) { this.label = v; }
        public long getJoined() { return joined; }
        public void setJoined(long v) { this.joined = v; }
        public long getWon() { return won; }
        public void setWon(long v) { this.won = v; }
        public long getLost() { return lost; }
        public void setLost(long v) { this.lost = v; }
        public long getFailed() { return failed; }
        public void setFailed(long v) { this.failed = v; }
        public long getAmount() { return amount; }
        public void setAmount(long v) { this.amount = v; }
        public Double getAvgTech() { return avgTech; }
        public void setAvgTech(Double v) { this.avgTech = v; }

        /** 수주율(%). 결과가 난 건(수주+탈락)이 없으면 null */
        public Double getRate() {
            long decided = won + lost;
            return decided == 0 ? null : Math.round(won * 1000.0 / decided) / 10.0;
        }

        public String getRateText() {
            Double r = getRate();
            return r == null ? "-" : r + "%";
        }

        public String getAvgTechText() {
            return avgTech == null ? "-" : String.format("%.1f", avgTech);
        }
    }

    /** 평가 항목별 우리 회사 평균 득점률 (FR-086) */
    public static class Artcl {
        private String artclNm;
        private Double rate;   // 평균 득점률(%)
        private long cnt;      // 집계에 쓴 공고 수

        public String getArtclNm() { return artclNm; }
        public void setArtclNm(String v) { this.artclNm = v; }
        public Double getRate() { return rate; }
        public void setRate(Double v) { this.rate = v; }
        public long getCnt() { return cnt; }
        public void setCnt(long v) { this.cnt = v; }
    }

    /** 과거 사업 이력 한 줄 (FR-087) */
    public static class History {
        private Long pbancSn;
        private String pbancNm;
        private String orderInstNm;
        private String teamNm;
        private Long budget;
        private String resultNm;     // 수주 / 탈락 / 유찰 / 결과 대기
        private String keptYn;       // 제안서 통합본 보관 여부 Y/N
        private String ddlnText;

        public Long getPbancSn() { return pbancSn; }
        public void setPbancSn(Long v) { this.pbancSn = v; }
        public String getPbancNm() { return pbancNm; }
        public void setPbancNm(String v) { this.pbancNm = v; }
        public String getOrderInstNm() { return orderInstNm; }
        public void setOrderInstNm(String v) { this.orderInstNm = v; }
        public String getTeamNm() { return teamNm; }
        public void setTeamNm(String v) { this.teamNm = v; }
        public Long getBudget() { return budget; }
        public void setBudget(Long v) { this.budget = v; }
        public String getResultNm() { return resultNm; }
        public void setResultNm(String v) { this.resultNm = v; }
        public String getKeptYn() { return keptYn; }
        public void setKeptYn(String v) { this.keptYn = v; }
        public String getDdlnText() { return ddlnText; }
        public void setDdlnText(String v) { this.ddlnText = v; }
    }
}
