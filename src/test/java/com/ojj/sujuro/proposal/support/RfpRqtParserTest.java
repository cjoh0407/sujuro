package com.ojj.sujuro.proposal.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ojj.sujuro.proposal.dto.RqtExtractDto;

/** 제안요청서 요구사항 추출 정규식 확인 (PDF 없이 PDFBox가 뽑은 것과 같은 모양의 글자로 시험한다) */
class RfpRqtParserTest {

    private static final String HEAD =
            "가. 요구사항 총괄표\n"
            + "요청사항 구분 ID 부여규칙 요구사항수\n"
            + "기능 기능 요구사항 SFR-00 3\n"
            + "비기능 성능 요구사항 PER-00 2\n";

    @Test
    void 개수가_맞으면_경고가_없다() {
        RqtExtractDto r = RfpRqtParser.parse(HEAD
                + "합 계 5\n"
                + "나. 요구사항 목록표\n"
                + "No ID 요구사항명\n"
                + "01 SFR 01 이용신청 일원화\n"
                + "02 SFR 02 연구분석 관련 프로세스 개선\n"
                + "- 18 -\n"
                + "No ID 요구사항명\n"
                + "03 SFR-003 로그인 절차 개선\n"
                + "04 PER 01 시스템 성능 일반요건\n"
                + "05 PER02 응답시간\n");
        assertEquals(5, r.getFoundCount());
        assertEquals("SFR-001", r.getItems().get(0).getRqtMttrNo());
        assertEquals("이용신청 일원화", r.getItems().get(0).getRqtMttrNm());
        assertEquals("SFR-003", r.getItems().get(2).getRqtMttrNo());
        assertEquals("PER-002", r.getItems().get(4).getRqtMttrNo());
        assertTrue(r.getWarnings().isEmpty(), r.getWarnings().toString());
    }

    @Test
    void 합계_숫자가_틀리면_경고만_남긴다() {
        RqtExtractDto r = RfpRqtParser.parse(HEAD
                + "합 계 4\n"
                + "01 SFR 01 가\n02 SFR 02 나\n03 SFR 03 다\n04 PER 01 라\n05 PER 02 마\n");
        assertEquals(5, r.getFoundCount());
        assertEquals(1, r.getWarnings().size());
        assertTrue(r.getWarnings().get(0).contains("합계(4개)"));
    }

