package com.ojj.sujuro.result.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.result.dto.EvaluationArticleDTO;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.ResultBidDTO;
import com.ojj.sujuro.result.dto.ResultOutcomeDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;
import com.ojj.sujuro.result.dto.ResultsViewDTO;
import com.ojj.sujuro.result.dto.StatsAnalysisForm;
import com.ojj.sujuro.result.mapper.ResultMapper;
import com.ojj.sujuro.result.mapper.StatsMapper;
import com.ojj.sujuro.result.service.ResultCollectService;
import com.ojj.sujuro.result.service.ResultEntryService;
import com.ojj.sujuro.result.service.StatsService;
import com.ojj.sujuro.result.support.StatsFormat;

/** 평가·결과 입력 (경영지원실). 규칙은 인터페이스(ResultEntryService)에 있다. */
@Service
@Transactional
public class ResultEntryServiceImpl implements ResultEntryService {

    /** 장단점 한 칸의 DB 한도. 컬럼은 VARCHAR2(4000 CHAR) 지만 DB 한도가 4000바이트다(한글 한 글자 3바이트) */
    private static final int NOTE_MAX_BYTES = 4000;

    /** 점수·배점 컬럼이 NUMBER(10,3) */
    private static final int SCORE_SCALE = 3;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    static final int MAX_CMSNR = 20;
    static final int MAX_ITEMS = 30;
    private static final int NAME_MAX = 100;

    private static final Set<String> EXCEL_EXT = new HashSet<>(Arrays.asList("xls", "xlsx"));

    /** 항목 이름 끝의 배점 표시. 예: "전략 및 방법론(15)" */
    private static final Pattern MAX_SUFFIX = Pattern.compile("\\s*\\(\\s*\\d+(\\.\\d+)?\\s*\\)\\s*$");

    private final ResultMapper resultMapper;
    private final StatsMapper statsMapper;
    private final StatsService statsService;
    private final ResultCollectService resultCollectService;
    private final FileStorage storage;

    public ResultEntryServiceImpl(ResultMapper resultMapper, StatsMapper statsMapper, StatsService statsService,
                                  ResultCollectService resultCollectService, FileStorage storage) {
        this.resultMapper = resultMapper;
        this.statsMapper = statsMapper;
        this.statsService = statsService;
        this.resultCollectService = resultCollectService;
        this.storage = storage;
    }

