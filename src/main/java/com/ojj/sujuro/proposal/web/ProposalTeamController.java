package com.ojj.sujuro.proposal.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.proposal.dto.ProposalMemberDto;
import com.ojj.sujuro.proposal.service.ProposalService;

import static com.ojj.sujuro.proposal.web.ProposalController.TAB_PROPOSAL;

import lombok.RequiredArgsConstructor;

/**
 * 제안팀 구성 (FR-043~044). 제안 화면(/proposal)의 제안팀 카드에서 호출한다.
 * 주관팀 팀장만 할 수 있다 (AccessChecker.requireLeadTeamLeader, 아니면 403).
 * 처리 후 같은 사업건·같은 탭의 /proposal 화면으로 돌아간다.
 */
@Controller
@RequiredArgsConstructor
public class ProposalTeamController {

    private final AccessChecker accessChecker;
    private final ProposalService proposalService;

    /* ---------- 제안팀 구성 (FR-043~044): 주관팀 팀장 ---------- */

    /**
     * 인원 찾기 팝업의 후보 목록 (JSON). 팝업을 열거나 필터를 바꾸면 offset=0으로, 목록 끝까지 스크롤하면
     * 다음 offset으로 부른다(무한 스크롤). 화면은 proposal.js가 fetch로 받아 줄을 덧붙인다. (FR-043 v4.0)
     * 응답: { "items": [{userId, userNm, teamNm, jbgdNm}], "hasMore": true/false }
     */
    @GetMapping("/proposal/team/candidates")
    @ResponseBody
    public Map<String, Object> candidates(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                                          @RequestParam(value = "teamSn", required = false) Long teamSn,
                                          @RequestParam(value = "rank", required = false) String rank,
                                          @RequestParam(value = "q", required = false) String q,
                                          @RequestParam(value = "offset", defaultValue = "0") int offset) {
        accessChecker.requireLeadTeamLeader(bid, user);
        List<ProposalMemberDto> found = proposalService.addableUsers(bid, teamSn, rank, q, offset);
        boolean hasMore = found.size() > ProposalService.CANDIDATE_PAGE;
        List<Map<String, String>> items = new ArrayList<>();
        for (int i = 0; i < found.size() && i < ProposalService.CANDIDATE_PAGE; i++) {
            ProposalMemberDto u = found.get(i);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("userId", u.getUserId());
            m.put("userNm", u.getUserNm());
            m.put("teamNm", u.getTeamNm());
            m.put("jbgdNm", u.getJbgdNm());
            items.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", items);
        res.put("hasMore", hasMore);
        return res;
    }

    /**
     * 제안팀 인원 추가. 인원 찾기 팝업에서 체크한 사람(userId 여러 개)을 한 번에 넣는다 (FR-043 v4.0).
     * 다른 팀 인원이면 그 팀이 참여팀으로 함께 지정된다.
     */
    @PostMapping("/proposal/team/member/add")
    public String addMember(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam(value = "userId", required = false) List<String> userIds, RedirectAttributes ra) {
        return run(user, bid, tab, ra, () -> {
            List<String> names = proposalService.addMembers(bid, userIds);
            return names.size() == 1 ? "'" + names.get(0) + "'을(를) 제안팀에 추가했습니다."
                    : names.size() + "명(" + String.join(", ", names) + ")을 제안팀에 추가했습니다.";
        });
    }

    /** 제안팀 인원 제외 (주관팀 팀장은 제외 불가, 담당 목차가 있으면 담당자를 먼저 바꾼다) */
    @PostMapping("/proposal/team/member/remove")
    public String removeMember(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                               @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                               @RequestParam("userId") String userId, RedirectAttributes ra) {
        return run(user, bid, tab, ra, () ->
                "'" + proposalService.removeMember(bid, userId) + "'을(를) 제안팀에서 뺐습니다.");
    }

    /** 참여팀 지정 (체크한 팀만 참여팀으로 남는다) */
    @PostMapping("/proposal/team/teams")
    public String saveTeams(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                            @RequestParam(value = "tab", defaultValue = TAB_PROPOSAL) String tab,
                            @RequestParam(value = "teamSn", required = false) List<Long> teamSns, RedirectAttributes ra) {
        return run(user, bid, tab, ra, () -> {
            proposalService.saveTeams(bid, teamSns);
            return "참여팀을 저장했습니다.";
        });
    }

    /** 주관팀 팀장 확인(아니면 403) 후 사업건을 열고 처리한다. 안내 띠·redirect는 FlashRedirect가 맡는다. */
    private String run(LoginUser user, Long bid, String tab, RedirectAttributes ra, Supplier<String> action) {
        accessChecker.requireLeadTeamLeader(bid, user);
        return FlashRedirect.run(ra, ProposalController.PATH, bid, ProposalController.tabParam(tab), () -> {
            proposalService.openBid(bid, user.getEmpNo());
            return action.get();
        });
    }
}
