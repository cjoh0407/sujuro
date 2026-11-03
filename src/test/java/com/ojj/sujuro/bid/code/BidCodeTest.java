package com.ojj.sujuro.bid.code;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ojj.sujuro.bid.dto.StepCountDTO;

/** 화면의 진행상태 일곱 가지와 공고 목록의 단계 탭 이름 (11/03 수정사항) */
class BidCodeTest {

    @Test
    void 공고상태_코드를_진행상태_일곱_가지로_보여_준다() {
        assertEquals("배정 전", BidCode.progressNm("PS010"));
        assertEquals("배정 완료", BidCode.progressNm("PS020"));
        assertEquals("제안 작성", BidCode.progressNm("PS030"));
        assertEquals("제안 작성", BidCode.progressNm("PS040"));
        assertEquals("제안 제출", BidCode.progressNm("PS050"));
        assertEquals("결과 등록", BidCode.progressNm("PS060"));
        assertEquals("유찰", BidCode.progressNm("PS070"));
        assertEquals("불참", BidCode.progressNm("PS090"));
        assertEquals("제안 작성", BidCode.progressNm("PS030 ")); // CHAR(5) 뒤 공백
    }

    @Test
    void 유찰은_빨강_끝난_공고는_회색이다() {
        assertEquals("danger", BidCode.progressPillClass("PS070"));
        assertEquals("neutral", BidCode.progressPillClass("PS060"));
        assertEquals("neutral", BidCode.progressPillClass("PS090"));
    }

    @Test
    void 개찰과_계약은_탭에서_개찰결과_계약현황으로_부른다() {
        assertEquals("개찰결과", tab("PP040", "개찰").getTabNm());
        assertEquals("계약현황", tab("PP050", "계약").getTabNm());
        assertEquals("사전규격", tab("PP020", "사전규격").getTabNm());
    }

    private static StepCountDTO tab(String cd, String nm) {
        StepCountDTO t = new StepCountDTO();
        t.setStepCd(cd);
        t.setStepNm(nm);
        return t;
    }
}
