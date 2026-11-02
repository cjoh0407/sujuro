package com.ojj.sujuro.schedule.service;

import com.ojj.sujuro.schedule.dto.ScheduleEvent;
import com.ojj.sujuro.schedule.dto.ScheduleView;
import com.ojj.sujuro.schedule.mapper.ScheduleMapper;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * - 일정은 사업건 단위다. 영업부·경영지원실은 모든 사업을 팀 탭(전체, 팀별)으로, 팀장·팀원은 제안팀원인 사업만 본다. (FR-091)
 * - 참여가 확정된 사업건의 제출 마감이 자동으로 나타난다. 발표일·서류 마감은 넣지 않는다. (FR-092)
 * - 주관팀 팀장만 우리 팀이 주관하는 진행 중 사업에 일정을 등록·수정·삭제한다. 참여팀 팀장·팀원은 보기만 한다. (FR-093)
 *   오늘 이전 날짜(오늘이면 지난 시각)는 등록·수정할 수 없다. 지난 일정과 끝난 사업의 일정은 삭제만 된다.
 *   수정할 때 붙은 사업건은 바꾸지 않는다.
 * - 일정은 팀 색으로 구분하고, 제출 마감(자동)과 직접 등록한 일정은 글자로 구분한다. (FR-094)
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

    /** 지금 시각. 서버 시간대와 상관없이 한국 시간으로 본다. 테스트에서는 고정 시각으로 바꾼다 */
    private Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    void setClock(Clock clock) {
        this.clock = clock;
    }

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
        v.setToday(LocalDate.now(clock).toString());

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
        List<CodeOption> editable = leader ? mapper.findEditablePbancs(leaderTeamSn) : new ArrayList<CodeOption>();
        for (ScheduleEvent e : events) {
            // 주관팀 팀장이 자기 팀이 만든 일정만 눌러서 관리한다. 참여팀 팀장에게는 고칠 일정이 없다(보기만).
            e.setEditable(leader && !e.isAuto() && leaderTeamSn.equals(e.getTeamSn()));
            if (e.isEditable()) {
                markChangeable(e, editable);
            }
        }
        buildCells(v, month, events);

        v.setCanEdit(leader);
        if (leader) {
            v.setEditProjects(editable);
            if (editId != null) {
                ScheduleEvent one = mapper.findInternalOne(editId, leaderTeamSn);
                if (one != null) {
                    one.setEditable(true);
                    markChangeable(one, editable);
                }
                v.setEditing(one);
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
        String dateTime = checkNotPast(checkDate(date), time);
        mapper.insert(teamSn, pbancSn, KIND_INTERNAL, checkedTitle, dateTime, user.getEmpNo());
        notifyProposalTeam(pbancSn, pbancNm, "일정이 등록되었습니다", checkedTitle, dateTime);
    }

    /** 이름·날짜·시간만 고친다. 붙은 사업건은 그대로 둔다(화면에서 다른 값을 보내도 무시). */
    @Transactional
    public void edit(LoginUser user, Long schdlSn, String title, String date, String time) {
        Long teamSn = requireLeaderTeam(user);
        ScheduleEvent before = mapper.findInternalOne(schdlSn, teamSn);
        if (before == null) {
            throw new BusinessException("수정할 일정을 찾을 수 없습니다. 자기 팀의 내부 일정만 수정할 수 있습니다.");
        }
        String reason = lockReason(before, mapper.findEditablePbancs(teamSn));
        if (reason != null) {
            throw new BusinessException(reason);
        }
        String checkedTitle = checkTitle(title);
        String dateTime = checkNotPast(checkDate(date), time);
        int n = mapper.update(schdlSn, teamSn, checkedTitle, dateTime);
        if (n == 0) {
            throw new BusinessException("수정할 일정을 찾을 수 없습니다. 자기 팀의 내부 일정만 수정할 수 있습니다.");
        }
        notifyProposalTeam(before.getPbancSn(), before.getPbancNm(), "일정이 변경되었습니다", checkedTitle, dateTime);
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

    /** 눌러서 관리할 수 있는 일정에, 내용까지 고칠 수 있는지와 그 이유를 적는다 */
    private void markChangeable(ScheduleEvent e, List<CodeOption> editable) {
        String reason = lockReason(e, editable);
        e.setChangeable(reason == null);
        e.setLockReason(reason);
    }

    /** 고칠 수 없는 이유. 고칠 수 있으면 null. 지난 일정과 끝난 사업의 일정은 삭제만 된다 */
    private String lockReason(ScheduleEvent e, List<CodeOption> editable) {
        if (isPast(e)) {
            return "지난 일정은 수정할 수 없습니다. 잘못 넣은 일정이면 삭제할 수 있습니다.";
        }
        if (e.getPbancSn() == null || !contains(editable, e.getPbancSn())) {
            return "진행 중이 아닌 사업의 일정은 수정할 수 없습니다. 삭제만 할 수 있습니다.";
        }
        return null;
    }

    /** 일정 시각이 지났는가. 시간이 없는 일정(하루 종일)은 그날이 지나야 지난 일정이다 */
    private boolean isPast(ScheduleEvent e) {
        LocalDate d;
        try {
            d = LocalDate.parse(e.getEventDate());
        } catch (RuntimeException ex) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        if (e.getTimeText() == null) {
            return d.isBefore(today);
        }
        try {
            return LocalDateTime.of(d, LocalTime.parse(e.getTimeText())).isBefore(LocalDateTime.now(clock));
        } catch (RuntimeException ex) {
            return d.isBefore(today);
        }
    }

    /**
     * 오늘 이전 날짜는 받지 않는다. 오늘이면서 시간을 넣었다면 지금 이후만 받는다. (시간을 비우면 하루 종일이라 오늘도 된다)
     * 검사한 날짜와 시간을 "yyyy-MM-dd HH:mm" 으로 돌려준다.
     */
    String checkNotPast(String date, String time) {
        String hm = checkTime(time);
        LocalDate d = LocalDate.parse(date);
        LocalDate today = LocalDate.now(clock);
        if (d.isBefore(today)) {
            throw new BusinessException("오늘 이전 날짜에는 일정을 등록할 수 없습니다.");
        }
        boolean hasTime = time != null && !time.trim().isEmpty();
        if (hasTime && d.equals(today)
                && LocalTime.parse(hm).isBefore(LocalTime.now(clock).withSecond(0).withNano(0))) {
            throw new BusinessException("이미 지난 시각입니다. 지금 이후 시각을 입력해 주세요.");
        }
        return d + " " + hm;
    }

    private static boolean contains(List<CodeOption> options, Long sn) {
        for (CodeOption o : options) {
            if (o.getCode().equals(String.valueOf(sn))) {
                return true;
            }
        }
        return false;
    }

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
        throw new BusinessException("일정을 등록할 수 없는 사업입니다. 우리 팀이 주관하는 진행 중인 사업만 고를 수 있습니다. (참여팀은 일정을 볼 수만 있습니다)");
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
        Map<Long, String> teams = new LinkedHashMap<>();
        for (ScheduleEvent e : events) {
            byDate.computeIfAbsent(e.getEventDate(), k -> new ArrayList<>()).add(e);
            if (e.getTeamSn() != null) {
                teams.putIfAbsent(e.getTeamSn(), e.getTeamNm());
            }
        }
        // 팀 색 범례: 이번 달에 나온 팀만, 팀 번호 순
        teams.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(t -> {
            CodeOption o = new CodeOption();
            o.setCode(String.valueOf(t.getKey()));
            o.setName(t.getValue());
            v.getLegendTeams().add(o);
        });
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
