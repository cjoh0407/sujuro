package com.ojj.sujuro.user.mapper;

import com.ojj.sujuro.user.dto.CodeOption;
import com.ojj.sujuro.user.dto.UserDto;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사용자 관리 SQL. 쿼리는 src/main/resources/mapper/user/UserMapper.xml 에 있다. */
@Mapper
public interface UserMapper {

    // ---- 비밀번호 (FR-002, FR-008) ----

    /** 저장된 암호화 비밀번호 */
    String findPasswordHash(String userId);

    /** 비밀번호와 계정 상태를 바꾸고 로그인 실패 횟수를 0으로 되돌린다. (잠금도 함께 풀린다) */
    int updatePassword(@Param("userId") String userId,
                       @Param("enpswd") String enpswd,
                       @Param("acntSttsCd") String acntSttsCd);

    // ---- 직원 관리 (FR-003 ~ FR-008) ----

    /** 직원 목록. keyword는 이름·사번에 포함된 글자, status는 계정상태코드, teamSn은 소속 팀이며 비어 있으면 조건에서 뺀다. */
    List<UserDto> search(@Param("keyword") String keyword, @Param("status") String status, @Param("teamSn") Long teamSn);

    /** 전체 직원 수 */
    int countAll();

    UserDto findById(@Param("userId") String userId);

    /** 같은 사번이 이미 있으면 1 */
    int countById(@Param("userId") String userId);

    /** 같은 이메일을 쓰는 다른 직원 수. exceptUserId 는 빼고 센다(수정할 때 본인). */
    int countByEmail(@Param("emlAddr") String emlAddr, @Param("exceptUserId") String exceptUserId);

    // ----- 팀 이동·비활성 때 진행 중 사업 정리 -----

    /** 이 직원이 제안팀원인 진행 중 사업(참여 확정·제안 작성 중·제출 완료)과 그 사업에서 맡은 목차 수 */
    List<com.ojj.sujuro.user.dto.UserProjectDto> findActiveProposals(@Param("userId") String userId);

    /** 이 팀이 주관하는 진행 중 사업 수 */
    int countActiveLedProjects(@Param("teamSn") Long teamSn);

    /** 이 팀의 다른 팀장 수 (비활성 제외, userId 제외) */
    int countOtherLeaders(@Param("teamSn") Long teamSn, @Param("userId") String userId);

    /** 진행 중 사업의 제안팀원 명단에서 뺀다 */
    int deleteActiveMemberships(@Param("userId") String userId);

    /** 진행 중 사업에서 이 직원이 맡은 목차의 담당자를 비운다 */
    int clearActiveTocOwner(@Param("userId") String userId);

    int insert(@Param("userId") String userId,
               @Param("userNm") String userNm,
               @Param("teamSn") Long teamSn,
               @Param("jbgdCd") String jbgdCd,
               @Param("authrtCd") String authrtCd,
               @Param("enpswd") String enpswd,
               @Param("acntSttsCd") String acntSttsCd,
               @Param("telno") String telno,
               @Param("emlAddr") String emlAddr,
               @Param("rgtrId") String rgtrId);

    int updateInfo(@Param("userId") String userId,
                   @Param("userNm") String userNm,
                   @Param("teamSn") Long teamSn,
                   @Param("jbgdCd") String jbgdCd,
                   @Param("authrtCd") String authrtCd,
                   @Param("telno") String telno,
                   @Param("emlAddr") String emlAddr);

    int updateStatus(@Param("userId") String userId, @Param("acntSttsCd") String acntSttsCd);

    // ---- 선택 목록 ----

    /** 사용 중인 팀의 부서코드. 없거나 사용하지 않는 팀이면 null */
    String findDeptCd(@Param("teamSn") Long teamSn);

    /** 사용 중인 팀 목록 (code=팀 번호, name=팀 이름, extra=부서코드) */
    List<CodeOption> findTeams();

    /** 직급 목록 (공통코드 JBGDS) */
    List<CodeOption> findRanks();

    /** 계정 상태 목록 (공통코드 ACSTS) */
    List<CodeOption> findStatuses();
}
