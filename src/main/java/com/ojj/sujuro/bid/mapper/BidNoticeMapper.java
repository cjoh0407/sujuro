package com.ojj.sujuro.bid.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ojj.sujuro.bid.dto.AtchFileDTO;
import com.ojj.sujuro.bid.dto.BidLinkDTO;
import com.ojj.sujuro.bid.dto.BidNoticeDTO;
import com.ojj.sujuro.bid.dto.BidNoticeSearchDTO;
import com.ojj.sujuro.bid.dto.CodeOptionDTO;
import com.ojj.sujuro.bid.dto.CollectHistoryDTO;
import com.ojj.sujuro.bid.dto.CollectLogDTO;
import com.ojj.sujuro.bid.dto.CollectRunDTO;
import com.ojj.sujuro.bid.dto.NoticeDetailDTO;
import com.ojj.sujuro.bid.dto.NoticeRegisterDTO;
import com.ojj.sujuro.bid.dto.PreSpecLinkDTO;
import com.ojj.sujuro.bid.dto.ProcessHistoryDTO;
import com.ojj.sujuro.bid.dto.StatusCountDTO;
import com.ojj.sujuro.bid.dto.TeamOptionDTO;
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

    /** 나라장터 공고 첨부 한 건 (FR-036). 등록자가 null 이면 공고 등록자로 넣는다. */
    int insertAtchFile(AtchFileDTO param);

    /** 공고 상세의 첨부 목록. 입찰공고 첨부 먼저, 사전규격 규격서 뒤 */
    List<AtchFileDTO> findNoticeFiles(Long pbancSn);

    /** 입찰공고번호가 있고 이 단계에 있는 공고. 정정·개찰(PP030), 계약(PP040) 확인 대상 */
    List<TrackingNoticeDTO> findBidStageNotices(@Param("stepCd") String stepCd);

    /** 나라장터 첨부가 하나도 없는 사전규격·입찰공고 단계 공고. 수집이 첨부를 다시 받아 볼 대상 */
    List<TrackingNoticeDTO> findNoticesWithoutFiles(@Param("preSpecStepCd") String preSpecStepCd,
                                                    @Param("bidStepCd") String bidStepCd);

    /** 입찰공고 정정·취소 반영. 저장된 차수가 fromBidPbancCycl 이 아니면 0을 돌려준다. */
    int updateBidCorrection(BidLinkDTO param);

    /** 단계만 올린다 (개찰·계약). sttsCd 가 있으면 공고상태도 바꾼다(유찰). 이미 바뀌었으면 0 */
    int updateStep(@Param("pbancSn") Long pbancSn, @Param("fromStepCd") String fromStepCd,
                   @Param("toStepCd") String toStepCd, @Param("sttsCd") String sttsCd);

    /** 최근 minutes 분 안에 이 실행구분으로 남은 수집로그 수 */
    int countRecentRuns(@Param("excnSeCd") String excnSeCd, @Param("minutes") int minutes);

    /** 마지막 수집 실행. 없으면 null */
    CollectRunDTO findLastRun();

    /** 이 공고의 다음 수집이력 순번 */
    int nextCollectSeq(Long pbancSn);

    /** 수집로그 한 행 */
    int insertCollectLog(CollectLogDTO param);

    /* ---------- 담당 팀 배정 · 참여 결정 (FR-037~040) ---------- */

    /** 배정할 수 있는 팀 (이 부서, 사용 중인 팀). 팀장 이름 포함 */
    List<TeamOptionDTO> findTeamOptions(@Param("deptCd") String deptCd, @Param("leaderCd") String leaderCd,
                                        @Param("inactiveCd") String inactiveCd);

    /** 공통코드 한 그룹 */
    List<CodeOptionDTO> findCodeOptions(@Param("groupCd") String groupCd);

    /** 배정·재배정. 지금 상태·팀이 fromSttsCd·fromTeamSn 이 아니면(그사이 바뀌었으면) 0 */
    int updateAssign(@Param("pbancSn") Long pbancSn, @Param("teamSn") Long teamSn,
                     @Param("ptcpShpCd") String ptcpShpCd, @Param("toSttsCd") String toSttsCd,
                     @Param("fromSttsCd") String fromSttsCd, @Param("fromTeamSn") Long fromTeamSn);

    /** 영업부의 불참 종결. 지금 상태가 fromSttsCd 가 아니면 0 */
    int updateClosed(@Param("pbancSn") Long pbancSn, @Param("fromSttsCd") String fromSttsCd,
                     @Param("toSttsCd") String toSttsCd);

    /** 이 사용자가 이 공고에 배정된 팀의 팀장이면 1 */
    int countAssignedLeader(@Param("pbancSn") Long pbancSn, @Param("userId") String userId,
                            @Param("leaderCd") String leaderCd);

    /** 팀장의 참여 결정. 이 팀이 배정된 결정 대기 공고가 아니면 0 */
    int updateJoined(@Param("pbancSn") Long pbancSn, @Param("teamSn") Long teamSn,
                     @Param("fromSttsCd") String fromSttsCd, @Param("toSttsCd") String toSttsCd);

    /** 팀장의 불참 결정. 영업부로 돌려보낸다. 이 팀이 배정된 결정 대기 공고가 아니면 0 */
    int updateReturned(@Param("pbancSn") Long pbancSn, @Param("teamSn") Long teamSn,
                       @Param("fromSttsCd") String fromSttsCd, @Param("toSttsCd") String toSttsCd,
                       @Param("rtrnSeCd") String rtrnSeCd);

    /** 제안팀원 한 명 (이미 있으면 넣지 않는다) */
    int insertProposalLeader(@Param("pbancSn") Long pbancSn, @Param("userId") String userId,
                             @Param("ptcpSeCd") String ptcpSeCd);

    /** 중도 철회: 이 사업건의 제안팀원을 모두 뺀다. 뺀 인원 수 */
    int deleteProposalMembers(@Param("pbancSn") Long pbancSn);

    /** 중도 철회: 이 사업건의 참여팀을 모두 뺀다 */
    int deleteJoinTeams(@Param("pbancSn") Long pbancSn);

    /** 중도 철회: 이 사업건 목차의 담당자 지정을 푼다. 목차 내용은 그대로다 */
    int clearTocOwners(@Param("pbancSn") Long pbancSn);
}
