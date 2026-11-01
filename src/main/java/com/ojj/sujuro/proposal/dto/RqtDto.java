package com.ojj.sujuro.proposal.dto;

import lombok.Getter;
import lombok.Setter;

/** RFP 요구사항 (TB_RQT_MTTR). 목차에 연결되지 않으면 "미매핑"이다. (FR-050~054) */
@Getter
@Setter
public class RqtDto {

    private Long rqtMttrSn;
    private Long pbancSn;
    /** 요구사항번호(RFP의 요구사항 ID). 같은 사업건 안에서 중복될 수 없다. */
    private String rqtMttrNo;
    /** 요구사항명. v4.0부터 요구사항은 ID·명칭만 받는다 (분류·정의·상세내용·쪽번호 컬럼은 삭제) */
    private String rqtMttrNm;
    private Long tocSn;
    private String rgtrId;

    /** 연결된 목차 (조회 때 계산) */
    private String tocNo;
    private String tocNm;
    /** 연결된 목차의 담당자 = 이 요구사항의 담당자 (FR-051) */
    private String picId;
    private String picNm;

    /** 어느 목차에도 연결되지 않았는지 */
    public boolean isUnmapped() {
        return tocSn == null;
    }
}
