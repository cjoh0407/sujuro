package com.ojj.sujuro.bid.dto;

import com.ojj.sujuro.bid.code.BidCode;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고 목록 탭 하나 (BidNoticeMapper.findStepCounts). 11/03 진행상태 탭에서 공고단계 탭으로 바꿨다.
 * 건수가 0인 단계도 탭으로 보여 주기 때문에 TB_COM_CD 를 기준으로 만든다.
 */
@Getter
@Setter
public class StepCountDTO {

    /** 공고단계코드 (PBSTP) */
    private String stepCd;
    private String stepNm;
    private int cnt;

    /** 탭 이름. 개찰·계약은 탭에서 '개찰결과'·'계약현황'으로 부른다 (11/03 수정사항) */
    public String getTabNm() {
        if (BidCode.is(stepCd, BidCode.STEP_OPENING)) {
            return "개찰결과";
        }
        if (BidCode.is(stepCd, BidCode.STEP_CONTRACT)) {
            return "계약현황";
        }
        return stepNm;
    }
}
