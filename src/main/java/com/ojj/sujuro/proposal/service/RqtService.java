package com.ojj.sujuro.proposal.service;

import java.util.List;

import com.ojj.sujuro.proposal.dto.RqtDto;
import com.ojj.sujuro.proposal.dto.RqtExtractDto;

/** 요구사항 등록·수정·삭제, 목차 매핑과 현황 계산 (FR-050~054, FR-016-1) */
public interface RqtService {

    String FILTER_ALL = "all";
    String FILTER_UNMAPPED = "unmapped";
    String FILTER_MINE = "mine";

    List<RqtDto> list(Long pbancSn);

    /** 필터: 전체 / 미매핑 / 내 담당(연결된 목차의 담당자가 나) (FR-054) */
    List<RqtDto> filter(List<RqtDto> all, String filter, String userId);

    int unmappedCount(List<RqtDto> all);

    /** 매핑률(%). 요구사항이 없으면 0 (FR-053) */
    int progress(List<RqtDto> all);

    /** 수정 화면에 채울 요구사항 한 건. 이 사업건 것이 아니면 예외 */
    RqtDto findForEdit(Long pbancSn, Long rqtMttrSn);

    void register(Long pbancSn, RqtDto in, String userId);

    /**
     * 제안요청서 PDF에서 요구사항 ID·명칭을 뽑는다 (FR-050-1). 저장하지 않고 미리보기용 결과만 돌려준다.
     * 이 사업건에 이미 있는 ID는 exists로 표시한다. 총괄표와 개수가 다르면 경고만 담는다.
     * PDF는 제안팀이 요구사항 총괄표·목록표 쪽만 골라서 올린다.
     */
    RqtExtractDto extract(Long pbancSn, java.io.InputStream pdf, String fileNm);

    /**
     * 미리보기에서 고른 요구사항을 한 번에 등록한다. 비어 있거나 이미 있는 ID, 목록 안에서 겹친 ID는 건너뛴다.
     * {등록한 수, 건너뛴 수}를 돌려준다. 등록한 요구사항은 미매핑 상태다.
     */
    int[] registerAll(Long pbancSn, List<String> nos, List<String> names, String userId);

    /** PDF 추출 등록: seNms 는 줄마다 구분명(총괄표의 요청사항 구분, 없으면 null). 11/09 */
    int[] registerAll(Long pbancSn, List<String> nos, List<String> names, List<String> seNms, String userId);

    void edit(Long pbancSn, Long rqtMttrSn, RqtDto in, String actorId);

    void delete(Long pbancSn, Long rqtMttrSn, String actorId);

    /**
     * 이 사업건 요구사항을 모두 지운다 (PDF를 다시 추출할 때). 목차 연결도 함께 사라지고,
     * Q&A의 관련 요구사항은 비운다(Q&A 기록은 남는다). 지운 수를 돌려준다.
     */
    int deleteAll(Long pbancSn);

    /**
     * 요구사항 등록·수정·삭제·매핑을 할 수 있는지. 이 사업건 주관팀 팀장(lead)이거나,
     * 이 사업건 목차를 하나 이상 맡은 담당자면 된다. (FR-016-1)
     */
    boolean canEdit(Long pbancSn, String userId, boolean lead);

    /** canEdit이 아니면 업무 예외 */
    void requireEditor(Long pbancSn, String userId, boolean lead);

    /**
     * 요구사항을 제안서 최하위 목차에 연결한다. tocSn이 null이면 미매핑으로 돌린다. (FR-051)
     * 요구사항 하나는 최하위 목차 하나에만 연결되고(비기능 요구사항 포함), 담당자를 지정한 최하위 목차에만 연결할 수 있다. (FR-049, 요구사항 v3.2)
     * 연결된 목차의 담당자가 요구사항 담당자가 된다.
     */
    void map(Long pbancSn, Long rqtMttrSn, Long tocSn, String actorId);
}
