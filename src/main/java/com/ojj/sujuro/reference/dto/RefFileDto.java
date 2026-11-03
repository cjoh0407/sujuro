package com.ojj.sujuro.reference.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 참고자료 한 건 (TB_REF_FILE). 제안서를 쓸 때 쓰는 폰트·이미지·공유 자료를 같은 팀끼리 나눈다.
 * 사업건이 아니라 팀에 붙는다. 삭제는 목록에서만 숨기고(DEL_YN='Y') 파일은 서버에 남긴다.
 */
@Getter
@Setter
public class RefFileDto {

    /** 자료구분코드 (공통코드 REFSE) */
    public static final String SE_FONT = "RF010";
    public static final String SE_IMAGE = "RF020";
    public static final String SE_DOC = "RF030";
    public static final String SE_ETC = "RF040";

    private Long refFileSn;
    private Long teamSn;
    private String refSeCd;
    private String orgnlFileNm;
    /** 서버에 저장한 이름 (UUID + 확장자) */
    private String strgFileNm;
    /** 업로드 폴더(app.upload-dir) 아래 상대 경로 (예: reference/3) */
    private String filePathNm;
    private Long fileSz;
    private String fileExtnNm;
    private String memoCn;
    private String rgtrId;
    private String rgtrNm;
    private Date regDt;

    /** 화면에 보여 줄 구분 이름 */
    public String getSeNm() {
        if (SE_FONT.equals(refSeCd)) {
            return "폰트";
        }
        if (SE_IMAGE.equals(refSeCd)) {
            return "이미지";
        }
        if (SE_DOC.equals(refSeCd)) {
            return "문서";
        }
        return "기타";
    }

    /** 화면에 보여 줄 크기 (KB / MB) */
    public String getSizeText() {
        if (fileSz == null) {
            return "-";
        }
        if (fileSz >= 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1fMB", fileSz / 1024.0 / 1024.0);
        }
        return Math.max(1, Math.round(fileSz / 1024.0)) + "KB";
    }

    /** 목록에서 작은 그림으로 보여 줄 수 있는 이미지인가 (SVG는 스크립트가 들어갈 수 있어 제외) */
    public boolean isPreviewable() {
        return SE_IMAGE.equals(refSeCd) && fileExtnNm != null && !"svg".equals(fileExtnNm);
    }
}
