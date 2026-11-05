package com.ojj.sujuro.result.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 사업 이력 관리 화면(stats/index.html) 왼쪽 블록에 필요한 값. 고른 연도·팀 하나의 사업 목록을 보여 준다.
 * 11/05 통계(요약 카드·차트·수주율·경쟁 업체)를 지웠다.
 */
@Getter
@Setter
public class StatsViewDTO {

    /** 볼 수 있는 개발팀이 없을 때의 안내. 값이 있으면 화면은 이 문구만 보여 준다 */
    private String lockMessage;

    private List<StatsTeamDTO> teams = new ArrayList<>();
    private Long teamSn;
    private String teamNm;

    /** 연도 드롭다운 (오래된 순). 입찰 마감일 기준 */
    private List<String> years = new ArrayList<>();
    private String year;

    /** 목록의 앞쪽, 진행 중 사업: 이 팀·고른 연도의 참여 확정·제안 작성 중·제출 완료 */
    private List<ResultBidDTO> inProgress = new ArrayList<>();
    /** 목록의 뒤쪽, 지난 사업: 이 팀·고른 연도의 결과 등록·유찰 */
    private List<StatsHistoryDTO> history = new ArrayList<>();

    public boolean isLocked() {
        return lockMessage != null;
    }

    /** 목록 맨 위 사업. 고른 사업이 없을 때 오른쪽에 보여 준다. 목록이 비어 있으면 null */
    public Long getFirstPbancSn() {
        if (!inProgress.isEmpty()) {
            return inProgress.get(0).getPbancSn();
        }
        return history.isEmpty() ? null : history.get(0).getPbancSn();
    }
}
