package com.ojj.sujuro.bid.service;

/**
 * 공고 수집 (FR-025, FR-025-1). 등록해 둔 공고가 다음 단계로 넘어갔는지 나라장터에서 확인한다.
 *
 * 지금 붙어 있는 것: 발주계획 → 사전규격, 발주계획·사전규격 → 입찰공고.
 * 아직인 것: 입찰공고 → 개찰(유찰 포함) → 계약, 입찰공고의 정정(차수) 반영.
 */
public interface NoticeCollectService {

    /**
     * 신규 사전규격을 받아 사전규격을 기다리는 발주계획과 맞춰 보고, 맞으면 사전규격 단계로 올린다.
     *
     * 발주계획과 사전규격은 서로를 가리키는 번호가 API 에 없어서 실수요기관명과 사업명으로 맞춘다.
     * 후보가 둘 이상이면 예산으로 좁히고, 그래도 여럿이면 잇지 않고 수집로그에 남긴다.
     *
     * @param excnSeCd 실행구분 (BidCode.EXCN_AUTO / EXCN_MANUAL)
     * @param exctrId  수동 실행이면 누른 사람의 사번, 자동이면 null
     * @return 사전규격 단계로 올린 공고 수
     */
    int collectPreSpec(String excnSeCd, String exctrId);

    /**
     * 입찰공고를 기다리는 발주계획·사전규격 공고에 입찰공고가 났는지 보고, 났으면 입찰공고 단계로 올린다.
     * 번호로 바로 이어지므로 이름으로 맞추지 않는다. 추적상태는 '연결됨'이 된다.
     *
     * @param excnSeCd 실행구분 (BidCode.EXCN_AUTO / EXCN_MANUAL)
     * @param exctrId  수동 실행이면 누른 사람의 사번, 자동이면 null
     * @return 입찰공고 단계로 올린 공고 수
     */
    int collectBidNotice(String excnSeCd, String exctrId);
}
