package com.ojj.sujuro.result.service;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsFileDTO;
import com.ojj.sujuro.result.dto.StatsViewDTO;

/**
 * 통계와 사업별 결과 분석 (FR-016, 083~088).
 *
 * 통계는 개발팀 단위로만 보여 준다. 경영지원실은 개발팀을 골라 보고(전체 합계는 없다), 팀장은 자기 팀만 본다.
 * 팀 범위는 화면에서 받은 값이 아니라 로그인한 사용자로 정한다. 팀장이 다른 팀 번호를 보내도 자기 팀이 나온다.
 * 경영지원실·팀장이 아니면 AccessDeniedException(403).
 */
public interface StatsService {

    /**
     * 통계 화면 한 장. 고른 팀·연도가 볼 수 있는 범위 밖이면 첫 팀·가장 최근 연도로 바꾼다.
     *
     * @param teamSn 경영지원실이 고른 팀. 팀장은 무시한다
     * @param year   "2026" 처럼 네 자리
     */
    StatsViewDTO build(LoginUser user, Long teamSn, String year);

    /** CSV (FR-088). 볼 수 있는 모든 팀 × 모든 연도의 집계. 엑셀이 한글을 읽도록 BOM 은 부르는 쪽이 붙인다. */
    String csv(LoginUser user);

    /**
     * 사업별 결과 분석. 결과가 등록된 개발팀 사업이 아니면 null.
     * 팀장이 다른 팀 사업을 열면 AccessDeniedException(403).
     */
    StatsArchiveDTO findArchive(LoginUser user, Long pbancSn);

    /** 사업별 결과 분석의 최종 제안서. 사업을 볼 수 없거나 파일이 없으면 null (권한은 findArchive 와 같다) */
    StatsFileDTO findFinalFile(LoginUser user, Long pbancSn);

    /** 사업별 결과 분석의 평가표 엑셀. 사업을 볼 수 없거나 파일이 없으면 null (권한은 findArchive 와 같다) */
    EvaluationFileDTO findEvaluationFile(LoginUser user, Long pbancSn);
}
