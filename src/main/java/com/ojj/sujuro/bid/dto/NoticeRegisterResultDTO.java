package com.ojj.sujuro.bid.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공고 등록 결과. 목록 화면에 알림으로 보여 준다.
 * DB 에 넣는 값이 아니라 등록이 끝난 뒤 화면에 돌려주는 값이다.
 */
@Getter
@RequiredArgsConstructor
public class NoticeRegisterResultDTO {

    private final Long pbancSn;
    private final String pbancNm;

    /** 사람이 입력한 번호 */
    private final String registeredNo;

    /** 등록 시점에 이미 올라와 있던 본공고 번호. 없으면 null */
    private final String linkedBidNo;

    public boolean isAlreadyHasBidNotice() {
        return linkedBidNo != null;
    }
}
