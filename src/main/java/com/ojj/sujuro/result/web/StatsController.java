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

import com.ojj.sujuro.auth.LoginUser;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsFileDTO;
import com.ojj.sujuro.result.service.StatsService;

/**
 * 통계 (FR-083~088)와 사업별 결과 분석 (FR-087).
 *
 *   /stats                              통계 (team: 경영지원실이 고른 팀, year: 네 자리 연도)
 *   /stats/export                       CSV 내려받기
 *   /stats/bids/{pbancSn}               사업별 결과 분석
 *   /stats/bids/{pbancSn}/final-file    그 사업의 최종 제안서 내려받기
 *
 * 경영지원실·팀장만 들어온다 (SecurityConfig 의 /stats/** 규칙). 볼 수 있는 팀 범위는 StatsService 가 정한다.
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
                       Model model) {
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

    @GetMapping("/bids/{pbancSn}")
    public String archive(@AuthenticationPrincipal LoginUser user, @PathVariable Long pbancSn, Model model) {
        StatsArchiveDTO archive = statsService.findArchive(user, pbancSn);
        if (archive == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "결과가 등록된 사업을 찾을 수 없습니다.");
        }
        model.addAttribute("archive", archive);
        return "stats/archive";
    }

    @GetMapping("/bids/{pbancSn}/final-file")
    public ResponseEntity<Resource> finalFile(@AuthenticationPrincipal LoginUser user, @PathVariable Long pbancSn) {
        StatsFileDTO f = statsService.findFinalFile(user, pbancSn);
        if (f == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "보관된 제출 파일이 없습니다.");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getOrgnlFileNm(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(storage.load(f.getFilePathNm(), f.getStrgFileNm()));
    }
}
