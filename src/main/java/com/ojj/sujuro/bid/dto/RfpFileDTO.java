package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 입찰공고 첨부파일 한 건 (FR-036). API 의 ntceSpecFileNm1~10 / ntceSpecDocUrl1~10 한 쌍이다.
 * 파일은 받아 두지 않고 나라장터 내려받기 주소로 바로 보낸다. 파싱도 하지 않는다.
 *
 * 용량은 API 가 주지 않아서 없다. (2026-10-27 실호출 확인)
 */
@Getter
@RequiredArgsConstructor
public class RfpFileDTO {

    private final String fileNm;
    private final String fileUrl;

    /** 확장자를 대문자로. 예: "HWPX". 확장자가 없으면 "-" */
    public String getFormat() {
        int dot = fileNm.lastIndexOf('.');
        return dot < 0 || dot == fileNm.length() - 1 ? "-" : fileNm.substring(dot + 1).toUpperCase();
    }

    /**
     * 제안요청서인지. 첨부는 공고문·과업지시서·제안요청서가 섞여 오고 구분 필드가 없어서 파일명으로 본다.
     * 실측에서 "[제안요청서] ○○사업.hwpx" 처럼 파일명에 붙어 왔다.
     */
    public boolean isRfp() {
        return fileNm.contains("제안요청서");
    }
}
