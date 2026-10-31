package com.ojj.sujuro.proposal.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 목차 양식 (TB_TOC_FORM). 기관 대표 양식은 두지 않고, 주관팀이 저장한 양식만 있다. (FR-046~048, 요구사항 v3.2)
 * 양식은 저장한 팀(TEAM_SN)의 것이고, 그 팀이 주관팀인 사업건에서 그 팀 인원만 불러오고 저장한다.
 * 양식의 항목(목차)은 TB_TOC에 목차양식일련번호를 채워 저장한다.
 */
@Getter
@Setter
public class TocFormDto {

    private Long tocFormSn;
    /** 양식을 저장한 주관팀 */
    private Long teamSn;
    private String tocFormNm;
    /** 제안서(TC010) / 제안요약서(TC020) 양식 */
    private String tocSeCd;
    /** 양식을 저장한 사람 */
    private String rgtrId;
    private String rgtrNm;
    private String useYn;

    /** 이 양식에 들어 있는 목차 항목 수 (조회 때 계산) */
    private int itemCnt;

    public void setTocSeCd(String tocSeCd) { this.tocSeCd = tocSeCd == null ? null : tocSeCd.trim(); }

    public void setUseYn(String useYn) { this.useYn = useYn == null ? null : useYn.trim(); }

    public boolean isInUse() {
        return "Y".equals(useYn);
    }

    /** 불러오기 목록에 보여 줄 이름. 내가 저장했으면 "(내가 저장)", 아니면 저장한 사람 이름 (화면 설계 v3.2) */
    public String labelFor(String userId) {
        if (rgtrId != null && rgtrId.equals(userId)) {
            return tocFormNm + " (내가 저장)";
        }
        return tocFormNm + (rgtrNm == null ? "" : " · " + rgtrNm + " 저장");
    }
}
