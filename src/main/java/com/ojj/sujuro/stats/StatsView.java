package com.ojj.sujuro.stats;

import java.util.ArrayList;
import java.util.List;

import com.ojj.sujuro.stats.StatsRows.Artcl;
import com.ojj.sujuro.stats.StatsRows.History;
import com.ojj.sujuro.stats.StatsRows.Tally;
import com.ojj.sujuro.user.CodeOption;

/** 통계 화면에 필요한 값 모음 (FR-083 ~ 087) */
public class StatsView {
    private String year;                              // 선택한 연도 (없으면 null)
    private List<String> years = new ArrayList<>();
    private Long selectedTeamSn;                      // 경영지원실이 고른 팀 (전체면 null)
    private List<CodeOption> teams = new ArrayList<>(); // 경영지원실만 채워진다
    private String scopeText;
    private Tally current;                            // 선택 연도 요약 (FR-083)
    private List<Tally> yearly = new ArrayList<>();
    private List<Tally> byTeam = new ArrayList<>();
    private List<Tally> byBizType = new ArrayList<>();
    private List<Tally> byShape = new ArrayList<>();
    private List<Artcl> articles = new ArrayList<>();
    private List<History> history = new ArrayList<>();

    public String getYear() { return year; }
    public void setYear(String v) { this.year = v; }
    public List<String> getYears() { return years; }
    public void setYears(List<String> v) { this.years = v; }
    public Long getSelectedTeamSn() { return selectedTeamSn; }
    public void setSelectedTeamSn(Long v) { this.selectedTeamSn = v; }
    public List<CodeOption> getTeams() { return teams; }
    public void setTeams(List<CodeOption> v) { this.teams = v; }
    public String getScopeText() { return scopeText; }
    public void setScopeText(String v) { this.scopeText = v; }
    public Tally getCurrent() { return current; }
    public void setCurrent(Tally v) { this.current = v; }
    public List<Tally> getYearly() { return yearly; }
    public void setYearly(List<Tally> v) { this.yearly = v; }
    public List<Tally> getByTeam() { return byTeam; }
    public void setByTeam(List<Tally> v) { this.byTeam = v; }
    public List<Tally> getByBizType() { return byBizType; }
    public void setByBizType(List<Tally> v) { this.byBizType = v; }
    public List<Tally> getByShape() { return byShape; }
    public void setByShape(List<Tally> v) { this.byShape = v; }
    public List<Artcl> getArticles() { return articles; }
    public void setArticles(List<Artcl> v) { this.articles = v; }
    public List<History> getHistory() { return history; }
    public void setHistory(List<History> v) { this.history = v; }
}
