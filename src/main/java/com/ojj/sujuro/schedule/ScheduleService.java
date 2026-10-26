package com.ojj.sujuro.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.schedule.ScheduleView.Cell;
import com.ojj.sujuro.user.CodeOption;

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
public class ScheduleService {

    /** 팀장이 직접 만들 수 있는 일정 구분 (SC010 제출 마감, SC040 발표는 공고에서 자동으로 들어온다) */
    static final Set<String> INTERNAL_KINDS = new HashSet<>(Arrays.asList("SC020", "SC030", "SC050"));

    private static final int TITLE_MAX = 256;

    private final ScheduleMapper mapper;

    public ScheduleService(ScheduleMapper mapper) {
        this.mapper = mapper;
    }

    // ===== 조회 =====

    public ScheduleView month(LoginUser user, String ym, Long teamParam, Long editId) {
        YearMonth month = parseMonth(ym);
        boolean seesAll = user.getRole() == Role.SALES || user.getRole() == Role.MGMT;
        Long myTeamSn = seesAll ? null : mapper.findMyTeamSn(user.getEmpNo());

        ScheduleView v = new ScheduleView();
        v.setYm(month.toString());
        v.setLabel(month.getYear() + "년 " + month.getMonthValue() + "월");
        v.setPrevYm(month.minusMonths(1).toString());
        v.setNextYm(month.plusMonths(1).toString());
        v.setKinds(mapper.findKinds());

        // 볼 팀 범위: 영업부·경영지원실은 고른 팀(없으면 전체), 팀장·팀원은 자기 팀만
        Long scopeTeamSn;
        if (seesAll) {
            List<CodeOption> teams = mapper.findDevTeams();
            v.setTeams(teams);
            scopeTeamSn = validTeam(teams, teamParam);
            v.setScopeText(scopeTeamSn == null ? "전체 개발팀 일정" : teamName(teams, scopeTeamSn) + " 일정");
        } else {
            scopeTeamSn = myTeamSn;
            v.setScopeText(user.getTeam() + " 일정");
        }
        v.setSelectedTeamSn(scopeTeamSn);

        // 팀장·팀원인데 소속 팀이 없으면 아무것도 보여 주지 않는다. (null 을 그대로 쓰면 모든 팀이 보이므로)
        List<ScheduleEvent> events = new ArrayList<>();
        if (seesAll || scopeTeamSn != null) {
            String from = month.atDay(1).toString();
            String to = month.atEndOfMonth().toString();
            events.addAll(mapper.findAutoEvents(scopeTeamSn, from, to));
            events.addAll(mapper.findInternalEvents(scopeTeamSn, from, to));
        }
        buildCells(v, month, events);

        // 팀장이 자기 팀 내부 일정을 관리할 수 있는지 (FR-093)
        Long leaderTeamSn = user.getRole() == Role.LEADER ? (myTeamSn != null ? myTeamSn : mapper.findMyTeamSn(user.getEmpNo())) : null;
        boolean leader = leaderTeamSn != null;
        v.setCanEdit(leader);
        if (leader) {
            List<CodeOption> editable = new ArrayList<>();
            for (CodeOption k : v.getKinds()) {
                if (INTERNAL_KINDS.contains(k.getCode())) {
                    editable.add(k);
                }
            }
            v.setEditableKinds(editable);
            if (editId != null) {
                v.setEditing(mapper.findInternalOne(editId, leaderTeamSn));
            }
        }
        return v;
    }

    // ===== 내부 일정 추가·수정·삭제 (팀장, 자기 팀만) =====

    @Transactional
    public void add(LoginUser user, String kindCd, String title, String date) {
        Long teamSn = requireLeaderTeam(user);
        mapper.insert(teamSn, checkKind(kindCd), checkTitle(title), checkDate(date), user.getEmpNo());
    }

    @Transactional
    public void edit(LoginUser user, Long schdlSn, String kindCd, String title, String date) {
        Long teamSn = requireLeaderTeam(user);
        int n = mapper.update(schdlSn, teamSn, checkKind(kindCd), checkTitle(title), checkDate(date));
        if (n == 0) {
            throw new BusinessException("수정할 일정을 찾을 수 없습니다. 자기 팀의 내부 일정만 수정할 수 있습니다.");
        }
    }

    @Transactional
    public void delete(LoginUser user, Long schdlSn) {
        Long teamSn = requireLeaderTeam(user);
        if (mapper.delete(schdlSn, teamSn) == 0) {
            throw new BusinessException("삭제할 일정을 찾을 수 없습니다. 자기 팀의 내부 일정만 삭제할 수 있습니다.");
        }
    }

    // ===== 내부 =====

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

    static String checkKind(String kindCd) {
        if (kindCd == null || !INTERNAL_KINDS.contains(kindCd)) {
            throw new BusinessException("일정 구분을 골라 주세요. (내부 1차 마감, 내부 2차 마감, 서류 마감)");
        }
        return kindCd;
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

    private static YearMonth parseMonth(String ym) {
        try {
            return ym == null || ym.isEmpty() ? YearMonth.now() : YearMonth.parse(ym);
        } catch (DateTimeParseException e) {
            return YearMonth.now();
        }
    }

    /** 팀 탭에 있는 팀 번호만 인정한다. 그 밖의 값은 전체로 본다. */
    private static Long validTeam(List<CodeOption> teams, Long teamParam) {
        if (teamParam == null) {
            return null;
        }
        for (CodeOption t : teams) {
            if (t.getCode().equals(String.valueOf(teamParam))) {
                return teamParam;
            }
        }
        return null;
    }

    private static String teamName(List<CodeOption> teams, Long teamSn) {
        for (CodeOption t : teams) {
            if (t.getCode().equals(String.valueOf(teamSn))) {
                return t.getName();
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
