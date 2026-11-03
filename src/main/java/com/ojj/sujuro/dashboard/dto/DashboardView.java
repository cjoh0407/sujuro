package com.ojj.sujuro.dashboard.dto;

import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.dashboard.dto.DashboardRows.Bid;
import com.ojj.sujuro.dashboard.dto.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MemberProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MgmtAlert;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MyToc;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TodoItem;
import com.ojj.sujuro.dashboard.dto.DashboardRows.Win;
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

    /** 레이더 차트의 한 꼭짓점 (팀 하나) */
    @Getter
    public static class RadarPoint {
        private final String cx;        // 값 점 위치
        private final String cy;
        private final String name;
        private final String valueText; // 4건
        private final String lx;        // 글자 기준 x
        private final String nameY;
        private final String valueY;
        private final String anchor;    // start, middle, end
        private final boolean top;      // 가장 많은 팀(점을 크게)

        public RadarPoint(String cx, String cy, String name, String valueText, String lx, String nameY, String valueY,
                String anchor, boolean top) {
            this.cx = cx;
            this.cy = cy;
            this.name = name;
            this.valueText = valueText;
            this.lx = lx;
            this.nameY = nameY;
            this.valueY = valueY;
            this.anchor = anchor;
            this.top = top;
        }
    }

    /** 영업부 '팀별 진행 현황' 레이더. 좌표는 서버(DashboardCharts)가 계산한다 */
    @Getter
    public static class Radar {
        private final List<String> rings = new ArrayList<>();       // 겹친 다각형 꼭짓점들 (안쪽부터)
        private final List<String[]> spokes = new ArrayList<>();    // 중심에서 꼭짓점으로 가는 선 {x2, y2}
        private final List<GridLine> ringLabels = new ArrayList<>(); // 눈금 숫자 (y 위치, 글자)
        private String shape;                                       // 값 다각형
        private final List<RadarPoint> points = new ArrayList<>();
        private String aria;

        public void setShape(String shape) { this.shape = shape; }

        public void setAria(String aria) { this.aria = aria; }

        public boolean isBlank() { return points.isEmpty(); }
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

    /** 눈금 한 줄(레이더 눈금 숫자) */
    @Getter
    public static class GridLine {
        private final String y;
        private final String label;

        public GridLine(String y, String label) {
            this.y = y;
            this.label = label;
        }
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
        private String title;   // 11월 일정
        private boolean countMode; // 영업부·경영지원실: 모든 팀 일정이 보여 팀 색 점 대신 날짜마다 건수 숫자를 쓴다
        private final List<ScheduleView.Cell> cells = new ArrayList<>();
        private final List<Upcoming> upcoming = new ArrayList<>();
    }

    private String roleKey;     // SALES, LEADER, MEMBER, MGMT
    private String todayText;   // 2026년 11월 3일 화요일
    private final List<Stat> stats = new ArrayList<>();
    private Chart mainChart;    // 영업부: 팀별 진행 현황 레이더, 경영지원실: 연도별 성과 버블
    private MiniCal miniCal;

    // 목록은 마감이 가까운 순으로 불러오고, 칸 안에서 스크롤한다. 불러오는 건수는 쿼리가 막는다(최대 30건).
    private Radar radar = new Radar();                        // 영업부: 팀별 진행 현황 레이더
    private List<Bid> dueBids = new ArrayList<>();            // 영업부: 곧 마감되는 공고 3건
    private List<Bid> todoBids = new ArrayList<>();           // 영업부: 처리할 공고
    private List<MgmtAlert> attention = new ArrayList<>();    // 경영지원실: 주의가 필요한 항목
    private List<Win> wins = new ArrayList<>();               // 경영지원실: 올해 수주한 사업
    private String winTotal;                                  // 합계 38.2억
    private List<DocRow> docRows = new ArrayList<>();         // 경영지원실: 제출서류 준비 현황
    private List<LeaderProject> leaderProjects = new ArrayList<>(); // 팀장: 사업별 제안서 진행 현황
    private List<TodoItem> todos = new ArrayList<>();               // 팀장: 처리할 일
    private List<MemberProject> memberProjects = new ArrayList<>(); // 팀원: 사업별 제안서 진행 현황
    private List<MyToc> myTocs = new ArrayList<>();                 // 팀원: 내 할 일
    private List<NotificationDto> alerts = new ArrayList<>();       // 최근 알림
}