    @Override
    @Transactional(readOnly = true)
    public ResultsViewDTO build(LoginUser user, Long bid) {
        requireMgmt(user);
        ResultsViewDTO v = new ResultsViewDTO();
        v.setReady(resultCollectService.isReady());
        List<ResultBidDTO> bids = resultMapper.findResultBids();
        v.setBids(bids);
        if (bids.isEmpty()) {
            return v;
        }
        Long sn = bids.stream().anyMatch(b -> b.getPbancSn().equals(bid)) ? bid : bids.get(0).getPbancSn();
        ResultBidDTO picked = resultMapper.findResultBid(sn);
        v.setBid(picked);
        if (picked.isResulted()) {
            v.setArchive(statsService.findArchive(user, sn)); // 개발팀 사업이 아니거나 결과 행이 없으면 null
            v.setCompanies(resultMapper.findCompanies(sn));
        }
        return v;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 나라장터를 부르는 동안 DB 연결을 잡지 않는다
    public String importResult(LoginUser user, Long pbancSn) {
        requireMgmt(user);
        ResultTargetDTO target = resultMapper.findResultTarget(pbancSn);
        if (target == null) {
            throw new BusinessException("제출 완료 상태인 사업만 나라장터에서 결과를 가져올 수 있습니다.");
        }
        if (!resultCollectService.isReady()) {
            throw new BusinessException("우리 회사 사업자번호(company.biz-no)가 설정되지 않아 결과를 가져올 수 없습니다.");
        }
        ResultOutcomeDTO out = resultCollectService.register(target);
        if (out.getKind() == ResultOutcomeDTO.Kind.REGISTERED) {
            return out.getMessage() + ". 평가표 엑셀을 올리고 항목별 점수를 입력하세요.";
        }
        if (out.getKind() == ResultOutcomeDTO.Kind.NOT_LISTED) {
            throw new BusinessException(out.getMessage());
        }
        throw new BusinessException("아직 나라장터에 최종 낙찰자가 올라오지 않았습니다. "
                + "낙찰자가 정해지면 평일 오후 5시 수집 때 자동으로 등록됩니다.");
    }

    @Override
    public void saveAnalysis(LoginUser user, Long pbancSn, StatsAnalysisForm form) {
        requireMgmt(user);
        requireArchive(pbancSn);
        resultMapper.updateAnalysis(pbancSn,
                note(form.getStrptCn(), "강점"),
                note(form.getSbptCsCn(), "탈락·감점 원인"),
                note(form.getSplmntCn(), "차기 보완점"),
                user.getEmpNo());
    }

    @Override
    public void uploadEvaluationFile(LoginUser user, Long pbancSn, MultipartFile file) {
        requireMgmt(user);
        requireArchive(pbancSn);
        Long coSn = requireOurCompany(pbancSn);
        String name = FileStorage.checkUpload(file);
        String ext = FileStorage.extension(name);
        if (!EXCEL_EXT.contains(ext)) {
            throw new BusinessException("평가표는 나라장터에서 내려받은 엑셀 파일(xls, xlsx)만 올릴 수 있습니다.");
        }

        String subDir = "evaluation/" + pbancSn;
        String stored = storage.save(file, subDir, ext);
        try {
            EvaluationFileDTO f = new EvaluationFileDTO();
            f.setOrgnlFileNm(name);
            f.setStrgFileNm(stored);
            f.setFilePathNm(subDir);
            String ver = String.valueOf(resultMapper.nextEvaluationFileVer(pbancSn));
            resultMapper.clearLastEvaluationFiles(pbancSn);
            resultMapper.insertEvaluationFile(pbancSn, ver, f, file.getSize(), FileStorage.extColumn(ext), user.getEmpNo());
            resultMapper.clearLastUploads(coSn);
            resultMapper.insertUpload(coSn, f.getFileSn(), user.getEmpNo());
            resultMapper.markUploaded(coSn);
        } catch (RuntimeException e) {
            storage.deleteQuietly(subDir, stored);
            throw e;
        }
    }

    @Override
    public void saveEvaluation(LoginUser user, Long pbancSn, EvaluationForm form) {
        requireMgmt(user);
        requireArchive(pbancSn);
        Long coSn = requireOurCompany(pbancSn);
        if (statsMapper.findEvaluationFile(pbancSn) == null) {
            throw new BusinessException("평가표 엑셀을 먼저 올려 주세요. 올린 엑셀을 보며 항목과 점수를 입력합니다.");
        }

        int cmsnr = cmsnrCount(form.getCmsnrCnt());
        List<EvaluationForm.Row> rows = form.getFilledItems();
        if (rows.isEmpty()) {
            throw new BusinessException("평가 항목을 한 줄 이상 입력하세요.");
        }
        if (rows.size() > MAX_ITEMS) {
            throw new BusinessException("평가 항목은 " + MAX_ITEMS + "개까지 입력할 수 있습니다.");
        }

        List<EvaluationArticleDTO> articles = new ArrayList<>();
        List<List<BigDecimal>> scores = new ArrayList<>();
        Set<String> names = new HashSet<>();
        BigDecimal maxSum = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            EvaluationForm.Row r = rows.get(i);
            String line = (i + 1) + "번째 항목";
            String nm = articleName(r.getNm());
            if (nm.isEmpty()) {
                throw new BusinessException(line + "의 이름 칸이 비었습니다.");
            }
            if (nm.length() > NAME_MAX) {
                throw new BusinessException(line + "의 이름은 " + NAME_MAX + "자까지 입력할 수 있습니다.");
            }
            if (!names.add(nm)) {
                throw new BusinessException("같은 평가 항목 이름이 두 번 있습니다: " + nm);
            }
            BigDecimal max = number(r.getMax(), line + "(" + nm + ")의 배점");
            if (max.signum() <= 0 || max.compareTo(HUNDRED) > 0) {
                throw new BusinessException(line + "(" + nm + ")의 배점은 0보다 크고 100 이하로 입력하세요.");
            }
            maxSum = maxSum.add(max);

            List<BigDecimal> row = new ArrayList<>();
            for (int j = 0; j < cmsnr; j++) {
                String label = line + "(" + nm + ")의 평가위원" + (j + 1) + " 점수";
                BigDecimal s = number(r.scoreAt(j), label);
                if (s.signum() < 0 || s.compareTo(max) > 0) {
                    throw new BusinessException(label + "는 0~" + StatsFormat.score(max.doubleValue()) + "(배점) 사이로 입력하세요.");
                }
                row.add(s);
            }

            EvaluationArticleDTO a = new EvaluationArticleDTO();
            a.setPbancSn(pbancSn);
            a.setEvlArtclNm(nm);
            a.setEvlScorScr(max);
            a.setSortSeq(i + 1);
            articles.add(a);
            scores.add(row);
        }
        if (maxSum.compareTo(HUNDRED) > 0) {
            throw new BusinessException("배점 합계가 100점을 넘습니다(" + StatsFormat.score(maxSum.doubleValue()) + "점). 엑셀의 배점기준을 확인하세요.");
        }

        resultMapper.deleteScores(pbancSn);
        resultMapper.deleteSubArticles(pbancSn);
        resultMapper.deleteArticles(pbancSn);
        for (int i = 0; i < articles.size(); i++) {
            EvaluationArticleDTO a = articles.get(i);
            resultMapper.insertArticle(a);
            List<BigDecimal> row = scores.get(i);
            for (int j = 0; j < row.size(); j++) {
                resultMapper.insertScore(coSn, a.getEvlArtclSn(), j + 1, row.get(j));
            }
        }
        resultMapper.updateUploadCmsnrCnt(coSn, cmsnr);
    }

