package com.ojj.sujuro.proposal.rqt;

/** RFP 요구사항 (TB_RQT_MTTR). 목차에 연결되지 않으면 "미매핑"이다. (FR-050~054) */
public class RqtDto {

    private Long rqtMttrSn;
    private Long pbancSn;
    private String rqtMttrNo;
    private String clsfNm;
    private String rqtMttrNm;
    private String rqtMttrDfn;
    private String dtlCn;
    private String pageNo;
    private Long tocSn;
    private String rgtrId;

    /** 연결된 목차 (조회 때 계산) */
    private String tocNo;
    private String tocNm;
    /** 연결된 목차의 담당자 = 이 요구사항의 담당자 (FR-051) */
    private String picId;
    private String picNm;

    public Long getRqtMttrSn() { return rqtMttrSn; }
    public void setRqtMttrSn(Long rqtMttrSn) { this.rqtMttrSn = rqtMttrSn; }

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    /** 요구사항번호(RFP의 요구사항 ID). 같은 사업건 안에서 중복될 수 없다. */
    public String getRqtMttrNo() { return rqtMttrNo; }
    public void setRqtMttrNo(String rqtMttrNo) { this.rqtMttrNo = rqtMttrNo; }

    public String getClsfNm() { return clsfNm; }
    public void setClsfNm(String clsfNm) { this.clsfNm = clsfNm; }

    public String getRqtMttrNm() { return rqtMttrNm; }
    public void setRqtMttrNm(String rqtMttrNm) { this.rqtMttrNm = rqtMttrNm; }

    public String getRqtMttrDfn() { return rqtMttrDfn; }
    public void setRqtMttrDfn(String rqtMttrDfn) { this.rqtMttrDfn = rqtMttrDfn; }

    public String getDtlCn() { return dtlCn; }
    public void setDtlCn(String dtlCn) { this.dtlCn = dtlCn; }

    /** RFP 원본 쪽 */
    public String getPageNo() { return pageNo; }
    public void setPageNo(String pageNo) { this.pageNo = pageNo; }

    public Long getTocSn() { return tocSn; }
    public void setTocSn(Long tocSn) { this.tocSn = tocSn; }

    public String getRgtrId() { return rgtrId; }
    public void setRgtrId(String rgtrId) { this.rgtrId = rgtrId; }

    public String getTocNo() { return tocNo; }
    public void setTocNo(String tocNo) { this.tocNo = tocNo; }

    public String getTocNm() { return tocNm; }
    public void setTocNm(String tocNm) { this.tocNm = tocNm; }

    public String getPicId() { return picId; }
    public void setPicId(String picId) { this.picId = picId; }

    public String getPicNm() { return picNm; }
    public void setPicNm(String picNm) { this.picNm = picNm; }

    /** 어느 목차에도 연결되지 않았는지 */
    public boolean isUnmapped() {
        return tocSn == null;
    }
}
