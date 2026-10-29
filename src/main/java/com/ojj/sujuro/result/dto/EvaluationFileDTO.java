package com.ojj.sujuro.result.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/** 마지막으로 올린 평가표 엑셀 (TB_ATCH_FILE, 파일구분 FL070) */
@Getter
@Setter
public class EvaluationFileDTO {

    private Long fileSn;
    private String orgnlFileNm;
    private String strgFileNm;
    private String filePathNm;
    private String rgtrNm;
    private Date regDt;
}
