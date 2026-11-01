package com.ojj.sujuro.schedule.service;

import com.ojj.sujuro.schedule.dto.ScheduleEvent;
import com.ojj.sujuro.schedule.dto.ScheduleView;
import com.ojj.sujuro.schedule.mapper.ScheduleMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.schedule.dto.ScheduleView.Cell;
import com.ojj.sujuro.user.dto.CodeOption;
import lombok.RequiredArgsConstructor;

/**
 * 일정 (FR-091 ~ FR-094)
 *
 * - 팀마다 다른 일정을 월 캘린더로 보여 준다. 영업부·경영지원실은 팀 탭(전체, 팀별)을 고르고, 팀장·팀원은 자기 팀만 본다. (FR-091)
 * - 공고의 제출 마감·발표일·서류 마감은 담당 팀 일정에 자동으로 나타난다. (FR-092)
 * - 내부 일정(내부 1차·2차 마감, 서류 마감)은 팀장이 자기 팀 것만 추가·수정·삭제한다. (FR-093)
 * - 일정 구분은 색으로 구분한다. (FR-094)
 *
 * 보는 팀과 고칠 수 있는 팀은 화면에서 받은 값이 아니라 로그인한 사용자의 역할과 소속 팀으로 정한다. (NFR-001)
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** 팀장이 직접 등록하는 일정은 모두 "내부 일정"(SC020)이다. 구분은 고르지 않고 이름·날짜·시간을 직접 쓴다. */
    static final String KIND_INTERNAL = "SC020";

    private static final int TITLE_MAX = 256;

    private final ScheduleMapper mapper;
    private final NotificationService notificationService;

    // ===== 조회 =====

    public ScheduleView month(LoginUser user, String ym, Long teamParam, Long pbancParam, Long editId) {
        YearMonth month = parseMonth(ym);
        boolean seesAll = user.getRole() == Role.SALES || user.getRole() == Role.MGMT;

        ScheduleView v = new ScheduleView();
        v.setYm(month.toString());
        v.setLabel(month.getYear() + "년 " + month.getMonthValue() + "월");
        v.setPrevYm(month.minusMonths(1).toString());
        v.setNextYm(month.plusMonths(1).toString());
        v.setKinds(mapper.findKinds());

        // 볼 범위: 영업부·경영지원실은 모든 사업(고른 팀 탭), 팀장·팀원은 내가 제안팀원인 사업건만
        Long scopeTeamSn = null;
        String memberId = null;
        Long pbancSn = null;
        if (seesAll) {
            List<CodeOption> teams = mapper.findDevTeams();
            v.setTeams(teams);
            scopeTeamSn = validOption(teams, teamParam);
            v.setScopeText(scopeTeamSn == null ? "전체 팀의 사업 일정" : optionName(teams, scopeTeamSn) + "의 사업 일정");
        } else {
            memberId = user.getEmpNo();
            List<CodeOption> mine = mapper.findMyPbancs(memberId);
            v.setProjects(mine);
            pbancSn = validOption(mine, pbancParam);
            v.setScopeText("내가 참여하는 사업 일정");
        }
        v.setSelectedTeamSn(scopeTeamSn);
        v.setSelectedPbancSn(pbancSn);

        // 팀장이 자기 팀 사업건에 일정을 관리할 수 있는지 (FR-093)
        Long leaderTeamSn = user.getRole() == Role.LEADER ? mapper.findMyTeamSn(user.getEmpNo()) : null;
        boolean leader = leaderTeamSn != null;

        // 팀장·팀원인데 참여하는 사업건이 없으면 아무것도 보여 주지 않는다. (null 을 그대로 쓰면 모든 사업건이 보이므로)
        List<ScheduleEvent> events = new ArrayList<>();
        if (seesAll || !v.getProjects().isEmpty()) {
            String from = month.atDay(1).toString();
            String to = month.atEndOfMonth().toString();
            events.addAll(mapper.findAutoEvents(scopeTeamSn, memberId, pbancSn, from, to));
            events.addAll(mapper.findInternalEvents(scopeTeamSn, memberId, pbancSn, from, to));
        }
        for (ScheduleEvent e : events) {
            // 참여팀 팀장은 주관팀이 만든 일정을 볼 수만 있다. 자기 팀이 만든 내부 일정만 고칠 수 있다.
            e.setEditable(leader && !e.isAuto() && leaderTeamSn.equals(e.getTeamSn()));
        }
        buildCells(v, month, events);

        v.setCanEdit(leader);
        if (leader) {
            v.setEditProjects(mapper.findEditablePbancs(leaderTeamSn));
            if (editId != null) {
                v.setEditing(mapper.findInternalOne(editId, leaderTeamSn));
            }
        }
        return v;
    }

    // ===== 내부 일정 추가·수정·삭제 (주관팀 팀장, 자기 팀이 주관하는 사업건만) =====

    @Transactional
    public void add(LoginUser user, Long pbancSn, String title, String date, String time) {
        Long teamSn = requireLeaderTeam(user);
        String pbancNm = requireEditablePbanc(teamSn, pbancSn);
        String checkedTitle = checkTitle(title);
        String dateTime = checkDate(date) + " " + checkTime(time);
        mapper.insert(teamSn, pbancSn, KIND_INTERNAL, checkedTitle, dateTime, user.getEmpNo());
        notifyProposalTeam(pbancSn, pbancNm, "일정이 등록되었습니다", checkedTitle, dateTime);
    }

    @Transactional
    public void edit(LoginUser user, Long schdlSn, Long pbancSn, String title, String date, String time) {
        Long teamSn = requireLeaderTeam(user);
        String pbancNm = requireEditablePbanc(teamSn, pbancSn);
        String checkedTitle = checkTitle(title);
        String dateTime = checkDate(date) + " " + checkTime(time);
        int n = mapper.update(schdlSn, teamSn, pbancSn, checkedTitle, dateTime);
        if (n == 0) {
            throw new BusinessException("수정할 일정을 찾을 수 없습니다. 자기 팀의 내부 일정만 수정할 수 있습니다.");
        }
        notifyProposalTeam(pbancSn, pbancNm, "일정이 변경되었습니다", checkedTitle, dateTime);
    }

    @Transactional
    public void delete(LoginUser user, Long schdlSn) {
        Long teamSn = requireLeaderTeam(user);
        ScheduleEvent before = mapper.findInternalOne(schdlSn, teamSn); // 알림에 쓸 이름·날짜. 지우기 전에 읽는다
        if (mapper.delete(schdlSn, teamSn) == 0) {
            throw new BusinessException("삭제할 일정을 찾을 수 없습니다. 자기 팀의 내부 일정만 삭제할 수 있습니다.");
        }
        if (before != null && before.getPbancSn() != null) {
            String when = before.getEventDate() + (before.getTimeText() == null ? "" : " " + before.getTimeText());
            notifyProposalTeam(before.getPbancSn(), before.getPbancNm(), "일정이 삭제되었습니다", before.getTitle(), when);
        }
    }

    // ===== 내부 =====

    /** 그 사업건의 제안팀원 전원(등록한 본인 포함, 주관팀 팀장도 포함)에게 일정 등록·변경·삭제를 알린다. (FR-034, NT160) */
    private void notifyProposalTeam(Long pbancSn, String pbancNm, String what, String title, String when) {
        String name = pbancNm == null ? "" : "[" + pbancNm + "] ";
        notificationService.sendToProposalTeam(pbancSn, NotificationType.SCHEDULE_CHANGED,
                name + "'" + title + "' " + what + ". (" + when + ")");
    }

    /** 우리 팀이 주관하는 진행 중 사업건이 아니면 안내. 사업건 이름을 돌려준다. */
    private String requireEditablePbanc(Long teamSn, Long pbancSn) {
        if (pbancSn == null) {
            throw new BusinessException("일정을 등록할 사업을 골라 주세요.");
        }
        for (CodeOption o : mapper.findEditablePbancs(teamSn)) {
            if (o.getCode().equals(String.valueOf(pbancSn))) {
                return o.getName();
            }
        }
        throw new BusinessException("일정을 등록할 수 없는 사업입니다. 우리 팀이 주관하는 진행 중인 사업만 고를 수 있습니다.");
    }

    /** 팀장이 아니면 403, 소속 팀이 없으면 업무 안내 */
    private Long requireLeaderTeam(LoginUser user) {
        if (user.getRole() != Role.LEADER) {
            throw new AccessDeniedException("내부 일정은 팀장만 관리할 수 있습니다.");
        }
        Long teamSn = mapper.findMyTeamSn(user.getEmpNo());
        if (teamSn == null) {
            throw new BusinessException("소속 팀이 없어 일정을 관리할 수 없습니다.");
        }
        return teamSn;
    }

    static String checkTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new BusinessException("일정 이름을 입력해 주세요.");
        }
        if (t.length() > TITLE_MAX) {
            throw new BusinessException("일정 이름은 " + TITLE_MAX + "자까지 입력할 수 있습니다.");
        }
        return t;
    }

    static String checkDate(String date) {
        try {
            LocalDate d = LocalDate.parse(date == null ? "" : date.trim());
            if (d.getYear() < 2000 || d.getYear() > 2100) {
                throw new BusinessException("날짜를 2000년~2100년 사이로 입력해 주세요.");
            }
            return d.toString();
        } catch (DateTimeParseException e) {
            throw new BusinessException("날짜를 올바르게 입력해 주세요. (예: 2026-10-29)");
        }
    }

    /** 시간은 비워 둘 수 있다(하루 종일). 입력하면 HH:mm 으로 맞춘다. */
    static String checkTime(String time) {
        String t = time == null ? "" : time.trim();
        if (t.isEmpty()) {
            return "00:00";
        }
        try {
            return java.time.LocalTime.parse(t).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new BusinessException("시간을 올바르게 입력해 주세요. (예: 14:00)");
        }
    }

    private static YearMonth parseMonth(String ym) {
        try {
            return ym == null || ym.isEmpty() ? YearMonth.now() : YearMonth.parse(ym);
        } catch (DateTimeParseException e) {
            return YearMonth.now();
        }
    }

    /** 목록에 있는 번호만 인정한다(팀 탭, 사업별 보기). 그 밖의 값은 전체로 본다. */
    private static Long validOption(List<CodeOption> options, Long param) {
        if (param == null) {
            return null;
        }
        for (CodeOption o : options) {
            if (o.getCode().equals(String.valueOf(param))) {
                return param;
            }
        }
        return null;
    }

    private static String optionName(List<CodeOption> options, Long sn) {
        for (CodeOption o : options) {
            if (o.getCode().equals(String.valueOf(sn))) {
                return o.getName();
            }
        }
        return "";
    }

    /** 일요일부터 시작하는 월 캘린더 칸을 만든다. 앞쪽은 빈 칸. */
    private static void buildCells(ScheduleView v, YearMonth month, List<ScheduleEvent> events) {
        Map<String, List<ScheduleEvent>> byDate = new LinkedHashMap<>();
        events.sort(Comparator.comparing(ScheduleEvent::getEventDate)
                .thenComparing(e -> e.getTimeText() == null ? "" : e.getTimeText())
                .thenComparing(e -> e.getTitle() == null ? "" : e.getTitle()));
        for (ScheduleEvent e : events) {
            byDate.computeIfAbsent(e.getEventDate(), k -> new ArrayList<>()).add(e);
        }
        int lead = month.atDay(1).getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : month.atDay(1).getDayOfWeek().getValue();
        for (int i = 0; i < lead; i++) {
            v.getCells().add(new Cell(0, false, new ArrayList<ScheduleEvent>()));
        }
        LocalDate today = LocalDate.now();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            List<ScheduleEvent> list = byDate.get(date.toString());
            v.getCells().add(new Cell(d, date.equals(today), list == null ? new ArrayList<ScheduleEvent>() : list));
        }
    }
}
