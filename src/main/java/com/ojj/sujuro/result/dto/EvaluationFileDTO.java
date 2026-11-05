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
    /** 이 파일을 올린 뒤 저장한 평가위원 수 (TB_EVL_TBL_UPLD.CMSNR_CNT). 올리기만 하고 아직 저장하지 않았으면 null */
    private Integer cmsnrCnt;

    /** 올리기만 하고 아직 저장하지 않은 엑셀인지. 그러면 입력 칸을 이 엑셀에서 채운다 (11/05) */
    public boolean isUnsaved() {
        return cmsnrCnt == null;
    }
}
