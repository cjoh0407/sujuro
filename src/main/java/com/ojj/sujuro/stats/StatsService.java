package com.ojj.sujuro.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.stats.StatsRows.Artcl;
import com.ojj.sujuro.stats.StatsRows.History;
import com.ojj.sujuro.stats.StatsRows.Tally;
import com.ojj.sujuro.user.CodeOption;

/**
 * 통계 (FR-083 ~ 088)
 *
 * - 경영지원실은 전체 팀(또는 고른 팀), 팀장은 자기 팀만 본다. 그 밖의 역할은 들어올 수 없다. (SecurityConfig 와 이중 확인)
 * - 팀 범위는 화면에서 받은 값이 아니라 로그인한 사용자로 정한다. 팀장이 team 값을 보내도 무시한다. (NFR-001)
 * - 팀장인데 소속 팀이 없으면 아무것도 보여 주지 않는다. (null 을 그대로 쓰면 모든 팀이 보이므로)
 */
@Service
public class StatsService {

    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    /** CSV 로 내려받을 수 있는 표 */
    public static final List<String> SECTIONS = java.util.Arrays.asList("yearly", "team", "type", "shape", "article", "history");

    private final StatsMapper mapper;

    public StatsService(StatsMapper mapper) {
        this.mapper = mapper;
    }

    public StatsView build(LoginUser user, String yearParam, Long teamParam) {
        Scope scope = scope(user, teamParam);
        StatsView v = new StatsView();
        v.setSelectedTeamSn(scope.teamSn);
        v.setScopeText(scope.text);
        v.setTeams(scope.teams);
        if (scope.empty) {
            return v;
        }

        List<String> years = mapper.findYears(scope.teamSn);
        v.setYears(years);
        String year = pickYear(years, yearParam);
        v.setYear(year);

        v.setYearly(mapper.byYear(scope.teamSn));
        for (Tally t : v.getYearly()) {
            if (t.getLabel() != null && t.getLabel().trim().equals(year)) {
                v.setCurrent(t);
            }
        }
        if (year != null) {
            v.setByTeam(mapper.byTeam(year, scope.teamSn));
            v.setByBizType(mapper.byBizType(year, scope.teamSn));
            v.setByShape(mapper.byShape(year, scope.teamSn));
            v.setArticles(mapper.articles(year, scope.teamSn));
            v.setHistory(mapper.history(year, scope.teamSn));
        }
        return v;
    }

    /** CSV 한 개 (FR-088). 화면과 같은 범위·같은 숫자를 쓴다. 엑셀에서 한글이 깨지지 않도록 호출하는 쪽이 BOM 을 붙인다. */
    public String csv(LoginUser user, String section, String yearParam, Long teamParam) {
        if (!SECTIONS.contains(section)) {
            throw new BusinessException("내려받을 수 없는 통계입니다.");
        }
        StatsView v = build(user, yearParam, teamParam);
        List<List<String>> rows = new ArrayList<>();
        switch (section) {
            case "yearly":
                rows.add(row("연도", "참여", "수주", "탈락", "유찰", "수주율", "수주 금액", "평균 기술점수"));
                tallyRows(rows, v.getYearly());
                break;
            case "team":
                rows.add(row("팀", "참여", "수주", "탈락", "유찰", "수주율", "수주 금액", "평균 기술점수"));
                tallyRows(rows, v.getByTeam());
                break;
            case "type":
                rows.add(row("사업 유형", "참여", "수주", "탈락", "유찰", "수주율", "수주 금액", "평균 기술점수"));
                tallyRows(rows, v.getByBizType());
                break;
            case "shape":
                rows.add(row("참여 형태", "참여", "수주", "탈락", "유찰", "수주율", "수주 금액", "평균 기술점수"));
                tallyRows(rows, v.getByShape());
                break;
            case "article":
                rows.add(row("평가 항목", "평균 득점률(%)", "집계 공고 수"));
                for (Artcl a : v.getArticles()) {
                    rows.add(row(a.getArtclNm(), String.valueOf(a.getRate()), String.valueOf(a.getCnt())));
                }
                break;
            default:
                rows.add(row("공고 번호", "사업명", "발주기관", "담당 팀", "예산", "결과", "제안서 보관", "입찰 마감일"));
                for (History h : v.getHistory()) {
                    rows.add(row(String.valueOf(h.getPbancSn()), h.getPbancNm(), h.getOrderInstNm(), h.getTeamNm(),
                            h.getBudget() == null ? "" : String.valueOf(h.getBudget()), h.getResultNm(),
                            "Y".equals(h.getKeptYn()) ? "보관" : "없음", h.getDdlnText()));
                }
                break;
        }
        return toCsv(rows);
    }

    private void tallyRows(List<List<String>> rows, List<Tally> list) {
        for (Tally t : list) {
            rows.add(row(t.getLabel(), String.valueOf(t.getJoined()), String.valueOf(t.getWon()),
                    String.valueOf(t.getLost()), String.valueOf(t.getFailed()), t.getRateText(),
                    String.valueOf(t.getAmount()), t.getAvgTechText()));
        }
    }

    private static List<String> row(String... cells) {
        List<String> r = new ArrayList<>();
        for (String c : cells) {
            r.add(c);
        }
        return r;
    }

    static String toCsv(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> r : rows) {
            for (int i = 0; i < r.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(cell(r.get(i)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /** 쉼표·따옴표·줄바꿈은 따옴표로 감싸고, 엑셀이 수식으로 읽는 글자(= + - @)로 시작하면(숫자와 "-" 자리표시는 그대로 둔다) 앞에 ' 를 붙인다. */
    static String cell(String s) {
        if (s == null) {
            return "";
        }
        String t = s;
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0 && !"-".equals(t) && !isNumber(t)) {
            t = "'" + t;
        }
        if (t.indexOf(',') >= 0 || t.indexOf('"') >= 0 || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) {
            t = "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }

    private static boolean isNumber(String t) {
        try {
            Double.parseDouble(t);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String pickYear(List<String> years, String param) {
        if (param != null && YEAR.matcher(param).matches() && years.contains(param)) {
            return param;
        }
        return years.isEmpty() ? null : years.get(0);
    }

    private Scope scope(LoginUser user, Long teamParam) {
        Scope s = new Scope();
        if (user.getRole() == Role.MGMT) {
            s.teams = mapper.findDevTeams();
            for (CodeOption t : s.teams) {
                if (teamParam != null && t.getCode().equals(teamParam.toString())) {
                    s.teamSn = teamParam;
                    s.text = t.getName() + " 통계";
                }
            }
            if (s.teamSn == null) {
                s.text = "전체 개발팀 통계";
            }
            return s;
        }
        if (user.getRole() == Role.LEADER) {
            s.teamSn = mapper.findMyTeamSn(user.getEmpNo());
            if (s.teamSn == null) {
                s.empty = true;
                s.text = "소속 팀이 없어 통계를 볼 수 없습니다.";
            } else {
                s.text = "우리 팀 통계";
            }
            return s;
        }
        throw new AccessDeniedException("통계는 경영지원실과 팀장만 볼 수 있습니다.");
    }

    private static class Scope {
        Long teamSn;
        boolean empty;
        String text;
        List<CodeOption> teams = new ArrayList<>();
    }
}
