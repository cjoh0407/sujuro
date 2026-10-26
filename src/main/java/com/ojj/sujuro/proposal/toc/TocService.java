package com.ojj.sujuro.proposal.toc;

import java.util.List;
import java.util.Map;

/**
 * 사업건 목차 (FR-046, FR-047, FR-056).
 * 공통 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 공통 양식은 바뀌지 않는다.
 */
public interface TocService {

    /** 사업건의 목차 전체 (부모 아래에 자식 순서) */
    List<TocDto> list(Long pbancSn);

    /** 목차 쪽수 합계. 묶음은 세부 목차 합이므로 세부 목차만 더한다. */
    int totalPages(List<TocDto> tocs);

    /** 묶음별 쪽수. key=묶음 목차일련번호 */
    Map<Long, Integer> groupPages(List<TocDto> tocs);

    /** 공통 양식을 이 사업건 목차로 복사한다. 이미 목차가 있으면 예외. 복사한 개수를 돌려준다. (FR-046) */
    int importCommon(Long pbancSn);

    /** 목차를 추가한다. upTocSn이 null이면 최상위 (FR-047) */
    void add(Long pbancSn, Long upTocSn, String name, Integer pages);

    /** 목차 이름과 예상 쪽수를 고친다. */
    void edit(Long pbancSn, Long tocSn, String name, Integer pages);

    /** 목차를 지우고, 연결된 요구사항이 미매핑으로 바뀐 건수를 돌려준다. (FR-056) */
    int delete(Long pbancSn, Long tocSn);
}
