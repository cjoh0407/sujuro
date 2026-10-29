package com.ojj.sujuro.dashboard.service;

import com.ojj.sujuro.dashboard.dto.DashboardView;
import com.ojj.sujuro.dashboard.mapper.DashboardMapper;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.dashboard.dto.DashboardRows.DocRow;
import com.ojj.sujuro.dashboard.dto.DashboardRows.LeaderCounts;
import com.ojj.sujuro.dashboard.dto.DashboardRows.SalesCounts;
import com.ojj.sujuro.dashboard.dto.DashboardView.Stat;
import com.ojj.sujuro.notification.dto.NotificationDto;
import com.ojj.sujuro.notification.mapper.NotificationInboxMapper;

/**
 * 대시보드 구성 (FR-089, 090)
 *
 * - 사용자 소속 팀 기준으로 구성한다. 영업부·경영지원실은 전체 팀 현황을, 팀장·팀원은 자기 팀만 본다. (FR-089)
 * - 역할별 할 일을 맨 위에 둔다. (FR-090)
 *     영업부: 배정 대기 · 팀장 응답 대기 / 팀장: 참여 결정 대기 · 미매핑 요구사항
 *     팀원: 내 담당 목차 · 마감 / 경영지원실: 제출서류 준비 현황 · 나라장터 등록 대기
 * - 보는 범위는 화면에서 받은 값이 아니라 로그인한 사용자(역할·소속 팀)로만 정한다. (NFR-001)
 */
@Service
public class DashboardService {

    private static final int ALERT_LIMIT = 5;

    private final DashboardMapper mapper;
    private final NotificationInboxMapper inboxMapper;

    public DashboardService(DashboardMapper mapper, NotificationInboxMapper inboxMapper) {
        this.mapper = mapper;
        this.inboxMapper = inboxMapper;
    }

    public DashboardView build(LoginUser user) {
        DashboardView v = new DashboardView();
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
        v.setAlerts(recentAlerts(user.getEmpNo()));
        return v;
    }

    private void buildSales(DashboardView v) {
        v.setRoleKey("SALES");
        v.setSubtitle("영업부 · 전체 팀 현황과 처리할 일");
        SalesCounts c = mapper.salesCounts();
        v.getStats().add(new Stat("배정 대기", c.getAssignWait(), "신규 입찰공고"));
        v.getStats().add(new Stat("팀장 응답 대기", c.getAwaitingTeam(), "배정 완료 · 결정 전"));
        v.getStats().add(new Stat("반환 건", c.getReturned(), "불참·중도 철회로 돌아옴"));
        v.getStats().add(new Stat("입찰공고 대기", c.getTracking(), "발주계획·사전규격 추적 중"));
        v.setTodoBids(mapper.salesTodo());
        v.setTeamStats(mapper.teamStats(null, false));
    }

    private void buildLeader(DashboardView v, LoginUser user) {
        v.setRoleKey("LEADER");
        v.setSubtitle(user.getTeam() + " · 참여 결정과 제안 진행");
        LeaderCounts c = mapper.leaderCounts(user.getEmpNo());
        v.getStats().add(new Stat("참여 결정 대기", c.getDecisionWait(), "우리 팀 배정"));
        v.getStats().add(new Stat("진행 중 사업", c.getInProgress(), "제안팀 구성 사업"));
        v.getStats().add(new Stat("미매핑 요구사항", c.getUnmapped(), "목차 연결 필요"));
        v.getStats().add(new Stat("마감 7일 이내", c.getDueSoon(), "제출 마감 기준"));
        v.setTodoBids(mapper.leaderDecisionWait(user.getEmpNo()));
        v.setTeamBids(mapper.teamProposals(user.getEmpNo()));
        v.setTeamStats(mapper.teamStats(user.getEmpNo(), true));
    }

    private void buildMember(DashboardView v, LoginUser user) {
        v.setRoleKey("MEMBER");
        v.setSubtitle(user.getTeam() + " · 내 담당 목차");
        v.setMyTocs(mapper.myTocs(user.getEmpNo()));
        v.setTeamStats(mapper.teamStats(user.getEmpNo(), true));
    }

    private void buildMgmt(DashboardView v) {
        v.setRoleKey("MGMT");
        v.setSubtitle("경영지원실 · 전체 팀 현황과 제출서류");
        List<DocRow> rows = mapper.docRows();
        int waitReg = 0;
        int missing = 0;
        for (DocRow r : rows) {
            if (r.isWaitingRegistration()) {
                waitReg++;
            }
            missing += r.getMissingRequired();
        }
        v.getStats().add(new Stat("나라장터 등록 대기", waitReg, "필수 서류 준비 완료"));
        v.getStats().add(new Stat("필수 서류 누락", missing, "준비 완료 전"));
        v.getStats().add(new Stat("결과 입력 대기", mapper.resultWaitCount(), "제출 완료"));
        v.setDocRows(rows);
        v.setTeamStats(mapper.teamStats(null, false));
    }

    private List<NotificationDto> recentAlerts(String userId) {
        List<NotificationDto> all = inboxMapper.findMine(userId, false);
        return all.size() > ALERT_LIMIT ? all.subList(0, ALERT_LIMIT) : all;
    }
}
