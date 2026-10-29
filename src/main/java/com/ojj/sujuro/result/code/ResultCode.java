package com.ojj.sujuro.result.code;

/**
 * 결과·통계에서 쓰는 공통코드 (db/02_code_data.sql). SQL 은 mapper/result/StatsMapper.xml 에 같은 값을 적어 둔다.
 */
public final class ResultCode {

    private ResultCode() {
    }

    /* 입찰결과 BDRST */
    public static final String WON = "BR010";   // 수주
    public static final String LOST = "BR020";  // 탈락
}
