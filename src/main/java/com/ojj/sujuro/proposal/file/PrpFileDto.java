package com.ojj.sujuro.proposal.file;

import java.util.Date;

/**
 * 제안서 파일 (TB_ATCH_FILE 중 파일구분 FL020 제안서 파트 / FL030 제안서 통합본 / FL040 제안요약서). (FR-057~066)
 * 같은 대상(목차 또는 통합본)에 다시 올리면 새 버전으로 쌓이고 이전 버전은 지우지 않는다. (FR-059, NFR-009)
 * 화면에서 삭제하면 삭제여부만 Y로 바꿔 목록에서 숨기고, 원본과 이력(삭제자·일시·사유)은 남긴다.
 * 제안서 파일 외에 제출서류(FL050)·조견표(FL060) 파일 정보도 이 클래스로 주고받는다.
 */
public class PrpFileDto {

    /** 파일구분코드 (공통코드 FILSE) */
    public static final String SE_PART = "FL020";    // 제안서 파트 (목차별)
    public static final String SE_FULL = "FL030";    // 제안서 통합본
    public static final String SE_SUMMARY = "FL040"; // 제안요약서 (목차별)

    private Long fileSn;
    private Long pbancSn;
    private String fileSeCd;
    private Long tocSn;
    private Integer intgCycl;
    private String verNo;
    private String lastYn;
    private String dcsmkrId;
    private String dcsmkrNm;
    private Date cfmtnDt;
    private String orgnlFileNm;
    private String strgFileNm;
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

    public Long getFileSn() { return fileSn; }
    public void setFileSn(Long fileSn) { this.fileSn = fileSn; }

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    public String getFileSeCd() { return fileSeCd; }
    public void setFileSeCd(String fileSeCd) { this.fileSeCd = fileSeCd == null ? null : fileSeCd.trim(); }

    /** 목차별 파일(파트·제안요약서)일 때 목차일련번호 */
    public Long getTocSn() { return tocSn; }
    public void setTocSn(Long tocSn) { this.tocSn = tocSn; }

    /** 통합본 차수 (1차, 2차 ...) */
    public Integer getIntgCycl() { return intgCycl; }
    public void setIntgCycl(Integer intgCycl) { this.intgCycl = intgCycl; }

    public String getVerNo() { return verNo; }
    public void setVerNo(String verNo) { this.verNo = verNo; }

    public String getLastYn() { return lastYn; }
    public void setLastYn(String lastYn) { this.lastYn = lastYn == null ? null : lastYn.trim(); }

    public String getDcsmkrId() { return dcsmkrId; }
    public void setDcsmkrId(String dcsmkrId) { this.dcsmkrId = dcsmkrId; }

    public String getDcsmkrNm() { return dcsmkrNm; }
    public void setDcsmkrNm(String dcsmkrNm) { this.dcsmkrNm = dcsmkrNm; }

    public Date getCfmtnDt() { return cfmtnDt; }
    public void setCfmtnDt(Date cfmtnDt) { this.cfmtnDt = cfmtnDt; }

    public String getOrgnlFileNm() { return orgnlFileNm; }
    public void setOrgnlFileNm(String orgnlFileNm) { this.orgnlFileNm = orgnlFileNm; }

    /** 서버에 저장한 이름 (UUID + 확장자) */
    public String getStrgFileNm() { return strgFileNm; }
    public void setStrgFileNm(String strgFileNm) { this.strgFileNm = strgFileNm; }

    /** 업로드 폴더(app.upload-dir) 아래 상대 경로 (예: proposal/12) */
    public String getFilePathNm() { return filePathNm; }
    public void setFilePathNm(String filePathNm) { this.filePathNm = filePathNm; }

    public Long getFileSz() { return fileSz; }
    public void setFileSz(Long fileSz) { this.fileSz = fileSz; }

    public String getFileExtnNm() { return fileExtnNm; }
    public void setFileExtnNm(String fileExtnNm) { this.fileExtnNm = fileExtnNm; }

    public String getMemoCn() { return memoCn; }
    public void setMemoCn(String memoCn) { this.memoCn = memoCn; }

    public String getRgtrId() { return rgtrId; }
    public void setRgtrId(String rgtrId) { this.rgtrId = rgtrId; }

    public String getRgtrNm() { return rgtrNm; }
    public void setRgtrNm(String rgtrNm) { this.rgtrNm = rgtrNm; }

    public Date getRegDt() { return regDt; }
    public void setRegDt(Date regDt) { this.regDt = regDt; }

    public String getDelYn() { return delYn; }
    public void setDelYn(String delYn) { this.delYn = delYn == null ? null : delYn.trim(); }

    public String getDltrId() { return dltrId; }
    public void setDltrId(String dltrId) { this.dltrId = dltrId; }

    public String getDltrNm() { return dltrNm; }
    public void setDltrNm(String dltrNm) { this.dltrNm = dltrNm; }

    public Date getDelDt() { return delDt; }
    public void setDelDt(Date delDt) { this.delDt = delDt; }

    public String getDelRsn() { return delRsn; }
    public void setDelRsn(String delRsn) { this.delRsn = delRsn; }

    public String getTargetNm() { return targetNm; }
    public void setTargetNm(String targetNm) { this.targetNm = targetNm; }

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
