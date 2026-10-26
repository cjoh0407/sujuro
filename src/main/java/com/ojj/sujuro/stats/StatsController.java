package com.ojj.sujuro.stats;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ojj.sujuro.auth.LoginUser;

/**
 * 통계 (FR-083 ~ 088). 주소: /stats , /stats/export
 * 경영지원실·팀장만 들어온다 (SecurityConfig 의 /stats/** 규칙). 범위는 {@link StatsService} 가 정한다.
 */
@Controller
public class StatsController {

    private static final String BOM = "﻿";
    private static final String[][] FILE_NAMES = {
        {"yearly", "연도별"}, {"team", "팀별"}, {"type", "사업유형별"}, {"shape", "참여형태별"},
        {"article", "평가항목별"}, {"history", "과거사업이력"}
    };

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public String view(@AuthenticationPrincipal LoginUser user,
                       @RequestParam(value = "year", required = false) String year,
                       @RequestParam(value = "team", required = false) Long team,
                       Model model) {
        model.addAttribute("view", service.build(user, year, team));
        return "stats/index";
    }

    /** CSV 내려받기 (FR-088). UTF-8 + BOM 이라 엑셀에서 바로 열린다. */
    @GetMapping("/stats/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal LoginUser user,
                                         @RequestParam("section") String section,
                                         @RequestParam(value = "year", required = false) String year,
                                         @RequestParam(value = "team", required = false) Long team) {
        String body = service.csv(user, section, year, team);
        String name = "통계_" + label(section) + (year == null ? "" : "_" + year.replaceAll("[^0-9]", "")) + ".csv";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        h.setContentDisposition(ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(h).body((BOM + body).getBytes(StandardCharsets.UTF_8));
    }

    private static String label(String section) {
        for (String[] f : FILE_NAMES) {
            if (f[0].equals(section)) {
                return f[1];
            }
        }
        return section;
    }
}
