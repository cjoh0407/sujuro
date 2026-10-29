package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.result.dto.EvaluationArticleDTO;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.ResultBidDTO;
import com.ojj.sujuro.result.dto.ResultCompanyDTO;
import com.ojj.sujuro.result.dto.ResultOutcomeDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;
import com.ojj.sujuro.result.dto.ResultsViewDTO;
import com.ojj.sujuro.result.dto.StatsAnalysisForm;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.mapper.ResultMapper;
import com.ojj.sujuro.result.mapper.StatsMapper;
import com.ojj.sujuro.result.service.ResultCollectService;
import com.ojj.sujuro.result.service.StatsService;

/**
 * 평가·결과 입력 (경영지원실): 화면, 결과 가져오기, 장단점 분석, 평가표 엑셀 보관, 평가표 직접 입력.
 * 평가표 숫자는 나라장터 기술평가점수 조회 화면(R26BK01541118, 엑사아이엔티)에 보이는 위원 1~3 의 실제 값이다.
 */
class ResultEntryServiceImplTest {

    private ResultMapper resultMapper;
    private StatsMapper statsMapper;
    private StatsService statsService;
    private ResultCollectService collect;
    private FileStorage storage;
    private ResultEntryServiceImpl service;

    private final LoginUser mgmt = user("2022-0009", Role.MGMT);

    @BeforeEach
    void setUp() {
        resultMapper = mock(ResultMapper.class);
        statsMapper = mock(StatsMapper.class);
        statsService = mock(StatsService.class);
        collect = mock(ResultCollectService.class);
        storage = mock(FileStorage.class);
        service = new ResultEntryServiceImpl(resultMapper, statsMapper, statsService, collect, storage);
        when(statsMapper.findArchive(8L)).thenReturn(new StatsArchiveDTO());
        when(resultMapper.findOurCoEvlRsltSn(8L)).thenReturn(31L);
        when(statsMapper.findEvaluationFile(8L)).thenReturn(new EvaluationFileDTO());
        when(collect.isReady()).thenReturn(true);
    }

    @Test
    void 화면은_고른_사업건을_보이고_목록에_없으면_첫_사업건() {
        when(resultMapper.findResultBids()).thenReturn(Arrays.asList(bid(5L, "PS050"), bid(8L, "PS060")));
        when(resultMapper.findResultBid(5L)).thenReturn(bid(5L, "PS050"));
        when(resultMapper.findResultBid(8L)).thenReturn(bid(8L, "PS060"));
        StatsArchiveDTO archive = new StatsArchiveDTO();
        when(statsService.findArchive(mgmt, 8L)).thenReturn(archive);

        ResultsViewDTO v = service.build(mgmt, 8L);
        assertEquals(8L, v.getBid().getPbancSn());
        assertEquals(archive, v.getArchive());
        verify(resultMapper).findCompanies(8L);

        ResultsViewDTO w = service.build(mgmt, 999L);
        assertEquals(5L, w.getBid().getPbancSn()); // 제출 완료가 맨 앞
        assertEquals(null, w.getArchive());
        verify(resultMapper, never()).findCompanies(5L);

        assertThrows(AccessDeniedException.class, () -> service.build(user("2024-0031", Role.LEADER), 8L));
    }

