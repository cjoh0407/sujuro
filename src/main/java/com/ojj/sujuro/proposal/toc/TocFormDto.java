package com.ojj.sujuro.proposal.toc;

/**
 * 목차 양식 (TB_TOC_FORM). 대법원·한국고용정보원 기본 양식과 사용자가 저장한 커스텀 양식이 있다. (FR-046~048)
 * 양식의 항목(하위 목차)은 TB_TOC에 목차양식일련번호를 채워 저장한다.
 */
public class TocFormDto {

    /** 목차양식구분코드 (공통코드 TFMSE) */
    public static final String SE_BASIC = "TF010";  // 기본 양식
    public static final String SE_CUSTOM = "TF020"; // 커스텀

    private Long tocFormSn;
    private String tocFormNm;
    private String tocFormSeCd;
    private String rgtrId;
    private String useYn;

    /** 이 양식에 들어 있는 목차 항목 수 (조회 때 계산) */
    private int itemCnt;

    public Long getTocFormSn() { return tocFormSn; }
    public void setTocFormSn(Long tocFormSn) { this.tocFormSn = tocFormSn; }

    public String getTocFormNm() { return tocFormNm; }
    public void setTocFormNm(String tocFormNm) { this.tocFormNm = tocFormNm; }

    public String getTocFormSeCd() { return tocFormSeCd; }
    public void setTocFormSeCd(String tocFormSeCd) { this.tocFormSeCd = tocFormSeCd == null ? null : tocFormSeCd.trim(); }

    /** 커스텀 양식을 저장한 사람. 기본 양식은 null */
    public String getRgtrId() { return rgtrId; }
    public void setRgtrId(String rgtrId) { this.rgtrId = rgtrId; }

    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn == null ? null : useYn.trim(); }

    public int getItemCnt() { return itemCnt; }
    public void setItemCnt(int itemCnt) { this.itemCnt = itemCnt; }

    public boolean isCustom() {
        return SE_CUSTOM.equals(tocFormSeCd);
    }

    public boolean isInUse() {
        return "Y".equals(useYn);
    }

    /** 불러오기 목록에 보여 줄 이름 (화면 설계 v3.4) */
    public String getLabel() {
        return tocFormNm + (isCustom() ? " (내가 저장한 양식)" : "");
    }
}
