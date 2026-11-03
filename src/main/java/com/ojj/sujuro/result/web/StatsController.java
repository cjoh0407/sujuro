package com.ojj.sujuro.result.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsFileDTO;
import com.ojj.sujuro.result.service.StatsService;

/**
 * 통계 (FR-083~088)와 사업별 결과 분석 (FR-080, 081, 087).
 *
 *   /stats                                    통계 (team: 경영지원실이 고른 팀, year: 네 자리 연도,
 *                                             bid: 왼쪽 드롭다운에서 고른 사업. 있으면 오른쪽에 그 사업의 결과 분석)
 *   /stats/export                             CSV 내려받기
 *   /stats/bids/{pbancSn}                     예전 사업별 결과 분석 주소. /stats?bid= 로 보낸다 (11/03 통계 2:8 화면)
 *   /stats/bids/{pbancSn}/final-file          그 사업의 최종 제안서 내려받기
 *   /stats/bids/{pbancSn}/evaluation-file     평가표 엑셀 내려받기
 *
 * 경영지원실·팀장만 들어온다 (SecurityConfig 의 /stats/** 규칙). 볼 수 있는 팀 범위는 StatsService 가 정한다.
 * 모두 읽기 화면이다. 결과·평가표·장단점 입력은 평가·결과 입력(ResultController, /results)에서 한다.
 */
@Controller
@RequestMapping("/stats")
public class StatsController {

    private static final String BOM = "﻿";

    private final StatsService statsService;
    private final FileStorage storage;

    public StatsController(StatsService statsService, FileStorage storage) {
        this.statsService = statsService;
        this.storage = storage;
    }

    @GetMapping
    public String view(@AuthenticationPrincipal LoginUser user,
                       @RequestParam(required = false) Long team,
                       @RequestParam(required = false) String year,
                       @RequestParam(required = false) Long bid,
                       Model model) {
        if (bid != null) {
            StatsArchiveDTO archive = statsService.findArchive(user, bid);
            if (archive == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결과가 등록된 사업을 찾을 수 없습니다.");
            }
            team = archive.getTeamSn(); // 왼쪽 드롭다운은 고른 사업의 팀 사업으로 채운다
            model.addAttribute("archive", archive);
            // 이 화면은 모두 읽기만 한다. 평가·결과 입력으로 가는 버튼을 보여 준다 (팀장은 자기 팀 사업만 여기 온다)
            model.addAttribute("canEdit", user.getRole() == Role.MGMT || user.getRole() == Role.LEADER);
        }
        model.addAttribute("view", statsService.build(user, team, year));
        return "stats/index";
    }

    /** CSV (FR-088). UTF-8 + BOM 이라 엑셀에서 바로 열린다. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal LoginUser user) {
        String name = "통계_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body((BOM + statsService.csv(user)).getBytes(StandardCharsets.UTF_8));
    }

    /** 예전 주소 (평가·결과 입력의 [사업별 결과 분석] 버튼 등). 통계 화면 오른쪽에 그 사업을 띄운다. 권한·404 는 view 가 본다 */
    @GetMapping("/bids/{pbancSn}")
    public String archive(@PathVariable Long pbancSn, RedirectAttributes ra) {
        ra.addAttribute("bid", pbancSn);
        return "redirect:/stats";
    }

    @GetMapping("/bids/{pbancSn}/final-file")
    public ResponseEntity<Resource> finalFile(@AuthenticationPrincipal LoginUser user, @PathVariable Long pbancSn) {
        StatsFileDTO f = statsService.findFinalFile(user, pbancSn);
        if (f == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보관된 제출 파일이 없습니다.");
        }
        return download(f.getOrgnlFileNm(), storage.load(f.getFilePathNm(), f.getStrgFileNm()));
    }

    @GetMapping("/bids/{pbancSn}/evaluation-file")
    public ResponseEntity<Resource> evaluationFile(@AuthenticationPrincipal LoginUser user, @PathVariable Long pbancSn) {
        EvaluationFileDTO f = statsService.findEvaluationFile(user, pbancSn);
        if (f == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "올린 평가표가 없습니다.");
        }
        return download(f.getOrgnlFileNm(), storage.load(f.getFilePathNm(), f.getStrgFileNm()));
    }

    private static ResponseEntity<Resource> download(String name, Resource body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
