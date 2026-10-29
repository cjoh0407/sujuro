package com.ojj.sujuro.result.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.ResultsViewDTO;
import com.ojj.sujuro.result.dto.StatsAnalysisForm;
import com.ojj.sujuro.result.service.ResultEntryService;

/**
 * 평가·결과 입력 (FR-077~081). 경영지원실 전용 (SecurityConfig 의 /results/** 규칙).
 * UI 목업 v3.5 의 rResults() 화면과 같은 구성이다.
 *
 *   /results?bid=                  화면 (bid: 사업건. 없으면 제출 완료가 먼저)
 *   POST /results/import           나라장터에서 결과 가져오기 (제출 완료 사업)
 *   POST /results/evaluation-file  평가표 엑셀 올리기 (내려받기는 /stats/bids/{bid}/evaluation-file)
 *   POST /results/evaluation       평가 항목·배점·위원별 점수 입력
 *   POST /results/analysis         장단점 분석 입력
 *
 * 저장하면 같은 사업건 화면으로 돌아가고, 결과는 안내 띠(resultMessage·resultError)로 보여 준다.
 */
@Controller
@RequestMapping("/results")
@PreAuthorize("hasRole('MGMT')")
public class ResultController {

    private static final String PATH = "/results";

    private final ResultEntryService resultEntryService;

    public ResultController(ResultEntryService resultEntryService) {
        this.resultEntryService = resultEntryService;
    }

    @GetMapping
    public String view(@AuthenticationPrincipal LoginUser user,
                       @RequestParam(required = false) Long bid, Model model) {
        ResultsViewDTO view = resultEntryService.build(user, bid);
        model.addAttribute("view", view);
        if (!model.containsAttribute("evaluationForm") && view.getArchive() != null) {
            model.addAttribute("evaluationForm", view.getArchive().getEvaluationForm());
        }
        return "results/index";
    }

    @PostMapping("/import")
    public String importResult(@AuthenticationPrincipal LoginUser user, @RequestParam Long bid, RedirectAttributes ra) {
        try {
            ra.addFlashAttribute("resultMessage", resultEntryService.importResult(user, bid));
        } catch (BusinessException e) {
            ra.addFlashAttribute("resultError", e.getMessage());
        }
        return back(ra, bid);
    }

    @PostMapping("/evaluation-file")
    public String uploadEvaluationFile(@AuthenticationPrincipal LoginUser user, @RequestParam Long bid,
                                       @RequestParam(required = false) MultipartFile file, RedirectAttributes ra) {
        try {
            resultEntryService.uploadEvaluationFile(user, bid, file);
            ra.addFlashAttribute("evaluationMessage", "평가표 엑셀을 올렸습니다. 엑셀을 보며 아래에 항목과 점수를 입력하세요.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("evaluationError", e.getMessage());
        }
        return back(ra, bid);
    }

    @PostMapping("/evaluation")
    public String saveEvaluation(@AuthenticationPrincipal LoginUser user, @RequestParam Long bid,
                                 EvaluationForm form, RedirectAttributes ra) {
        try {
            resultEntryService.saveEvaluation(user, bid, form);
            ra.addFlashAttribute("evaluationMessage", "평가 항목과 점수를 저장했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("evaluationError", e.getMessage());
            ra.addFlashAttribute("evaluationForm", form); // 입력한 값을 다시 채워 준다
        }
        return back(ra, bid);
    }

    @PostMapping("/analysis")
    public String saveAnalysis(@AuthenticationPrincipal LoginUser user, @RequestParam Long bid,
                               StatsAnalysisForm form, RedirectAttributes ra) {
        try {
            resultEntryService.saveAnalysis(user, bid, form);
            ra.addFlashAttribute("analysisMessage", "장단점 분석을 저장했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("analysisError", e.getMessage());
            ra.addFlashAttribute("analysisForm", form);
        }
        return back(ra, bid);
    }

    private static String back(RedirectAttributes ra, Long bid) {
        ra.addAttribute("bid", bid);
        return "redirect:" + PATH;
    }
}
