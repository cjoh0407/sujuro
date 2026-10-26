package com.ojj.sujuro.user;

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

    /** 직원 목록. keyword는 이름·사번에 포함된 글자, status는 계정상태코드이며 비어 있으면 조건에서 뺀다. */
    List<UserDto> search(@Param("keyword") String keyword, @Param("status") String status);

    UserDto findById(@Param("userId") String userId);

    /** 같은 사번이 이미 있으면 1 */
    int countById(@Param("userId") String userId);

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

    /** 직급 목록 (공통코드 JBGDS). 외부(JG030)는 외부 인력을 쓰지 않기로 해서 제외한다. */
    List<CodeOption> findRanks();

    /** 계정 상태 목록 (공통코드 ACSTS). 발급 요청(AS010)은 외부 인력용이라 제외한다. */
    List<CodeOption> findStatuses();
}
