package com.ojj.sujuro.bid.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.dto.CodeOptionDTO;
import com.ojj.sujuro.bid.dto.NoticeDetailDTO;
import com.ojj.sujuro.bid.dto.TeamOptionDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeTeamService;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.history.service.HistoryService;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.notification.code.NotificationType;

/**
 * 담당 팀 배정 · 참여 결정. 흐름은 인터페이스(NoticeTeamService)에 있다.
 * 상태·단계 규칙은 NoticeDetailDTO 에 있어 화면(버튼을 막는 것)과 여기(요청을 막는 것)가 같은 판단을 한다.
 *
 * 공고 행을 바꿀 때 WHERE 에 지금 상태·팀을 건다. 화면을 연 뒤 다른 사람이 먼저 처리했으면 0행이 되고,
 * 그때는 덮어쓰지 않고 다시 확인하라고 안내한다.
 */
@Service
public class NoticeTeamServiceImpl implements NoticeTeamService {

    /** 처리사유 컬럼은 4000자다. 앞에 붙이는 말("불참 · ")을 빼고도 넉넉하게 막는다. */
    private static final int MAX_REASON = 1000;

    private static final String CHANGED_MEANWHILE = "그사이 다른 사람이 이 공고를 처리했습니다. 화면을 새로 고쳐 지금 상태를 확인하세요.";

    private final BidNoticeMapper bidNoticeMapper;
    private final HistoryService historyService;
    private final NotificationService notificationService;

    public NoticeTeamServiceImpl(BidNoticeMapper bidNoticeMapper, HistoryService historyService,
                                 NotificationService notificationService) {
        this.bidNoticeMapper = bidNoticeMapper;
        this.historyService = historyService;
        this.notificationService = notificationService;
    }

    @Override
    public List<TeamOptionDTO> findTeamOptions() {
        return bidNoticeMapper.findTeamOptions(BidCode.DEPT_DEV, Role.CD_LEADER, LoginUser.STATUS_INACTIVE);
    }

    @Override
    public List<CodeOptionDTO> findPtcpShpOptions() {
        return bidNoticeMapper.findCodeOptions(BidCode.GROUP_PTCP_SHP);
    }

    @Override
    @Transactional
    public String assign(Long pbancSn, Long teamSn, String ptcpShpCd, String userId) {
        NoticeDetailDTO notice = load(pbancSn);
        if (!notice.isAssignable()) {
            throw new BusinessException("'" + notice.getPbancSttsNm() + "' 상태에서는 배정을 바꿀 수 없습니다. 팀에서 철회하면 영업부로 돌아옵니다.");
        }
        if (notice.getAssignBlockReason() != null) {
            throw new BusinessException(notice.getAssignBlockReason());
        }
        if (teamSn == null) {
            throw new BusinessException("주관 팀을 고르세요.");
        }
        TeamOptionDTO team = findTeamOptions().stream()
                .filter(t -> t.getTeamSn().equals(teamSn))
                .findFirst()
                .orElseThrow(() -> new BusinessException("배정할 수 없는 팀입니다. 개발부 팀 중에서 고르세요."));
        String shpCd = blankToNull(ptcpShpCd);
        String shpNm = shpCd == null ? null : findPtcpShpOptions().stream()
                .filter(c -> c.getCd().trim().equals(shpCd))
                .map(CodeOptionDTO::getCdNm)
                .findFirst()
                .orElseThrow(() -> new BusinessException("참여 형태를 다시 고르세요."));

        boolean sameTeam = teamSn.equals(notice.getSprvsnTeamSn());
        if (sameTeam && Objects.equals(shpCd, blankToNull(notice.getPtcpShpCd()))) {
            throw new BusinessException("이미 " + team.getTeamNm() + "에 같은 참여 형태로 배정돼 있습니다.");
        }
        // 팀이 있던 공고(결정 전 팀 바꾸기)나 팀에서 돌아온 공고를 다시 맡기면 재배정이다 (FR-040, FR-041)
        boolean reassign = notice.getSprvsnTeamSn() != null || notice.isReturned();

        int updated = bidNoticeMapper.updateAssign(pbancSn, teamSn, shpCd, BidCode.STTS_ASSIGNED,
                notice.getPbancSttsCd().trim(), notice.getSprvsnTeamSn());
        if (updated == 0) {
            throw new BusinessException(CHANGED_MEANWHILE);
        }
        historyService.record(pbancSn, reassign ? BidCode.PRCS_REASSIGN : BidCode.PRCS_ASSIGN, userId, teamSn,
                assignNote(notice, sameTeam, shpNm));

        if (sameTeam) {
            return "참여 형태를 바꿨습니다.";
        }
        notificationService.sendToTeamLeaders(teamSn, NotificationType.ASSIGNED, pbancSn,
                "'" + nameOf(notice) + "' 공고가 " + team.getTeamNm() + "에 배정되었습니다. 공고 상세에서 참여 여부를 결정하세요.");
        String done = (reassign ? "재배정" : "배정") + "했습니다. ";
        return team.getLeaderNm() == null
                ? done + team.getTeamNm() + "에 팀장 계정이 없어 알림은 보내지 못했습니다."
                : done + team.getTeamNm() + " 팀장(" + team.getLeaderNm() + ")에게 알렸습니다.";
    }

