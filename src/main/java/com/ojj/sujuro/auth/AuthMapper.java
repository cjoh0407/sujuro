package com.ojj.sujuro.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 로그인·권한 SQL. 쿼리는 src/main/resources/mapper/auth/AuthMapper.xml 에 있다. */
@Mapper
public interface AuthMapper {

    AuthUser findByUserId(String userId);

    void increaseFailCount(String userId);

    void lockIfExceeded(@Param("userId") String userId, @Param("maxFail") int maxFail);

    void resetFailCount(String userId);
}
