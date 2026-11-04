package com.ojj.sujuro.proposal.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.proposal.dto.DocDto;
import com.ojj.sujuro.proposal.dto.EvlArtclDto;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.PrpFileDto;
import com.ojj.sujuro.proposal.dto.SubmissionDto;


/** 제출서류·조견표(평가 항목)·나라장터 등록 SQL. 쿼리는 src/main/resources/mapper/proposal/DocMapper.xml 에 있다. */
@Mapper
public interface DocMapper {

    /* ---------- 사업건 ---------- */

    /** 경영지원실이 볼 사업건: 참여 확정·제안 작성 중·제출 완료·결과 등록 */
    List<ProposalBidDto> findBidsForMgmt();

    /** 사업건 한 건 (사업명 줄용, 제안팀 여부와 관계없이) */
    ProposalBidDto findBid(@Param("pbancSn") Long pbancSn);

    SubmissionDto findSubmission(@Param("pbancSn") Long pbancSn);

    /** 나라장터 등록 완료: 상태를 제출 완료(PS050)로, 등록 일시·담당자를 기록 (FR-073) */
    int updateSubmitted(@Param("pbancSn") Long pbancSn, @Param("userId") String userId);

    /* ---------- 제출서류 체크리스트 (FR-067~070) ---------- */

    /** 체크리스트 (가장 최근 파일 포함). 필수 먼저, 등록 순 */
    List<DocDto> findDocs(@Param("pbancSn") Long pbancSn);

    DocDto findDoc(@Param("dcmntSn") Long dcmntSn);

    int insertDoc(DocDto doc);

    int updateDocStatus(@Param("dcmntSn") Long dcmntSn, @Param("prgrsSttsCd") String prgrsSttsCd);

    int updateDocDue(@Param("dcmntSn") Long dcmntSn, @Param("sbmsnDdlnDt") Date sbmsnDdlnDt);

    int deleteDoc(@Param("dcmntSn") Long dcmntSn);

    /** 이 서류에 올린 파일 전부 (삭제한 기록 포함). 서류를 지울 때 파일 정보를 먼저 읽는다 */
    List<PrpFileDto> findFilesOfDoc(@Param("dcmntSn") Long dcmntSn);

    /** 이 서류에 올린 파일 기록 전부 삭제 (서류를 지우려면 먼저 지워야 한다) */
    int deleteFilesOfDoc(@Param("dcmntSn") Long dcmntSn);

    /* ---------- 조견표: 평가 항목·배점·쪽번호 (FR-071) ---------- */

    /** 이 사업건 평가 항목 (정렬순서대로) */
    List<EvlArtclDto> findArticles(@Param("pbancSn") Long pbancSn);

    EvlArtclDto findArticle(@Param("evlArtclSn") Long evlArtclSn);

    /** 평가 항목 추가. 정렬순서는 맨 뒤 */
    int insertArticle(EvlArtclDto a);

    int deleteArticle(@Param("evlArtclSn") Long evlArtclSn);

    /** 쪽번호 저장 (비우면 NULL) */
    int updateArticlePage(@Param("evlArtclSn") Long evlArtclSn, @Param("pageNo") String pageNo);

    /** 이 평가 항목을 쓰는 위원별 점수·세부 항목 수 (결과 분석에서 쓰고 있으면 지우지 않는다) */
    int countArticleUses(@Param("evlArtclSn") Long evlArtclSn);

    /* ---------- 파일 (FL050 제출서류. FL060 조견표 파일은 v4.1부터 올리지 않고 이전 기록만 남는다) ---------- */

    /** 파일 정보를 넣는다 (서류 파일이면 dcmntSn 채움). 만들어진 번호가 file.fileSn에 채워진다. */
    int insertFile(@Param("f") PrpFileDto f, @Param("dcmntSn") Long dcmntSn);

    /** 이 사업건 제출서류·(예전) 조견표 파일 중 삭제(숨김)한 것 (최근 삭제 먼저) */
    List<PrpFileDto> findDeletedFiles(@Param("pbancSn") Long pbancSn);

    /** 삭제(숨김) 처리. 원본과 이력은 남긴다 (NFR-009) */
    int markFileDeleted(@Param("fileSn") Long fileSn, @Param("userId") String userId, @Param("reason") String reason);

    PrpFileDto findFile(@Param("fileSn") Long fileSn);
}
