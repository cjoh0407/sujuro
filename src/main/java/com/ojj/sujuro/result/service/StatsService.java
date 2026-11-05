package com.ojj.sujuro.result.service;

import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.StatsArchiveDTO;
import com.ojj.sujuro.result.dto.StatsViewDTO;

/**
 * 사업 이력 관리와 사업별 결과 분석 (FR-016, 080, 081, 087). 11/05 통계(집계·차트·CSV)는 지웠다.
 *
 * 개발팀 단위로만 보여 준다. 경영지원실은 개발팀을 골라 보고, 팀장은 자기 팀만 본다.
 * 팀 범위는 화면에서 받은 값이 아니라 로그인한 사용자로 정한다. 팀장이 다른 팀 번호를 보내도 자기 팀이 나온다.
 * 경영지원실·팀장이 아니면 AccessDeniedException(403).
 */
public interface StatsService {

    /**
     * 사업 이력 관리 화면의 연도·팀과 사업 목록. 고른 팀·연도가 볼 수 있는 범위 밖이면 첫 팀·가장 최근 연도로 바꾼다.
     *
     * @param teamSn 경영지원실이 고른 팀. 팀장은 무시한다
     * @param year   "2026" 처럼 네 자리
     */
    StatsViewDTO build(LoginUser user, Long teamSn, String year);

    /**
     * 사업별 결과 분석. 결과가 등록된 개발팀 사업이 아니면 null.
     * 팀장이 다른 팀 사업을 열면 AccessDeniedException(403).
     */
    StatsArchiveDTO findArchive(LoginUser user, Long pbancSn);

    /**
     * 아직 결과가 없는 사업(진행 중·유찰)의 공고 기본 정보. 참여 확정 뒤의 개발팀 사업이 아니면 null. 권한은 findArchive 와 같다.
     */
    StatsArchiveDTO findPending(LoginUser user, Long pbancSn);

    /** 사업별 결과 분석의 평가표 엑셀. 사업을 볼 수 없거나 파일이 없으면 null (권한은 findArchive 와 같다) */
    EvaluationFileDTO findEvaluationFile(LoginUser user, Long pbancSn);
}
