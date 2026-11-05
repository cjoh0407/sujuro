package com.ojj.sujuro.result.service;

import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.result.dto.EvaluationForm;
import com.ojj.sujuro.result.dto.ManualResultForm;
import com.ojj.sujuro.result.dto.ResultsViewDTO;
import com.ojj.sujuro.result.dto.StatsAnalysisForm;

/**
 * 평가·결과 입력 (FR-077~081, /results). 경영지원실과 팀장이 쓴다 (회의 결정, 2026-11-02 반영).
 *   경영지원실 : 모든 사업을 보고, 나라장터 결과 가져오기·평가표 엑셀 올리기. 점수·장단점은 읽기만
 *   팀장       : 자기 팀이 맡은(주관) 사업만 보고, 평가표 엑셀 올리기·평가 항목·점수·장단점 입력
 * 평가표는 개찰 업체마다 따로 올리고 저장한다(coSn, 없으면 우리 회사). 업체별로 항목 점수를 비교하기 위해서다.
 * 사업별 결과 분석(항목별 득점률)에는 우리 회사 점수만 들어간다.
 * 권한이 없으면 AccessDeniedException(403), 결과가 등록된 사업이 아니거나 값이 틀리면 BusinessException.
 * 순위·점수는 입력하지 않는다. 나라장터 개찰 결과에서 가져온다(ResultCollectService).
 * 컨소시엄 참여·하도급은 나라장터에서 가져올 수 없어 결과와 수주 금액을 경영지원실이 직접 입력한다(saveManualResult).
 */
public interface ResultEntryService {

    /**
     * 화면 한 장. bid 가 목록에 없으면 첫 사업건(제출 완료가 맨 앞)을 고른다.
     * 개찰 업체 목록은 제출 완료부터 담는다(참여한 업체 전체).
     * 결과 등록 사업이면 사업별 결과 분석과 같은 값, 고른 업체(coSn)의 평가표, 업체별 비교를 같이 담는다.
     * 팀장에게는 고른 업체의 평가표 엑셀을 읽어 입력 칸을 채워 준다(excelForm). 저장한 점수가 없거나, 엑셀을 새로 올리고 아직 저장하지 않았을 때 읽는다.
     * 누가 올렸든 같다(경영지원실이 다시 올려도 팀장 화면에 채워진다). 저장은 팀장이 확인하고 [평가표 저장]으로 한다.
     */
    ResultsViewDTO build(LoginUser user, Long bid, Long coSn);

    /**
     * [나라장터에서 결과 가져오기]. 제출 완료 사업 하나를 지금 확인한다(평일 오후 5시 수집을 기다리지 않고).
     * 등록하면 안내 문구를 돌려준다. 아직 최종 낙찰자가 없거나, 개찰 목록에 우리 회사가 없거나,
     * 사업자번호가 설정되지 않았으면 BusinessException.
     */
    String importResult(LoginUser user, Long pbancSn);

    /**
     * 컨소시엄 참여·하도급 사업의 결과 직접 입력 (11/04, 경영지원실만). 개찰 목록에 주관사·원도급사 이름으로 올라와
     * 나라장터에서 가져올 수 없다. 결과(수주·탈락)와 수주 금액만 받는다. 수주 금액은 우리 회사가 받는 몫이다.
     * 제출 완료면 결과 등록으로 올리고 담당 팀 팀장에게 장단점 입력을 알린다. 이미 결과 등록이면 값만 고친다.
     * 다른 참여 형태이거나 값이 틀리면 BusinessException.
     *
     * @return 화면에 보여 줄 안내 문구
     */
    String saveManualResult(LoginUser user, Long pbancSn, ManualResultForm form);

    /** 장단점 분석 (FR-081). 빈 칸은 지운다. 한 칸이 4000바이트(한글 약 1,300자)를 넘으면 BusinessException */
    void saveAnalysis(LoginUser user, Long pbancSn, StatsAnalysisForm form);

    /**
     * 평가표 엑셀 올리기. 나라장터 기술평가점수 조회의 [엑셀다운로드] 파일을 업체(coSn)별로 보관한다.
     * 읽는 것은 화면을 만들 때(build) 한다. 엑셀 모양이 달라 못 읽어도 올리기는 된다(직접 입력).
     * 엑셀(xls·xlsx)만 받는다. 개찰 목록에 우리 회사 행이 있어야 한다(결과가 자동 등록된 사업).
     */
    void uploadEvaluationFile(LoginUser user, Long pbancSn, Long coSn, MultipartFile file);

    /**
     * 평가표 저장: 엑셀에서 읽어 채운(또는 직접 적은) 평가 항목·배점·평가위원별 점수를 업체(coSn)별로 저장한다.
     * 그 업체의 엑셀을 먼저 올려야 한다. 저장할 때마다 그 업체의 점수를 통째로 바꾼다(다른 업체 점수는 그대로).
     * 평가 항목은 업체들이 함께 쓴다. 이름이 같은 항목은 다시 쓰고, 어느 업체도 쓰지 않게 된 항목은 지운다.
     * 항목 이름 끝의 "(15)" 같은 배점 표시는 떼고 저장한다. 사업이 달라도 같은 항목으로 묶기 위해서다.
     */
    void saveEvaluation(LoginUser user, Long pbancSn, Long coSn, EvaluationForm form);
}
