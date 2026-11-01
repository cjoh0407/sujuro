package com.ojj.sujuro.proposal.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/** 제안요청서 PDF에서 뽑은 요구사항 한 줄 (FR-050-1). 사람이 확인·수정한 뒤 등록한다. */
@Getter
@Setter
public class RqtExtractItemDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 목록표의 No (원본 그대로, 예: "01") */
    private String no;
    /** 요구사항 ID. 접두어와 번호를 합쳐 만든다 (예: SFR + 01 → SFR-001) */
    private String rqtMttrNo;
    /** 요구사항 명칭 */
    private String rqtMttrNm;
    /** 이 사업건에 같은 ID가 이미 등록돼 있으면 true (등록할 때 건너뛴다) */
    private boolean exists;
    /** 확인이 필요한 이유 (ID 중복 등). 없으면 null */
    private String note;
}
