package com.ojj.sujuro.proposal.dto;

import java.util.Date;

/** 사업건의 나라장터 등록(제출) 정보 (TB_BID_PBANC). (FR-073) */
public class SubmissionDto {

    /** 공고상태코드 (공통코드 PBSTS) */
    public static final String ST_CONFIRMED = "PS030"; // 참여 확정
    public static final String ST_WRITING = "PS040";   // 제안 작성 중
    public static final String ST_SUBMITTED = "PS050"; // 제출 완료
    public static final String ST_RESULT = "PS060";    // 결과 등록

    private Long pbancSn;
    private String pbancSttsCd;
    private Date sbmsnDt;
    private String prsnrId;
    private String prsnrNm;

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    public String getPbancSttsCd() { return pbancSttsCd; }
    public void setPbancSttsCd(String pbancSttsCd) { this.pbancSttsCd = pbancSttsCd == null ? null : pbancSttsCd.trim(); }

    /** 나라장터 등록 일시 */
    public Date getSbmsnDt() { return sbmsnDt; }
    public void setSbmsnDt(Date sbmsnDt) { this.sbmsnDt = sbmsnDt; }

    /** 등록한 경영지원실 담당자 */
    public String getPrsnrId() { return prsnrId; }
    public void setPrsnrId(String prsnrId) { this.prsnrId = prsnrId; }

    public String getPrsnrNm() { return prsnrNm; }
    public void setPrsnrNm(String prsnrNm) { this.prsnrNm = prsnrNm; }

    /** 이미 제출(나라장터 등록)했는지. 제출한 뒤에는 체크리스트·조견표를 고칠 수 없다. */
    public boolean isSubmitted() {
        return ST_SUBMITTED.equals(pbancSttsCd) || ST_RESULT.equals(pbancSttsCd);
    }

    /** 나라장터 등록 완료 처리를 할 수 있는 상태인지 (참여 확정·제안 작성 중) (NFR-012) */
    public boolean isSubmittable() {
        return ST_CONFIRMED.equals(pbancSttsCd) || ST_WRITING.equals(pbancSttsCd);
    }
}
