package com.ojj.sujuro.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사업건 접근 검사용 조회. SQL은 mapper/auth/AccessMapper.xml */
@Mapper
public interface AccessMapper {

    /** 사업건의 제안팀원이면 1, 아니면 0 (TB_PRP_TMMBR) */
    int countProposalMember(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);
}
