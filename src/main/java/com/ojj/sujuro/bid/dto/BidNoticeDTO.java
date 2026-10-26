package com.ojj.sujuro.bid.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import com.ojj.sujuro.bid.BidCode;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고 목록 한 줄 (BidNoticeMapper.findNotices). DB 값을 담는 그릇이다.
 * 코드값은 TB_COM_CD 를 조인해 이름까지 같이 받아 화면에서 코드를 다시 풀지 않는다.
 *
 * MyBatis 가 기본 생성자와 setter 로 값을 채우므로 @Setter 가 필요하다.
 * 필드 이름은 컬럼명을 그대로 따른다. SQL 의 alias 와 맞아야 매핑되기 때문이다.
 * 화면에 바로 쓰려고 계산해서 내보내는 값(예산 표기, 담당 팀 표기)은 아래에 직접 적어 둔다.
 */
@Getter
@Setter
public class BidNoticeDTO {

    /** 1억 (원). 예산을 "7.6억"처럼 보여 줄 때 쓴다. */
    private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");

    /** 공고일련번호. 공고를 구분하는 키 */
    private Long pbancSn;

    /** 화면에 보여 줄 공고번호. 입찰공고번호가 없으면 사전규격번호가 들어온다. */
    private String pbancNo;

    /** 공고단계코드 (PBSTP) */
    private String pbancStepCd;
    private String pbancStepNm;

    /** 공고명. 사전규격을 등록한 직후에는 아직 비어 있을 수 있다. */
    private String pbancNm;

    private String orderInstNm;

    /** 배정예산금액 (원) */
    private BigDecimal altmntBgtAmt;

    /** 입찰마감일시. 사전규격 단계에서는 아직 없다. */
    private Date bidDdlnDt;

    /** 주관 팀명. 미배정이면 비어 있다. */
    private String sprvsnTeamNm;

    /** 참여 팀명을 쉼표로 이어 붙인 값 (TB_PTCP_TEAM) */
    private String joinTeamNms;

    /** 공고상태코드 (PBSTS) */
    private String pbancSttsCd;
    private String pbancSttsNm;

    /** 반환구분명 (불참/중도 철회). 영업부로 돌아온 공고만 값이 있다. */
    private String rtrnSeNm;

    /** 사전규격 단계인지. 단계구분 칸의 색을 정할 때 쓴다. */
    public boolean isPreSpec() {
        return BidCode.is(pbancStepCd, BidCode.STEP_PRE_SPEC);
    }

    /** 팀에서 영업부로 돌아온 공고인지 */
    public boolean isReturned() {
        return rtrnSeNm != null && !rtrnSeNm.isEmpty();
    }

    /** 공고상태 pill 색 이름 */
    public String getSttsPillClass() {
        return BidCode.sttsPillClass(pbancSttsCd);
    }

    /** 예산을 억 단위로 보여 준다. 예: 760000000 → "7.6억". 값이 없으면 "-" */
    public String getBudgetText() {
        if (altmntBgtAmt == null) {
            return "-";
        }
        return altmntBgtAmt.divide(HUNDRED_MILLION, 1, RoundingMode.HALF_UP).toPlainString() + "억";
    }

    /** 담당 팀. 주관 팀에 참여 팀을 더해 보여 준다. 예: "개발1팀 + 개발2팀". 미배정이면 null */
    public String getTeamText() {
        if (sprvsnTeamNm == null || sprvsnTeamNm.isEmpty()) {
            return null;
        }
        if (joinTeamNms == null || joinTeamNms.isEmpty()) {
            return sprvsnTeamNm;
        }
        return sprvsnTeamNm + " + " + joinTeamNms;
    }
}
