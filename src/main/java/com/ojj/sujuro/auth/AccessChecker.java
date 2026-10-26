package com.ojj.sujuro.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 사업건 단위 접근 검사. (FR-011, NFR-001, NFR-004)
 *
 * 목차·요구사항·제안서 파일·질의응답은 그 사업건의 제안팀원만 본다. 다른 팀과 권한(경영지원실·영업부 포함)은 예외 없이 막는다.
 * 메뉴를 숨기는 것만으로는 막은 것이 아니므로, 사업건 번호를 받는 모든 요청에서 서버가 이 클래스로 다시 확인한다.
 *
 * 컨트롤러·서비스에서 사용 (사업건 번호를 받는 요청의 맨 앞에서):
 *   accessChecker.requireProposalMember(pbancSn, user);   // 제안팀원이 아니면 403
 *   accessChecker.requireProposalLeader(pbancSn, user);   // 수정은 제안팀 팀장만
 *
 * 화면에서 버튼 노출 여부를 정할 때:
 *   th:if="${@accessChecker.isProposalMember(bid.pbancSn, #authentication.principal)}"
 *
 * 제안팀원이 아닌 접근은 AccessDeniedException(403, templates/error/4xx.html)으로 처리한다.
 * 업무 안내(400)가 아니라 권한 오류로 구분해, 사업건 번호를 바꿔 넣어 보는 시도에 존재 여부를 알려주지 않는다.
 */
@Component("accessChecker")
public class AccessChecker {

    private final AccessMapper mapper;

    public AccessChecker(AccessMapper mapper) {
        this.mapper = mapper;
    }

    /** 이 사용자가 사업건의 제안팀원인가. 비활성·잠금 계정은 로그인 자체가 막히므로 여기서는 보지 않는다. */
    public boolean isProposalMember(Long pbancSn, LoginUser user) {
        if (pbancSn == null || user == null) {
            return false;
        }
        return mapper.countProposalMember(pbancSn, user.getEmpNo()) > 0;
    }

    /** 제안팀원이면서 팀장 권한인가. (목차·요구사항 수정 가능 여부) */
    public boolean isProposalLeader(Long pbancSn, LoginUser user) {
        return user != null && user.getRole() == Role.LEADER && isProposalMember(pbancSn, user);
    }

    /** 제안팀원이 아니면 403 */
    public void requireProposalMember(Long pbancSn, LoginUser user) {
        if (!isProposalMember(pbancSn, user)) {
            throw new AccessDeniedException("해당 사업건의 제안팀만 접근할 수 있습니다.");
        }
    }

    /** 제안팀 팀장이 아니면 403 */
    public void requireProposalLeader(Long pbancSn, LoginUser user) {
        if (!isProposalLeader(pbancSn, user)) {
            throw new AccessDeniedException("해당 사업건 제안팀의 팀장만 수정할 수 있습니다.");
        }
    }
}
