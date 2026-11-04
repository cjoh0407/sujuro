package com.ojj.sujuro.bid.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 공고 상세의 나라장터 원문 주소 (11/04): 사전규격은 등록번호로 만들고, 입찰공고부터는 API 가 준 주소를 쓴다 */
class NoticeDetailDTOTest {

    private static final String BID_URL = "https://www.g2b.go.kr/link/PNPE027_01/single/?bidPbancNo=R26BK99990001&bidPbancOrd=000";

    @Test
    void 사전규격은_등록번호로_주소를_만든다() {
        NoticeDetailDTO n = notice("PP020", "R26BD99990001", null);

        assertEquals("https://www.g2b.go.kr/link/PRVA004_02/single/?bfSpecRegNo=R26BD99990001", n.getSourceUrl());
    }

    @Test
    void 사전규격이라도_등록번호가_없으면_주소가_없다() {
        assertNull(notice("PP020", null, null).getSourceUrl());
        assertNull(notice("PP020", "", null).getSourceUrl());
    }

    @Test
    void 입찰공고부터는_사전규격_번호가_있어도_API_가_준_주소를_쓴다() {
        for (String step : new String[] { "PP030", "PP040", "PP050" }) {
            assertEquals(BID_URL, notice(step, "R26BD99990001", BID_URL).getSourceUrl(), step);
        }
    }

    @Test
    void 발주계획과_주소를_못_받은_입찰공고는_주소가_없다() {
        assertNull(notice("PP010", null, null).getSourceUrl());
        assertNull(notice("PP030", "R26BD99990001", null).getSourceUrl());
    }

    private static NoticeDetailDTO notice(String stepCd, String bfSpecRgstNo, String pbancDtlUrl) {
        NoticeDetailDTO n = new NoticeDetailDTO();
        n.setPbancStepCd(stepCd);
        n.setBfSpecRgstNo(bfSpecRgstNo);
        n.setPbancDtlUrl(pbancDtlUrl);
        return n;
    }
}
