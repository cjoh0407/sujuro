package com.ojj.sujuro.bid.service;

import com.ojj.sujuro.bid.dto.CollectResultDTO;

/**
 * 공고 수집 (FR-025, FR-025-1, FR-028). 등록해 둔 공고의 단계가 바뀌었거나 내용이 정정됐는지 나라장터에서 확인한다.
 *
 * 수집 한 번에 일곱 가지를 차례로 확인한다.
 *   1. 발주계획 → 사전규격    신규 사전규격 목록과 기관명·사업명으로 맞춘다
 *   2. 발주·사전 → 입찰공고   통합공개 API 에 번호로 묻는다
 *   3. 입찰공고 정정·취소      입찰공고 목록(등록일시·변경일시)에서 차수·값이 바뀐 공고를 찾는다
 *   4. 입찰공고 → 개찰         개찰일이 지난 공고의 개찰 결과를 묻는다. 유찰이면 상태도 유찰
 *   5. 개찰 → 계약             통합공개 API 의 계약정보로 본다
 *   6. 결과 등록              제출 완료 공고에 최종 낙찰자가 나오면 우리 회사(사업자번호) 순위·점수로 수주/탈락 등록
 *                            (result.service.ResultCollectService)
 *   7. 빠진 첨부              첨부가 하나도 없는 사전규격·입찰공고 공고의 첨부를 번호로 받는다
 * 단계마다 확인할 공고가 없으면 그 단계는 나라장터를 부르지 않는다.
 *
 * 자동 수집은 평일 오후 5시 하루 한 번이다(g2b.collect.cron). 요구사항(FR-025)은 5시 전후로 촘촘하게지만
 * 하루 한 번으로 정했다(2026-10-28). 그 사이에는 영업부가 [지금 수집] 으로 돌린다.
 */
public interface NoticeCollectService {

    /**
     * 수집을 한 번 돌린다. 자동·수동 공통이다. 다른 수집이 돌고 있으면 기다리지 않고 BusinessException 을 던진다.
     *
     * @param excnSeCd 실행구분 (BidCode.EXCN_AUTO / EXCN_MANUAL)
     * @param exctrId  수동 실행이면 누른 사람의 사번, 자동이면 null
     */
    CollectResultDTO collect(String excnSeCd, String exctrId);

    /**
     * [지금 수집]. 방금(g2b.collect.manual-cooldown-minutes 안에) 수동 수집을 했으면 BusinessException 을 던진다.
     * 나라장터 API 는 오퍼레이션마다 하루 호출 한도가 있어 연달아 누르지 못하게 막는다.
     */
    CollectResultDTO collectNow(String exctrId);
}
