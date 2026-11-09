package com.ojj.sujuro.proposal.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.proposal.dto.DocDto;
import com.ojj.sujuro.proposal.dto.EvlArtclDto;
import com.ojj.sujuro.proposal.dto.ProposalBidDto;
import com.ojj.sujuro.proposal.dto.PrpFileDto;
import com.ojj.sujuro.proposal.dto.SubmissionDto;


/**
 * 제출서류 · 조견표 · 나라장터 등록 (FR-015, FR-067~073).
 *   경영지원실(admin): 체크리스트 항목 추가·삭제·기한, 모든 서류 상태, 나라장터 등록 완료 처리
 *   제안팀(team): 제안팀 담당 서류의 상태
 *   함께: 서류 파일 올리기와 내려받기
 *   조견표(웹 입력, FR-071): 경영지원실이 평가 항목·배점을 추가·삭제하고, 제안팀이 항목별 쪽번호를 적는다
 * 누가 이 사업건을 열 수 있는지는 컨트롤러가 먼저 확인한다. 나라장터 등록 뒤에는 모두 읽기 전용이다.
 */
public interface DocService {

    /** 경영지원실이 볼 수 있는 사업건 (참여 확정 이후) */
    List<ProposalBidDto> bidsForMgmt();

    /** 경영지원실이 이 사업건을 볼 수 있는지 (참여 확정 이후 상태인지) */
    boolean isMgmtVisible(Long pbancSn);

    ProposalBidDto bid(Long pbancSn);

    SubmissionDto submission(Long pbancSn);

    List<DocDto> docs(Long pbancSn);

    /** 조견표 평가 항목 (정렬순서대로) */
    List<EvlArtclDto> articles(Long pbancSn);

    /* ---------- 체크리스트 (경영지원실) ---------- */

    /** 서류 항목 추가. due는 yyyy-MM-dd (비우면 기한 없음) */
    void addDoc(Long pbancSn, String name, boolean essential, String ownerCd, String due);

    /** 서류 항목 삭제. 파일이 올라간 항목은 지울 수 없다 (NFR-009) */
    void deleteDoc(Long pbancSn, Long dcmntSn);

    void changeDue(Long pbancSn, Long dcmntSn, String due);

    /** 준비 상태 변경. 제안팀은 제안팀 담당 서류만 바꿀 수 있다. */
    void changeStatus(Long pbancSn, Long dcmntSn, String statusCd, boolean admin);

    /** 서류 파일 올리기 (경영지원실·제안팀). 파일을 올리면 완료가 된다. (FR-069) */
    void uploadDoc(Long pbancSn, Long dcmntSn, MultipartFile file, String userId);

    /* ---------- 조견표 (FR-071) ---------- */

    /** 평가 항목 추가 (경영지원실). 이름은 사업건 안에서 겹칠 수 없고, 배점 합계는 100점을 넘을 수 없다. */
    void addArticle(Long pbancSn, String name, String score);

    /** 평가 항목 삭제 (경영지원실). 결과 분석에서 점수를 넣은 항목은 지울 수 없다. */
    void deleteArticle(Long pbancSn, Long evlArtclSn);

    /**
     * 쪽번호 저장 (제안팀). 항목 번호와 쪽번호를 같은 순서로 받는다. 비우면 지운다.
     * 형식: 숫자, 숫자-숫자, 쉼표로 여러 개 (예: 12-15, 3, 20-21). 저장한 항목 수를 돌려준다.
     */
    int savePages(Long pbancSn, List<Long> evlArtclSns, List<String> pageNos);

    /**
     * 조견표 편집 창의 [저장] (11/07). 한 번에 저장한다.
     * 경영지원실(admin): 기존 항목 이름·배점 수정, delSns 삭제, newNames/newScores 추가 (이름 중복·배점 합계 100점 검사)
     * 제안팀: 쪽번호만 저장 (savePages 와 같다)
     */
    void saveArticles(Long pbancSn, boolean admin, List<Long> sns, List<String> names, List<String> scores,
                      List<String> pages, List<Long> delSns, List<String> newNames, List<String> newScores);

    /* ---------- 나라장터 등록 (경영지원실) ---------- */

    /**
     * 나라장터 등록 완료 처리. 체크리스트가 있고 필수 서류에 모두 파일이 있어야(완료) 한다.
     * 상태를 "제출 완료"로 바꾸고 등록 일시·담당자를 기록하며, 처리 이력과 알림을 남긴다. (FR-073)
     */
    void register(Long pbancSn, String userId);

    /** 삭제(숨김)한 제출서류·(예전) 조견표 파일 (최근 삭제 먼저) */
    List<PrpFileDto> deletedFiles(Long pbancSn);

    /**
     * 제출서류·조견표 파일을 삭제한다. 실제로 지우지 않고 목록에서만 숨기며 원본·삭제자·일시·사유를 남긴다. (NFR-009)
     * 올린 사람, 주관팀 팀장(lead), 경영지원실(admin)만 할 수 있고 나라장터 등록 뒤에는 안 된다. 사유는 반드시 입력한다.
     */
    void deleteFile(Long pbancSn, Long fileSn, String reason, String userId, boolean admin, boolean lead);

    /** 화면 버튼 노출용: 이 파일을 지금 사용자가 지울 수 있는지 (규칙은 deleteFile과 같다) */
    boolean canDeleteFile(String rgtrId, boolean submitted, String userId, boolean admin, boolean lead);

    /** 내려받을 제출서류·조견표 파일. 이 사업건 것이 아니면 예외 */
    PrpFileDto findForDownload(Long pbancSn, Long fileSn);
}