    private static void requireMgmt(LoginUser user) {
        if (user.getRole() != Role.MGMT) {
            throw new AccessDeniedException("사업별 결과 분석의 입력은 경영지원실이 합니다.");
        }
    }

    private void requireArchive(Long pbancSn) {
        if (statsMapper.findArchive(pbancSn) == null) {
            throw new BusinessException("결과가 등록된 사업을 찾을 수 없습니다.");
        }
    }

    private Long requireOurCompany(Long pbancSn) {
        Long coSn = resultMapper.findOurCoEvlRsltSn(pbancSn);
        if (coSn == null) {
            throw new BusinessException("개찰 목록에 우리 회사 행이 없어 평가표를 넣을 수 없습니다. "
                    + "나라장터 개찰 결과로 자동 등록된 사업만 평가표를 넣을 수 있습니다.");
        }
        return coSn;
    }

    /** 위원 수 1~20 */
    static int cmsnrCount(String s) {
        String t = s == null ? "" : s.trim();
        if (t.matches("\\d{1,2}")) {
            int n = Integer.parseInt(t);
            if (n >= 1 && n <= MAX_CMSNR) {
                return n;
            }
        }
        throw new BusinessException("평가위원 수는 1~" + MAX_CMSNR + "명으로 입력하세요.");
    }

    /** 앞뒤 공백과 끝의 배점 표시 "(15)" 를 뗀다 */
    static String articleName(String s) {
        String t = s == null ? "" : s.trim();
        return MAX_SUFFIX.matcher(t).replaceFirst("").trim();
    }

    /** 숫자 칸 하나. 소수 셋째 자리까지 반올림 */
    static BigDecimal number(String s, String label) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) {
            throw new BusinessException(label + " 칸이 비었습니다.");
        }
        try {
            return new BigDecimal(t).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BusinessException(label + " 칸에 숫자를 입력하세요.");
        }
    }

    /** 장단점 한 칸. 앞뒤 공백을 지우고, 비면 null */
    static String note(String s, String label) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.getBytes(StandardCharsets.UTF_8).length > NOTE_MAX_BYTES) {
            throw new BusinessException(label + "은 한글 기준 1,300자까지 입력할 수 있습니다.");
        }
        return t;
    }
}
