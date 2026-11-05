package com.ojj.sujuro.result.service.impl;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.result.dto.ArticleScoreTableDTO;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsTeamDTO;
import com.ojj.sujuro.result.dto.StatsViewDTO;
import com.ojj.sujuro.result.mapper.StatsMapper;
import com.ojj.sujuro.result.service.StatsService;

/** 사업 이력 관리. 범위 규칙은 인터페이스(StatsService), 조회 기준은 mapper/result/StatsMapper.xml 에 있다. */
@Service
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    static final String LOCK_NO_TEAM = "사업 이력은 개발팀 단위로 제공됩니다.";

    private final StatsMapper statsMapper;

    public StatsServiceImpl(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    @Override
    public StatsViewDTO build(LoginUser user, Long teamSn, String year) {
        List<StatsTeamDTO> teams = teamsOf(user);
        StatsViewDTO v = new StatsViewDTO();
        if (teams.isEmpty()) {
            v.setLockMessage(LOCK_NO_TEAM);
            return v;
        }
        StatsTeamDTO team = teams.stream().filter(t -> t.getTeamSn().equals(teamSn)).findFirst().orElse(teams.get(0));
        v.setTeams(teams);
        v.setTeamSn(team.getTeamSn());
        v.setTeamNm(team.getTeamNm());

        List<String> years = yearsOf(teamSnsOf(teams));
        String picked = year != null && YEAR.matcher(year).matches() && years.contains(year) ? year : years.get(years.size() - 1);
        v.setYears(years);
        v.setYear(picked);

        v.setInProgress(statsMapper.inProgress(team.getTeamSn(), picked));
        v.setHistory(statsMapper.history(team.getTeamSn(), picked));
        return v;
    }

    @Override
    public StatsArchiveDTO findArchive(LoginUser user, Long pbancSn) {
        StatsArchiveDTO a = readable(user, pbancSn);
        if (a == null) {
            return null;
        }
        a.setArticles(statsMapper.articleRates(pbancSn));
        a.setArticleTable(ArticleScoreTableDTO.of(statsMapper.companyArticleScores(pbancSn)));
        a.setEvaluationFile(statsMapper.findEvaluationFile(pbancSn));
        a.setOurCompanyListed(statsMapper.countOurCompany(pbancSn) > 0);
        a.setEvaluationForm(EvaluationForm.fromSaved(statsMapper.findEvaluationScores(pbancSn)));
        return a;
    }

    @Override
    public StatsArchiveDTO findPending(LoginUser user, Long pbancSn) {
        return checked(user, statsMapper.findPendingBid(pbancSn));
    }

    @Override
    public EvaluationFileDTO findEvaluationFile(LoginUser user, Long pbancSn) {
        return readable(user, pbancSn) == null ? null : statsMapper.findEvaluationFile(pbancSn);
    }

    /** 사업 하나를 이 사용자가 볼 수 있는지. 없는 사업이면 null, 볼 수 없으면 403 */
    private StatsArchiveDTO readable(LoginUser user, Long pbancSn) {
        return checked(user, statsMapper.findArchive(pbancSn));
    }

    private StatsArchiveDTO checked(LoginUser user, StatsArchiveDTO a) {
        List<StatsTeamDTO> teams = teamsOf(user);
        if (a != null && teams.stream().noneMatch(t -> t.getTeamSn().equals(a.getTeamSn()))) {
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
        throw new AccessDeniedException("사업 이력은 경영지원실과 팀장만 볼 수 있습니다.");
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
}
