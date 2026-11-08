package com.ojj.sujuro.proposal.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 제출서류 체크리스트 항목 (TB_SBMSN_DCMNT) + 가장 최근에 올린 파일. (FR-067~070)
 * 파일은 TB_ATCH_FILE(파일구분 FL050)에 쌓이고 이전 버전은 지우지 않는다. (NFR-009)
 * 삭제(숨김)한 파일은 "가장 최근 파일"과 파일 수에서 빠지고, 버전 번호 계산과 항목 삭제 판단에는 포함된다.
 */
@Getter
@Setter
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
    /** 가장 최근 파일을 올린 사람 (삭제 권한 판단) */
    private String fileRgtrId;
    private String orgnlFileNm;
    private Long fileSz;
    private String fileRgtrNm;
    private Date fileRegDt;
    /** 이 서류에 올린 파일 수 (삭제한 것 제외) */
    private int fileCnt;
    /** 삭제(숨김)한 것까지 포함한 파일 수. 버전 번호와 항목 삭제 판단에 쓴다. */
    private int fileTotal;

    public void setEsntlYn(String esntlYn) { this.esntlYn = esntlYn == null ? null : esntlYn.trim(); }

    public void setTkcgSeCd(String tkcgSeCd) { this.tkcgSeCd = tkcgSeCd == null ? null : tkcgSeCd.trim(); }

    public void setPrgrsSttsCd(String prgrsSttsCd) { this.prgrsSttsCd = prgrsSttsCd == null ? null : prgrsSttsCd.trim(); }

    public boolean isEssential() {
        return "Y".equals(esntlYn);
    }

    /** 제안팀이 준비하는 서류인지. 제안팀은 이런 서류의 준비 상태만 바꿀 수 있다. */
    public boolean isTeamOwned() {
        return OWNER_TEAM.equals(tkcgSeCd);
    }

    /** 체크리스트의 "최종 제안서" 항목인지 (띄어쓰기 무시). 위쪽 통계 카드에 준비 상태를 보여 준다. */
    public boolean isFinalProposal() {
        return dcmntNm != null && dcmntNm.replace(" ", "").contains("최종제안서");
    }

    /** 완료 = 등록된 파일이 있다 (11/08: 사람이 고르지 않고 파일로 정한다) */
    public boolean isReady() {
        return fileCnt > 0;
    }

    public String getOwnerNm() {
        return isTeamOwned() ? "제안팀" : "경영지원실";
    }

    public String getStatusNm() {
        return isReady() ? "완료" : "미완료"; // 파일이 있으면 완료
    }

    /** 준비 상태 표시 색 (common.css pill) */
    public String getStatusPill() {
        return isReady() ? "success" : "neutral";
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

    /** 제출 기한 날짜 칸 값 (yyyy-MM-dd) */
    public String getDueDate() {
        return sbmsnDdlnDt == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(sbmsnDdlnDt);
    }

    /** 제출 기한 시간 칸 값 (HH:mm). 0시면 시간을 넣지 않은 것으로 보고 비운다 */
    public String getDueTime() {
        if (sbmsnDdlnDt == null) {
            return "";
        }
        String t = new java.text.SimpleDateFormat("HH:mm").format(sbmsnDdlnDt);
        return "00:00".equals(t) ? "" : t;
    }

    /** 화면 표시: 날짜, 시간이 있으면 "날짜 시간" */
    public String getDueLabel() {
        if (sbmsnDdlnDt == null) {
            return "-";
        }
        String t = getDueTime();
        return t.isEmpty() ? getDueDate() : getDueDate() + " " + t;
    }
}
