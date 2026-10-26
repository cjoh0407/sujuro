package com.ojj.sujuro.proposal.bid;

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.auth.Role;

/**
 * 목차 화면에서 로그인한 사용자가 할 수 있는 일 (요구사항 v3.2 FR-046~049, NFR-004, 화면 설계 v3.2).
 *   load   : 목차 양식 불러오기·저장 — 이 사업건 주관팀 소속 제안팀원
 *   edit   : 목차 추가·이름 수정·삭제·쪽수 — 제안팀원 전체(주관팀·참여팀 인원)
 *   assign : 최하위 목차 담당자 지정, 페이지 제한, 요구사항 관리 — 주관팀 팀장
 * 요구사항 등록·연결은 assign이거나 목차 담당자면 된다(RqtService.canEdit).
 */
public class ProposalPerm {

    private final boolean load;
    private final boolean edit;
    private final boolean assign;

    public ProposalPerm(boolean load, boolean edit, boolean assign) {
        this.load = load;
        this.edit = edit;
        this.assign = assign;
    }

    public static ProposalPerm of(ProposalBidDto bid, LoginUser user) {
        boolean writer = bid.isProposalWriter();
        boolean myTeam = bid.isMySupervisingTeam();
        return new ProposalPerm(writer && myTeam, writer, writer && myTeam && user.getRole() == Role.LEADER);
    }

    public boolean isLoad() { return load; }

    public boolean isEdit() { return edit; }

    public boolean isAssign() { return assign; }
}
