package com.ojj.sujuro.proposal.toc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 목차 (TB_TOC). 공고일련번호가 있으면 그 사업건의 목차, 목차양식일련번호가 있으면 목차 양식의 항목이다. (FR-046~048)
 * 제안서와 제안요약서 목차를 목차구분코드로 나눈다. (FR-049, FR-063)
 * 목차 단계에는 제한이 없다. 하위 목차가 있는 목차는 "묶음"이고, 하위가 없는 "최하위 목차"에만
 * 담당자·예상 쪽수·요구사항을 둔다. 번호는 자동으로 매기지 않고 사용자가 입력한다. (요구사항 v3.2, 화면 설계 v3.2)
 */
public class TocDto {

    /** 목차 번호로 보는 앞부분: 1 / 1.2.3 / Ⅰ. / IV. / 가. / A. / (1) / 1) */
    private static final Pattern NO_PATTERN = Pattern.compile(
            "^((?:\\d+(?:\\.\\d+)*\\.?|[\u2160-\u216B]+\\.?|[IVX]+\\.|[가-힣]\\.|[A-Za-z]\\.|\\(\\d+\\)|\\d+\\)))\\s+(.+)$");

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
    /** 묶음 목차일 때 아래에 있는 최하위 목차 수·요구사항 수·쪽수 합계 (TocService.list에서 계산) */
    private int leafCnt;
    private int subRqtCnt;
    private int subPages;

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

    public int getLeafCnt() { return leafCnt; }
    public void setLeafCnt(int leafCnt) { this.leafCnt = leafCnt; }

    public int getSubRqtCnt() { return subRqtCnt; }
    public void setSubRqtCnt(int subRqtCnt) { this.subRqtCnt = subRqtCnt; }

    public int getSubPages() { return subPages; }
    public void setSubPages(int subPages) { this.subPages = subPages; }

    /** 화면에 보여 줄 목차 이름 (번호 포함). 예: "1.2 시스템 구성" */
    public String getLabel() {
        return tocNo == null || tocNo.isEmpty() ? tocNm : tocNo + " " + tocNm;
    }

    /** 들여쓰기 (px). 단계마다 20px */
    public int getIndent() {
        return 14 + depth * 20;
    }

    /** 예상 쪽수. 비어 있으면 0 */
    public int getPages() {
        return expcPageCnt == null ? 0 : expcPageCnt;
    }

    /** 하위 목차가 있는 묶음 목차인지. 묶음에는 담당자·쪽수·요구사항을 두지 않는다. */
    public boolean isGroup() {
        return childCnt > 0;
    }

    /** 최하위 목차인지. 담당자·쪽수·요구사항은 최하위 목차에만 둔다. */
    public boolean isLeaf() {
        return childCnt == 0;
    }

    /**
     * 사용자가 입력한 "번호 포함" 목차 이름을 [번호, 이름]으로 나눈다. 번호로 볼 앞부분이 없으면 번호는 null.
     * 예: "1.2.3 보안 방안" → ["1.2.3", "보안 방안"], "Ⅱ. 제안 개요" → ["Ⅱ.", "제안 개요"], "부록" → [null, "부록"]
     */
    public static String[] splitLabel(String label) {
        String s = label == null ? "" : label.trim();
        Matcher m = NO_PATTERN.matcher(s);
        if (m.matches()) {
            return new String[] { m.group(1), m.group(2).trim() };
        }
        return new String[] { null, s };
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
