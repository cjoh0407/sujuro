package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.Setter;

/** 공통코드 하나를 고르는 칸의 한 줄. 예: 참여형태 PF010 주관 */
@Getter
@Setter
public class CodeOptionDTO {

    private String cd;
    private String cdNm;
}
