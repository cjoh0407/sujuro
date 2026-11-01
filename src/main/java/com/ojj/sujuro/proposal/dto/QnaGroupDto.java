package com.ojj.sujuro.proposal.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/**
 * 질의응답 목록의 한 묶음 (FR-076). 관련 요구사항이 연결된 1단계 목차별로 묶고,
 * 목차에 연결되지 않은 요구사항의 Q&A와 관련 요구사항이 없는 Q&A는 따로 묶는다.
 */
@Getter
public class QnaGroupDto {

    /** 묶음 종류: 1단계 목차 / 목차 미연결 요구사항 / 관련 요구사항 없음 */
    public static final String KIND_TOC = "toc";
    public static final String KIND_UNMAPPED = "unmapped";
    public static final String KIND_NONE = "none";

    private final String kind;
    private final String title;
    private final List<QnaDto> items = new ArrayList<>();

    public QnaGroupDto(String kind, String title) {
        this.kind = kind;
        this.title = title;
    }
}
