package com.ojj.sujuro.result.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.result.dto.BidRsltDTO;
import com.ojj.sujuro.result.dto.CoEvlRsltDTO;
import com.ojj.sujuro.result.dto.EvaluationArticleDTO;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.ResultBidDTO;
import com.ojj.sujuro.result.dto.ResultCompanyDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;

/** 결과 등록(수집)과 평가·결과 입력(경영지원실). SQL 은 mapper/result/ResultMapper.xml */
@Mapper
public interface ResultMapper {

    /* ---- 결과 등록 (수집) ---- */

    /** 제출 완료(PS050)이고 개찰·계약 단계인 공고 */
    List<ResultTargetDTO> findResultTargets();

    /** 제출 완료인 공고 하나 (단계 상관없음). 아니면 null */
    ResultTargetDTO findResultTarget(@Param("pbancSn") Long pbancSn);

    /** 공고의 기술·가격 배점(비율)이 비어 있으면 채운다 */
    int fillScoring(@Param("pbancSn") Long pbancSn, @Param("tech") BigDecimal tech, @Param("prc") BigDecimal prc);

    /** 개찰 목록의 업체 한 곳. 이미 있으면 순위·점수만 고친다 */
    int mergeCoEvlRslt(CoEvlRsltDTO co);

    /** 우리 회사 결과. 이미 있으면 결과·순위·점수·낙찰금액만 고친다(장단점은 그대로) */
    int mergeBidRslt(BidRsltDTO r);

    /** 제출 완료 → 결과 등록. 그사이 상태가 바뀌었으면 0 */
    int updateToResult(@Param("pbancSn") Long pbancSn);

    /* ---- 평가·결과 입력 화면 (경영지원실) ---- */

    /** 사업건 목록: 담당 팀이 있는 참여 확정·제안 작성 중·제출 완료·결과 등록·유찰 공고 */
    List<ResultBidDTO> findResultBids();

    /** 사업건 하나의 머리 줄 */
    ResultBidDTO findResultBid(@Param("pbancSn") Long pbancSn);

    /** 개찰 업체 (순위 순) */
    List<ResultCompanyDTO> findCompanies(@Param("pbancSn") Long pbancSn);

    /* ---- 입력 (경영지원실) ---- */

    /** 장단점 분석 (FR-081). null 은 그 칸을 지운다 */
    int updateAnalysis(@Param("pbancSn") Long pbancSn, @Param("strptCn") String strptCn,
                       @Param("sbptCsCn") String sbptCsCn, @Param("splmntCn") String splmntCn,
                       @Param("userId") String userId);

    /** 개찰 목록의 우리 회사 행. 없으면 null */
    Long findOurCoEvlRsltSn(@Param("pbancSn") Long pbancSn);

    /** 평가표 파일의 다음 버전 번호 */
    int nextEvaluationFileVer(@Param("pbancSn") Long pbancSn);

    /** 평가표 파일 한 건. 저장 뒤 f.fileSn 이 채워진다 */
    int insertEvaluationFile(@Param("pbancSn") Long pbancSn, @Param("verNo") String verNo,
                             @Param("f") EvaluationFileDTO f, @Param("fileSz") long fileSz,
                             @Param("fileExtnNm") String fileExtnNm, @Param("userId") String userId);

    /** 이 공고의 평가표 파일·업로드 기록에서 '마지막' 표시를 뗀다 */
    int clearLastEvaluationFiles(@Param("pbancSn") Long pbancSn);

    int clearLastUploads(@Param("coEvlRsltSn") Long coEvlRsltSn);

    int insertUpload(@Param("coEvlRsltSn") Long coEvlRsltSn, @Param("fileSn") Long fileSn, @Param("userId") String userId);

    int markUploaded(@Param("coEvlRsltSn") Long coEvlRsltSn);

    /** 이 공고의 평가 항목과 위원 점수를 모두 지운다 (다시 입력하기 전에). 점수 → 세부 항목 → 항목 순서로 부른다 */
    int deleteScores(@Param("pbancSn") Long pbancSn);

    int deleteSubArticles(@Param("pbancSn") Long pbancSn);

    int deleteArticles(@Param("pbancSn") Long pbancSn);

    /** 평가 항목 한 줄. 저장 뒤 a.evlArtclSn 이 채워진다 */
    int insertArticle(EvaluationArticleDTO a);

    int insertScore(@Param("coEvlRsltSn") Long coEvlRsltSn, @Param("evlArtclSn") Long evlArtclSn,
                    @Param("cmsnrNo") int cmsnrNo, @Param("evlScr") BigDecimal evlScr);

    /** 마지막 업로드 기록에 위원 수를 적는다 */
    int updateUploadCmsnrCnt(@Param("coEvlRsltSn") Long coEvlRsltSn, @Param("cmsnrCnt") int cmsnrCnt);
}
