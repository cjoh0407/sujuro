package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 화면의 "이름 : 값" 한 칸. 공고 상세의 사업 내용처럼 항목이 공고마다 들쭉날쭉한 곳에 쓴다. */
@Getter
@RequiredArgsConstructor
public class InfoItemDTO {

    private final String label;
    private final String value;
}
