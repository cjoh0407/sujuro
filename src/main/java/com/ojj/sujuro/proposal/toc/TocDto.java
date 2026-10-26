package com.ojj.sujuro.proposal.toc;

/**
 * 목차 (TB_TOC). 공고일련번호가 있으면 그 사업건의 목차, 목차양식일련번호가 있으면 목차 양식의 항목이다. (FR-046~048)
 * 제안서와 제안요약서 목차를 목차구분코드로 나눈다. (FR-049, FR-063)
 * 목차는 2단계다. 최상위는 "큰 목차"(묶음)라 담당자·쪽수·요구사항을 두지 않고, 그 아래 "세부 목차"에 둔다. (화면 설계 v3.4)
 */
public class TocDto {

    /** 목차구분코드 (공통코드 TOCSE) */
    public static final String SE_PROPOSAL = "TC010"; // 제안서
    public static final String SE_SUMMARY = "TC020";  // 제안요약서

    private Long tocSn;
    private Long pbancSn;
    private Long tocFormSn;
    private Long upTocSn;
    private String tocSeCd;
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
    /** 이 목차에 올라간 파일 수 (조회 때 계산) */
    private int fileCnt;

    public Long getTocSn() { return tocSn; }
    public void setTocSn(Long tocSn) { this.tocSn = tocSn; }

    public Long getPbancSn() { return pbancSn; }
    public void setPbancSn(Long pbancSn) { this.pbancSn = pbancSn; }

    /** 목차 양식의 항목일 때만 값이 있다. 사업건 목차는 null */
    public Long getTocFormSn() { return tocFormSn; }
    public void setTocFormSn(Long tocFormSn) { this.tocFormSn = tocFormSn; }

    public Long getUpTocSn() { return upTocSn; }
    public void setUpTocSn(Long upTocSn) { this.upTocSn = upTocSn; }

    /** 제안서(TC010) / 제안요약서(TC020) */
    public String getTocSeCd() { return tocSeCd; }
    public void setTocSeCd(String tocSeCd) { this.tocSeCd = tocSeCd == null ? null : tocSeCd.trim(); }

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

    public int getFileCnt() { return fileCnt; }
    public void setFileCnt(int fileCnt) { this.fileCnt = fileCnt; }

    /** 예상 쪽수. 비어 있으면 0 */
    public int getPages() {
        return expcPageCnt == null ? 0 : expcPageCnt;
    }

    /** 큰 목차(최상위)인지. 큰 목차에는 담당자·쪽수·요구사항을 두지 않는다. */
    public boolean isGroup() {
        return upTocSn == null;
    }

    /** 파일 상태: 파일이 없으면 미착수, 있으면 작성 중, 검토 완료(TS030)로 표시했으면 검토 완료 (FR-066) */
    public String getFileStatus() {
        if (fileCnt == 0) {
            return "미착수";
        }
        return "TS030".equals(prgrsSttsCd == null ? null : prgrsSttsCd.trim()) ? "검토 완료" : "작성 중";
    }

    /** 파일 상태 표시 색 (common.css pill) */
    public String getFileStatusPill() {
        String st = getFileStatus();
        return "검토 완료".equals(st) ? "success" : "작성 중".equals(st) ? "accent" : "neutral";
    }

    /** 목차구분코드가 제안서·제안요약서 중 하나인지 */
    public static boolean isValidSe(String tocSeCd) {
        return SE_PROPOSAL.equals(tocSeCd) || SE_SUMMARY.equals(tocSeCd);
    }
}
