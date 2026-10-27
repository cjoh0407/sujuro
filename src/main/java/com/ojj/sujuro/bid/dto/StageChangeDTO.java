package com.ojj.sujuro.bid.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * 단계 전환·정정 때 바뀐 값 (FR-035-1). 직전 수집이력과 마지막 수집이력을 나란히 놓는다.
 *
 * 예) 사전규격 R26BD00011088 → 입찰공고 R26BK00945112 : 예산 700,000,000원 → 760,000,000원
 *     입찰공고 0차 → 1차(정정) : 입찰 마감 11-05 10:00 → 11-12 10:00
 *
 * 값은 화면에 보여 줄 글자로 바꿔 담는다. 비교도 그 글자로 한다.
 */
@Getter
@Setter
public class StageChangeDTO {

    /** 비교 전·후가 어느 수집이었는지 (예: "사전규격 R26BD00011088", "입찰공고 R26BK00945112 · 1차") */
    private String beforeLabel;
    private String afterLabel;
    private LocalDateTime beforeClctDt;
    private LocalDateTime afterClctDt;

    private final List<Item> items = new ArrayList<>();

    /** 바뀐 항목 수. 카드 머리에 보여 준다. */
    public long getChangedCnt() {
        return items.stream().filter(Item::isChanged).count();
    }

    /** 비교 항목 한 줄. 값이 없으면 null 이고 화면에는 "-" 로 나온다. */
    @Getter
    @RequiredArgsConstructor
    public static class Item {
        private final String itemNm;
        private final String before;
        private final String after;

        /**
         * 양쪽 다 값이 있는데 다를 때만 바뀐 것으로 본다.
         * 이전 단계에 없던 값이 새로 생긴 것(예: 사전규격엔 마감일이 없다)은 바뀐 것이 아니다.
         */
        public boolean isChanged() {
            return before != null && after != null && !before.equals(after);
        }
    }
}
