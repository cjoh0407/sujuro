package com.ojj.sujuro.proposal.dto;

import java.util.Date;

/**
 * 제출서류 체크리스트 항목 (TB_SBMSN_DCMNT) + 가장 최근에 올린 파일. (FR-067~070)
 * 파일은 TB_ATCH_FILE(파일구분 FL050)에 쌓이고 이전 버전은 지우지 않는다. (NFR-009)
 * 삭제(숨김)한 파일은 "가장 최근 파일"과 파일 수에서 빠지고, 버전 번호 계산과 항목 삭제 판단에는 포함된다.
 */
public class DocDto {

    /** 담당구분코드 (공통코드 TKCGS) */
    public static final String OWNER_MGMT = "TK010"; // 경영지원실
    public static final String OWNER_TEAM = "TK020"; // 제안팀

    /** 서류준비상태 (공통코드 DCPST) */
    public static final String ST_NONE = "DS010";  // 미준비
    public static final String ST_DOING = "DS020"; // 준비 중
    public static final String ST_READY = "DS030"; // 준비 완료

    private Long dcmntSn;
    private Long pbancSn;
    private String dcmntNm;
    private String esntlYn;
    private String tkcgSeCd;
    private String prgrsSttsCd;
    private Date sbmsnDdlnDt;
    private String rmrkCn;

    /** 가장 최근 파일 (없으면 null) */
    private Long fileSn;
    private String fileRgtrId;
    private String orgnlFileNm;
    private Long fileSz;
    private String fileRgtrNm;
    private Date fileRegDt;
    /** 이 서류에 올린 파일 수 (삭제한 것 제외) */
    private int fileCnt;
    /** 삭제(숨김)한 것까지 포함한 파일 수. 버전 번호와 항목 삭제 판단에 쓴다. */
    private int fileTotal;

    public Long getDcmntSn() { return dcmntSn; }
    public void setDcmntSn(Long dcmntSn) { this.dcmntSn = dcmntSn; }

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    public String getDcmntNm() { return dcmntNm; }
    public void setDcmntNm(String dcmntNm) { this.dcmntNm = dcmntNm; }

    public String getEsntlYn() { return esntlYn; }
    public void setEsntlYn(String esntlYn) { this.esntlYn = esntlYn == null ? null : esntlYn.trim(); }

    public String getTkcgSeCd() { return tkcgSeCd; }
    public void setTkcgSeCd(String tkcgSeCd) { this.tkcgSeCd = tkcgSeCd == null ? null : tkcgSeCd.trim(); }

    public String getPrgrsSttsCd() { return prgrsSttsCd; }
    public void setPrgrsSttsCd(String prgrsSttsCd) { this.prgrsSttsCd = prgrsSttsCd == null ? null : prgrsSttsCd.trim(); }

    public Date getSbmsnDdlnDt() { return sbmsnDdlnDt; }
    public void setSbmsnDdlnDt(Date sbmsnDdlnDt) { this.sbmsnDdlnDt = sbmsnDdlnDt; }

    public String getRmrkCn() { return rmrkCn; }
    public void setRmrkCn(String rmrkCn) { this.rmrkCn = rmrkCn; }

    public Long getFileSn() { return fileSn; }
    public void setFileSn(Long fileSn) { this.fileSn = fileSn; }

    /** 가장 최근 파일을 올린 사람 (삭제 권한 판단) */
    public String getFileRgtrId() { return fileRgtrId; }
    public void setFileRgtrId(String fileRgtrId) { this.fileRgtrId = fileRgtrId; }

    public String getOrgnlFileNm() { return orgnlFileNm; }
    public void setOrgnlFileNm(String orgnlFileNm) { this.orgnlFileNm = orgnlFileNm; }

    public Long getFileSz() { return fileSz; }
    public void setFileSz(Long fileSz) { this.fileSz = fileSz; }

    public String getFileRgtrNm() { return fileRgtrNm; }
    public void setFileRgtrNm(String fileRgtrNm) { this.fileRgtrNm = fileRgtrNm; }

    public Date getFileRegDt() { return fileRegDt; }
    public void setFileRegDt(Date fileRegDt) { this.fileRegDt = fileRegDt; }

    public int getFileCnt() { return fileCnt; }
    public void setFileCnt(int fileCnt) { this.fileCnt = fileCnt; }

    public int getFileTotal() { return fileTotal; }
    public void setFileTotal(int fileTotal) { this.fileTotal = fileTotal; }

    public boolean isEssential() {
        return "Y".equals(esntlYn);
    }

    /** 제안팀이 준비하는 서류인지. 제안팀은 이런 서류의 준비 상태만 바꿀 수 있다. */
    public boolean isTeamOwned() {
        return OWNER_TEAM.equals(tkcgSeCd);
    }

    public boolean isReady() {
        return ST_READY.equals(prgrsSttsCd);
    }

    public String getOwnerNm() {
        return isTeamOwned() ? "제안팀" : "경영지원실";
    }

    public String getStatusNm() {
        return ST_READY.equals(prgrsSttsCd) ? "준비 완료" : ST_DOING.equals(prgrsSttsCd) ? "준비 중" : "미준비";
    }

    /** 준비 상태 표시 색 (common.css pill) */
    public String getStatusPill() {
        return ST_READY.equals(prgrsSttsCd) ? "success" : ST_DOING.equals(prgrsSttsCd) ? "warning" : "neutral";
    }

    public String getSizeLabel() {
        if (fileSz == null) {
            return "";
        }
        if (fileSz >= 1024L * 1024L) {
            return String.format("%.1fMB", fileSz / 1024.0 / 1024.0);
        }
        return Math.max(1, Math.round(fileSz / 1024.0)) + "KB";
    }
}
