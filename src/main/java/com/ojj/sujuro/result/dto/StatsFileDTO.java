package com.ojj.sujuro.result.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/** 사업별 결과 분석의 제출 산출물: 팀장이 최종본으로 확정한 제안서 통합본 (TB_ATCH_FILE, FL030, LAST_YN = Y) */
@Getter
@Setter
public class StatsFileDTO {

    private Long fileSn;
    private String orgnlFileNm;
    private String strgFileNm;
    private String filePathNm;

    /** 최종본으로 확정한 팀장 이름 */
    private String dcsmkrNm;
    private Date cfmtnDt;
}
