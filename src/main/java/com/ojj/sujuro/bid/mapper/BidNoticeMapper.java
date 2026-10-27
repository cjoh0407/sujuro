package com.ojj.sujuro.bid.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.bid.dto.BidLinkDTO;
import com.ojj.sujuro.bid.dto.BidNoticeDTO;
import com.ojj.sujuro.bid.dto.BidNoticeSearchDTO;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.CollectLogDTO;
import com.ojj.sujuro.bid.dto.NoticeDetailDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterDTO;
import com.ojj.sujuro.bid.dto.PreSpecLinkDTO;
import com.ojj.sujuro.bid.dto.ProcessHistoryDTO;
import com.ojj.sujuro.bid.dto.StatusCountDTO;
import com.ojj.sujuro.bid.dto.TrackingNoticeDTO;

/** 공고 SQL. 쿼리는 src/main/resources/mapper/bid/BidNoticeMapper.xml 에 있다. */
@Mapper
public interface BidNoticeMapper {

    /** 공고 목록 한 페이지 (FR-017 ~ 020) */
    List<BidNoticeDTO> findNotices(BidNoticeSearchDTO search);

    /** 같은 조건으로 걸러진 전체 건수. 페이지 수를 셀 때 쓴다. */
    int countNotices(BidNoticeSearchDTO search);

    /**
     * 상태별 건수. 탭에 붙일 숫자다.
     * 건수가 0인 상태도 탭으로 보여 줘야 해서 TB_COM_CD(PBSTS)를 기준으로 왼쪽 조인한다.
     * UI 목업 v3.2 와 같게 검색어와 무관하게 전체를 센다.
     */
    List<StatusCountDTO> findStatusCounts();

    /** 공고 상세 한 건 (FR-035). 없으면 null */
    NoticeDetailDTO findDetail(Long pbancSn);

    /** 공고수집이력, 오래된 순 (FR-035 단계 이력, FR-035-1 변경 비교). API 원문도 같이 읽는다. */
    List<CollectHistoryDTO> findCollectHistories(Long pbancSn);

    /** 같은 사전규격번호가 이미 등록돼 있는지 (FR-021 중복 등록 차단) */
    int countByBfSpecRgstNo(String bfSpecRgstNo);

    /** 같은 입찰공고번호가 이미 등록돼 있는지. 아직 부르는 곳이 없다 - 입찰공고번호 등록 경로에서 쓴다. */
    int countByBidPbancNo(String bidPbancNo);

    /** 공고를 새로 넣는다. 끝나면 param 의 pbancSn 에 새 번호가 담긴다. */
    int insertNotice(NoticeRegisterDTO param);

    /** 공고수집이력 한 행. API에서 가져온 값의 스냅샷이다. */
    int insertCollectHistory(CollectHistoryDTO param);

    /** 공고처리이력 한 행. 사람이 한 일을 남긴다. */
    int insertProcessHistory(ProcessHistoryDTO param);

    /** 같은 발주계획번호가 이미 등록돼 있는지 (FR-021 중복 등록 차단) */
    int countByOrderPlanNo(String orderPlanNo);

    /** 이 단계·추적상태에 있는 공고 중 발주계획번호가 있는 것. 수집이 신규 사전규격과 맞춰 볼 대상이다. */
    List<TrackingNoticeDTO> findTrackingNotices(@Param("stepCd") String stepCd,
                                                @Param("trckSttsCd") String trckSttsCd);

    /** 발주계획 행을 사전규격 단계로 올린다. 이미 올라갔으면 0을 돌려준다. */
    int updateToPreSpec(PreSpecLinkDTO param);

    /** 입찰공고를 기다리는 공고 (추적상태가 이 값이고 입찰공고번호가 없는 행). 발주계획·사전규격 둘 다다. */
    List<TrackingNoticeDTO> findBidWaitingNotices(@Param("trckSttsCd") String trckSttsCd);

    /** 발주계획·사전규격 행을 입찰공고 단계로 올린다. 이미 올라갔으면 0을 돌려준다. */
    int updateToBidNotice(BidLinkDTO param);

    /** 이 공고의 다음 수집이력 순번 */
    int nextCollectSeq(Long pbancSn);

    /** 수집로그 한 행 */
    int insertCollectLog(CollectLogDTO param);
}
