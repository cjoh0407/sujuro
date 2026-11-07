package com.ojj.sujuro.dashboard.dto;

import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.dashboard.dto.DashboardRows.Bid;
import com.ojj.sujuro.dashboard.dto.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MemberProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MyToc;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TodoItem;
import com.ojj.sujuro.notification.dto.NotificationDto;
import com.ojj.sujuro.schedule.dto.ScheduleView;
import lombok.Getter;
import lombok.Setter;

/**
 * 대시보드 화면에 필요한 모든 값. 역할에 해당하는 항목만 채워진다. (FR-089, 090)
 *
 * 화면 배치(모든 역할 같은 뼈대): 배너 → 아이콘 카드 → 역할별 블록들 → 목록 칸 + 미니 캘린더, 오른쪽에 최근 알림.
 * 그래프는 라이브러리 없이 그린다. 좌표·원 크기는 여기(서버)에서 계산해 두고 화면은 그대로 그리기만 한다.
 */
@Getter
@Setter
public class DashboardView {

    /** 맨 위 아이콘 카드 한 칸 (이름 + 숫자만. 눌러서 이동하지 않는다). tone: accent, warning, danger, info, success */
    @Getter
    public static class Stat {
        private final String label;
        private final int value;
        private final String unit;   // 건, 개
        private final String icon;   // inbox, hour, alarm, doc, up, down, clip, brief, link, list, pen, ok, cal
        private final String tone;

        public Stat(String label, int value, String icon, String tone) {
            this(label, value, "건", icon, tone);
        }

        public Stat(String label, int value, String unit, String icon, String tone) {
            this.label = label;
            this.value = value;
            this.unit = unit;
            this.icon = icon;
            this.tone = tone;
        }

        /** 숫자 색: 위험 카드는 0이 아닐 때 빨강, 완료 카드는 초록, 그 밖은 파랑 */
        public String getNumTone() {
            return "danger".equals(tone) && value > 0 ? "danger" : ("success".equals(tone) ? "success" : "accent");
        }
    }

    /**
     * 영업부 '팀별 진행 현황' (11/07): 왼쪽 도넛(배정/미배정) + 오른쪽 팀별 막대.
     * 지금 작업 중인 공고만 센다: 미배정(배정 대기), 배정(팀장 결정 대기·참여 확정·제안 작성 중).
     * 제출이 끝났거나 결과가 나온 공고, 불참·철회로 끝난 공고는 넣지 않는다.
     */
    @Getter
    @Setter
    public static class TeamLoad {
        private int assigned;     // 팀에 배정돼 작업 중인 공고 (모든 팀 합)
        private int unassigned;   // 아직 팀에 배정하지 않은 공고
        private int assignedPct;  // 도넛에서 배정이 차지하는 비율 (0~100)
        private final List<TeamBar> teams = new ArrayList<>();

        public int getTotal() { return assigned + unassigned; }

        /** 팀도 없고 공고도 없으면 그래프 대신 안내 문구 */
        public boolean isBlank() { return teams.isEmpty() && getTotal() == 0; }
    }

    /** 팀별 막대 한 줄. pct 는 가장 많은 팀을 100으로 본 막대 길이 */
    @Getter
    public static class TeamBar {
        private final String name;
        private final int count;
        private final int pct;

        public TeamBar(String name, int count, int pct) {
            this.name = name;
            this.count = count;
            this.pct = pct;
        }
    }

    /** 연도별 성과 꺾은선의 한 해. 좌표는 서버(DashboardCharts)가 계산한다 */
    @Getter
    @Setter
    public static class LineYear {
        private String year;          // 2026
        private String x;
        private String yearY;
        private boolean current;      // 올해
        private boolean empty;        // 결과가 없는 해(점을 찍지 않는다)
        private boolean hasRate;
        private String rateY;
        private String rateText;      // 50%
        private String rateLabelY;
        private boolean hasAmt;
        private String amtY;
        private String amtText;       // 49.3억
        private String amtLabelY;
    }

