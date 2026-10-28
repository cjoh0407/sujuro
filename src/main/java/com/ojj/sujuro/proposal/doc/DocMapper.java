package com.ojj.sujuro.proposal.doc;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.proposal.bid.ProposalBidDto;
import com.ojj.sujuro.proposal.file.PrpFileDto;

/** 제출서류·조견표·나라장터 등록 SQL. 쿼리는 src/main/resources/mapper/proposal/DocMapper.xml 에 있다. */
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

    /* ---------- 파일 (FL050 제출서류 / FL060 조견표) ---------- */

    /** 파일 정보를 넣는다 (서류 파일이면 dcmntSn 채움). 만들어진 번호가 file.fileSn에 채워진다. */
    int insertFile(@Param("f") PrpFileDto f, @Param("dcmntSn") Long dcmntSn);

    /** 이 사업건 조견표 파일 중 삭제하지 않은 것 (최신 버전 먼저) (FR-072) */
    List<PrpFileDto> findCheatFiles(@Param("pbancSn") Long pbancSn);

    /** 이 사업건 제출서류·조견표 파일 중 삭제(숨김)한 것 (최근 삭제 먼저) */
    List<PrpFileDto> findDeletedFiles(@Param("pbancSn") Long pbancSn);

    /** 삭제(숨김) 처리. 원본과 이력은 남긴다 (NFR-009) */
    int markFileDeleted(@Param("fileSn") Long fileSn, @Param("userId") String userId, @Param("reason") String reason);

    /** 이 사업건 조견표 파일 수 (삭제한 것 포함 → 버전 번호가 되돌아가지 않는다) */
    int countCheatFiles(@Param("pbancSn") Long pbancSn);

    PrpFileDto findFile(@Param("fileSn") Long fileSn);
}
