package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공고처리이력 한 행 (TB_PBANC_PRCS_HSTRY).
 * 사람이 한 일을 남긴다. 처리구분은 공통코드 PRCSE (관심 공고 등록/배정/참여 결정 …).
 * 불참·중도 철회는 사유가 필수다.
 *
 * 필드 이름은 컬럼명을 그대로 따른다. insert SQL 이 #{prcsSeCd} 처럼 직접 참조하기 때문이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProcessHistoryDTO {

    private Long pbancSn;

    /** 배정·결정 대상 팀. 등록처럼 팀과 무관한 처리면 null */
    private Long trgtTeamSn;

    /** 처리구분코드 (PRCSE) */
    private String prcsSeCd;

    private String prcsRsn;
    private String prcrId;

    /** 팀과 무관한 처리(등록·결과 등록 등)에 쓰는 생성자 */
    public ProcessHistoryDTO(Long pbancSn, String prcsSeCd, String prcsRsn, String prcrId) {
        this.pbancSn = pbancSn;
        this.prcsSeCd = prcsSeCd;
        this.prcsRsn = prcsRsn;
        this.prcrId = prcrId;
    }
}
