package com.ojj.sujuro.result.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 결과를 기다리는 공고: 제출 완료(PS050)인 공고 (ResultMapper.findResultTargets, findResultTarget) */
@Getter
@Setter
public class ResultTargetDTO {

    private Long pbancSn;
    private String bidPbancNo;
    private String pbancNm;
    /** 기술 배점(비율). 비어 있으면 결과를 등록할 때 입찰공고 API 에서 채운다(10/29 전에 등록한 공고) */
    private BigDecimal techScorScr;
    /** 주관 팀. 결과가 등록되면 이 팀의 팀장에게 평가 점수·장단점을 입력하라고 알린다 */
    private Long sprvsnTeamSn;
    /**
     * 컨소시엄 참여·하도급 사업에서 함께 참여한 업체(주관사·원도급사)의 사업자번호 10자리 (11/10).
     * 개찰 목록에 우리 회사 대신 이 업체가 올라오므로, 값이 있으면 우리 회사 번호(company.biz-no) 대신 이 번호의 행을 우리 쪽으로 본다.
     * 담당 팀 팀장이 결과 화면에서 넣고, 개찰 목록의 그 업체 행(OUR_CO_YN = 'Y')에 남아 자동 수집이 이어받는다.
     */
    private String partnerBizNo;
}
