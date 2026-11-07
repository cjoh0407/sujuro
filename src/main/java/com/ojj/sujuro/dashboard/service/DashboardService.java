package com.ojj.sujuro.dashboard.service;

import com.ojj.sujuro.dashboard.dto.DashboardView;
import com.ojj.sujuro.dashboard.mapper.DashboardMapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.dashboard.dto.DashboardRows.Bid;
import com.ojj.sujuro.dashboard.dto.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderCounts;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderProject;
import com.ojj.sujuro.dashboard.dto.DashboardRows.MyToc;
import com.ojj.sujuro.dashboard.dto.DashboardRows.ResultWait;
import com.ojj.sujuro.dashboard.dto.DashboardRows.SalesCounts;
import com.ojj.sujuro.dashboard.dto.DashboardRows.TodoItem;
import com.ojj.sujuro.dashboard.dto.DashboardRows.YearStat;
import com.ojj.sujuro.dashboard.dto.DashboardView.Stat;
import com.ojj.sujuro.notification.dto.NotificationDto;
import com.ojj.sujuro.notification.mapper.NotificationInboxMapper;
import com.ojj.sujuro.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;

/**
 * 대시보드 구성 (FR-089, 090)
 *
 * 모든 역할이 같은 뼈대: 배너(날짜·한 줄 요약) → 아이콘 카드 → 역할별 블록 → 목록 칸 + 미니 캘린더, 오른쪽 최근 알림.
 *   영업부     카드 3: 배정 대기 · 팀장 응답 대기 · 마감 3일 이내 미배정
 *              블록: 팀별 진행 현황(레이더) · 곧 마감되는 공고 3건 · 처리할 공고
 *   경영지원실 카드 4: 필수 서류 누락 · 나라장터 등록 대기 · 결과 가져오기 대기 · 제출 마감 임박
 *              블록: 연도별 성과(꺾은선) · 제출서류 준비 현황 (11/07 올해 수주한 사업·주의가 필요한 항목 블록 제거)
 *   팀장       카드 4: 참여 결정 대기 · 진행 중 사업 · 미매핑 요구사항 · 마감 7일 이내
 *              블록: 사업별 제안서 진행 현황(작은 원 3개) · 처리할 일
 *   팀원       카드 4: 내 담당 목차 · 작성 중 · 검토 완료 · 마감 7일 이내
 *              블록: 사업별 제안서 진행 현황(작은 원 3개) · 내 할 일
 * 팀장·팀원의 숫자·목록은 본인이 제안팀원인 사업 기준이다(눌러서 열 수 있는 사업만).
 * 목록은 칸 안에서 스크롤한다. 쿼리가 최대 30건에서 끊어 주므로 칸이 길게 늘어나지 않는다.
 * 미니 캘린더는 일정관리 화면과 같은 범위·팀 색을 쓴다 (ScheduleService.month). 영업부·경영지원실은 점 대신 건수.
 * 보는 범위는 화면에서 받은 값이 아니라 로그인한 사용자(역할·소속 팀)로만 정한다. (NFR-001)
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int ALERT_LIMIT = 8;
    /** '제출 마감 임박' 기준 일수 (경영지원실) */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN);

    private final DashboardMapper mapper;
    private final NotificationInboxMapper inboxMapper;
    private final ScheduleService scheduleService;

    public DashboardView build(LoginUser user) {
        return build(user, null);
    }

    /**
     * calYm: 미니 캘린더로 볼 달(2026-12). 없거나 형식이 틀리면 이번 달. (11/07 캘린더 화살표)
     */
    public DashboardView build(LoginUser user, String calYm) {
        DashboardView v = new DashboardView();
        LocalDate today = LocalDate.now(SEOUL);
        v.setTodayText(today.format(TODAY));
        switch (user.getRole()) {
            case SALES:
                buildSales(v);
                break;
            case LEADER:
                buildLeader(v, user);
                break;
            case MGMT:
                buildMgmt(v);
                break;
            default:
                buildMember(v, user);
                break;
        }
        boolean allTeams = "SALES".equals(v.getRoleKey()) || "MGMT".equals(v.getRoleKey());
        YearMonth calMonth = parseYm(calYm, YearMonth.from(today));
        v.setMiniCal(DashboardCharts.miniCal(scheduleService.month(user, calMonth.toString(), null, null, null),
                scheduleService.month(user, calMonth.plusMonths(1).toString(), null, null, null), today, allTeams));
        v.setAlerts(recentAlerts(user.getEmpNo()));
        return v;
    }

    private void buildSales(DashboardView v) {
        v.setRoleKey("SALES");
        SalesCounts c = mapper.salesCounts();
        v.getStats().add(new Stat("배정 대기", c.getAssignWait(), "inbox", "accent"));
        v.getStats().add(new Stat("팀장 응답 대기", c.getAwaitingTeam(), "hour", "warning"));
        v.getStats().add(new Stat("마감 3일 이내 미배정", c.getDueSoon(), "alarm", "danger"));

        v.setTodoBids(mapper.salesTodo());
        v.setTeamLoad(DashboardCharts.teamLoad(mapper.teamStats(null, false), c.getAssignWait()));
    }

    private void buildMgmt(DashboardView v) {
        v.setRoleKey("MGMT");
        List<DocRow> rows = mapper.docRows();
        int waitReg = 0;
        int missing = 0;
        int missingBids = 0;
        int dueSoon = 0;
        for (DocRow r : rows) {
            if (r.isWaitingRegistration()) {
                waitReg++;
            }
            missing += r.getMissingRequired();
            if (r.getMissingRequired() > 0) {
                missingBids++;
            }
            if (r.isDueSoonUnready()) {
                dueSoon++;
            }
        }
        ResultWait rw = mapper.resultWait();
        int resultWait = rw == null ? 0 : rw.getCnt();
        v.getStats().add(new Stat("필수 서류 누락", missing, "doc", "danger"));
        v.getStats().add(new Stat("나라장터 등록 대기", waitReg, "up", "accent"));
        v.getStats().add(new Stat("결과 가져오기 대기", resultWait, "down", "warning"));
        v.getStats().add(new Stat("제출 마감 임박", dueSoon, "cal", "danger"));

        List<YearStat> years = mapper.yearStats();
        v.setMainChart(DashboardCharts.yearLine(years));
        v.setDocRows(rows);
    }

    private void buildLeader(DashboardView v, LoginUser user) {
        v.setRoleKey("LEADER");
        LeaderCounts c = mapper.leaderCounts(user.getEmpNo());
        List<LeaderProject> projects = mapper.leaderProjects(user.getEmpNo());
        List<TodoItem> todos = mapper.leaderTodos(user.getEmpNo());
        v.getStats().add(new Stat("참여 결정 대기", c.getDecisionWait(), "clip", "accent"));
        v.getStats().add(new Stat("진행 중 사업", c.getInProgress(), "brief", "info"));
        v.getStats().add(new Stat("미매핑 요구사항", c.getUnmapped(), "link", "warning"));
        v.getStats().add(new Stat("마감 7일 이내", c.getDueSoon(), "cal", "danger"));
        v.setLeaderProjects(projects);
        v.setTodos(todos);
    }

    private void buildMember(DashboardView v, LoginUser user) {
        v.setRoleKey("MEMBER");
        List<MyToc> tocs = mapper.myTocs(user.getEmpNo());
        int doing = 0;
        int done = 0;
        int dueSoon = 0;
        for (MyToc t : tocs) {
            String cd = t.getTocStatusCd() == null ? "" : t.getTocStatusCd().trim();
            if ("TS020".equals(cd)) {
                doing++;
            } else if ("TS030".equals(cd)) {
                done++;
            }
            if (t.getDday() != null && t.getDday() >= 0 && t.getDday() <= 7) {
                dueSoon++;
            }
        }
        v.getStats().add(new Stat("내 담당 목차", tocs.size(), "개", "list", "accent"));
        v.getStats().add(new Stat("작성 중", doing, "개", "pen", "info"));
        v.getStats().add(new Stat("검토 완료", done, "개", "ok", "success"));
        v.getStats().add(new Stat("마감 7일 이내", dueSoon, "건", "cal", "danger"));
        v.setMyTocs(tocs);
        v.setMemberProjects(mapper.memberProjects(user.getEmpNo()));
    }

    private List<NotificationDto> recentAlerts(String userId) {
        return inboxMapper.findPage(userId, 0, ALERT_LIMIT);
    }


    /** "2026-12" → YearMonth. 비었거나 틀리면 기본값 */
    private static YearMonth parseYm(String ym, YearMonth fallback) {
        if (ym == null || ym.trim().isEmpty()) {
            return fallback;
        }
        try {
            return YearMonth.parse(ym.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return fallback;
        }
    }
}
