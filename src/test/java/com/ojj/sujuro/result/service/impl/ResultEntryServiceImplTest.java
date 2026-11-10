package com.ojj.sujuro.result.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.common.file.FileStorage;
import com.ojj.sujuro.result.dto.CompanyArticleScoreDTO;
import com.ojj.sujuro.result.dto.EvaluationArticleDTO;
import com.ojj.sujuro.result.dto.EvaluationCompareDTO;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.EvaluationScoreDTO;
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
 * 평가·결과 입력 (경영지원실·팀장): 화면, 결과 가져오기, 장단점 분석, 업체별 평가표 엑셀 보관·읽기·저장, 업체별 비교.
 * 평가표 숫자는 나라장터 기술평가점수 조회 화면(R26BK01541118, 엑사아이엔티)에 보이는 위원 1~3 의 실제 값이다.
 * 8번 사업의 개찰 업체: 31 = 우리 회사(지엔소프트, 2위), 32 = 엑사아이엔티(1위).
 */
class ResultEntryServiceImplTest {

    private static final long OURS = 31L;
    private static final long OTHER = 32L;

    private ResultMapper resultMapper;
    private StatsMapper statsMapper;
    private StatsService statsService;
    private ResultCollectService collect;
    private FileStorage storage;
    private ResultEntryServiceImpl service;

    private final LoginUser mgmt = user("2022-0009", Role.MGMT);
    /** 8번 사업을 맡은 개발1팀(3) 팀장 */
    private final LoginUser leader = user("2024-0031", Role.LEADER);
    /** 다른 개발팀(4) 팀장 */
    private final LoginUser otherLeader = user("2024-0099", Role.LEADER);

    @BeforeEach
    void setUp() {
        resultMapper = mock(ResultMapper.class);
        statsMapper = mock(StatsMapper.class);
        statsService = mock(StatsService.class);
        collect = mock(ResultCollectService.class);
        storage = mock(FileStorage.class);
        service = new ResultEntryServiceImpl(resultMapper, statsMapper, statsService, collect, storage);
        when(statsMapper.findArchive(8L)).thenReturn(archive(3L));
        when(statsMapper.findDevTeamSnOf("2024-0031")).thenReturn(3L);
        when(statsMapper.findDevTeamSnOf("2024-0099")).thenReturn(4L);
        when(resultMapper.findCompanies(8L)).thenReturn(Arrays.asList(
                company(OTHER, 1, "엑사아이엔티 주식회사", "N", 90.0, 85.284, 10.0, 100.0),
                company(OURS, 2, "지엔소프트(주)", "Y", 87.0, 84.564, 9.992, 96.992)));
        when(resultMapper.findCompanyEvaluationFile(OURS)).thenReturn(file("고용노동 사업_[ 지엔소프트(주) ].xlsx"));
        when(resultMapper.findCompanyEvaluationFile(OTHER)).thenReturn(file("고용노동 사업_[ 엑사아이엔티 주식회사 ].xlsx"));
        when(storage.load("evaluation/8", "abc.xlsx")).thenReturn(new ByteArrayResource(excelBytes()));
        when(collect.isReady()).thenReturn(true);
    }

    @Test
    void 화면은_고른_사업건을_보이고_목록에_없으면_첫_사업건() {
        when(resultMapper.findResultBids(null)).thenReturn(Arrays.asList(bid(5L, "PS050"), bid(8L, "PS060")));
        when(resultMapper.findResultBid(5L)).thenReturn(bid(5L, "PS050"));
        when(resultMapper.findResultBid(8L)).thenReturn(bid(8L, "PS060"));
        StatsArchiveDTO archive = archive(3L);
        when(statsService.findArchive(mgmt, 8L)).thenReturn(archive);

        ResultsViewDTO v = service.build(mgmt, 8L, null);
        assertEquals(8L, v.getBid().getPbancSn());
        assertEquals(archive, v.getArchive());
        assertEquals(OURS, v.getCompany().getCoEvlRsltSn()); // 업체를 고르지 않으면 우리 회사

        ResultsViewDTO w = service.build(mgmt, 999L, null);
        assertEquals(5L, w.getBid().getPbancSn()); // 제출 완료가 맨 앞
        assertEquals(null, w.getArchive());
        assertEquals(null, w.getCompany()); // 평가표는 결과 등록 뒤에 넣는다
        verify(resultMapper).findCompanies(5L); // 개찰 업체 목록은 제출 완료부터 보인다

        assertThrows(AccessDeniedException.class, () -> service.build(user("2025-0001", Role.MEMBER), 8L, null));
    }

