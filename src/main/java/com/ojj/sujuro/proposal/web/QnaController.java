package com.ojj.sujuro.proposal.web;

import javax.servlet.http.HttpSession;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.QnaBidDto;
import com.ojj.sujuro.proposal.dto.QnaDto;
import com.ojj.sujuro.proposal.service.ProposalService;
import com.ojj.sujuro.proposal.service.QnaService;

import lombok.RequiredArgsConstructor;

/**
 * 질의응답 기록 화면 (FR-074~076-1). 사업건 단위. 제안팀(주관+참여)이 쓰고, 영업부·경영지원실은 모든 사업건을 읽기만 한다.
 *   탭 exp: 예상 Q&A — 제안 평가 전에 예상 질문과 준비 답변 (FR-074)
 *   탭 prs: 발표 Q&A — 발표 때 실제로 나온 질문과 답변 (FR-075)
 *   입력은 질문·답변·관련 요구사항(선택)만. 목록은 관련 요구사항의 1단계 목차별로 묶은 표이고 질문·답변으로 검색한다. (FR-076)
 *   이전 사업 Q&A 보기: 내가 제안팀이었던 다른 사업건의 Q&A를 읽기 전용으로 본다. 복사하지 않는다. (FR-076-1)
 * 권한 (AccessChecker, 없으면 403):
 *   보기           — requireQnaReader (그 사업건 제안팀, 영업부, 경영지원실)
 *   등록·수정      — requireProposalMember (제안팀 누구나 기록한다, FR-075. 영업부·경영지원실은 403)
 *   삭제           — 제안팀원 중 작성자 또는 주관팀 팀장 (서비스에서 확인)
 *   이전 사업 보기 — 제안팀원은 내가 제안팀이었던 사업건, 영업부·경영지원실은 모든 사업건 (읽기 전용)
 * 나라장터 제출 뒤에도 발표가 있으므로 잠그지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class QnaController {

    static final String TAB_EXPECTED = "exp";
    static final String TAB_PRESENTED = "prs";
    private static final String PATH = "/qa";

    private final AccessChecker accessChecker;
    private final ProposalService proposalService;
    private final QnaService qnaService;

    @GetMapping("/qa")
    public String index(@AuthenticationPrincipal LoginUser user,
                        @RequestParam(value = "bid", required = false) Long bid,
                        @RequestParam(value = "tab", defaultValue = TAB_EXPECTED) String tab,
                        @RequestParam(value = "edit", required = false) Long edit,
                        @RequestParam(value = "q", required = false) String keyword,
                        @RequestParam(value = "past", required = false) Long past,
                        HttpSession session, Model model) {
        String me = user.getEmpNo();
        boolean reader = accessChecker.isQnaViewer(user); // 영업부·경영지원실: 모든 사업건을 읽기만
        List<ProposalBidDto> bids = reader ? proposalService.proposalBids() : proposalService.myBids(me);
        model.addAttribute("bids", bids);
        model.addAttribute("canWrite", !reader);
        if (bids.isEmpty()) {
            return "proposal/qa";
        }
        Long pbancSn = CurrentBid.pick(bids, bid, session); // 다른 제안 화면에서 고른 사업건을 이어서 연다
        accessChecker.requireQnaReader(pbancSn, user);
        ProposalBidDto current = reader ? proposalService.viewBid(pbancSn) : proposalService.openBid(pbancSn, me);
        String tk = TAB_PRESENTED.equals(tab) ? TAB_PRESENTED : TAB_EXPECTED;
        String q = keyword == null ? "" : keyword.trim();

        List<QnaDto> all = qnaService.list(pbancSn);
        List<QnaDto> expected = qnaService.filter(all, QnaDto.SE_EXPECTED);
        List<QnaDto> presented = qnaService.filter(all, QnaDto.SE_PRESENTED);
        List<QnaDto> items = TAB_PRESENTED.equals(tk) ? presented : expected;
        int unanswered = 0;
        for (QnaDto x : items) {
            if (x.isUnanswered()) {
                unanswered++;
            }
        }
        List<QnaDto> found = qnaService.search(items, q);

        model.addAttribute("me", me);
        model.addAttribute("bid", current);
        model.addAttribute("tab", tk);
        model.addAttribute("q", q);
        model.addAttribute("canDeleteAll", accessChecker.isLeadTeamLeader(pbancSn, user));
        model.addAttribute("expCnt", expected.size());
        model.addAttribute("prsCnt", presented.size());
        model.addAttribute("itemCnt", items.size());
        model.addAttribute("foundCnt", found.size());
        model.addAttribute("unansweredCnt", unanswered);
        model.addAttribute("groups", qnaService.group(pbancSn, found));
        model.addAttribute("rqts", qnaService.rqtOptions(pbancSn));
        if (edit != null) {
            model.addAttribute("editQna", qnaService.findForEdit(pbancSn, edit));
        }

        // 이전 사업 Q&A 보기 (읽기 전용, FR-076-1)
        List<QnaBidDto> pastBids = reader ? qnaService.allPastBids(pbancSn) : qnaService.pastBids(me, pbancSn);
        model.addAttribute("pastBids", pastBids);
        if (past != null) {
            accessChecker.requireQnaReader(past, user);
            for (QnaBidDto pb : pastBids) {
                if (pb.getPbancSn().equals(past)) {
                    model.addAttribute("pastBid", pb);
                    model.addAttribute("pastGroups", qnaService.group(past, qnaService.list(past)));
                }
            }
        }
        return "proposal/qa";
    }

    @PostMapping("/qa/add")
    public String add(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                      @RequestParam(value = "tab", defaultValue = TAB_EXPECTED) String tab,
                      QnaDto in, RedirectAttributes ra) {
        in.setQstnAnsSeCd(TAB_PRESENTED.equals(tab) ? QnaDto.SE_PRESENTED : QnaDto.SE_EXPECTED);
        return run(user, bid, tab, ra, () -> {
            qnaService.register(bid, in, user.getEmpNo());
            return TAB_PRESENTED.equals(tab) ? "발표 Q&A를 기록했습니다." : "예상 Q&A를 등록했습니다.";
        });
    }

    @PostMapping("/qa/edit")
    public String edit(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                       @RequestParam(value = "tab", defaultValue = TAB_EXPECTED) String tab,
                       @RequestParam("qstnAnsSn") Long qstnAnsSn, QnaDto in, RedirectAttributes ra) {
        return run(user, bid, tab, ra, () -> {
            qnaService.edit(bid, qstnAnsSn, in);
            return "질의응답을 수정했습니다.";
        });
    }

    @PostMapping("/qa/delete")
    public String delete(@AuthenticationPrincipal LoginUser user, @RequestParam("bid") Long bid,
                         @RequestParam(value = "tab", defaultValue = TAB_EXPECTED) String tab,
                         @RequestParam("qstnAnsSn") Long qstnAnsSn, RedirectAttributes ra) {
        return run(user, bid, tab, ra, () -> {
            qnaService.delete(bid, qstnAnsSn, user.getEmpNo(), accessChecker.isLeadTeamLeader(bid, user));
            return "질의응답을 삭제했습니다.";
        });
    }

    /** 제안팀원 확인(아니면 403) 후 사업건을 열고 처리한다. 안내 띠·redirect는 FlashRedirect가 맡는다. */
    private String run(LoginUser user, Long bid, String tab, RedirectAttributes ra, Supplier<String> action) {
        accessChecker.requireProposalMember(bid, user);
        String tk = TAB_PRESENTED.equals(tab) ? TAB_PRESENTED : TAB_EXPECTED;
        return FlashRedirect.run(ra, PATH, bid, tk, () -> {
            proposalService.openBid(bid, user.getEmpNo());
            return action.get();
        });
    }
}
