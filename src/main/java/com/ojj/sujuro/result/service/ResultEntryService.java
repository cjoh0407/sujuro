package com.ojj.sujuro.result.service;

import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.ResultsViewDTO;
import com.ojj.sujuro.result.dto.StatsAnalysisForm;

/**
 * 평가·결과 입력 (FR-077~081, /results). 경영지원실 전용이다. 경영지원실이 아니면 AccessDeniedException(403),
 * 결과가 등록된 사업이 아니거나 값이 틀리면 BusinessException.
 * 순위·점수는 입력하지 않는다. 나라장터 개찰 결과에서 가져온다(ResultCollectService).
 */
public interface ResultEntryService {

    /**
     * 화면 한 장. bid 가 목록에 없으면 첫 사업건(제출 완료가 맨 앞)을 고른다.
     * 결과 등록 사업이면 사업별 결과 분석과 같은 값과 개찰 업체 목록을 같이 담는다.
     */
    ResultsViewDTO build(LoginUser user, Long bid);

    /**
     * [나라장터에서 결과 가져오기]. 제출 완료 사업 하나를 지금 확인한다(평일 오후 5시 수집을 기다리지 않고).
     * 등록하면 안내 문구를 돌려준다. 아직 최종 낙찰자가 없거나, 개찰 목록에 우리 회사가 없거나,
     * 사업자번호가 설정되지 않았으면 BusinessException.
     */
    String importResult(LoginUser user, Long pbancSn);

    /** 장단점 분석 (FR-081). 빈 칸은 지운다. 한 칸이 4000바이트(한글 약 1,300자)를 넘으면 BusinessException */
    void saveAnalysis(LoginUser user, Long pbancSn, StatsAnalysisForm form);

    /**
     * 평가표 엑셀 올리기. 나라장터 기술평가점수 조회의 [엑셀다운로드] 파일을 보관만 하고 읽지 않는다.
     * 엑셀(xls·xlsx)만 받는다. 개찰 목록에 우리 회사 행이 있어야 한다(결과가 자동 등록된 사업).
     */
    void uploadEvaluationFile(LoginUser user, Long pbancSn, MultipartFile file);

    /**
     * 평가표 직접 입력: 올린 엑셀을 보며 평가 항목·배점·평가위원별 점수(우리 회사)를 적는다. 엑셀을 먼저 올려야 한다.
     * 저장할 때마다 이 사업의 평가 항목과 점수를 통째로 바꾼다.
     * 항목 이름 끝의 "(15)" 같은 배점 표시는 떼고 저장한다. 사업이 달라도 같은 항목으로 묶기 위해서다.
     */
    void saveEvaluation(LoginUser user, Long pbancSn, EvaluationForm form);
}
