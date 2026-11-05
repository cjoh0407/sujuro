package com.ojj.sujuro.result.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 통계 화면(stats/index.html) 한 장에 필요한 값 (FR-083~088).
 * 고른 연도·팀 하나를 보여 준다. 연도별 차트는 연도와 상관없이 그 팀 전체고, 왼쪽 목록의 사업은 고른 연도 것만이다 (11/04).
 * 11/03 평가 항목별 분석은 사업별 결과 분석으로만 옮겼다.
 */
@Getter
@Setter
public class StatsViewDTO {

    /** 화면 제목 아래 설명. 경영지원실과 팀장이 다르다 */
    private String scopeText;

    /** 볼 수 있는 개발팀이 없을 때의 안내. 값이 있으면 화면은 이 문구만 보여 준다 */
    private String lockMessage;

    private List<StatsTeamDTO> teams = new ArrayList<>();
    private Long teamSn;
    private String teamNm;

    /** 연도 탭 (오래된 순). 입찰 마감일 기준 */
    private List<String> years = new ArrayList<>();
    private String year;

    /** 고른 연도·팀의 요약 카드 */
    private StatsTallyDTO summary;
    /** 연도별 참여·수주 건수 차트 */
    private StatsChartDTO chart;
    /** 연도별 수주 금액 차트 (11/05) */
    private StatsChartDTO amountChart;
    private List<StatsRateDTO> ptcpShpRates = new ArrayList<>();
    /** 왼쪽 목록의 앞쪽, 진행 중 사업: 이 팀·고른 연도의 참여 확정·제안 작성 중·제출 완료 */
    private List<ResultBidDTO> inProgress = new ArrayList<>();
    /** 왼쪽 목록의 뒤쪽, 지난 사업: 이 팀·고른 연도의 결과 등록·유찰 */
    private List<StatsHistoryDTO> history = new ArrayList<>();

    public boolean isLocked() {
        return lockMessage != null;
    }
}
