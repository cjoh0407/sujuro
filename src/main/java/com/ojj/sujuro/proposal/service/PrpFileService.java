package com.ojj.sujuro.proposal.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.proposal.dto.PrpFileDto;

/**
 * 제안서 파일 관리 (FR-057~066, NFR-009).
 * 사업건 접근(제안팀원)과 팀장 전용 기능은 컨트롤러가 AccessChecker로 먼저 확인한다.
 */
public interface PrpFileService {

    /** 목차별 파일을 목차일련번호로 묶는다. 목록 안은 최신 버전이 먼저 (FR-061) */
    Map<Long, List<PrpFileDto>> tocFiles(Long pbancSn, String fileSeCd);

    /** 통합본 전체 (최신 차수 먼저) */
    List<PrpFileDto> fullFiles(Long pbancSn);

    /**
     * 목차별 파일을 올린다 (제안서 파트 FL020 / 제안요약서 FL040). 같은 목차에 다시 올리면 새 버전이 된다. (FR-058, FR-059, FR-063)
     * 최하위 목차에만 올릴 수 있고, 주관팀 팀장(lead) 또는 그 목차 담당자만 올린다.
     * 검토 완료였던 목차에 새 버전을 올리면 다시 "작성 중"이 된다. 붙은 버전 번호를 돌려준다.
     */
    String uploadToc(Long pbancSn, String fileSeCd, Long tocSn, MultipartFile file, String userId, boolean lead);

    /** 통합본을 올린다. 1차, 2차처럼 차수가 늘어난다. (FR-060) 붙은 차수를 돌려준다. */
    int uploadFull(Long pbancSn, MultipartFile file, String userId);

    /** 목차 진행 상태: 검토 완료로 표시하거나 되돌린다. 파일이 없는 목차는 검토 완료로 할 수 없다. (FR-066) */
    void markReviewed(Long pbancSn, Long tocSn, boolean done);

    /** 통합본 하나를 최종본으로 확정한다. 이전 최종본 표시는 풀린다. (FR-064) */
    void confirmFinal(Long pbancSn, Long fileSn, String userId);

    /** 삭제(숨김)된 파일 목록 (해당 구분, 최근 삭제 먼저). "삭제된 파일 보기"에 쓴다. */
    List<PrpFileDto> deletedFiles(Long pbancSn, String fileSeCd);

    /**
     * 파일을 삭제한다. 실제로 지우지 않고 목록에서만 숨기며 원본·삭제자·일시·사유를 남긴다. (NFR-009)
     * 올린 사람과 주관팀 팀장(lead)만 할 수 있다. 최종본, 검토 완료 목차의 파일, 나라장터 제출 후 파일은 지울 수 없다.
     * 사유는 반드시 입력한다.
     */
    void deleteFile(Long pbancSn, Long fileSn, String reason, String userId, boolean lead);

    /** 이 파일을 지금 사용자가 지울 수 있는지 (화면 버튼 노출용, 규칙은 deleteFile과 같다) */
    boolean canDelete(PrpFileDto f, String tocProgress, boolean submitted, String userId, boolean lead);

    /** 사업건이 나라장터 제출(제출 완료·결과 등록) 상태인지 */
    boolean isSubmitted(Long pbancSn);

    /** 내려받을 파일 정보. 이 사업건의 제안서 파일이 아니면 예외 (FR-062) */
    PrpFileDto findForDownload(Long pbancSn, Long fileSn);

    /** 올릴 수 있는 형식 PDF / PPT(ppt·pptx). 그 밖의 형식은 빈 문자열 */
    static String formatOf(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        String ext = i < 0 ? "" : name.substring(i + 1).trim().toLowerCase(java.util.Locale.ROOT);
        return "pdf".equals(ext) ? "PDF" : ("ppt".equals(ext) || "pptx".equals(ext)) ? "PPT" : "";
    }

    /** 확장자를 뺀 이름 (앞뒤 공백 제거, 대소문자 무시용 소문자) */
    static String baseName(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        return (i < 0 ? name : name.substring(0, i)).trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 한 버전(통합본은 차수)에 묶이는 파일의 키 */
    static int versionKey(PrpFileDto f) {
        try {
            return f.getIntgCycl() != null ? f.getIntgCycl() : Integer.parseInt(f.getVerNo().trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** 같은 버전(통합본은 차수)의 PDF·PPT를 한 묶음으로, 최신 버전 먼저, 묶음 안은 PDF → PPT 순 */
    static List<List<PrpFileDto>> groupVersions(List<?> files) { // 화면(SpEL)이 부르므로 제네릭 변환을 피해 List<?>로 받는다
        Map<Integer, List<PrpFileDto>> m = new LinkedHashMap<>();
        for (Object o : files) {
            PrpFileDto f = (PrpFileDto) o;
            m.computeIfAbsent(versionKey(f), k -> new ArrayList<>()).add(f);
        }
        List<List<PrpFileDto>> out = new ArrayList<>();
        m.entrySet().stream().sorted((a, b) -> Integer.compare(b.getKey(), a.getKey())).forEach(e -> {
            List<PrpFileDto> g = e.getValue();
            g.sort(Comparator.comparing((PrpFileDto x) -> x.getFmt().isEmpty() ? "Z" : x.getFmt()).thenComparing(PrpFileDto::getFileSn));
            out.add(g);
        });
        return out;
    }

    /** 목록을 목차일련번호별로 묶는 도우미 */
    static Map<Long, List<PrpFileDto>> groupByToc(List<PrpFileDto> files) {
        Map<Long, List<PrpFileDto>> m = new LinkedHashMap<>();
        for (PrpFileDto f : files) {
            m.computeIfAbsent(f.getTocSn(), k -> new java.util.ArrayList<>()).add(f);
        }
        return m;
    }
}
