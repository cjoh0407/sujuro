package com.ojj.sujuro.proposal.toc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 사업건 목차 (FR-046, FR-047, FR-056).
 * 공통 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 공통 양식은 바뀌지 않는다.
 * 목차는 2단계(묶음 > 세부)까지만 둔다.
 */
@Service
public class TocServiceImpl implements TocService {

    private final TocMapper mapper;

    public TocServiceImpl(TocMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TocDto> list(Long pbancSn) {
        return mapper.findByBid(pbancSn);
    }

    /** 목차 쪽수 합계. 묶음은 세부 목차 합으로 계산되므로 세부 목차만 더한다. */
    @Override
    public int totalPages(List<TocDto> tocs) {
        int sum = 0;
        for (TocDto t : tocs) {
            if (!t.isGroup()) {
                sum += t.getPages();
            }
        }
        return sum;
    }

    /** 묶음별 쪽수 (화면에 보여 줄 때 쓴다). key=묶음 목차일련번호 */
    @Override
    public Map<Long, Integer> groupPages(List<TocDto> tocs) {
        Map<Long, Integer> m = new HashMap<>();
        for (TocDto t : tocs) {
            if (t.getUpTocSn() != null) {
                m.merge(t.getUpTocSn(), t.getPages(), Integer::sum);
            }
        }
        return m;
    }

    /** 공통 양식을 이 사업건 목차로 복사한다. 이미 목차가 있으면 덮어쓰지 않는다. (FR-046) */
    @Transactional
    @Override
    public int importCommon(Long pbancSn) {
        if (mapper.countByBid(pbancSn) > 0) {
            throw new BusinessException("이미 목차가 있어 공통 양식을 불러올 수 없습니다. 기존 목차를 정리한 뒤 다시 시도해 주세요.");
        }
        List<TocDto> common = mapper.findCommon();
        if (common.isEmpty()) {
            throw new BusinessException("등록된 공통 목차 양식이 없습니다. 관리자에게 초기 데이터 등록을 요청해 주세요.");
        }
        Map<Long, Long> idMap = new HashMap<>();
        for (TocDto src : common) {
            TocDto t = new TocDto();
            t.setPbancSn(pbancSn);
            t.setUpTocSn(src.getUpTocSn() == null ? null : idMap.get(src.getUpTocSn()));
            t.setTocNm(src.getTocNm());
            t.setSortSeq(src.getSortSeq());
            mapper.insert(t);
            idMap.put(src.getTocSn(), t.getTocSn());
        }
        renumber(pbancSn);
        return common.size();
    }

    @Transactional
    @Override
    public void add(Long pbancSn, Long upTocSn, String name, Integer pages) {
        String nm = cleanName(name);
        checkPages(pages);
        TocDto parent = null;
        if (upTocSn != null) {
            parent = own(pbancSn, upTocSn);
            if (parent.getUpTocSn() != null) {
                throw new BusinessException("목차는 2단계까지만 만들 수 있습니다.");
            }
        }
        TocDto t = new TocDto();
        t.setPbancSn(pbancSn);
        t.setUpTocSn(upTocSn);
        t.setTocNm(nm);
        t.setSortSeq(mapper.nextSortSeq(pbancSn, upTocSn));
        t.setExpcPageCnt(pages);
        mapper.insert(t);
        if (parent != null) {
            // 세부 목차가 생기면 상위는 묶음이 되므로 상위의 쪽수는 비운다.
            mapper.updatePages(upTocSn, null);
        }
        renumber(pbancSn);
    }

    @Transactional
    @Override
    public void edit(Long pbancSn, Long tocSn, String name, Integer pages) {
        own(pbancSn, tocSn);
        String nm = cleanName(name);
        checkPages(pages);
        boolean group = mapper.countChildren(tocSn) > 0;
        mapper.update(tocSn, nm, group ? null : pages);
    }

    /**
     * 목차를 지운다. 세부 목차가 있거나 제안서 파일이 올라가 있으면 막는다.
     * 연결된 요구사항은 지워지지 않고 "미매핑"으로 돌아간다. (FR-056)
     * 세부 목차가 있는 묶음을 어떻게 할지는 명세에 없어, 먼저 세부 목차를 지우도록 막아 두었다.
     */
    @Transactional
    @Override
    public int delete(Long pbancSn, Long tocSn) {
        TocDto t = own(pbancSn, tocSn);
        if (mapper.countChildren(tocSn) > 0) {
            throw new BusinessException("세부 목차가 있는 목차는 지울 수 없습니다. 세부 목차를 먼저 지워 주세요.");
        }
        if (mapper.countFiles(tocSn) > 0) {
            throw new BusinessException("이 목차에 올라간 제안서 파일이 있어 지울 수 없습니다.");
        }
        int unmapped = countRqt(t);
        mapper.clearRequirements(tocSn);
        mapper.clearQuestions(tocSn);
        mapper.delete(tocSn);
        renumber(pbancSn);
        return unmapped;
    }

    private int countRqt(TocDto t) {
        for (TocDto x : mapper.findByBid(t.getPbancSn())) {
            if (x.getTocSn().equals(t.getTocSn())) {
                return x.getRqtCnt();
            }
        }
        return 0;
    }

    /** 사업건의 목차 번호(1, 1.1, 2 …)를 순서대로 다시 매긴다. */
    private void renumber(Long pbancSn) {
        int top = 0;
        Map<Long, String> noMap = new HashMap<>();
        Map<Long, Integer> childCnt = new HashMap<>();
        for (TocDto t : mapper.findByBid(pbancSn)) {
            String no;
            if (t.getUpTocSn() == null) {
                no = String.valueOf(++top);
            } else {
                int n = childCnt.merge(t.getUpTocSn(), 1, Integer::sum);
                no = noMap.get(t.getUpTocSn()) + "." + n;
            }
            noMap.put(t.getTocSn(), no);
            if (!no.equals(t.getTocNo())) {
                mapper.updateTocNo(t.getTocSn(), no);
            }
        }
    }

    private TocDto own(Long pbancSn, Long tocSn) {
        TocDto t = mapper.findOne(tocSn);
        if (t == null || t.getPbancSn() == null || !t.getPbancSn().equals(pbancSn)) {
            throw new BusinessException("이 사업건의 목차가 아닙니다.");
        }
        return t;
    }

    private String cleanName(String name) {
        String nm = name == null ? "" : name.trim();
        if (nm.isEmpty()) {
            throw new BusinessException("목차 이름을 입력해 주세요.");
        }
        if (nm.length() > 200) {
            throw new BusinessException("목차 이름은 200자까지 입력할 수 있습니다.");
        }
        return nm;
    }

    private void checkPages(Integer pages) {
        if (pages != null && (pages < 0 || pages > 9999)) {
            throw new BusinessException("예상 쪽수는 0~9999 사이로 입력해 주세요.");
        }
    }
}