    /** 이력 사유. 예: "불참 반환 뒤 재배정 · 참여 형태 주관", "이전 개발2팀 · 참여 형태 하도급" */
    private static String assignNote(NoticeDetailDTO notice, boolean sameTeam, String shpNm) {
        List<String> parts = new ArrayList<>();
        if (notice.isReturned()) {
            parts.add(notice.getRtrnSeNm() + " 반환 뒤 재배정");
        } else if (notice.getSprvsnTeamNm() != null && !sameTeam) {
            parts.add("이전 " + notice.getSprvsnTeamNm());
        }
        if (shpNm != null) {
            parts.add("참여 형태 " + shpNm);
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    @Override
    @Transactional
    public String close(Long pbancSn, String reason, String userId) {
        NoticeDetailDTO notice = load(pbancSn);
        String why = requireReason(reason, "불참 처리 사유를 입력하세요.");
        if (!notice.isClosable()) {
            throw new BusinessException("신규 상태인 공고만 불참으로 종결할 수 있습니다. 지금은 '" + notice.getPbancSttsNm() + "'입니다.");
        }
        if (bidNoticeMapper.updateClosed(pbancSn, BidCode.STTS_NEW, BidCode.STTS_DECLINED) == 0) {
            throw new BusinessException(CHANGED_MEANWHILE);
        }
        historyService.record(pbancSn, BidCode.PRCS_DECLINE, userId, null, why);
        return "불참으로 종결했습니다.";
    }

    @Override
    public boolean isAssignedLeader(Long pbancSn, LoginUser user) {
        return user != null && user.getRole() == Role.LEADER
                && bidNoticeMapper.countAssignedLeader(pbancSn, user.getEmpNo(), Role.CD_LEADER) > 0;
    }

    @Override
    @Transactional
    public String decide(Long pbancSn, boolean join, String reason, LoginUser user) {
        // 배정된 팀의 팀장이 아니면 공고가 있는지도 알려 주지 않는다 (AccessChecker 와 같은 방식)
        if (!isAssignedLeader(pbancSn, user)) {
            throw new AccessDeniedException("이 공고에 배정된 팀의 팀장만 참여 여부를 결정할 수 있습니다.");
        }
        NoticeDetailDTO notice = load(pbancSn);
        String why = join ? optionalReason(reason) : requireReason(reason, "불참 사유를 입력하세요.");
        if (!notice.isAwaitingDecision()) {
            throw new BusinessException("배정 완료 상태인 공고만 결정할 수 있습니다. 지금은 '" + notice.getPbancSttsNm() + "'입니다.");
        }
        if (notice.getDecideBlockReason() != null) {
            throw new BusinessException(notice.getDecideBlockReason());
        }
        Long teamSn = notice.getSprvsnTeamSn();
        String who = "'" + nameOf(notice) + "' " + notice.getSprvsnTeamNm() + " 팀장이 ";

        if (join) {
            if (notice.isCanceled()) {
                throw new BusinessException("취소된 공고라 참여할 수 없습니다. 불참으로 영업부에 돌려주세요.");
            }
            if (bidNoticeMapper.updateJoined(pbancSn, teamSn, BidCode.STTS_ASSIGNED, BidCode.STTS_JOINED) == 0) {
                throw new BusinessException(CHANGED_MEANWHILE);
            }
            // 제안 업무 화면은 제안팀원에게만 사업건을 보여 준다. 결정한 팀장이 먼저 들어가 나머지 인원을 넣는다 (FR-043).
            bidNoticeMapper.insertProposalLeader(pbancSn, user.getEmpNo(), BidCode.PTCP_LEAD);
            historyService.record(pbancSn, BidCode.PRCS_DECIDE, user.getEmpNo(), teamSn,
                    why == null ? "참여" : "참여 · " + why);
            notificationService.sendToRole(Role.SALES, NotificationType.PARTICIPATION_DECIDED, pbancSn,
                    who + "참여를 결정했습니다.");
            return "참여를 결정했습니다. 영업부에 알렸습니다. 제안 업무 화면에서 제안팀을 구성하세요.";
        }

        if (bidNoticeMapper.updateReturned(pbancSn, teamSn, BidCode.STTS_ASSIGNED, BidCode.STTS_NEW,
                BidCode.RTRN_DECLINE) == 0) {
            throw new BusinessException(CHANGED_MEANWHILE);
        }
        historyService.record(pbancSn, BidCode.PRCS_DECIDE, user.getEmpNo(), teamSn, "불참 · " + why + " (영업부로 반환)");
        notificationService.sendToRole(Role.SALES, NotificationType.PARTICIPATION_DECIDED, pbancSn,
                who + "불참을 결정했습니다. 다른 팀에 재배정하거나 불참으로 종결하세요.");
        return "불참을 결정했습니다. 공고가 영업부로 돌아갑니다.";
    }

    @Override
    @Transactional
    public String withdraw(Long pbancSn, String reason, LoginUser user) {
        if (!isAssignedLeader(pbancSn, user)) {
            throw new AccessDeniedException("이 공고에 배정된 팀의 팀장만 중도 철회할 수 있습니다.");
        }
        NoticeDetailDTO notice = load(pbancSn);
        String why = requireReason(reason, "철회 사유를 입력하세요.");
        if (!notice.isWithdrawable()) {
            throw new BusinessException("참여 확정·제안 작성 중인 공고만 중도 철회할 수 있습니다. 지금은 '" + notice.getPbancSttsNm() + "'입니다.");
        }
        Long teamSn = notice.getSprvsnTeamSn();
        // WHERE 에 지금 상태를 걸어, 그사이 제출 완료로 넘어갔으면 철회하지 않는다
        if (bidNoticeMapper.updateReturned(pbancSn, teamSn, notice.getPbancSttsCd().trim(), BidCode.STTS_NEW,
                BidCode.RTRN_WITHDRAW) == 0) {
            throw new BusinessException(CHANGED_MEANWHILE);
        }
        // 제안팀을 푼다. 철회한 팀은 이 사업건을 더 보지 않고, 새로 맡는 팀은 빈 제안팀에서 시작한다.
        int members = bidNoticeMapper.deleteProposalMembers(pbancSn);
        bidNoticeMapper.deleteJoinTeams(pbancSn);
        bidNoticeMapper.clearTocOwners(pbancSn);

        historyService.record(pbancSn, BidCode.PRCS_WITHDRAW, user.getEmpNo(), teamSn, why + " (영업부로 이동)");
        notificationService.sendToRole(Role.SALES, NotificationType.WITHDRAWN, pbancSn,
                "'" + nameOf(notice) + "' " + notice.getSprvsnTeamNm() + " 팀장이 중도 철회를 신청했습니다. 다른 팀에 재배정하거나 불참으로 종결하세요.");
        return "중도 철회했습니다. 공고가 영업부로 넘어갔고, 제안팀 " + members + "명의 배정이 풀렸습니다.";
    }

    private NoticeDetailDTO load(Long pbancSn) {
        NoticeDetailDTO notice = bidNoticeMapper.findDetail(pbancSn);
        if (notice == null) {
            throw new BusinessException("공고를 찾을 수 없습니다.");
        }
        return notice;
    }

    /** 알림 문구에 넣을 공고 이름. 공고명을 아직 못 받았으면 번호 */
    private static String nameOf(NoticeDetailDTO notice) {
        return notice.getPbancNm() != null ? notice.getPbancNm().trim() : notice.getPbancNo();
    }

    private static String requireReason(String reason, String message) {
        String why = optionalReason(reason);
        if (why == null) {
            throw new BusinessException(message);
        }
        return why;
    }

    private static String optionalReason(String reason) {
        String why = blankToNull(reason);
        if (why != null && why.length() > MAX_REASON) {
            throw new BusinessException("사유는 " + MAX_REASON + "자까지 입력할 수 있습니다.");
        }
        return why;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
