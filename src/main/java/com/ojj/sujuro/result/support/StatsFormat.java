package com.ojj.sujuro.result.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 통계 화면의 숫자 표시. 금액은 공고 목록(BidNoticeDTO)과 같은 "억" 단위로 보여 준다. */
public final class StatsFormat {

    private static final BigDecimal HUNDRED_MILLION = new BigDecimal(100_000_000L);

    private StatsFormat() {
    }

    /** 원 → 억, 소수 한 자리. 예: 1590000000 → "15.9억". 값이 없으면 "-" */
    public static String eok(Long won) {
        if (won == null) {
            return "-";
        }
        return BigDecimal.valueOf(won).divide(HUNDRED_MILLION, 1, RoundingMode.HALF_UP).toPlainString() + "억";
    }

    /** 비율(%)을 소수 한 자리로 반올림한다. 분모가 0 이면 null */
    public static Double percent(double part, double whole) {
        if (whole <= 0) {
            return null;
        }
        return round1(part * 100 / whole);
    }

    /** 소수 한 자리 반올림 */
    public static Double round1(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 66.7 → "66.7", 100.0 → "100". 값이 없으면 "-" */
    public static String number(Double v) {
        if (v == null) {
            return "-";
        }
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 평가 점수는 저장한 그대로(소수 셋째 자리까지) 보여 준다. 96.992 → "96.992", 90.0 → "90". 값이 없으면 "-" */
    public static String score(Double v) {
        if (v == null) {
            return "-";
        }
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
