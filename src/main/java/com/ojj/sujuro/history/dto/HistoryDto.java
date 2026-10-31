package com.ojj.sujuro.history.dto;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/** 공고 처리 이력 한 줄 (공고 상세 화면의 "배정·결정 이력"에 쓴다). */
@Getter
@Setter
public class HistoryDto {

    private Long prcsHstrySn;
    private String prcsSeCd;     // 처리구분코드
    private String prcsSeNm;     // 처리구분 이름
    private Long trgtTeamSn;
    private String trgtTeamNm;   // 배정·결정 대상 팀
    private String prcsRsn;      // 처리사유
    private String prcrId;
    private String prcrNm;       // 처리자 이름
    private Date prcsDt;
}