    @Test
    void 결과_가져오기는_제출_완료만_하고_결과에_따라_안내한다() {
        ResultTargetDTO t = new ResultTargetDTO();
        t.setPbancSn(5L);
        when(resultMapper.findResultTarget(5L)).thenReturn(t);

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.registered("결과 등록: 수주 · 1위"));
        assertEquals("결과 등록: 수주 · 1위. 평가표 엑셀을 올리고 항목별 점수를 입력하세요.", service.importResult(mgmt, 5L));

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.waiting());
        assertTrue(message(() -> service.importResult(mgmt, 5L)).startsWith("아직 나라장터에 최종 낙찰자가 올라오지 않았습니다"));

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.notListed("개찰 목록에 우리 회사가 없어"));
        assertEquals("개찰 목록에 우리 회사가 없어", message(() -> service.importResult(mgmt, 5L)));

        assertTrue(message(() -> service.importResult(mgmt, 6L)).startsWith("제출 완료 상태인 사업만"));
        when(collect.isReady()).thenReturn(false);
        assertTrue(message(() -> service.importResult(mgmt, 5L)).contains("사업자번호(company.biz-no)"));
    }

    @Test
    void 비교는_우리가_1위면_2위와_아니면_1위와_한다() {
        ResultsViewDTO v = new ResultsViewDTO();
        // R26BK01541118 실제 개찰 점수. 지엔소프트(2위)를 우리로 가정
        v.getCompanies().add(company(1, "엑사아이엔티 주식회사", "N", 90.0, 85.284, 10.0, 100.0));
        v.getCompanies().add(company(2, "지엔소프트(주)", "Y", 87.0, 84.564, 9.992, 96.992));
        ResultsViewDTO.Compare c = v.getCompare();
        assertEquals(1, c.getOtherRank());
        assertEquals("−3.008", c.getTotalDiff());
        assertEquals("−3", c.getTechDiff());
        assertEquals("−0.008", c.getPrcDiff());
        assertEquals("−0.72", c.getOrgnlDiff());

        v.getCompanies().get(0).setOurCoYn("Y");
        v.getCompanies().get(1).setOurCoYn("N");
        assertEquals("+3.008", v.getCompare().getTotalDiff());
        assertEquals(2, v.getCompare().getOtherRank());
    }

    private static ResultBidDTO bid(Long sn, String stts) {
        ResultBidDTO b = new ResultBidDTO();
        b.setPbancSn(sn);
        b.setPbancNm("사업 " + sn);
        b.setPbancSttsCd(stts);
        return b;
    }

    private static ResultCompanyDTO company(int rank, String nm, String ours, Double tech, Double orgnl, Double prc, Double total) {
        ResultCompanyDTO c = new ResultCompanyDTO();
        c.setOpengRnk(rank);
        c.setCoNm(nm);
        c.setOurCoYn(ours);
        c.setTechEvlScr(tech);
        c.setTechEvlOrgnlScr(orgnl);
        c.setPrcEvlScr(prc);
        c.setSnthsEvlScr(total);
        return c;
    }

    @Test
    void 장단점은_경영지원실만_입력하고_빈_칸은_지운다() {
        service.saveAnalysis(mgmt, 8L, analysis("  보안 아키텍처 \n", "", null));
        verify(resultMapper).updateAnalysis(8L, "보안 아키텍처", null, null, "2022-0009");

        assertThrows(AccessDeniedException.class,
                () -> service.saveAnalysis(user("2024-0031", Role.LEADER), 8L, analysis("a", "b", "c")));
        assertEquals("결과가 등록된 사업을 찾을 수 없습니다.", message(() -> service.saveAnalysis(mgmt, 9L, analysis("a", null, null))));
    }

    @Test
    void 장단점_한_칸은_4000바이트까지() {
        String ok = repeat("가", 1333); // 3999바이트
        service.saveAnalysis(mgmt, 8L, analysis(ok, null, null));
        verify(resultMapper).updateAnalysis(8L, ok, null, null, "2022-0009");

        assertEquals("탈락·감점 원인은 한글 기준 1,300자까지 입력할 수 있습니다.",
                message(() -> service.saveAnalysis(mgmt, 8L, analysis(null, repeat("가", 1334), null))));
    }

    @Test
    void 평가표_엑셀은_보관만_하고_우리_회사_행에_잇는다() {
        when(storage.save(any(), eq("evaluation/8"), eq("xlsx"))).thenReturn("abc.xlsx");
        when(resultMapper.nextEvaluationFileVer(8L)).thenReturn(2);
        doAnswer(inv -> {
            ((EvaluationFileDTO) inv.getArgument(2)).setFileSn(77L);
            return 1;
        }).when(resultMapper).insertEvaluationFile(eq(8L), eq("2"), any(), anyLong(), eq("xlsx"), eq("2022-0009"));

        service.uploadEvaluationFile(mgmt, 8L, excel("평가결과.xlsx"));

        verify(resultMapper).clearLastEvaluationFiles(8L);
        verify(resultMapper).clearLastUploads(31L);
        verify(resultMapper).insertUpload(31L, 77L, "2022-0009");
        verify(resultMapper).markUploaded(31L);
    }

    @Test
    void 평가표는_엑셀만_받고_DB_저장이_실패하면_파일을_지운다() {
        assertTrue(message(() -> service.uploadEvaluationFile(mgmt, 8L, excel("평가결과.pdf"))).contains("xls, xlsx"));
        verify(storage, never()).save(any(), anyString(), anyString());

        when(storage.save(any(), anyString(), anyString())).thenReturn("abc.xlsx");
        doThrow(new IllegalStateException("DB 오류")).when(resultMapper).insertUpload(anyLong(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> service.uploadEvaluationFile(mgmt, 8L, excel("평가결과.xlsx")));
        verify(storage).deleteQuietly("evaluation/8", "abc.xlsx");

        when(resultMapper.findOurCoEvlRsltSn(8L)).thenReturn(null);
        assertTrue(message(() -> service.uploadEvaluationFile(mgmt, 8L, excel("평가결과.xlsx"))).contains("우리 회사 행이 없어"));
    }

    @Test
    void 평가표_입력은_항목과_위원_점수를_통째로_바꾸고_이름의_배점_표시는_뗀다() {
        long[] sn = {100};
        doAnswer(inv -> {
            ((EvaluationArticleDTO) inv.getArgument(0)).setEvlArtclSn(++sn[0]);
            return 1;
        }).when(resultMapper).insertArticle(any());

        EvaluationForm f = form("3",
                row("전략 및 방법론(15)", "15", "14.5", "14", "15"),
                row("기술 및 기능(30)", "30", "27.9", "28.4", "28.4"),
                row("", "", "", "", ""));
        service.saveEvaluation(mgmt, 8L, f);

        InOrder order = inOrder(resultMapper);
        order.verify(resultMapper).deleteScores(8L);
        order.verify(resultMapper).deleteSubArticles(8L);
        order.verify(resultMapper).deleteArticles(8L);

        ArgumentCaptor<EvaluationArticleDTO> a = ArgumentCaptor.forClass(EvaluationArticleDTO.class);
        verify(resultMapper, times(2)).insertArticle(a.capture());
        assertEquals("전략 및 방법론", a.getAllValues().get(0).getEvlArtclNm());
        assertEquals(new BigDecimal("15.000"), a.getAllValues().get(0).getEvlScorScr());
        assertEquals(2, a.getAllValues().get(1).getSortSeq());

        verify(resultMapper, times(6)).insertScore(eq(31L), anyLong(), anyInt(), any());
        verify(resultMapper).insertScore(31L, 101L, 1, new BigDecimal("14.500"));
        verify(resultMapper).insertScore(31L, 102L, 3, new BigDecimal("28.400"));
        verify(resultMapper).updateUploadCmsnrCnt(31L, 3);
    }

    @Test
    void 평가표_입력이_틀리면_아무것도_지우지_않고_안내한다() {
        assertEquals("평가위원 수는 1~20명으로 입력하세요.",
                message(() -> service.saveEvaluation(mgmt, 8L, form("0", row("전략", "15", "14")))));
        assertEquals("평가 항목을 한 줄 이상 입력하세요.",
                message(() -> service.saveEvaluation(mgmt, 8L, form("1", row("", "", "")))));
        assertTrue(message(() -> service.saveEvaluation(mgmt, 8L, form("1", row("전략", "15", "16"))))
                .endsWith("평가위원1 점수는 0~15(배점) 사이로 입력하세요."));
        assertTrue(message(() -> service.saveEvaluation(mgmt, 8L, form("2", row("전략", "15", "14", " "))))
                .endsWith("평가위원2 점수 칸이 비었습니다."));
        assertEquals("같은 평가 항목 이름이 두 번 있습니다: 전략",
                message(() -> service.saveEvaluation(mgmt, 8L, form("1", row("전략(10)", "10", "9"), row("전략", "10", "9")))));
        assertTrue(message(() -> service.saveEvaluation(mgmt, 8L, form("1", row("가", "60", "50"), row("나", "50", "40"))))
                .startsWith("배점 합계가 100점을 넘습니다(110점)"));
        verify(resultMapper, never()).deleteScores(anyLong());

        when(statsMapper.findEvaluationFile(8L)).thenReturn(null);
        assertTrue(message(() -> service.saveEvaluation(mgmt, 8L, form("1", row("전략", "15", "14"))))
                .startsWith("평가표 엑셀을 먼저 올려 주세요"));
        assertThrows(AccessDeniedException.class,
                () -> service.saveEvaluation(user("2024-0031", Role.LEADER), 8L, form("1", row("전략", "15", "14"))));
    }

    @Test
    void 항목_이름은_끝의_배점_표시만_뗀다() {
        assertEquals("전략 및 방법론", ResultEntryServiceImpl.articleName(" 전략 및 방법론(15) "));
        assertEquals("프로젝트 관리", ResultEntryServiceImpl.articleName("프로젝트 관리 ( 16 )"));
        assertEquals("성능 및 품질", ResultEntryServiceImpl.articleName("성능 및 품질(25.5)"));
        assertEquals("1차(서면) 평가", ResultEntryServiceImpl.articleName("1차(서면) 평가"));
    }

    private static EvaluationForm form(String cmsnr, EvaluationForm.Row... rows) {
        EvaluationForm f = new EvaluationForm();
        f.setCmsnrCnt(cmsnr);
        f.getItems().addAll(Arrays.asList(rows));
        return f;
    }

    private static EvaluationForm.Row row(String nm, String max, String... scores) {
        EvaluationForm.Row r = new EvaluationForm.Row();
        r.setNm(nm);
        r.setMax(max);
        r.getScores().addAll(Arrays.asList(scores));
        return r;
    }

    private static MockMultipartFile excel(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8));
    }

    private static StatsAnalysisForm analysis(String strpt, String sbpt, String splmnt) {
        StatsAnalysisForm f = new StatsAnalysisForm();
        f.setStrptCn(strpt);
        f.setSbptCsCn(sbpt);
        f.setSplmntCn(splmnt);
        return f;
    }

    private static String message(Executable call) {
        return assertThrows(BusinessException.class, call).getMessage();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static LoginUser user(String empNo, Role role) {
        return new LoginUser(empNo, empNo, "사원", "개발1팀", role, LoginUser.STATUS_ACTIVE, "x");
    }
}
