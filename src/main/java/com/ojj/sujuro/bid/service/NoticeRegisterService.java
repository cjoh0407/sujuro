package com.ojj.sujuro.bid.service;

import com.ojj.sujuro.bid.dto.NoticeRegisterResultDTO;

/**
 * 공고 최초 등록 (FR-021, 022). 영업부가 나라장터에서 찾은 번호를 넣으면 API로 내용을 가져와 등록한다.
 * 키워드로 공고를 찾아 주는 기능은 없다. 공고는 사람이 찾는다.
 */
public interface NoticeRegisterService {

    /**
     * 사전규격등록번호로 등록한다.
     *
     * @param rawNo  사람이 입력한 번호. 공백·소문자가 섞여 와도 된다
     * @param userId 등록하는 영업부 직원의 사번
     * @return 화면에 보여 줄 등록 결과
     */
    NoticeRegisterResultDTO registerByPreSpecNo(String rawNo, String userId);
}