    @Test
    void 경영지원실은_가져오기와_엑셀만_팀장은_자기_팀_사업의_점수와_장단점을_입력한다() {
        when(resultMapper.findResultBids(null)).thenReturn(Arrays.asList(bid(8L, "PS060")));
        when(resultMapper.findResultBids(3L)).thenReturn(Arrays.asList(bid(8L, "PS060")));
        when(resultMapper.findResultBid(8L)).thenReturn(bid(8L, "PS060"));
        when(statsService.findArchive(mgmt, 8L)).thenReturn(archive(3L));
        when(statsService.findArchive(leader, 8L)).thenReturn(archive(3L));

        ResultsViewDTO m = service.build(mgmt, 8L, null);
        assertTrue(m.isCanImport());
        assertTrue(m.isCanUpload());
        assertFalse(m.isCanScore());

        ResultsViewDTO l = service.build(leader, 8L, null);
        verify(resultMapper).findResultBids(3L); // 팀장은 자기 팀 사업만
        assertFalse(l.isCanImport());
        assertTrue(l.isCanUpload());
        assertTrue(l.isCanScore());

        // 개발팀이 아닌 팀장은 볼 사업이 없다
        ResultsViewDTO none = service.build(user("2024-0500", Role.LEADER), null, null);
        assertTrue(none.getBids().isEmpty());
        assertEquals(null, none.getBid());
    }

    @Test
    void 팀장_화면은_고른_업체의_엑셀을_읽어_입력_칸을_채우고_저장은_하지_않는다() {
        leaderView();

        ResultsViewDTO v = service.build(leader, 8L, OTHER);
        assertEquals(OTHER, v.getCompany().getCoEvlRsltSn());
        EvaluationForm f = v.getExcelForm();
        assertEquals("2", f.getCmsnrCnt());
        assertEquals("전략 및 방법론(15)", f.getItems().get(0).getNm());
        assertEquals("14", f.getItems().get(0).scoreAt(1));
        assertTrue(v.getExcelNote().startsWith("올린 엑셀에서 평가 항목 1개와 평가위원 2명의 점수를 읽어 채웠습니다"));
        verify(resultMapper, never()).insertArticle(any());

        // 저장을 마친 엑셀이면(위원 수가 기록돼 있으면) 저장한 값을 보인다
        when(resultMapper.findCompanyScores(OTHER)).thenReturn(Collections.singletonList(score(1, "14.5")));
        EvaluationFileDTO savedFile = file("고용노동 사업_[ 엑사아이엔티 주식회사 ].xlsx");
        savedFile.setCmsnrCnt(1);
        when(resultMapper.findCompanyEvaluationFile(OTHER)).thenReturn(savedFile);
        ResultsViewDTO saved = service.build(leader, 8L, OTHER);
        assertNull(saved.getExcelForm());
        assertEquals("14.5", saved.getCompanyForm().getItems().get(0).scoreAt(0));

        // 엑셀을 새로 올리면(위원 수가 비어 있으면) 저장한 점수가 있어도 입력 칸을 엑셀에서 채운다. 저장은 하지 않는다
        when(resultMapper.findCompanyEvaluationFile(OTHER)).thenReturn(file("고용노동 사업_[ 엑사아이엔티 주식회사 ].xlsx"));
        assertEquals("2", service.build(leader, 8L, OTHER).getExcelForm().getCmsnrCnt());
        verify(resultMapper, never()).insertArticle(any());

        // 모양이 다른 파일이면 이유만 알리고 직접 입력하게 둔다
        when(storage.load("evaluation/8", "abc.xlsx")).thenReturn(new ByteArrayResource("x".getBytes(StandardCharsets.UTF_8)));
        ResultsViewDTO bad = service.build(leader, 8L, OTHER);
        assertNull(bad.getExcelForm());
        assertTrue(bad.getExcelError().contains("열지 못했습니다"));
    }

