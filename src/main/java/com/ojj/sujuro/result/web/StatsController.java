package com.ojj.sujuro.result.web;

import java.nio.charset.StandardCharsets;

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

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsViewDTO;
import com.ojj.sujuro.result.service.StatsService;

/**
 * 사업 이력 관리 (11/05 통계를 지우고 새로 만든 탭)와 사업별 결과 분석 (FR-080, 081, 087).
 *
 *   /bid-history                                    화면 (team: 경영지원실이 고른 팀, year: 네 자리 연도,
 *                                                   bid: 왼쪽 목록에서 고른 사업. 없으면 목록 맨 위 사업.
 *                                                   오른쪽에 그 사업의 결과 분석, 아직 결과가 없으면 공고 기본 정보와 안내 문구)
 *   /bid-history/bids/{pbancSn}/evaluation-file     평가표 엑셀 내려받기
 *
 * 경영지원실·팀장만 들어온다 (SecurityConfig 의 /bid-history/** 규칙). 볼 수 있는 팀 범위는 StatsService 가 정한다.
 * 모두 읽기 화면이다. 결과·평가표·장단점 입력은 평가·결과 입력(ResultController, /results)에서 한다.
 */
@Controller
@RequestMapping("/bid-history")
public class StatsController {

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
            StatsArchiveDTO archive = archiveOf(user, bid);
            if (archive == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사업을 찾을 수 없습니다.");
            }
            // 왼쪽 목록은 고른 사업의 팀·연도 사업으로 채운다. 다른 화면에서 바로 와도 고른 사업이 목록에 있다
            team = archive.getTeamSn();
            if (archive.getListYear() != null) {
                year = archive.getListYear();
            }
            addArchive(model, user, archive);
        }
        StatsViewDTO view = statsService.build(user, team, year);
        model.addAttribute("view", view);
        // 사업을 고르지 않았으면 제안 화면처럼 목록 맨 위 사업을 보여 준다 (연도·팀을 바꿨을 때도 같다)
        if (bid == null && view.getFirstPbancSn() != null) {
            addArchive(model, user, archiveOf(user, view.getFirstPbancSn()));
        }
        return "stats/index";
    }

    /** 결과가 있으면 결과 분석, 아직 없으면 공고 기본 정보. 볼 수 없는 사업이면 null */
    private StatsArchiveDTO archiveOf(LoginUser user, Long pbancSn) {
        StatsArchiveDTO archive = statsService.findArchive(user, pbancSn);
        return archive != null ? archive : statsService.findPending(user, pbancSn);
    }

    private static void addArchive(Model model, LoginUser user, StatsArchiveDTO archive) {
        model.addAttribute("archive", archive);
        // 이 화면은 모두 읽기만 한다. 평가·결과 입력으로 가는 버튼을 보여 준다 (팀장은 자기 팀 사업만 여기 온다)
        model.addAttribute("canEdit", user.getRole() == Role.MGMT || user.getRole() == Role.LEADER);
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
