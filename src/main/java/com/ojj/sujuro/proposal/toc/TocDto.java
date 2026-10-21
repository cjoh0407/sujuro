package com.ojj.sujuro.proposal.toc;

/**
 * 제안서 목차 (TB_TOC). 공고일련번호가 NULL이면 회사 공통 양식, 값이 있으면 그 사업건의 목차다. (FR-046~048)
 * 상위목차가 있으면 하위 목차이고, 하위 목차가 있는 목차는 "묶음(그룹)"이라 담당자·쪽수를 두지 않는다.
 */
public class TocDto {

    private Long tocSn;
    private Long pbancSn;
    private Long upTocSn;
    private String tocNo;
    private String tocNm;
    private int sortSeq;
    private String picId;
    private String picNm;
    private Integer expcPageCnt;
    private String prgrsSttsCd;

    /** 이 목차에 연결된 요구사항 수 (조회 때 계산) */
    private int rqtCnt;
    /** 하위 목차 수 (조회 때 계산) */
    private int childCnt;
    /** 화면에서 들여쓰기에 쓰는 깊이. 0이 최상위 (화면용) */
    private int depth;

    public Long getTocSn() { return tocSn; }
    public void setTocSn(Long tocSn) { this.tocSn = tocSn; }

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    public Long getUpTocSn() { return upTocSn; }
    public void setUpTocSn(Long upTocSn) { this.upTocSn = upTocSn; }

    public String getTocNo() { return tocNo; }
    public void setTocNo(String tocNo) { this.tocNo = tocNo; }

    public String getTocNm() { return tocNm; }
    public void setTocNm(String tocNm) { this.tocNm = tocNm; }

    /** 같은 상위 목차 안에서의 순서 */
    public int getSortSeq() { return sortSeq; }
    public void setSortSeq(int sortSeq) { this.sortSeq = sortSeq; }

    public String getPicId() { return picId; }
    public void setPicId(String picId) { this.picId = picId; }

    public String getPicNm() { return picNm; }
    public void setPicNm(String picNm) { this.picNm = picNm; }

    public Integer getExpcPageCnt() { return expcPageCnt; }
    public void setExpcPageCnt(Integer expcPageCnt) { this.expcPageCnt = expcPageCnt; }

    public String getPrgrsSttsCd() { return prgrsSttsCd; }
    public void setPrgrsSttsCd(String prgrsSttsCd) { this.prgrsSttsCd = prgrsSttsCd; }

    public int getRqtCnt() { return rqtCnt; }
    public void setRqtCnt(int rqtCnt) { this.rqtCnt = rqtCnt; }

    public int getChildCnt() { return childCnt; }
    public void setChildCnt(int childCnt) { this.childCnt = childCnt; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    /** 예상 쪽수. 비어 있으면 0 */
    public int getPages() {
        return expcPageCnt == null ? 0 : expcPageCnt;
    }

    /** 하위 목차를 가진 묶음인지 */
    public boolean isGroup() {
        return childCnt > 0;
    }
}
