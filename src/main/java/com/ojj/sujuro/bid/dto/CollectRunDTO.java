package com.ojj.sujuro.bid.dto;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/** 마지막 수집 실행 (TB_CLCT_LOG 중 공고와 상관없는 실행 단위 행). 공고 목록 위에 보여 준다. */
@Getter
@Setter
public class CollectRunDTO {

    private Date excnDt;

    /** 실행구분 이름 (자동 / 수동) */
    private String excnSeNm;

    /** 수동이면 누른 사람 이름. 자동이면 null */
    private String exctrNm;
}
