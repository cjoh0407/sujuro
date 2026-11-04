package com.ojj.sujuro.result.service;

import java.util.List;

import com.ojj.sujuro.result.dto.ResultOutcomeDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;

/**
 * 결과 등록 (FR-077). 공고 수집(NoticeCollectServiceImpl)이 매번 부른다.
 *
 * 제출 완료한 공고의 개찰 업체는 참여한 곳을 모두 저장한다(TB_CO_EVL_RSLT). 낙찰자가 아직 없거나 우리 회사가 목록에 없어도 저장한다 (11/04).
 * 최종 낙찰자가 올라오면, 그 목록에서 우리 회사 사업자번호(company.biz-no)의 행을 찾아
 * 순위·기술·가격·종합 점수를 그대로 저장하고, 낙찰자가 우리면 수주·아니면 탈락으로 등록한다. 상태는 결과 등록이 된다.
 * 사람이 점수를 입력하지 않는다. 낙찰자는 개찰 몇 주 뒤에 올라와서, 올라올 때까지 매 수집마다 다시 묻는다.
 */
public interface ResultCollectService {

    /** 우리 회사 사업자번호(10자리)가 설정돼 있는지. 없으면 결과를 확인하지 않는다 */
    boolean isReady();

    /** 결과를 기다리는 공고: 제출 완료이고 개찰·계약 단계. 컨소시엄 참여·하도급은 직접 입력하므로 뺀다 (11/04) */
    List<ResultTargetDTO> findTargets();

    /**
     * 공고 하나를 확인한다. 나라장터를 먼저 부르고, 저장은 공고마다 따로 커밋한다.
     * 나라장터가 응답하지 않으면 BusinessException.
     */
    ResultOutcomeDTO register(ResultTargetDTO target);
}
