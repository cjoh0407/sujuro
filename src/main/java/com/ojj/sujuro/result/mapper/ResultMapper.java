package com.ojj.sujuro.result.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.result.dto.BidRsltDTO;
import com.ojj.sujuro.result.dto.CoEvlRsltDTO;
import com.ojj.sujuro.result.dto.CompanyArticleScoreDTO;
import com.ojj.sujuro.result.dto.EvaluationArticleDTO;
import com.ojj.sujuro.result.dto.EvaluationFileDTO;
import com.ojj.sujuro.result.dto.EvaluationScoreDTO;
import com.ojj.sujuro.result.dto.ResultBidDTO;
import com.ojj.sujuro.result.dto.ResultCompanyDTO;
import com.ojj.sujuro.result.dto.ResultTargetDTO;

/** 결과 등록(수집)과 평가·결과 입력(경영지원실·팀장). SQL 은 mapper/result/ResultMapper.xml */
@Mapper
public interface ResultMapper {

    /* ---- 결과 등록 (수집) ---- */

    /**
     * 제출 완료(PS050)이고 개찰·계약 단계인 공고. 컨소시엄 참여·하도급은 참여 업체 사업자번호를 넣어 둔(개찰 목록에 우리 쪽 행이 있는)
     * 사업만 오고, 그 번호가 partnerBizNo 에 담긴다. 나머지 컨소시엄 참여·하도급은 직접 입력하므로 뺀다 (11/10)
     */
    List<ResultTargetDTO> findResultTargets();

    /** 제출 완료인 공고 하나 (단계 상관없음). 아니거나 컨소시엄 참여·하도급이면 null */
    ResultTargetDTO findResultTarget(@Param("pbancSn") Long pbancSn);

    /** 제출 완료인 컨소시엄 참여·하도급 공고 하나 (단계 상관없음, 11/10). 아니면 null. 참여 업체 사업자번호로 결과를 가져올 때 쓴다 */
    ResultTargetDTO findPartnerTarget(@Param("pbancSn") Long pbancSn);

    /** 공고의 기술·가격 배점(비율)이 비어 있으면 채운다 */
    int fillScoring(@Param("pbancSn") Long pbancSn, @Param("tech") BigDecimal tech, @Param("prc") BigDecimal prc);

    /** 개찰 목록의 업체 한 곳. 이미 있으면 순위·점수만 고친다 */
    int mergeCoEvlRslt(CoEvlRsltDTO co);

    /** 우리 회사 결과. 이미 있으면 결과·순위·점수·낙찰금액만 고친다(장단점은 그대로) */
    int mergeBidRslt(BidRsltDTO r);

    /** 제출 완료 → 결과 등록. 그사이 상태가 바뀌었으면 0 */
    int updateToResult(@Param("pbancSn") Long pbancSn);

    /* ---- 평가·결과 입력 화면 (경영지원실·팀장) ---- */

    /** 사업건 목록: 담당 팀이 있는 참여 확정·제안 작성 중·제출 완료·결과 등록·유찰 공고. teamSn 을 주면 그 팀 사업만 */
    List<ResultBidDTO> findResultBids(@Param("teamSn") Long teamSn);

    /** 사업건 하나의 머리 줄 */
    ResultBidDTO findResultBid(@Param("pbancSn") Long pbancSn);

    /** 개찰 업체 (순위 순) */
    List<ResultCompanyDTO> findCompanies(@Param("pbancSn") Long pbancSn);

    /** 업체 한 곳이 저장한 평가 항목·위원별 점수 (입력 칸을 채울 때). 저장한 것이 없으면 빈 목록 */
    List<EvaluationScoreDTO> findCompanyScores(@Param("coEvlRsltSn") Long coEvlRsltSn);

    /** 업체별·항목별 위원 평균 점수 (업체별 평가표 비교). 점수를 저장한 업체만 나온다 */
    List<CompanyArticleScoreDTO> findCompanyArticleScores(@Param("pbancSn") Long pbancSn);

    /** 업체 한 곳의 마지막 평가표 파일. 없으면 null */
    EvaluationFileDTO findCompanyEvaluationFile(@Param("coEvlRsltSn") Long coEvlRsltSn);

    /* ---- 입력 (경영지원실·팀장) ---- */

    /** 장단점 분석 (FR-081). null 은 그 칸을 지운다 */
    int updateAnalysis(@Param("pbancSn") Long pbancSn, @Param("strptCn") String strptCn,
                       @Param("sbptCsCn") String sbptCsCn, @Param("splmntCn") String splmntCn,
                       @Param("userId") String userId);

    /** 평가표 파일의 다음 버전 번호 */
    int nextEvaluationFileVer(@Param("pbancSn") Long pbancSn);

    /** 평가표 파일 한 건. 저장 뒤 f.fileSn 이 채워진다 */
    int insertEvaluationFile(@Param("pbancSn") Long pbancSn, @Param("verNo") String verNo,
                             @Param("f") EvaluationFileDTO f, @Param("fileSz") long fileSz,
                             @Param("fileExtnNm") String fileExtnNm, @Param("userId") String userId);

    /** 이 업체의 지난 평가표 파일에서 '마지막' 표시를 뗀다 (업체마다 평가표가 따로 있다). clearLastUploads 보다 먼저 부른다 */
    int clearLastEvaluationFiles(@Param("coEvlRsltSn") Long coEvlRsltSn);

    int clearLastUploads(@Param("coEvlRsltSn") Long coEvlRsltSn);

    int insertUpload(@Param("coEvlRsltSn") Long coEvlRsltSn, @Param("fileSn") Long fileSn, @Param("userId") String userId);

    int markUploaded(@Param("coEvlRsltSn") Long coEvlRsltSn);

    /** 이 업체의 위원 점수를 모두 지운다 (다시 저장하기 전에). 다른 업체 점수는 그대로 둔다 */
    int deleteCompanyScores(@Param("coEvlRsltSn") Long coEvlRsltSn);

    /** 이 공고의 평가 항목 (세부 항목 제외). 업체들이 같은 항목을 함께 쓴다 */
    List<EvaluationArticleDTO> findArticles(@Param("pbancSn") Long pbancSn);

    /** 평가 항목 한 줄. 저장 뒤 a.evlArtclSn 이 채워진다 */
    int insertArticle(EvaluationArticleDTO a);

    /** 이미 있는 항목의 배점·순서를 고친다 */
    int updateArticle(EvaluationArticleDTO a);

    /** 어느 업체도 점수를 넣지 않은 항목을 지운다. 세부 항목 → 항목 순서로 부른다 */
    int deleteUnusedSubArticles(@Param("pbancSn") Long pbancSn);

    int deleteUnusedArticles(@Param("pbancSn") Long pbancSn);

    int insertScore(@Param("coEvlRsltSn") Long coEvlRsltSn, @Param("evlArtclSn") Long evlArtclSn,
                    @Param("cmsnrNo") int cmsnrNo, @Param("evlScr") BigDecimal evlScr);

    /** 마지막 업로드 기록에 위원 수를 적는다 */
    int updateUploadCmsnrCnt(@Param("coEvlRsltSn") Long coEvlRsltSn, @Param("cmsnrCnt") int cmsnrCnt);
}