    /** 연도별 성과 위쪽 요약 한 칸 */
    @Getter
    public static class Kpi {
        private final String label;
        private final String value;
        private final String unit;
        private final String chip;      // ▲ 7%p 같은 변화 표시. 없으면 null
        private final boolean chipUp;

        public Kpi(String label, String value, String unit, String chip, boolean chipUp) {
            this.label = label;
            this.value = value;
            this.unit = unit;
            this.chip = chip;
            this.chipUp = chipUp;
        }
    }

    /**
     * 그래프 카드 하나 (경영지원실 연도별 성과 버블).
     * 그릴 것이 없으면 emptyText 안내를 쓴다.
     */
    @Getter
    @Setter
    public static class Chart {
        private String kind = "line";
        private String title;
        private String aria;
        private String emptyText;   // 그릴 것이 없을 때 안내
        private final List<Kpi> kpis = new ArrayList<>();
        private final List<LineYear> points = new ArrayList<>();
        private String ratePoints;  // 수주율 점들을 잇는 선 (x,y x,y ...)
        private String amtPoints;   // 수주 금액 점들을 잇는 선

        public boolean isLine() {
            return "line".equals(kind);
        }

        public boolean isBlank() {
            return points.isEmpty();
        }
    }

    /** 다가오는 일정 한 줄 (미니 캘린더 아래) */
    @Getter
    public static class Upcoming {
        private final String date;   // 11/12
        private final String label;  // [마감] 10:00 사업명
        private final int teamNo;

        public Upcoming(String date, String label, int teamNo) {
            this.date = date;
            this.label = label;
            this.teamNo = teamNo;
        }
    }

    /** 미니 캘린더: 이번 달 칸(일정관리 화면과 같은 범위·팀 색)과 다가오는 일정 3건 */
    @Getter
    @Setter
    public static class MiniCal {
        private String title;   // 2026년 11월
        private String ym;      // 보는 달 2026-11
        private String prevYm;  // 화살표: 이전 달
        private String nextYm;  // 화살표: 다음 달
        private boolean current; // 보는 달이 이번 달이면 true ([오늘] 버튼을 숨긴다)
        private boolean countMode; // 영업부·경영지원실: 모든 팀 일정이 보여 팀 색 점 대신 날짜마다 건수 숫자를 쓴다
        private final List<ScheduleView.Cell> cells = new ArrayList<>();
        private final List<Upcoming> upcoming = new ArrayList<>();
    }

    private String roleKey;     // SALES, LEADER, MEMBER, MGMT
    private String todayText;   // 2026년 11월 3일 화요일
    private final List<Stat> stats = new ArrayList<>();
    private Chart mainChart;    // 경영지원실: 연도별 성과
    private MiniCal miniCal;

    // 목록은 마감이 가까운 순으로 불러오고, 칸 안에서 스크롤한다. 불러오는 건수는 쿼리가 막는다(최대 30건).
    private TeamLoad teamLoad = new TeamLoad();               // 영업부: 팀별 진행 현황 (도넛 + 팀별 막대)
    private List<Bid> todoBids = new ArrayList<>();           // 영업부: 처리할 공고
    private List<DocRow> docRows = new ArrayList<>();         // 경영지원실: 제출서류 준비 현황
    private List<LeaderProject> leaderProjects = new ArrayList<>(); // 팀장: 사업별 제안서 진행 현황
    private List<TodoItem> todos = new ArrayList<>();               // 팀장: 처리할 일
    private List<MemberProject> memberProjects = new ArrayList<>(); // 팀원: 사업별 제안서 진행 현황
    private List<MyToc> myTocs = new ArrayList<>();                 // 팀원: 내 할 일
    private List<NotificationDto> alerts = new ArrayList<>();       // 최근 알림
}
