package com.ojj.sujuro.reference.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.reference.dto.RefFileDto;
import com.ojj.sujuro.reference.mapper.RefFileMapper;

import lombok.RequiredArgsConstructor;

/**
 * 참고자료: 제안서를 쓸 때 쓰는 폰트·이미지·공유 자료를 같은 팀끼리 나눈다.
 * 보이는 범위는 로그인한 사람의 소속 팀 하나뿐이다. 팀은 화면에서 받지 않고 서버가 사용자 정보로 정한다.
 * (다른 팀의 자료 번호를 넣어 봐도 "찾을 수 없다"만 돌려준다.)
 * 삭제는 올린 사람과 팀장만 할 수 있고, 목록에서만 숨긴다.
 */
@Service
@RequiredArgsConstructor
public class RefFileService {

    private static final Set<String> FONT_EXT = new HashSet<>(Arrays.asList("ttf", "otf", "woff", "woff2", "ttc"));
    private static final Set<String> IMAGE_EXT = new HashSet<>(Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg"));
    private static final Set<String> DOC_EXT = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp", "hwpx", "txt", "csv", "md"));
    /** 목록에서 작은 그림으로 보여도 되는 확장자 */
    private static final Set<String> PREVIEW_EXT = new HashSet<>(Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp"));

    private static final int MEMO_MAX = 500;

    private final RefFileMapper mapper;
    private final FileStorage storage;

    /** 확장자로 자료구분을 정한다. 모르는 확장자는 기타 */
    public static String categoryOf(String ext) {
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (FONT_EXT.contains(e)) {
            return RefFileDto.SE_FONT;
        }
        if (IMAGE_EXT.contains(e)) {
            return RefFileDto.SE_IMAGE;
        }
        if (DOC_EXT.contains(e)) {
            return RefFileDto.SE_DOC;
        }
        return RefFileDto.SE_ETC;
    }

    /** 그림으로 바로 열어 봐도 되는 확장자인가 */
    public static boolean isPreviewExt(String ext) {
        return ext != null && PREVIEW_EXT.contains(ext.toLowerCase(Locale.ROOT));
    }

    /** 삭제할 수 있는 사람: 올린 사람 본인 또는 팀장 */
    public static boolean canDelete(RefFileDto f, String userId, boolean leader) {
        return f != null && userId != null && (leader || userId.equals(f.getRgtrId()));
    }

    /** 로그인한 사용자의 소속 팀. 팀이 없으면 접근 거부(403) */
    public Long requireTeam(LoginUser user) {
        Long teamSn = user == null ? null : mapper.findTeamSn(user.getEmpNo());
        if (teamSn == null) {
            throw new AccessDeniedException("소속 팀이 없어 참고자료를 볼 수 없습니다.");
        }
        return teamSn;
    }

    /** 팀의 자료 전체 (삭제하지 않은 것, 최근 순) */
    public List<RefFileDto> all(Long teamSn) {
        return mapper.findByTeam(teamSn);
    }

    /** 구분(RF010~RF040, 비우면 전체)과 파일명·메모 검색어로 거른다 */
    public static List<RefFileDto> filter(List<RefFileDto> all, String se, String keyword) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<RefFileDto> out = new ArrayList<>();
        for (RefFileDto f : all) {
            if (se != null && !se.isEmpty() && !se.equals(f.getRefSeCd())) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = ((f.getOrgnlFileNm() == null ? "" : f.getOrgnlFileNm()) + " "
                        + (f.getMemoCn() == null ? "" : f.getMemoCn())).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) {
                    continue;
                }
            }
            out.add(f);
        }
        return out;
    }

    /** 한 페이지에 보여 줄 자료 수 */
    public static final int PAGE_SIZE = 10;

    /** 전체 페이지 수 (자료가 없어도 1) */
    public static int totalPages(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** 페이지 번호를 1 ~ 마지막 페이지 안으로 맞춘다 (잘못된 번호는 가까운 쪽으로) */
    public static int clampPage(int page, int total) {
        return Math.min(Math.max(1, page), totalPages(total));
    }

    /** 해당 페이지의 자료만 잘라 낸다 */
    public static <T> List<T> pageOf(List<T> list, int page) {
        int p = clampPage(page, list.size());
        int from = (p - 1) * PAGE_SIZE;
        return new ArrayList<>(list.subList(from, Math.min(list.size(), from + PAGE_SIZE)));
    }

    /** 구분별 건수 (탭에 보여 준다). 순서: 폰트·이미지·문서·기타 */
    public static Map<String, Integer> counts(List<RefFileDto> all) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(RefFileDto.SE_FONT, 0);
        m.put(RefFileDto.SE_IMAGE, 0);
        m.put(RefFileDto.SE_DOC, 0);
        m.put(RefFileDto.SE_ETC, 0);
        for (RefFileDto f : all) {
            m.merge(f.getRefSeCd(), 1, Integer::sum);
        }
        return m;
    }

    /** 올리기. 용량·실행 파일 검사는 FileStorage 와 같다 (파일당 50MB) */
    public void upload(Long teamSn, MultipartFile file, String memo, String userId) {
        String name = FileStorage.checkUpload(file);
        String ext = FileStorage.extension(name);
        RefFileDto f = new RefFileDto();
        f.setTeamSn(teamSn);
        f.setRefSeCd(categoryOf(ext));
        f.setOrgnlFileNm(name);
        f.setFileSz(file.getSize());
        f.setFileExtnNm(FileStorage.extColumn(ext));
        String m = memo == null ? "" : memo.trim();
        f.setMemoCn(m.isEmpty() ? null : (m.length() > MEMO_MAX ? m.substring(0, MEMO_MAX) : m));
        f.setRgtrId(userId);
        f.setFilePathNm("reference/" + teamSn);
        String stored = storage.save(file, f.getFilePathNm(), ext);
        f.setStrgFileNm(stored);
        try {
            mapper.insert(f);
        } catch (RuntimeException e) {
            storage.deleteQuietly(f.getFilePathNm(), stored);
            throw e;
        }
    }

    /** 내 팀의 자료 한 건. 없거나 삭제됐거나 다른 팀 것이면 같은 안내를 한다 */
    public RefFileDto open(Long teamSn, Long refFileSn) {
        RefFileDto f = refFileSn == null ? null : mapper.findOne(refFileSn);
        if (f == null || teamSn == null || !teamSn.equals(f.getTeamSn())) {
            throw new BusinessException("자료를 찾을 수 없습니다.");
        }
        return f;
    }

    /** 삭제(숨김). 올린 사람과 팀장만 */
    public void delete(Long teamSn, Long refFileSn, String userId, boolean leader) {
        RefFileDto f = open(teamSn, refFileSn);
        if (!canDelete(f, userId, leader)) {
            throw new BusinessException("올린 사람과 팀장만 삭제할 수 있습니다.");
        }
        mapper.markDeleted(refFileSn, userId);
    }
}
