package com.ojj.sujuro.proposal.toc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 사업건 목차 (FR-046~049, FR-056, FR-063).
 * 목차 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 저장된 양식은 바뀌지 않는다.
 * 제안서와 제안요약서 목차는 목차구분코드로 나눠, 번호·순서·쪽수를 각각 따로 계산한다.
 * 목차는 큰 목차 > 세부 목차 2단계이고, 담당자·쪽수·요구사항은 세부 목차에만 둔다. (화면 설계 v3.4)
 */
@Service
public class TocServiceImpl implements TocService {

    private final TocMapper mapper;

    public TocServiceImpl(TocMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TocDto> list(Long pbancSn, String tocSeCd) {
        return mapper.findByBid(pbancSn, checkSe(tocSeCd));
    }

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

    @Override
    public Map<Long, Integer> groupPages(List<TocDto> tocs) {
        Map<Long, Integer> m = new HashMap<>();
        for (TocDto t : tocs) {
            if (!t.isGroup()) {
                m.merge(t.getUpTocSn(), t.getPages(), Integer::sum);
            }
        }
        return m;
    }

    @Override
    public Map<Long, Integer> groupRequirements(List<TocDto> tocs) {
        Map<Long, Integer> m = new HashMap<>();
        for (TocDto t : tocs) {
            if (!t.isGroup()) {
                m.merge(t.getUpTocSn(), t.getRqtCnt(), Integer::sum);
            }
        }
        return m;
    }

    @Override
    public int unassignedCount(List<TocDto> tocs) {
        int n = 0;
        for (TocDto t : tocs) {
            if (!t.isGroup() && t.getPicId() == null) {
                n++;
            }
        }
        return n;
    }

    /* ---------- 목차 양식 ---------- */

    @Override
    public List<TocFormDto> forms(String userId) {
        return mapper.findForms(userId);
    }

    @Transactional
    @Override
    public int importForm(Long pbancSn, String tocSeCd, Long tocFormSn, String userId) {
        String se = checkSe(tocSeCd);
        if (tocFormSn == null) {
            throw new BusinessException("불러올 목차 양식을 골라 주세요.");
        }
        TocFormDto form = mapper.findForm(tocFormSn);
        if (form == null || !form.isInUse() || (form.isCustom() && !userId.equals(form.getRgtrId()))) {
            throw new BusinessException("선택한 목차 양식을 찾을 수 없습니다.");
        }
        List<TocDto> items = mapper.findFormItems(tocFormSn);
        if (items.isEmpty()) {
            throw new BusinessException("'" + form.getTocFormNm() + "'에 목차 항목이 없습니다.");
        }
        if (mapper.countByBid(pbancSn, se) > 0) {
            if (mapper.countFilesByBid(pbancSn, se) > 0) {
                throw new BusinessException("이미 올린 파일이 있어 양식을 바꿀 수 없습니다. 필요한 목차만 직접 수정하세요.");
            }
            removeAll(pbancSn, se);
        }
        Map<Long, Long> idMap = new HashMap<>();
        for (TocDto src : items) {
            TocDto t = new TocDto();
            t.setPbancSn(pbancSn);
            t.setTocSeCd(se);
            t.setUpTocSn(src.getUpTocSn() == null ? null : idMap.get(src.getUpTocSn()));
            t.setTocNm(src.getTocNm());
            t.setSortSeq(src.getSortSeq());
            t.setExpcPageCnt(src.getUpTocSn() == null ? null : src.getExpcPageCnt());
            mapper.insert(t);
            idMap.put(src.getTocSn(), t.getTocSn());
        }
        renumber(pbancSn, se);
        return items.size();
    }

    @Transactional
    @Override
    public void saveAsForm(Long pbancSn, String tocSeCd, String formName, String userId) {
        String se = checkSe(tocSeCd);
        String nm = formName == null ? "" : formName.trim();
        if (nm.isEmpty()) {
            throw new BusinessException("저장할 양식 이름을 입력하세요.");
        }
        if (nm.length() > 100) {
            throw new BusinessException("양식 이름은 100자까지 입력할 수 있습니다.");
        }
        if (mapper.countFormName(nm, userId) > 0) {
            throw new BusinessException("같은 이름의 양식이 있습니다. 다른 이름을 쓰세요.");
        }
        List<TocDto> tocs = mapper.findByBid(pbancSn, se);
        if (tocs.isEmpty()) {
            throw new BusinessException("저장할 목차가 없습니다.");
        }
        TocFormDto form = new TocFormDto();
        form.setTocFormNm(nm);
        form.setTocFormSeCd(TocFormDto.SE_CUSTOM);
        form.setRgtrId(userId);
        mapper.insertForm(form);

        Map<Long, Long> idMap = new HashMap<>();
        for (TocDto src : tocs) {
            TocDto item = new TocDto();
            item.setTocFormSn(form.getTocFormSn());
            item.setTocSeCd(se);
            item.setUpTocSn(src.getUpTocSn() == null ? null : idMap.get(src.getUpTocSn()));
            item.setTocNo(src.getTocNo());
            item.setTocNm(src.getTocNm());
            item.setSortSeq(src.getSortSeq());
            item.setExpcPageCnt(src.getExpcPageCnt());
            mapper.insertFormItem(item);
            idMap.put(src.getTocSn(), item.getTocSn());
        }
    }

    /* ---------- 목차 편집 ---------- */

    @Transactional
    @Override
    public void add(Long pbancSn, String tocSeCd, Long upTocSn, String name, Integer pages) {
        String se = checkSe(tocSeCd);
        String nm = cleanName(name);
        checkPages(pages);
        if (upTocSn != null) {
            TocDto parent = own(pbancSn, upTocSn);
            if (!se.equals(parent.getTocSeCd())) {
                throw new BusinessException("제안서 목차와 제안요약서 목차는 서로 섞을 수 없습니다.");
            }
            if (!parent.isGroup()) {
                throw new BusinessException("세부 목차는 큰 목차 아래에만 만들 수 있습니다.");
            }
        }
        TocDto t = new TocDto();
        t.setPbancSn(pbancSn);
        t.setTocSeCd(se);
        t.setUpTocSn(upTocSn);
        t.setTocNm(nm);
        t.setSortSeq(mapper.nextSortSeq(pbancSn, se, upTocSn));
        t.setExpcPageCnt(upTocSn == null ? null : pages);
        mapper.insert(t);
        renumber(pbancSn, se);
    }

    @Transactional
    @Override
    public void edit(Long pbancSn, Long tocSn, String name, Integer pages) {
        TocDto t = own(pbancSn, tocSn);
        String nm = cleanName(name);
        checkPages(pages);
        mapper.update(tocSn, nm, t.isGroup() ? null : pages);
    }

    @Transactional
    @Override
    public int delete(Long pbancSn, Long tocSn) {
        TocDto t = own(pbancSn, tocSn);
        List<TocDto> targets = new ArrayList<>();
        if (t.isGroup()) {
            for (TocDto x : mapper.findByBid(pbancSn, t.getTocSeCd())) {
                if (tocSn.equals(x.getUpTocSn())) {
                    targets.add(x);
                }
            }
        }
        targets.add(t);
        for (TocDto x : targets) {
            if (mapper.countFiles(x.getTocSn()) > 0) {
                throw new BusinessException("'" + x.getTocNm() + "'에 올라간 파일이 있어 지울 수 없습니다.");
            }
        }
        int unmapped = 0;
        for (TocDto x : targets) { // 세부 목차를 먼저, 큰 목차를 마지막에 지운다
            unmapped += removeOne(x.getTocSn());
        }
        renumber(pbancSn, t.getTocSeCd());
        return unmapped;
    }

    @Transactional
    @Override
    public void assign(Long pbancSn, Long tocSn, String picId) {
        TocDto t = own(pbancSn, tocSn);
        if (t.isGroup()) {
            throw new BusinessException("큰 목차에는 담당자를 지정하지 않습니다. 세부 목차에 지정해 주세요.");
        }
        String pic = picId == null || picId.trim().isEmpty() ? null : picId.trim();
        if (pic != null && mapper.countAssignableMember(pbancSn, pic) == 0) {
            throw new BusinessException("담당자는 이 사업건의 제안팀원 중에서만 고를 수 있습니다.");
        }
        mapper.updatePic(tocSn, pic);
    }

    /* ---------- 내부 처리 ---------- */

    /** 사업건의 해당 구분 목차를 모두 지운다 (세부 목차 먼저). 파일 확인은 호출하는 쪽에서 한다. */
    private void removeAll(Long pbancSn, String tocSeCd) {
        List<TocDto> all = mapper.findByBid(pbancSn, tocSeCd);
        for (TocDto x : all) {
            if (!x.isGroup()) {
                removeOne(x.getTocSn());
            }
        }
        for (TocDto x : all) {
            if (x.isGroup()) {
                removeOne(x.getTocSn());
            }
        }
    }

    /** 목차 하나를 지운다. 연결된 요구사항·질문답변·조견표 연결을 먼저 정리하고, 미매핑된 요구사항 수를 돌려준다. */
    private int removeOne(Long tocSn) {
        int unmapped = mapper.countRequirements(tocSn);
        mapper.clearRequirements(tocSn);
        mapper.clearQuestions(tocSn);
        mapper.clearEvaluationMappings(tocSn);
        mapper.delete(tocSn);
        return unmapped;
    }

    /** 사업건의 목차 번호(1, 1.1, 2 …)를 순서대로 다시 매긴다. 제안서·제안요약서는 따로 번호를 매긴다. */
    private void renumber(Long pbancSn, String tocSeCd) {
        int top = 0;
        Map<Long, String> noMap = new HashMap<>();
        Map<Long, Integer> childCnt = new HashMap<>();
        for (TocDto t : mapper.findByBid(pbancSn, tocSeCd)) {
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

    /** 이 사업건의 목차인지 확인한다. 양식 항목(공고일련번호 없음)이나 다른 사업건 목차면 예외 */
    private TocDto own(Long pbancSn, Long tocSn) {
        TocDto t = mapper.findOne(tocSn);
        if (t == null || t.getPbancSn() == null || !t.getPbancSn().equals(pbancSn)) {
            throw new BusinessException("이 사업건의 목차가 아닙니다.");
        }
        return t;
    }

    private String checkSe(String tocSeCd) {
        if (!TocDto.isValidSe(tocSeCd)) {
            throw new BusinessException("목차 구분이 올바르지 않습니다.");
        }
        return tocSeCd;
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
