package com.ojj.sujuro.proposal.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 제안서 파일 (TB_ATCH_FILE 중 파일구분 FL020 제안서 파트 / FL030 제안서 통합본 / FL040 제안요약서). (FR-057~066)
 * 같은 대상(목차 또는 통합본)에 다시 올리면 새 버전으로 쌓이고 이전 버전은 지우지 않는다. (FR-059, NFR-009)
 * 화면에서 삭제하면 삭제여부만 Y로 바꿔 목록에서 숨기고, 원본과 이력(삭제자·일시·사유)은 남긴다.
 * 제안서 파일 외에 제출서류(FL050)·조견표(FL060) 파일 정보도 이 클래스로 주고받는다.
 */
@Getter
@Setter
public class PrpFileDto {

    /** 파일구분코드 (공통코드 FILSE) */
    public static final String SE_PART = "FL020";    // 제안서 파트 (목차별)
    public static final String SE_FULL = "FL030";    // 제안서 통합본
    public static final String SE_SUMMARY = "FL040"; // 제안요약서 (목차별)

    private Long fileSn;
    private Long pbancSn;
    private String fileSeCd;
    /** 목차별 파일(파트·제안요약서)일 때 목차일련번호 */
    private Long tocSn;
    /** 통합본 차수 (1차, 2차 ...) */
    private Integer intgCycl;
    private String verNo;
    private String lastYn;
    private String dcsmkrId;
    private String dcsmkrNm;
    private Date cfmtnDt;
    private String orgnlFileNm;
    /** 서버에 저장한 이름 (UUID + 확장자) */
    private String strgFileNm;
    /** 업로드 폴더(app.upload-dir) 아래 상대 경로 (예: proposal/12) */
    private String filePathNm;
    private Long fileSz;
    private String fileExtnNm;
    private String memoCn;
    private String rgtrId;
    private String rgtrNm;
    private Date regDt;
    private String delYn;
    private String dltrId;
    private String dltrNm;
    private Date delDt;
    private String delRsn;
    /** 화면 표시용 대상 이름 (목차 번호·이름, 통합본 차수, 서류명) */
    private String targetNm;

    public void setFileSeCd(String fileSeCd) { this.fileSeCd = fileSeCd == null ? null : fileSeCd.trim(); }

    public void setLastYn(String lastYn) { this.lastYn = lastYn == null ? null : lastYn.trim(); }

    public void setDelYn(String delYn) { this.delYn = delYn == null ? null : delYn.trim(); }

    /** 삭제(숨김)된 파일인지 */
    public boolean isDeleted() {
        return "Y".equals(delYn);
    }

    /** 팀장이 확정한 최종본인지 (FR-064) */
    public boolean isLast() {
        return "Y".equals(lastYn);
    }

    /** 화면에 보여 줄 크기 (KB / MB) */
    public String getSizeLabel() {
        if (fileSz == null) {
            return "-";
        }
        if (fileSz >= 1024L * 1024L) {
            return String.format("%.1fMB", fileSz / 1024.0 / 1024.0);
        }
        return Math.max(1, Math.round(fileSz / 1024.0)) + "KB";
    }
}
