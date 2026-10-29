package com.ojj.sujuro.result.service.impl;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.result.support.StatsFormat;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsChartDTO;
import com.ojj.sujuro.result.dto.StatsFileDTO;
import com.ojj.sujuro.result.dto.StatsTallyDTO;
import com.ojj.sujuro.result.dto.StatsTeamDTO;
import com.ojj.sujuro.result.dto.StatsViewDTO;
import com.ojj.sujuro.result.mapper.StatsMapper;
import com.ojj.sujuro.result.service.StatsService;

/** 통계. 범위 규칙은 인터페이스(StatsService), 집계 기준은 mapper/result/StatsMapper.xml 에 있다. */
@Service
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    /** 연도별 차트에 그리는 최대 연도 수 (최근 것부터) */
    private static final int CHART_YEARS = 5;

    static final String LOCK_NO_TEAM = "통계는 개발팀 단위로 제공됩니다.";

    private final StatsMapper statsMapper;

    public StatsServiceImpl(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    @Override
    public StatsViewDTO build(LoginUser user, Long teamSn, String year) {
        List<StatsTeamDTO> teams = teamsOf(user);
        StatsViewDTO v = new StatsViewDTO();
        v.setScopeText(user.getRole() == Role.LEADER ? "팀장은 자기 팀 통계만 봅니다" : "각 팀별 통계와 사업별 결과 분석");
        if (teams.isEmpty()) {
            v.setLockMessage(LOCK_NO_TEAM);
            return v;
        }
        StatsTeamDTO team = teams.stream().filter(t -> t.getTeamSn().equals(teamSn)).findFirst().orElse(teams.get(0));
        v.setTeams(teams);
        v.setTeamSn(team.getTeamSn());
        v.setTeamNm(team.getTeamNm());

        List<Long> teamSns = teamSnsOf(teams);
        List<String> years = yearsOf(teamSns);
        String picked = year != null && YEAR.matcher(year).matches() && years.contains(year) ? year : years.get(years.size() - 1);
        v.setYears(years);
        v.setYear(picked);

        Map<String, StatsTallyDTO> byYear = new HashMap<>();
        for (StatsTallyDTO t : statsMapper.tallyByYearTeam(teamSns)) {
            if (t.getTeamSn().equals(team.getTeamSn())) {
                byYear.put(t.getYear(), t);
            }
        }
        v.setSummary(byYear.getOrDefault(picked, StatsTallyDTO.empty(picked, team.getTeamSn(), team.getTeamNm())));
        v.setChart(StatsChartDTO.of(years.subList(Math.max(0, years.size() - CHART_YEARS), years.size()), byYear, picked));

        v.setPtcpShpRates(statsMapper.ptcpShpRates(team.getTeamSn(), picked));
        v.setArticles(statsMapper.articleRates(team.getTeamSn(), picked, null));
        v.setHistory(statsMapper.history(team.getTeamSn()));
        return v;
    }

    @Override
    public String csv(LoginUser user) {
        List<StatsTeamDTO> teams = teamsOf(user);
        List<List<String>> rows = new ArrayList<>();
        rows.add(row("연도", "팀", "참여", "결과 등록", "수주", "탈락", "유찰", "수주율(%)", "수주 금액(원)", "평균 기술점수"));
        if (teams.isEmpty()) {
            return toCsv(rows);
        }
        List<Long> teamSns = teamSnsOf(teams);
        Map<String, StatsTallyDTO> cells = new HashMap<>();
        for (StatsTallyDTO t : statsMapper.tallyByYearTeam(teamSns)) {
            cells.put(t.getYear() + "|" + t.getTeamSn(), t);
        }
        for (String y : yearsOf(teamSns)) {
            for (StatsTeamDTO team : teams) {
                StatsTallyDTO t = cells.getOrDefault(y + "|" + team.getTeamSn(),
                        StatsTallyDTO.empty(y, team.getTeamSn(), team.getTeamNm()));
                rows.add(row(y, team.getTeamNm(), String.valueOf(t.getJoined()), String.valueOf(t.getResultCnt()),
                        String.valueOf(t.getWon()), String.valueOf(t.getLost()), String.valueOf(t.getFailed()),
                        t.getRate() == null ? "" : StatsFormat.number(t.getRate()),
                        String.valueOf(t.getWonAmt()),
                        t.getAvgTech() == null ? "" : StatsFormat.number(StatsFormat.round1(t.getAvgTech()))));
            }
        }
        return toCsv(rows);
    }

    @Override
    public StatsArchiveDTO findArchive(LoginUser user, Long pbancSn) {
        StatsArchiveDTO a = readable(user, pbancSn);
        if (a == null) {
            return null;
        }
        a.setArticles(statsMapper.articleRates(null, null, pbancSn));
        a.setFinalFile(statsMapper.findFinalFile(pbancSn));
        a.setEvaluationFile(statsMapper.findEvaluationFile(pbancSn));
        a.setOurCompanyListed(statsMapper.countOurCompany(pbancSn) > 0);
        a.setEvaluationForm(EvaluationForm.fromSaved(statsMapper.findEvaluationScores(pbancSn)));
        return a;
    }

    @Override
    public StatsFileDTO findFinalFile(LoginUser user, Long pbancSn) {
        return readable(user, pbancSn) == null ? null : statsMapper.findFinalFile(pbancSn);
    }

    @Override
    public EvaluationFileDTO findEvaluationFile(LoginUser user, Long pbancSn) {
        return readable(user, pbancSn) == null ? null : statsMapper.findEvaluationFile(pbancSn);
    }

    /** 사업 하나를 이 사용자가 볼 수 있는지. 없는 사업이면 null, 볼 수 없으면 403 */
    private StatsArchiveDTO readable(LoginUser user, Long pbancSn) {
        List<StatsTeamDTO> teams = teamsOf(user);
        StatsArchiveDTO a = statsMapper.findArchive(pbancSn);
        if (a == null) {
            return null;
        }
        if (teams.stream().noneMatch(t -> t.getTeamSn().equals(a.getTeamSn()))) {
            throw new AccessDeniedException("자기 팀 사업의 결과 분석만 볼 수 있습니다.");
        }
        return a;
    }

    /** 이 사용자가 볼 수 있는 개발팀. 경영지원실은 모두, 팀장은 자기 팀(개발팀이 아니면 없음) */
    private List<StatsTeamDTO> teamsOf(LoginUser user) {
        if (user.getRole() == Role.MGMT) {
            return statsMapper.findDevTeams();
        }
        if (user.getRole() == Role.LEADER) {
            Long mine = statsMapper.findDevTeamSnOf(user.getEmpNo());
            return statsMapper.findDevTeams().stream()
                    .filter(t -> t.getTeamSn().equals(mine))
                    .collect(Collectors.toList());
        }
        throw new AccessDeniedException("통계는 경영지원실과 팀장만 볼 수 있습니다.");
    }

    private static List<Long> teamSnsOf(List<StatsTeamDTO> teams) {
        return teams.stream().map(t -> t.getTeamSn()).collect(Collectors.toList());
    }

    /** 참여 공고가 있는 연도 (오래된 순). 하나도 없으면 올해만 */
    private List<String> yearsOf(List<Long> teamSns) {
        List<String> years = new ArrayList<>(statsMapper.findYears(teamSns));
        if (years.isEmpty()) {
            years.add(String.valueOf(Year.now().getValue()));
        }
        return years;
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
            sb.append(r.stream().map(StatsServiceImpl::cell).collect(Collectors.joining(","))).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * CSV 한 칸. 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다.
     * 엑셀이 수식으로 읽는 글자(= + - @, 탭, CR)로 시작하면 앞에 ' 를 붙인다. 음수 같은 숫자는 그대로 둔다.
     */
    static String cell(String s) {
        if (s == null) {
            return "";
        }
        String t = s;
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0 && !isNumber(t)) {
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
}
