package com.ojj.sujuro.bid.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ojj.sujuro.bid.dto.BidNoticeDTO;
import com.ojj.sujuro.bid.dto.BidNoticeSearchDTO;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterDTO;
import com.ojj.sujuro.bid.dto.ProcessHistoryDTO;
import com.ojj.sujuro.bid.dto.StatusCountDTO;

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
}
