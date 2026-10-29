package com.ojj.sujuro.proposal.web;

import java.util.function.Supplier;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 제안 쪽 화면(/proposal, /files, /docs)의 POST 처리 공통 흐름.
 *   처리 실행 → 완료 문구는 "done", 업무 규칙 위반(BusinessException)은 "error"로 안내 띠에 담고
 *   같은 사업건(bid)·탭(tab) 화면으로 redirect 한다.
 * 권한 확인(AccessChecker)은 각 컨트롤러에서 이 메서드를 부르기 전에 한다. 권한이 없으면 그대로 403이 난다.
 */
final class FlashRedirect {

    private FlashRedirect() {
    }

    /** 처리 후 돌아가기. action은 완료 안내 문구를 돌려준다. tab이 null이면 tab 파라미터를 붙이지 않는다. */
    static String run(RedirectAttributes ra, String path, Long bid, String tab, Supplier<String> action) {
        try {
            ra.addFlashAttribute("done", action.get());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return to(ra, path, bid, tab);
    }

    /** 완료 문구가 고정일 때 */
    static String run(RedirectAttributes ra, String path, Long bid, String tab, String doneMessage, Runnable action) {
        return run(ra, path, bid, tab, () -> {
            action.run();
            return doneMessage;
        });
    }

    /** 처리 없이 돌아가기 */
    static String to(RedirectAttributes ra, String path, Long bid, String tab) {
        ra.addAttribute("bid", bid);
        if (tab != null) {
            ra.addAttribute("tab", tab);
        }
        return "redirect:" + path;
    }
}
