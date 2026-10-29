package com.ojj.sujuro.result.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 통계 화면(stats/index.html) 한 장에 필요한 값 (FR-083~088).
 * 고른 연도·팀 하나를 보여 준다. 연도별 차트와 과거 사업 이력은 연도와 상관없이 그 팀 전체다.
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
    private StatsChartDTO chart;
    private List<StatsRateDTO> ptcpShpRates = new ArrayList<>();
    private List<StatsArticleDTO> articles = new ArrayList<>();
    private List<StatsHistoryDTO> history = new ArrayList<>();

    public boolean isLocked() {
        return lockMessage != null;
    }

    /** 평가 항목 분석에 들어간 공고 수 */
    public int getArticleBidCnt() {
        return articles.stream().mapToInt(a -> a.getBidCnt()).max().orElse(0);
    }
}
