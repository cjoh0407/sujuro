package com.ojj.sujuro.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사용자 관리 SQL. 쿼리는 src/main/resources/mapper/user/UserMapper.xml 에 있다. */
@Mapper
public interface UserMapper {

    /** 저장된 암호화 비밀번호 */
    String findPasswordHash(String userId);

    /** 비밀번호와 계정 상태를 바꾸고 로그인 실패 횟수를 0으로 되돌린다. */
    int updatePassword(@Param("userId") String userId,
                       @Param("enpswd") String enpswd,
                       @Param("acntSttsCd") String acntSttsCd);
}
