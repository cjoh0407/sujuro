package com.ojj.sujuro.result.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.ResultsViewDTO;
import com.ojj.sujuro.result.dto.StatsAnalysisForm;
import com.ojj.sujuro.result.service.ResultEntryService;

/**
 * 평가·결과 입력 (FR-077~081). 경영지원실·팀장 (SecurityConfig 의 /results/** 규칙). 누가 무엇을 하는지는 ResultEntryService.
 * UI 목업 v3.5 의 rResults() 화면과 같은 구성이다.
 *
 *   /results?bid=&co=&fromFile=        화면 (bid: 사업건. 없으면 제출 완료가 먼저. co: 평가표를 볼 개찰 업체, 없으면 우리 회사.
 *                                      fromFile: 올린 엑셀에서 입력 칸을 다시 채운다)
 *   POST /results/import               나라장터에서 결과 가져오기 (제출 완료 사업)
 *   POST /results/evaluation-file      평가표 엑셀 올리기 (업체별)
 *   GET  /results/evaluation-file?bid=&co=  업체의 평가표 엑셀 내려받기 (우리 회사 것은 /stats/bids/{bid}/evaluation-file 도 된다)
 *   POST /results/evaluation           평가 항목·배점·위원별 점수 저장 (업체별, 엑셀에서 읽어 채운 값을 팀장이 확인해 저장)
 *   POST /results/analysis             장단점 분석 입력
 *
 * 저장하면 같은 사업건·업체 화면으로 돌아가고, 결과는 안내 띠(resultMessage·resultError)로 보여 준다.
 */
@Controller
@RequestMapping("/results")
@PreAuthorize("hasAnyRole('MGMT', 'LEADER')")
public class ResultController {

    private static final String PATH = "/results";

    private final ResultEntryService resultEntryService;
    private final FileStorage storage;

    public ResultController(ResultEntryService resultEntryService, FileStorage storage) {
        this.resultEntryService = resultEntryService;
        this.storage = storage;
    }

    @GetMapping
    public String view(@AuthenticationPrincipal LoginUser user,
                       @RequestParam(required = false) Long bid,
                       @RequestParam(required = false) Long co,
                       @RequestParam(defaultValue = "false") boolean fromFile, Model model) {
        ResultsViewDTO view = resultEntryService.build(user, bid, co, fromFile);
        model.addAttribute("view", view);
        // 입력 칸: 저장에 실패해 돌아왔으면 입력하던 값, 아니면 엑셀에서 읽은 값, 아니면 고른 업체가 저장한 값
        if (!model.containsAttribute("evaluationForm") && view.getCompanyForm() != null) {
            model.addAttribute("evaluationForm", view.getExcelForm() != null ? view.getExcelForm() : view.getCompanyForm());
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
        return back(ra, bid, null);
    }

    @PostMapping("/evaluation-file")
    public String uploadEvaluationFile(@AuthenticationPrincipal LoginUser user, @RequestParam Long bid,
                                       @RequestParam(required = false) Long co,
                                       @RequestParam(required = false) MultipartFile file, RedirectAttributes ra) {
        try {
            resultEntryService.uploadEvaluationFile(user, bid, co, file);
            if (user.getRole() == Role.LEADER) {
                ra.addFlashAttribute("evaluationMessage", "평가표 엑셀을 올렸습니다.");
                ra.addAttribute("fromFile", true); // 이미 저장한 점수가 있어도 새 엑셀에서 다시 채운다
            } else {
                ra.addFlashAttribute("evaluationMessage",
                        "평가표 엑셀을 올렸습니다. 담당 팀 팀장이 엑셀에서 읽은 항목과 점수를 확인해 저장합니다.");
            }
        } catch (BusinessException e) {
            ra.addFlashAttribute("evaluationError", e.getMessage());
        }
        return back(ra, bid, co);
    }

    @GetMapping("/evaluation-file")
    public ResponseEntity<Resource> evaluationFile(@AuthenticationPrincipal LoginUser user, @RequestParam Long bid,
                                                   @RequestParam(required = false) Long co) {
        EvaluationFileDTO f = resultEntryService.findCompanyEvaluationFile(user, bid, co);
        if (f == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "올린 평가표가 없습니다.");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getOrgnlFileNm(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(storage.load(f.getFilePathNm(), f.getStrgFileNm()));
    }

    @PostMapping("/evaluation")
    public String saveEvaluation(@AuthenticationPrincipal LoginUser user, @RequestParam Long bid,
                                 @RequestParam(required = false) Long co, EvaluationForm form, RedirectAttributes ra) {
        try {
            resultEntryService.saveEvaluation(user, bid, co, form);
            ra.addFlashAttribute("evaluationMessage", "평가 항목과 점수를 저장했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("evaluationError", e.getMessage());
            ra.addFlashAttribute("evaluationForm", form); // 입력한 값을 다시 채워 준다
        }
        return back(ra, bid, co);
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
        return back(ra, bid, null);
    }

    private static String back(RedirectAttributes ra, Long bid, Long co) {
        ra.addAttribute("bid", bid);
        if (co != null) {
            ra.addAttribute("co", co);
        }
        return "redirect:" + PATH;
    }
}
