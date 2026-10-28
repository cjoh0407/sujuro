package com.ojj.sujuro.bid.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 수집 한 번의 결과 (FR-025). [지금 수집] 을 누른 영업부에게 보여 주고, 자동 수집은 로그에 남긴다.
 * 건수는 그 단계로 바뀐 공고 수다. 확인했는데 바뀐 것이 없으면 0 이다.
 */
@Getter
@Setter
public class CollectResultDTO {

    private int toPreSpec;     // 발주계획 → 사전규격
    private int toBidNotice;   // 발주계획·사전규격 → 입찰공고
    private int corrected;     // 입찰공고 정정 (차수 변경, 마감일·예산 변경)
    private int canceled;      // 취소공고
    private int opened;        // 개찰 (유찰 포함)
    private int failedBid;     // 그중 유찰
    private int contracted;    // 계약
    private int filesFilled;   // 첨부가 없던 공고에 첨부를 받아 채운 수 (단계 변화는 아니다)

    /** 나라장터가 응답하지 않아 확인하지 못한 단계. 예: "사전규격 목록: 나라장터에서 지금 … 불러오지 못했습니다" */
    private final List<String> failures = new ArrayList<>();

    public void addFailure(String step, String message) {
        failures.add(step + ": " + message);
    }

    public int getChangedTotal() {
        return toPreSpec + toBidNotice + corrected + canceled + opened + contracted;
    }

    /** 예: "사전규격 전환 1건 · 정정 2건 · 개찰 1건(유찰 1)". 바뀐 공고가 없으면 "바뀐 공고가 없습니다" */
    public String getSummary() {
        List<String> parts = new ArrayList<>();
        add(parts, "사전규격 전환", toPreSpec);
        add(parts, "입찰공고 전환", toBidNotice);
        add(parts, "정정", corrected);
        add(parts, "취소공고", canceled);
        if (opened > 0) {
            parts.add("개찰 " + opened + "건" + (failedBid > 0 ? "(유찰 " + failedBid + ")" : ""));
        }
        add(parts, "계약", contracted);
        add(parts, "첨부 받음", filesFilled);
        return parts.isEmpty() ? "바뀐 공고가 없습니다" : String.join(" · ", parts);
    }

    private static void add(List<String> parts, String name, int cnt) {
        if (cnt > 0) {
            parts.add(name + " " + cnt + "건");
        }
    }
}