    @Test
    void 경영지원실_화면은_엑셀을_읽지_않는다() {
        when(resultMapper.findResultBids(null)).thenReturn(Arrays.asList(bid(8L, "PS060")));
        when(resultMapper.findResultBid(8L)).thenReturn(bid(8L, "PS060"));
        when(statsService.findArchive(mgmt, 8L)).thenReturn(archive(3L));

        assertNull(service.build(mgmt, 8L, OTHER).getExcelForm());
        verify(storage, never()).load(anyString(), anyString());
    }

    @Test
    void 업체별_비교는_점수를_저장한_업체만_우리_회사부터_보이고_차이는_그_업체_빼기_우리() {
        leaderView();
        when(resultMapper.findCompanyArticleScores(8L)).thenReturn(Arrays.asList(
                articleScore(OTHER, 101L, "전략 및 방법론", "15", 14.6),
                articleScore(OURS, 101L, "전략 및 방법론", "15", 14.0),
                articleScore(OTHER, 102L, "기술 및 기능", "30", 28.0),
                articleScore(OURS, 102L, "기술 및 기능", "30", 28.5)));

        EvaluationCompareDTO c = service.build(leader, 8L, null).getEvaluationCompare();
        assertTrue(c.isComparable());
        assertEquals("지엔소프트(주)", c.getColumns().get(0).getCoNm()); // 우리 회사가 첫 열
        assertEquals(2, c.getRows().size());
        EvaluationCompareDTO.Cell strategy = c.getRows().get(0).getCells().get(1);
        assertEquals("14.6", strategy.getText());
        assertEquals("+0.6", strategy.getDiff()); // 그 업체가 0.6 높다
        assertTrue(strategy.isBehind());
        assertEquals("−0.5", c.getRows().get(1).getCells().get(1).getDiff());
        assertFalse(c.getRows().get(1).getCells().get(1).isBehind());
        assertEquals("42.5", c.getColumns().get(0).getTotalText());
        assertEquals("+0.1", c.getColumns().get(1).getTotalDiff());

        // 한 업체만 저장했으면 비교하지 않는다
        when(resultMapper.findCompanyArticleScores(8L)).thenReturn(Collections.singletonList(
                articleScore(OURS, 101L, "전략 및 방법론", "15", 14.0)));
        assertFalse(service.build(leader, 8L, null).getEvaluationCompare().isComparable());
    }

    private void leaderView() {
        when(resultMapper.findResultBids(3L)).thenReturn(Arrays.asList(bid(8L, "PS060")));
        when(resultMapper.findResultBid(8L)).thenReturn(bid(8L, "PS060"));
        when(statsService.findArchive(leader, 8L)).thenReturn(archive(3L));
    }

