package com.ojj.sujuro.bid.service;

import com.ojj.sujuro.bid.dto.NoticeRegisterResultDTO;

/**
 * 공고 최초 등록 (FR-021, 022). 영업부가 나라장터에서 찾은 번호를 넣으면 API로 내용을 가져와 등록한다.
 * 키워드로 공고를 찾아 주는 기능은 없다. 공고는 사람이 찾는다.
 */
public interface NoticeRegisterService {

    /**
     * 번호 하나로 등록한다. 번호 형식을 보고 단계를 정한다 (FR-021, 022).
     *   R26DD… 발주계획 / R26BD… 사전규격 / R26BK… 입찰공고
     * 화면은 이 메서드만 부른다. 아래 단계별 메서드는 이 메서드가 고른 뒤 부른다.
     *
     * @param rawNo  사람이 입력한 번호. 공백·소문자가 섞여 와도 된다
     * @param userId 등록하는 영업부 직원의 사번
     * @return 화면에 보여 줄 등록 결과
     */
    NoticeRegisterResultDTO register(String rawNo, String userId);

    /**
     * 사전규격등록번호로 등록한다.
     *
     * @param rawNo  사람이 입력한 번호. 공백·소문자가 섞여 와도 된다
     * @param userId 등록하는 영업부 직원의 사번
     * @return 화면에 보여 줄 등록 결과
     */
    NoticeRegisterResultDTO registerByPreSpecNo(String rawNo, String userId);

    /**
     * 발주계획번호로 등록한다. 사전규격·입찰공고는 수집이 나중에 이어 붙인다 (FR-025-1).
     *
     * @param rawNo  사람이 입력한 번호. 공백·소문자가 섞여 와도 된다
     * @param userId 등록하는 영업부 직원의 사번
     * @return 화면에 보여 줄 등록 결과
     */
    NoticeRegisterResultDTO registerByOrderPlanNo(String rawNo, String userId);

    /**
     * 입찰공고번호로 등록한다. 발주계획·사전규격 없이 입찰공고부터 시작하는 사업이다 (FR-022).
     * 같은 사업을 발주계획·사전규격으로 이미 등록해 두었으면 새로 만들지 않는다 (FR-025-1 한 사업 한 행).
     *
     * @param rawNo  사람이 입력한 번호. 공백·소문자가 섞여 와도 된다
     * @param userId 등록하는 영업부 직원의 사번
     * @return 화면에 보여 줄 등록 결과
     */
    NoticeRegisterResultDTO registerByBidNo(String rawNo, String userId);
}
