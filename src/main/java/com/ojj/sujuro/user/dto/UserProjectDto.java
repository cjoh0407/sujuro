package com.ojj.sujuro.user.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 직원이 제안팀원으로 들어가 있는 진행 중 사업 한 건 (참여 확정·제안 작성 중·제출 완료).
 * 팀을 옮기거나 비활성으로 바꿀 때 "이 사업에서도 뺄까요?"를 묻는 데 쓴다.
 */
@Getter
@Setter
public class UserProjectDto {
    private Long pbancSn;
    private String pbancNm;
    private Long sprvsnTeamSn;   // 주관팀
    private String sprvsnTeamNm;
    private int tocCnt;          // 이 직원이 담당자인 목차 수
}
