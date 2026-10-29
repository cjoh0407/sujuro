package com.ojj.sujuro.result.dto;

import lombok.Getter;

/** 공고 하나의 결과 확인 결과. 수집이 이것을 보고 수집로그를 남긴다 */
@Getter
public final class ResultOutcomeDTO {

    public enum Kind {
        /** 아직 최종 낙찰자가 올라오지 않았다. 다음 수집에서 다시 묻는다 */
        WAITING,
        /** 결과를 등록했다 */
        REGISTERED,
        /** 낙찰자는 있는데 개찰 목록에 우리 회사가 없다. 사람이 확인해야 한다 */
        NOT_LISTED
    }

    private final Kind kind;
    private final String message;

    private ResultOutcomeDTO(Kind kind, String message) {
        this.kind = kind;
        this.message = message;
    }

    public static ResultOutcomeDTO waiting() {
        return new ResultOutcomeDTO(Kind.WAITING, null);
    }

    public static ResultOutcomeDTO registered(String message) {
        return new ResultOutcomeDTO(Kind.REGISTERED, message);
    }

    public static ResultOutcomeDTO notListed(String message) {
        return new ResultOutcomeDTO(Kind.NOT_LISTED, message);
    }
}
