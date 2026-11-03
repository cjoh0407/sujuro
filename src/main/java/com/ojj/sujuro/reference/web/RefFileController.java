package com.ojj.sujuro.reference.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.reference.dto.RefFileDto;
import com.ojj.sujuro.reference.service.RefFileService;

import lombok.RequiredArgsConstructor;

/**
 * 참고자료 화면. 팀장·팀원만 들어오고(SecurityConfig), 자기 소속 팀의 자료만 보고 올린다.
 * 팀은 요청으로 받지 않고 서버가 로그인 사용자로 정한다. 다른 팀 자료의 번호를 넣어도 열리지 않는다.
 *   보기·내려받기·올리기 — 팀장·팀원
 *   삭제                 — 올린 사람 본인 또는 팀장 (목록에서만 숨긴다)
 */
@Controller
@RequiredArgsConstructor
public class RefFileController {

    private static final String PATH = "/refs";

    private final RefFileService service;
    private final FileStorage storage;

    @GetMapping("/refs")
    public String index(@AuthenticationPrincipal LoginUser user,
                        @RequestParam(value = "se", required = false) String se,
                        @RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", defaultValue = "1") int page, Model model) {
        Long teamSn = service.requireTeam(user);
        List<RefFileDto> all = service.all(teamSn);
        model.addAttribute("teamNm", user.getTeam());
        List<RefFileDto> filtered = RefFileService.filter(all, se, q);
        int pageNo = RefFileService.clampPage(page, filtered.size());
        model.addAttribute("files", RefFileService.pageOf(filtered, pageNo));
        model.addAttribute("shown", filtered.size());
        model.addAttribute("page", pageNo);
        model.addAttribute("totalPages", RefFileService.totalPages(filtered.size()));
        model.addAttribute("counts", RefFileService.counts(all));
        model.addAttribute("total", all.size());
        model.addAttribute("se", se == null ? "" : se);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("me", user.getEmpNo());
        model.addAttribute("leader", user.getRole() == Role.LEADER);
        return "reference/refs";
    }

    @PostMapping("/refs/upload")
    public String upload(@AuthenticationPrincipal LoginUser user,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         @RequestParam(value = "memo", required = false) String memo,
                         @RequestParam(value = "se", required = false) String se, RedirectAttributes ra) {
        Long teamSn = service.requireTeam(user);
        try {
            service.upload(teamSn, file, memo, user.getEmpNo());
            ra.addFlashAttribute("done", "자료를 올렸습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return back(ra, se);
    }

    @PostMapping("/refs/delete")
    public String delete(@AuthenticationPrincipal LoginUser user, @RequestParam("refFileSn") Long refFileSn,
                         @RequestParam(value = "se", required = false) String se, RedirectAttributes ra) {
        Long teamSn = service.requireTeam(user);
        try {
            service.delete(teamSn, refFileSn, user.getEmpNo(), user.getRole() == Role.LEADER);
            ra.addFlashAttribute("done", "자료를 삭제했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return back(ra, se);
    }

    /** 내려받기 */
    @GetMapping("/refs/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal LoginUser user,
                                             @RequestParam("refFileSn") Long refFileSn) {
        RefFileDto f = service.open(service.requireTeam(user), refFileSn);
        Resource body = storage.load(f.getFilePathNm(), f.getStrgFileNm());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getOrgnlFileNm(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /** 목록의 작은 그림. 그림 확장자(png·jpg·gif·webp·bmp)만 바로 열고 나머지는 열지 않는다 */
    @GetMapping("/refs/view")
    public ResponseEntity<Resource> view(@AuthenticationPrincipal LoginUser user,
                                         @RequestParam("refFileSn") Long refFileSn) {
        RefFileDto f = service.open(service.requireTeam(user), refFileSn);
        if (!RefFileService.isPreviewExt(f.getFileExtnNm())) {
            throw new BusinessException("미리 볼 수 없는 파일입니다.");
        }
        Resource body = storage.load(f.getFilePathNm(), f.getStrgFileNm());
        MediaType type = MediaTypeFactory.getMediaType("x." + f.getFileExtnNm()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .contentType(type)
                .body(body);
    }

    private static String back(RedirectAttributes ra, String se) {
        if (se != null && !se.isEmpty()) {
            ra.addAttribute("se", se);
        }
        return "redirect:" + PATH;
    }
}
