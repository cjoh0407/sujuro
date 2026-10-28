package com.ojj.sujuro.bid.dto;

import java.util.Date;

import com.ojj.sujuro.bid.BidCode;

import lombok.Getter;
import lombok.Setter;

/**
 * 나라장터 공고 첨부 한 건 (TB_ATCH_FILE, FR-036). 파일은 받아 두지 않고 나라장터 내려받기 주소(ORGNL_URL)만 둔다.
 * 파일구분은 BidCode.FILE_RFP / FILE_PRE_SPEC / FILE_BID_ATCH 중 하나다.
 *
 * 필드 이름은 컬럼명을 그대로 따른다. insert SQL 이 #{orgnlFileNm} 처럼 직접 참조하기 때문이다.
 */
@Getter
@Setter
public class AtchFileDTO {

    private Long fileSn;
    private Long pbancSn;
    private String fileSeCd;

    /** 파일구분 이름. 읽을 때만 채워진다. */
    private String fileSeNm;

    /** 원본파일명. API 나 내려받기 응답 헤더에서 얻는다. 둘 다 없으면 "사전규격 규격서 1" 같은 이름을 붙인다. */
    private String orgnlFileNm;

    /** 나라장터 내려받기 주소 */
    private String orgnlUrl;

    /** 바이트. 내려받기 응답 헤더(Content-Length)에서 얻는다. 못 얻으면 null */
    private Long fileSz;

    /** 확장자. 컬럼이 5자라 그보다 길면 자른다. */
    private String fileExtnNm;

    /**
     * 버전번호. 입찰공고 첨부는 차수+1 이다(최초 공고 "1", 1차 정정 "2"). 정정이 나면 새 차수의 첨부를
     * 새 버전으로 더 넣고, 화면은 가장 큰 버전만 보여 준다. 사전규격 규격서는 "1" 하나다.
     */
    private String verNo;

    /** 등록자. 수집으로 넣을 때는 null 로 넘기고 SQL 이 공고 등록자로 채운다. */
    private String rgtrId;

    private Date regDt;

    public static AtchFileDTO of(String fileSeCd, String orgnlFileNm, String orgnlUrl) {
        AtchFileDTO file = new AtchFileDTO();
        file.setFileSeCd(fileSeCd);
        file.setOrgnlFileNm(orgnlFileNm);
        file.setOrgnlUrl(orgnlUrl);
        file.setFileExtnNm(extOf(orgnlFileNm));
        return file;
    }

    /** 파일명 끝의 확장자. 없거나 5자를 넘으면 앞 5자만 둔다. */
    public static String extOf(String fileNm) {
        if (fileNm == null) {
            return null;
        }
        int dot = fileNm.lastIndexOf('.');
        if (dot < 0 || dot == fileNm.length() - 1) {
            return null;
        }
        String ext = fileNm.substring(dot + 1).toLowerCase();
        return ext.length() > 5 ? ext.substring(0, 5) : ext;
    }

    /**
     * 제안요청서인지. 입찰공고 첨부는 저장할 때 FL010 으로 나눠 두었다. 사전규격 규격서는 구분 필드가 없어
     * 파일명으로 본다. 파일명은 기관마다 제각각이라 공백을 지우고 본다. (2026-10-28 사전규격 20건 실측)
     *   "[제안요청서] ○○사업.hwpx", "붙임2. 제안요청서(○○).hwp", "제안 요청서.hwpx"
     */
    public boolean isRfp() {
        return BidCode.is(fileSeCd, BidCode.FILE_RFP) || isRfpName(orgnlFileNm);
    }

    public static boolean isRfpName(String fileNm) {
        return fileNm != null && fileNm.replaceAll("\\s", "").contains("제안요청서");
    }

    public boolean isPreSpec() {
        return BidCode.is(fileSeCd, BidCode.FILE_PRE_SPEC);
    }

    /** 버전을 숫자로. 없거나 숫자가 아니면 1 */
    public int getVer() {
        try {
            return verNo == null ? 1 : Integer.parseInt(verNo.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** 확장자를 대문자로. 예: "HWPX". 없으면 "-" */
    public String getFormat() {
        return fileExtnNm == null ? "-" : fileExtnNm.toUpperCase();
    }

    /** 용량. 예: "417KB", "8.6MB". 모르면 null */
    public String getSizeText() {
        if (fileSz == null) {
            return null;
        }
        if (fileSz < 1024 * 1024) {
            return Math.max(1, Math.round(fileSz / 1024.0)) + "KB";
        }
        return String.format("%.1fMB", fileSz / (1024.0 * 1024.0));
    }
}
