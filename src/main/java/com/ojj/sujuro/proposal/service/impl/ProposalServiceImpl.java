package com.ojj.sujuro.proposal.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.ProposalMemberDto;
import com.ojj.sujuro.proposal.dto.ProposalTeamDto;
import com.ojj.sujuro.proposal.mapper.ProposalMapper;
import com.ojj.sujuro.proposal.service.ProposalService;

/** 사업건 선택과 접근 확인, 제안팀 구성, 쪽수 제한 (FR-011, FR-016-1, FR-043~044, FR-055) */
@Service
public class ProposalServiceImpl implements ProposalService {

    private static final String PT_LEAD = "PT010"; // 주관팀
    private static final String PT_JOIN = "PT020"; // 참여팀
    private static final String DEPT_DEV = "DP030"; // 개발부
    private static final String STATUS_INACTIVE = "AS050";

    private final ProposalMapper mapper;
    private final NotificationService notificationService;

    public ProposalServiceImpl(ProposalMapper mapper, NotificationService notificationService) {
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    @Override
    public List<ProposalBidDto> myBids(String userId) {
        return mapper.findMyBids(userId);
    }

    /** 제안팀원인 사업건만 열 수 있다. 아니면 업무 예외로 안내한다. */
    @Override
    public ProposalBidDto openBid(Long pbancSn, String userId) {
        ProposalBidDto bid = mapper.findBid(pbancSn);
        if (bid == null || mapper.countMember(pbancSn, userId) == 0) {
            throw new BusinessException("제안팀에 속한 사업건만 볼 수 있습니다.");
        }
        return bid;
    }

    @Override
    public List<ProposalMemberDto> members(Long pbancSn) {
        return mapper.findMembers(pbancSn);
    }

    /* ---------- 제안팀 구성 (FR-043~044) ---------- */

    @Override
    public List<ProposalMemberDto> addableUsers(Long pbancSn) {
        return mapper.findAddableUsers(pbancSn);
    }

    @Override
    public List<ProposalTeamDto> teamOptions(Long pbancSn) {
        return mapper.findTeamOptions(pbancSn);
    }

    @Transactional
    @Override
    public String addMember(Long pbancSn, String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException("추가할 인원을 골라 주세요.");
        }
        ProposalBidDto bid = mapper.findBid(pbancSn);
        ProposalMemberDto u = mapper.findUser(userId.trim());
        if (u == null || STATUS_INACTIVE.equals(u.getAcntSttsCd())) {
            throw new BusinessException("추가할 수 없는 사용자입니다.");
        }
        if (!DEPT_DEV.equals(u.getDeptCd()) || u.getTeamSn() == null
                || !(Role.CD_LEADER.equals(u.getAuthrtCd()) || Role.CD_MEMBER.equals(u.getAuthrtCd()))) {
            throw new BusinessException("제안팀에는 개발부 팀장·팀원만 넣을 수 있습니다.");
        }
        if (mapper.countMember(pbancSn, u.getUserId()) > 0) {
            throw new BusinessException("'" + u.getUserNm() + "'은(는) 이미 제안팀입니다.");
        }
        boolean leadTeam = u.getTeamSn().equals(bid.getSprvsnTeamSn());
        boolean newTeam = !leadTeam && mapper.countPtcpTeam(pbancSn, u.getTeamSn()) == 0;
        if (newTeam) {
            mapper.insertPtcpTeam(pbancSn, u.getTeamSn()); // 다른 팀 인원을 넣으면 그 팀이 참여팀이 된다
        }
        mapper.insertMember(pbancSn, u.getUserId(), leadTeam ? PT_LEAD : PT_JOIN);
        return u.getUserNm();
    }

    @Transactional
    @Override
    public String removeMember(Long pbancSn, String userId) {
        ProposalMemberDto m = userId == null ? null : mapper.findMember(pbancSn, userId.trim());
        if (m == null || !(PT_LEAD.equals(m.getPtcpSeCd()) || PT_JOIN.equals(m.getPtcpSeCd()))) {
            throw new BusinessException("이 사업건의 제안팀원이 아닙니다.");
        }
        if (!m.isRemovable()) {
            throw new BusinessException("주관팀 팀장은 제안팀에서 뺄 수 없습니다.");
        }
        int owned = mapper.countOwnedTocs(pbancSn, m.getUserId());
        if (owned > 0) {
            throw new BusinessException("'" + m.getUserNm() + "'이(가) 담당한 목차가 " + owned + "개 있습니다. 담당자를 먼저 바꿔 주세요.");
        }
        mapper.deleteMember(pbancSn, m.getUserId());
        return m.getUserNm();
    }

    @Transactional
    @Override
    public void saveTeams(Long pbancSn, List<Long> teamSns) {
        Set<Long> want = new HashSet<>();
        if (teamSns != null) {
            for (Long t : teamSns) {
                if (t != null) {
                    want.add(t);
                }
            }
        }
        List<ProposalTeamDto> options = mapper.findTeamOptions(pbancSn); // 주관팀은 여기에 없다
        List<String> blocked = new ArrayList<>();
        for (ProposalTeamDto t : options) {
            if (t.isPtcp() && !want.contains(t.getTeamSn()) && t.getMemberCnt() > 0) {
                blocked.add(t.getTeamNm() + "(" + t.getMemberCnt() + "명)");
            }
        }
        if (!blocked.isEmpty()) {
            throw new BusinessException("제안팀에 인원이 남아 있는 팀은 참여팀에서 뺄 수 없습니다: "
                    + String.join(", ", blocked) + ". 인원을 먼저 빼 주세요.");
        }
        for (ProposalTeamDto t : options) {
            boolean on = want.contains(t.getTeamSn());
            if (on && !t.isPtcp()) {
                mapper.insertPtcpTeam(pbancSn, t.getTeamSn());
            } else if (!on && t.isPtcp()) {
                mapper.deletePtcpTeam(pbancSn, t.getTeamSn());
            }
        }
    }

    @Transactional
    @Override
    public void changePageLimit(Long pbancSn, Integer limit) {
        if (limit != null && (limit < 1 || limit > 9999)) {
            throw new BusinessException("제한 쪽수는 1~9999 사이로 입력해 주세요.");
        }
        mapper.updatePageLimit(pbancSn, limit);
    }
}