    @Test
    void 결과_가져오기는_제출_완료만_하고_결과에_따라_안내한다() {
        ResultTargetDTO t = new ResultTargetDTO();
        t.setPbancSn(5L);
        when(resultMapper.findResultTarget(5L)).thenReturn(t);

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.registered("결과 등록: 수주 · 1위"));
        assertEquals("결과 등록: 수주 · 1위. 평가표 엑셀을 올려 주세요. 항목별 점수와 장단점은 담당 팀 팀장이 입력합니다.",
                service.importResult(mgmt, 5L));
        assertThrows(AccessDeniedException.class, () -> service.importResult(leader, 5L));

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.waiting());
        assertTrue(message(() -> service.importResult(mgmt, 5L)).startsWith("아직 나라장터에 최종 낙찰자가 올라오지 않았습니다"));

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.notListed("개찰 목록에 우리 회사가 없어"));
        assertEquals("개찰 목록에 우리 회사가 없어", message(() -> service.importResult(mgmt, 5L)));

        assertTrue(message(() -> service.importResult(mgmt, 6L)).startsWith("제출 완료 상태인 사업만"));
        when(collect.isReady()).thenReturn(false);
        assertTrue(message(() -> service.importResult(mgmt, 5L)).contains("사업자번호(company.biz-no)"));
    }

    @Test
    void 컨소시엄은_우리_회사_번호로_가져오지_않고_주관사_번호를_넣으라고_안내한다() {
        when(resultMapper.findResultBid(5L)).thenReturn(shapedBid(5L, "PS050", "PF020", "컨소시엄 참여")); // findResultTarget 은 null

        assertTrue(message(() -> service.importResult(mgmt, 5L)).contains("주관사·원도급사의 사업자번호를 넣어 결과를 가져옵니다"));
        verify(collect, never()).register(any());
    }

    /* 참여 업체로 결과 가져오기 (11/10): 컨소시엄 참여·하도급은 주관사·원도급사의 사업자번호로 그 업체의 결과·순위·점수·낙찰 금액을
       주관 사업과 똑같이 가져온다. 11/04 의 결과 직접 입력은 없앴다 */

    @Test
    void 참여_업체_번호는_그_사업을_맡은_팀의_팀장이_10자리로_넣는다() {
        ResultTargetDTO t = partnerTarget();
        when(collect.register(t)).thenReturn(ResultOutcomeDTO.registered("결과 등록: 수주 · 1위"));

        String msg = service.importPartnerResult(leader, 5L, " 220-86-57703 ");
        assertEquals("결과 등록: 수주 · 1위 (사업자번호 220-86-57703 업체 기준). 평가표 엑셀이 올라오면 항목별 점수와 장단점을 입력해 주세요.", msg);
        assertEquals("2208657703", t.getPartnerBizNo()); // 숫자만 넘긴다

        // 경영지원실·다른 팀 팀장은 넣지 못한다
        assertThrows(AccessDeniedException.class, () -> service.importPartnerResult(mgmt, 5L, "2208657703"));
        assertThrows(AccessDeniedException.class, () -> service.importPartnerResult(otherLeader, 5L, "2208657703"));
        assertTrue(message(() -> service.importPartnerResult(leader, 5L, "220-86-5770")).contains("10자리"));
        assertTrue(message(() -> service.importPartnerResult(leader, 5L, null)).contains("10자리"));
        verify(collect, times(1)).register(any());
    }

    @Test
    void 참여_업체_번호는_제출_완료인_컨소시엄_하도급에만_넣는다() {
        when(resultMapper.findResultBid(5L)).thenReturn(shapedBid(5L, "PS050", "PF010", "주관")); // findPartnerTarget 은 null
        assertTrue(message(() -> service.importPartnerResult(leader, 5L, "2208657703")).contains("컨소시엄 참여·하도급 사업에만"));

        when(resultMapper.findResultBid(5L)).thenReturn(shapedBid(5L, "PS060", "PF020", "컨소시엄 참여"));
        assertTrue(message(() -> service.importPartnerResult(leader, 5L, "2208657703")).startsWith("제출 완료 상태인 사업만"));
        verify(collect, never()).register(any());
    }

    @Test
    void 참여_업체를_저장하고_낙찰자를_기다리거나_목록에_없으면_안내한다() {
        ResultTargetDTO t = partnerTarget();

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.waiting());
        String waiting = service.importPartnerResult(leader, 5L, "2208657703");
        assertTrue(waiting.startsWith("사업자번호 220-86-57703 업체를 개찰 목록에서 찾아 저장했습니다"));
        assertTrue(waiting.contains("오후 5시 수집"));

        when(collect.register(t)).thenReturn(ResultOutcomeDTO.notListed("개찰 목록에 사업자번호 220-86-57703 업체가 없어"));
        assertEquals("개찰 목록에 사업자번호 220-86-57703 업체가 없어", message(() -> service.importPartnerResult(leader, 5L, "2208657703")));
    }

    @Test
    void 번호_칸은_그_사업을_맡은_팀의_팀장에게만_보인다() {
        when(resultMapper.findResultBids(any())).thenReturn(Arrays.asList(bid(5L, "PS050")));
        when(resultMapper.findResultBid(5L)).thenReturn(shapedBid(5L, "PS050", "PF020", "컨소시엄 참여")); // 주관 팀 3
        assertTrue(service.build(leader, 5L, null).isCanLinkPartner());
        assertFalse(service.build(mgmt, 5L, null).isCanLinkPartner());
        assertFalse(service.build(otherLeader, 5L, null).isCanLinkPartner());

        when(resultMapper.findResultBid(5L)).thenReturn(shapedBid(5L, "PS050", "PF010", "주관"));
        assertFalse(service.build(leader, 5L, null).isCanLinkPartner()); // 주관 사업은 우리 회사 번호로 가져온다
    }

    /** 제출 완료인 컨소시엄 사업 5. 주관 팀은 3(leader 의 팀) */
    private ResultTargetDTO partnerTarget() {
        ResultTargetDTO t = new ResultTargetDTO();
        t.setPbancSn(5L);
        t.setSprvsnTeamSn(3L);
        when(resultMapper.findPartnerTarget(5L)).thenReturn(t);
        return t;
    }

    @Test
    void 컨소시엄_사업의_우리_쪽_행은_주관사_하도급은_원도급사로_보인다() {
        when(resultMapper.findResultBids(any())).thenReturn(Arrays.asList(bid(5L, "PS050")));
        when(resultMapper.findCompanies(5L)).thenAnswer(inv -> Arrays.asList(
                company(41L, 1, "주관 업체", "Y", null, null, null, null),
                company(42L, 2, "다른 업체", "N", null, null, null, null)));

        ResultBidDTO consortium = shapedBid(5L, "PS050", "PF020", "컨소시엄 참여");
        consortium.setPartnerBizNo("2208657703");
        when(resultMapper.findResultBid(5L)).thenReturn(consortium);
        ResultsViewDTO v = service.build(mgmt, 5L, null);
        assertEquals("1위 주관 업체 (주관사)", v.getCompanies().get(0).getOptionLabel());
        assertEquals("2위 다른 업체", v.getCompanies().get(1).getOptionLabel());
        assertTrue(consortium.isPartnerLinked());
        assertTrue(consortium.isNaraResult());
        assertEquals("220-86-57703", consortium.getPartnerBizNoText());

        when(resultMapper.findResultBid(5L)).thenReturn(shapedBid(5L, "PS050", "PF030", "하도급"));
        assertEquals("1위 주관 업체 (원도급사)", service.build(mgmt, 5L, null).getCompanies().get(0).getOptionLabel());

        when(resultMapper.findResultBid(5L)).thenReturn(shapedBid(5L, "PS050", "PF010", "주관"));
        assertEquals("1위 주관 업체 (자사)", service.build(mgmt, 5L, null).getCompanies().get(0).getOptionLabel());
    }

    @Test
    void 참여_업체를_넣지_않은_컨소시엄은_나라장터_결과를_보이지_않는다() {
        ResultBidDTO b = shapedBid(5L, "PS060", "PF020", "컨소시엄 참여");
        assertFalse(b.isPartnerLinked());
        assertFalse(b.isNaraResult());
        assertTrue(shapedBid(5L, "PS060", "PF010", "주관").isNaraResult());

        assertTrue(shapedBid(5L, "PS060", null, null).isNaraResult()); // 참여 형태를 안 정했으면 주관처럼 우리 회사 번호로 가져온다

        StatsArchiveDTO a = new StatsArchiveDTO();
        a.setPtcpShpCd("PF020");
        assertTrue(a.isScoreless()); // 11/10 전에 직접 입력한 결과: 점수 칸이 없다
        a.setOurCompanyListed(true); // 주관사 번호로 가져와 개찰 목록에 우리 쪽 행이 있다
        assertFalse(a.isScoreless());
    }

    /** 참여 형태를 정한 사업 */
    private static ResultBidDTO shapedBid(Long sn, String stts, String shpCd, String shpNm) {
        ResultBidDTO b = bid(sn, stts);
        b.setSprvsnTeamSn(3L);
        b.setPtcpShpCd(shpCd);
        b.setPtcpShpNm(shpNm);
        return b;
    }

    @Test
    void 비교는_우리가_1위면_2위와_아니면_1위와_한다() {
        ResultsViewDTO v = new ResultsViewDTO();
        // R26BK01541118 실제 개찰 점수. 지엔소프트(2위)를 우리로 가정
        v.getCompanies().add(company(OTHER, 1, "엑사아이엔티 주식회사", "N", 90.0, 85.284, 10.0, 100.0));
        v.getCompanies().add(company(OURS, 2, "지엔소프트(주)", "Y", 87.0, 84.564, 9.992, 96.992));
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

    @Test
    void 장단점은_담당_팀_팀장만_입력하고_빈_칸은_지운다() {
        service.saveAnalysis(leader, 8L, analysis("  보안 아키텍처 \n", "", null));
        verify(resultMapper).updateAnalysis(8L, "보안 아키텍처", null, null, "2024-0031");

        assertThrows(AccessDeniedException.class, () -> service.saveAnalysis(mgmt, 8L, analysis("a", "b", "c")));
        assertThrows(AccessDeniedException.class, () -> service.saveAnalysis(otherLeader, 8L, analysis("a", "b", "c")));
        assertEquals("결과가 등록된 사업을 찾을 수 없습니다.", message(() -> service.saveAnalysis(leader, 9L, analysis("a", null, null))));
    }

    @Test
    void 장단점_한_칸은_4000바이트까지() {
        String ok = repeat("가", 1333); // 3999바이트
        service.saveAnalysis(leader, 8L, analysis(ok, null, null));
        verify(resultMapper).updateAnalysis(8L, ok, null, null, "2024-0031");

        assertEquals("실주·감점 원인은 한글 기준 1,300자까지 입력할 수 있습니다.",
                message(() -> service.saveAnalysis(leader, 8L, analysis(null, repeat("가", 1334), null))));
    }

    @Test
    void 평가표_엑셀은_경영지원실과_담당_팀_팀장이_업체별로_올린다() {
        when(storage.save(any(), anyString(), anyString())).thenReturn("abc.xlsx");
        service.uploadEvaluationFile(leader, 8L, OTHER, excel("평가결과.xlsx"));
        verify(resultMapper).clearLastEvaluationFiles(OTHER);
        verify(resultMapper).insertUpload(eq(OTHER), any(), eq("2024-0031"));
        verify(resultMapper).markUploaded(OTHER);

        assertThrows(AccessDeniedException.class, () -> service.uploadEvaluationFile(otherLeader, 8L, null, excel("평가결과.xlsx")));
        assertThrows(AccessDeniedException.class,
                () -> service.uploadEvaluationFile(user("2025-0001", Role.MEMBER), 8L, null, excel("평가결과.xlsx")));
        assertTrue(message(() -> service.uploadEvaluationFile(leader, 8L, 99L, excel("평가결과.xlsx"))).contains("개찰 업체가 아닙니다"));
    }

    @Test
    void 평가표_엑셀은_보관만_하고_고른_업체_행에_잇는다_고르지_않으면_우리_회사() {
        when(storage.save(any(), eq("evaluation/8"), eq("xlsx"))).thenReturn("abc.xlsx");
        when(resultMapper.nextEvaluationFileVer(8L)).thenReturn(2);
        doAnswer(inv -> {
            ((EvaluationFileDTO) inv.getArgument(2)).setFileSn(77L);
            return 1;
        }).when(resultMapper).insertEvaluationFile(eq(8L), eq("2"), any(), anyLong(), eq("xlsx"), eq("2022-0009"));

        service.uploadEvaluationFile(mgmt, 8L, null, excel("평가결과.xlsx"));

        InOrder order = inOrder(resultMapper);
        order.verify(resultMapper).clearLastEvaluationFiles(OURS); // 업로드 기록의 '마지막'을 떼기 전에
        order.verify(resultMapper).clearLastUploads(OURS);
        verify(resultMapper).insertUpload(OURS, 77L, "2022-0009");
        verify(resultMapper).markUploaded(OURS);
    }

    @Test
    void 평가표는_엑셀만_받고_DB_저장이_실패하면_파일을_지운다() {
        assertTrue(message(() -> service.uploadEvaluationFile(mgmt, 8L, null, excel("평가결과.pdf"))).contains("xls, xlsx"));
        verify(storage, never()).save(any(), anyString(), anyString());

        when(storage.save(any(), anyString(), anyString())).thenReturn("abc.xlsx");
        doThrow(new IllegalStateException("DB 오류")).when(resultMapper).insertUpload(anyLong(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> service.uploadEvaluationFile(mgmt, 8L, null, excel("평가결과.xlsx")));
        verify(storage).deleteQuietly("evaluation/8", "abc.xlsx");

        when(resultMapper.findCompanies(8L)).thenReturn(Collections.singletonList(
                company(OTHER, 1, "엑사아이엔티 주식회사", "N", 90.0, 85.284, 10.0, 100.0)));
        assertTrue(message(() -> service.uploadEvaluationFile(mgmt, 8L, OTHER, excel("평가결과.xlsx"))).contains("우리 회사 행이 없어"));
    }

    @Test
    void 평가표_저장은_그_업체_점수만_바꾸고_같은_이름_항목은_다시_쓴다() {
        // 다른 업체가 먼저 저장해 둔 항목: 전략 및 방법론(101)
        EvaluationArticleDTO existing = new EvaluationArticleDTO();
        existing.setEvlArtclSn(101L);
        existing.setEvlArtclNm("전략 및 방법론");
        when(resultMapper.findArticles(8L)).thenReturn(Collections.singletonList(existing));
        doAnswer(inv -> {
            ((EvaluationArticleDTO) inv.getArgument(0)).setEvlArtclSn(102L);
            return 1;
        }).when(resultMapper).insertArticle(any());

        EvaluationForm f = form("3",
                row("전략 및 방법론(15)", "15", "14.5", "14", "15"),
                row("기술 및 기능(30)", "30", "27.9", "28.4", "28.4"),
                row("", "", "", "", ""));
        service.saveEvaluation(leader, 8L, OTHER, f);

        InOrder order = inOrder(resultMapper);
        order.verify(resultMapper).deleteCompanyScores(OTHER); // 우리 회사 점수는 지우지 않는다
        order.verify(resultMapper).deleteUnusedSubArticles(8L);
        order.verify(resultMapper).deleteUnusedArticles(8L);
        verify(resultMapper, never()).deleteCompanyScores(OURS);

        ArgumentCaptor<EvaluationArticleDTO> updated = ArgumentCaptor.forClass(EvaluationArticleDTO.class);
        verify(resultMapper).updateArticle(updated.capture());
        assertEquals(101L, updated.getValue().getEvlArtclSn());
        assertEquals(new BigDecimal("15.000"), updated.getValue().getEvlScorScr());
        ArgumentCaptor<EvaluationArticleDTO> inserted = ArgumentCaptor.forClass(EvaluationArticleDTO.class);
        verify(resultMapper).insertArticle(inserted.capture());
        assertEquals("기술 및 기능", inserted.getValue().getEvlArtclNm());
        assertEquals(2, inserted.getValue().getSortSeq());

        verify(resultMapper, times(6)).insertScore(eq(OTHER), anyLong(), anyInt(), any());
        verify(resultMapper).insertScore(OTHER, 101L, 1, new BigDecimal("14.500"));
        verify(resultMapper).insertScore(OTHER, 102L, 3, new BigDecimal("28.400"));
        verify(resultMapper).updateUploadCmsnrCnt(OTHER, 3);
    }

    @Test
    void 평가표_입력이_틀리면_아무것도_지우지_않고_안내한다() {
        assertEquals("평가위원 수는 1~20명으로 입력해 주세요.",
                message(() -> service.saveEvaluation(leader, 8L, null, form("0", row("전략", "15", "14")))));
        assertEquals("평가 항목을 한 줄 이상 입력해 주세요.",
                message(() -> service.saveEvaluation(leader, 8L, null, form("1", row("", "", "")))));
        assertTrue(message(() -> service.saveEvaluation(leader, 8L, null, form("1", row("전략", "15", "16"))))
                .endsWith("평가위원1 점수는 0~15(배점) 사이로 입력해 주세요."));
        assertTrue(message(() -> service.saveEvaluation(leader, 8L, null, form("2", row("전략", "15", "14", " "))))
                .endsWith("평가위원2 점수 칸이 비었습니다."));
        assertEquals("같은 평가 항목 이름이 두 번 있습니다: 전략",
                message(() -> service.saveEvaluation(leader, 8L, null, form("1", row("전략(10)", "10", "9"), row("전략", "10", "9")))));
        assertTrue(message(() -> service.saveEvaluation(leader, 8L, null, form("1", row("가", "60", "50"), row("나", "50", "40"))))
                .startsWith("배점 합계가 100점을 넘습니다(110점)"));
        verify(resultMapper, never()).deleteCompanyScores(anyLong());

        when(resultMapper.findCompanyEvaluationFile(OTHER)).thenReturn(null);
        assertTrue(message(() -> service.saveEvaluation(leader, 8L, OTHER, form("1", row("전략", "15", "14"))))
                .startsWith("이 업체의 평가표 엑셀을 먼저 올려 주세요"));
        // 경영지원실과 다른 팀 팀장은 입력하지 않는다
        assertThrows(AccessDeniedException.class, () -> service.saveEvaluation(mgmt, 8L, null, form("1", row("전략", "15", "14"))));
        assertThrows(AccessDeniedException.class, () -> service.saveEvaluation(otherLeader, 8L, null, form("1", row("전략", "15", "14"))));
    }

    @Test
    void 항목_이름은_끝의_배점_표시만_뗀다() {
        assertEquals("전략 및 방법론", ResultEntryServiceImpl.articleName(" 전략 및 방법론(15) "));
        assertEquals("프로젝트 관리", ResultEntryServiceImpl.articleName("프로젝트 관리 ( 16 )"));
        assertEquals("성능 및 품질", ResultEntryServiceImpl.articleName("성능 및 품질(25.5)"));
        assertEquals("1차(서면) 평가", ResultEntryServiceImpl.articleName("1차(서면) 평가"));
    }

    private static StatsArchiveDTO archive(Long teamSn) {
        StatsArchiveDTO a = new StatsArchiveDTO();
        a.setTeamSn(teamSn);
        return a;
    }

    private static ResultBidDTO bid(Long sn, String stts) {
        ResultBidDTO b = new ResultBidDTO();
        b.setPbancSn(sn);
        b.setPbancNm("사업 " + sn);
        b.setPbancSttsCd(stts);
        return b;
    }

    private static ResultCompanyDTO company(Long sn, int rank, String nm, String ours, Double tech, Double orgnl, Double prc, Double total) {
        ResultCompanyDTO c = new ResultCompanyDTO();
        c.setCoEvlRsltSn(sn);
        c.setOpengRnk(rank);
        c.setCoNm(nm);
        c.setOurCoYn(ours);
        c.setTechEvlScr(tech);
        c.setTechEvlOrgnlScr(orgnl);
        c.setPrcEvlScr(prc);
        c.setSnthsEvlScr(total);
        return c;
    }

    private static EvaluationFileDTO file(String orgnlFileNm) {
        EvaluationFileDTO f = new EvaluationFileDTO();
        f.setOrgnlFileNm(orgnlFileNm);
        f.setFilePathNm("evaluation/8");
        f.setStrgFileNm("abc.xlsx");
        return f;
    }

    private static EvaluationScoreDTO score(int cmsnrNo, String scr) {
        EvaluationScoreDTO s = new EvaluationScoreDTO();
        s.setEvlArtclSn(101L);
        s.setEvlArtclNm("전략 및 방법론");
        s.setEvlScorScr(new BigDecimal("15"));
        s.setCmsnrNo(cmsnrNo);
        s.setEvlScr(new BigDecimal(scr));
        return s;
    }

    private static CompanyArticleScoreDTO articleScore(Long co, Long artcl, String nm, String max, double avg) {
        CompanyArticleScoreDTO s = new CompanyArticleScoreDTO();
        s.setCoEvlRsltSn(co);
        s.setEvlArtclSn(artcl);
        s.setEvlArtclNm(nm);
        s.setEvlScorScr(new BigDecimal(max));
        s.setAvgScr(avg);
        return s;
    }

    /** 나라장터 평가표 모양의 엑셀 한 줄짜리 */
    private static byte[] excelBytes() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("평가결과");
            Row head = s.createRow(0);
            head.createCell(0).setCellValue("평가항목");
            head.createCell(1).setCellValue("배점기준");
            head.createCell(2).setCellValue("평가위원1");
            head.createCell(3).setCellValue("평가위원2");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("전략 및 방법론(15)");
            r.createCell(1).setCellValue("15");
            r.createCell(2).setCellValue(14.5);
            r.createCell(3).setCellValue(14.0);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