    @Test
    void 빠진_요구사항과_접두어별_개수를_알려_준다() {
        RqtExtractDto r = RfpRqtParser.parse(HEAD
                + "합 계 5\n"
                + "01 SFR 01 가\n02 SFR 02 나\n04 PER 01 라\n05 PER 02 마\n");
        assertEquals(4, r.getFoundCount());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.startsWith("SFR 요구사항 개수가 다릅니다: 총괄표 3개 · 목록 2개")));
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("읽지 못한 번호가 있습니다: 3")));
    }

    @Test
    void 두_줄로_넘어간_이름을_이어_붙인다() {
        RqtExtractDto r = RfpRqtParser.parse("01 SFR 01 개인정보 접속기록 관리를 위한\n프로그램 개발 및 적용\n02 SFR 02 다음\n");
        assertEquals("개인정보 접속기록 관리를 위한 프로그램 개발 및 적용", r.getItems().get(0).getRqtMttrNm());
    }

    /** 위치가 있는 줄 만들기: 낱말은 공백으로 나누고, 처음 낱말 x는 번호 칸·이름 칸 위치를 흉내 낸다 */
    private static RfpRqtParser.Line line(float y, String text) {
        RfpRqtParser.Line l = new RfpRqtParser.Line(1, y);
        String[] w = text.trim().split("\\s+");
        boolean row = w.length >= 3 && w[0].matches("\\d+") && w[1].matches("[A-Z]{2,4}");
        for (int i = 0; i < w.length; i++) {
            float x = row && i < 3 ? 70 + i * 100 : 375 + (row ? i - 3 : i) * 30; // 이름은 x=375부터
            l.add(w[i], x, y);
        }
        return l;
    }

    @Test
    void 가운데_정렬_칸은_줄_간격으로_위아래_요구사항을_나눈다() {
        // 같은 칸 안의 줄 간격 18, 칸이 바뀌면 25. 02번 이름 첫 줄은 번호보다 위에 찍힌다
        List<RfpRqtParser.Line> ls = new ArrayList<>();
        ls.add(line(100, "01 SFR 01 통합 검색 기능 고도화"));
        ls.add(line(125, "전자결재 연계 및 관련 법령"));
        ls.add(line(143, "02 SFR 02 지침에 따른 이행 방안"));
        ls.add(line(161, "결과 보고"));
        ls.add(line(186, "03 SFR 03 대시보드 화면 개선"));
        RqtExtractDto r = RfpRqtParser.parse(ls);
        assertEquals("통합 검색 기능 고도화", r.getItems().get(0).getRqtMttrNm());
        assertEquals("전자결재 연계 및 관련 법령 지침에 따른 이행 방안 결과 보고", r.getItems().get(1).getRqtMttrNm());
        assertEquals("대시보드 화면 개선", r.getItems().get(2).getRqtMttrNm());
        assertEquals("이름 여러 줄", r.getItems().get(1).getNote());
        assertTrue(r.getWarnings().stream().noneMatch(w -> w.contains("확인 필요")), r.getWarnings().toString());
    }

    @Test
    void 번호만_따로_찍힌_줄도_이름을_모은다() {
        List<RfpRqtParser.Line> ls = new ArrayList<>();
        ls.add(line(100, "01 PER 01 응답시간"));
        ls.add(line(125, "부하 테스트 및"));
        ls.add(line(134, "02 PER 02"));
        ls.add(line(143, "성능 개선"));
        ls.add(line(168, "03 PER 03 일반 성능"));
        RqtExtractDto r = RfpRqtParser.parse(ls);
        assertEquals(3, r.getFoundCount());
        assertEquals("부하 테스트 및 성능 개선", r.getItems().get(1).getRqtMttrNm());
    }

    @Test
    void 목록이_없으면_수기_등록을_안내한다() {
        RqtExtractDto r = RfpRqtParser.parse("스캔한 문서\n");
        assertEquals(0, r.getFoundCount());
        assertEquals(1, r.getWarnings().size());
    }

    @Test
    void 총괄표의_구분명을_접두어별로_읽고_기능_비기능_분류는_뗀다() {
        RqtExtractDto r = RfpRqtParser.parse(HEAD
                + "제약사항 COR-00 1\n"
                + "합 계 6\n"
                + "01 SFR 01 가\n02 SFR 02 나\n03 SFR 03 다\n04 PER 01 라\n05 PER 02 마\n06 COR 01 바\n");
        assertEquals("기능 요구사항", r.getGroupNames().get("SFR"));
        assertEquals("성능 요구사항", r.getGroupNames().get("PER"));
        assertEquals("제약사항", r.getGroupNames().get("COR"));
        assertTrue(r.isGroupStart(0));
        assertTrue(!r.isGroupStart(1));
        assertTrue(r.isGroupStart(3));
        assertEquals("성능 요구사항", r.groupTitle(3));
        assertEquals("PER · 2개", r.groupSub(3));
        assertEquals("기능 요구사항", RfpRqtParser.groupName("기능 요구사항"));
        assertEquals("보안 요구사항", RfpRqtParser.groupName("비기능   보안 요구사항"));
    }

    @Test
    void 목록표_구분_칸이_번호와_ID_사이에_있어도_읽고_구분명으로_쓴다() {
        RqtExtractDto r = RfpRqtParser.parse("2-2 요구사항 목록\n"
                + "연번 구분 고유번호 요구사항 명칭\n"
                + "1 MAR 1 사업 대상 및 범위에 관한 이해\n"
                + "2 유지관리 수행 요구사항 MAR 2 유지관리 사업 수행 방안\n"
                + "3 (Maintenance Requirement) MAR 3 장애 관리 수행 방안\n"
                + "4 보안 요구사항 SER 1 보안 관리 일반\n"
                + "5 (Security Requirement) SER 2 인원 보안\n");
        assertEquals(5, r.getFoundCount());
        assertEquals("MAR-002", r.getItems().get(1).getRqtMttrNo());
        assertEquals("유지관리 사업 수행 방안", r.getItems().get(1).getRqtMttrNm());
        assertEquals("SER-002", r.getItems().get(4).getRqtMttrNo());
        assertEquals("인원 보안", r.getItems().get(4).getRqtMttrNm());
        assertEquals("유지관리 수행 요구사항", r.getGroupNames().get("MAR"));
        assertEquals("보안 요구사항", r.getGroupNames().get("SER"));
    }

    @Test
    void 총괄표가_접두어_영문이름_모양이어도_구분명과_개수를_읽는다() {
        RqtExtractDto r = RfpRqtParser.parse("2-1 요구사항 총괄표\n"
                + "요구사항 구분 요구사항 수\n"
                + "유지관리 수행 요구사항 MAR(Maintenance Requirement) 2\n"
                + "보안 요구사항 SER(Security Requirement)\n"
                + "1 MAR 1 가\n2 MAR 2 나\n3 SER 1 다\n");
        assertEquals(3, r.getFoundCount());
        assertEquals("유지관리 수행 요구사항", r.getGroupNames().get("MAR"));
        assertEquals("보안 요구사항", r.getGroupNames().get("SER"));
        assertEquals(Integer.valueOf(2), r.getExpected().get("MAR"));
        assertTrue(r.isSummaryFound());
    }

    @Test
    void 번호_없이_ID부터_나오는_목록과_구분별_개수를_읽는다() {
        RqtExtractDto r = RfpRqtParser.parse("(1) 요구사항 총괄표\n"
                + "구 분 요구사항ID 요구사항 명칭 요구사항 수\n"
                + "MAR-001 유지관리 수행 방안 제시\n"
                + "유지관리 수행 MAR-002 유지관리 대상\n"
                + "요구사항 MAR-003 정보화 평가 지원 3\n"
                + "성능 요구사항(PER) PER-001 웹페이지 응답시간 1\n"
                + "합계 4\n"
                + "요구사항 고유번호 MAR-001\n");   // 아래 상세 설명 표는 목록이 아니다
        assertEquals(4, r.getFoundCount());
        assertEquals("MAR-003", r.getItems().get(2).getRqtMttrNo());
        assertEquals("정보화 평가 지원", r.getItems().get(2).getRqtMttrNm());
        assertEquals("성능 요구사항", r.getGroupNames().get("PER"));
        assertEquals(Integer.valueOf(3), r.getExpected().get("MAR"));
        assertTrue(r.getWarnings().isEmpty(), r.getWarnings().toString());
    }

    @Test
    void 가운데_영문이_붙은_ID와_윗줄_구분명을_읽는다() {
        RqtExtractDto r = RfpRqtParser.parse("1) 요구사항 총괄표\n"
                + "기능요구사항 System Function\n"
                + "FUN-QMS00 2\n"
                + "(FUN) Requirement\n"
                + "성능요구사항\n"
                + "Performance Requirement PER-000 1\n"
                + "(PER)\n"
                + "합계 3\n"
                + "1 FUN-QMS01 결함등록연계 관리 개선\n"
                + "2 FUN-QMS02 결함처리 통계 기능 개선\n"
                + "3 PER-001 화면 디스플레이 시간\n");
        assertEquals(3, r.getFoundCount());
        assertEquals("FUN-QMS01", r.getItems().get(0).getRqtMttrNo());   // ID는 원래 모양 그대로
        assertEquals("결함등록연계 관리 개선", r.getItems().get(0).getRqtMttrNm());
        assertEquals("기능요구사항", r.getGroupNames().get("FUN"));
        assertEquals("성능요구사항", r.getGroupNames().get("PER"));
        assertEquals(Integer.valueOf(2), r.getExpected().get("FUN"));
        assertTrue(r.getWarnings().isEmpty(), r.getWarnings().toString());
    }
}
