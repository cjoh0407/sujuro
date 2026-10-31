package com.ojj.sujuro.dashboard.dto;

import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.dashboard.dto.DashboardRows.Bid;
import com.ojj.sujuro.dashboard.dto.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MyToc;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TeamStat;
import com.ojj.sujuro.notification.dto.NotificationDto;
import lombok.Getter;
import lombok.Setter;

/** 대시보드 화면에 필요한 모든 값. 역할에 해당하는 항목만 채워진다. (FR-089, 090) */
@Getter
@Setter
public class DashboardView {

    /** 숫자 카드 한 칸 */
    @Getter
    public static class Stat {
        private final String label;
        private final int value;
        private final String hint;

        public Stat(String label, int value, String hint) {
            this.label = label;
            this.value = value;
            this.hint = hint;
        }

    }

    private String roleKey;     // SALES, LEADER, MEMBER, MGMT
    private String subtitle;
    private final List<Stat> stats = new ArrayList<>();
    private List<Bid> todoBids = new ArrayList<>();       // 영업부: 처리할 공고, 팀장: 참여 결정 대기
    private List<Bid> teamBids = new ArrayList<>();       // 팀장: 우리 팀 제안 사업
    private List<MyToc> myTocs = new ArrayList<>();       // 팀원: 내 담당 목차
    private List<DocRow> docRows = new ArrayList<>();     // 경영지원실: 제출서류 준비 현황
    private List<TeamStat> teamStats = new ArrayList<>(); // 팀별 현황
    private List<NotificationDto> alerts = new ArrayList<>();
}
