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
 *
 * 우리 회사 번호는 쉼표로 여러 개를 줄 수 있다. 개찰 목록에 그중 하나라도 있으면 우리 회사다 (11/10).
 * 컨소시엄 참여·하도급은 개찰 목록에 우리 대신 주관사·원도급사가 올라온다. 그 사업을 맡은 팀의 팀장이 그 업체의 사업자번호를 넣으면
 * (ResultTargetDTO.partnerBizNo) 그 업체 행을 우리 쪽으로 보고 같은 방법으로 수주 여부·순위·점수·낙찰 금액을 가져온다 (11/10).
 * 직접 입력은 없다.
 */
public interface ResultCollectService {

    /** 우리 회사 사업자번호(10자리)가 하나라도 설정돼 있는지. 없으면 결과를 확인하지 않는다 */
    boolean isReady();

    /**
     * 결과를 기다리는 공고: 제출 완료이고 개찰·계약 단계.
     * 컨소시엄 참여·하도급은 참여 업체 사업자번호를 넣어 둔 사업만(partnerBizNo 가 채워져 온다). 나머지는 직접 입력한다 (11/04, 11/10)
     */
    List<ResultTargetDTO> findTargets();

    /**
     * 공고 하나를 확인한다. 나라장터를 먼저 부르고, 저장은 공고마다 따로 커밋한다.
     * target.partnerBizNo 가 있으면 우리 회사 번호 대신 그 번호의 행을 우리 쪽으로 본다. 이때 개찰 목록이 비었거나
     * 그 업체가 목록에 없으면 낙찰자가 나오기 전이어도 NOT_LISTED 다(번호를 남길 행이 없다).
     * 나라장터가 응답하지 않으면 BusinessException.
     */
    ResultOutcomeDTO register(ResultTargetDTO target);
}
