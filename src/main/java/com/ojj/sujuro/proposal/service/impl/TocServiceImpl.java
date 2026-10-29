package com.ojj.sujuro.proposal.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.dto.TocFormDto;
import com.ojj.sujuro.proposal.mapper.TocMapper;
import com.ojj.sujuro.proposal.service.TocService;

/**
 * 사업건 목차 (FR-046~049, FR-056, FR-063, 요구사항 v3.2).
 * 목차 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 저장된 양식은 바뀌지 않는다.
 * 목차 단계에는 제한이 없고 번호는 사용자가 입력한다(자동 번호 없음). 담당자·쪽수·요구사항은 최하위 목차에만 둔다.
 */
@Service
public class TocServiceImpl implements TocService {

    private final TocMapper mapper;

    public TocServiceImpl(TocMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TocDto> list(Long pbancSn, String tocSeCd) {
        List<TocDto> tocs = mapper.findByBid(pbancSn, checkSe(tocSeCd));
        for (int i = 0; i < tocs.size(); i++) {
            TocDto g = tocs.get(i);
            if (!g.isGroup()) {
                continue;
            }
            int leaf = 0, rqt = 0, pages = 0;
            for (int j = i + 1; j < tocs.size() && tocs.get(j).getDepth() > g.getDepth(); j++) {
                TocDto x = tocs.get(j);
                if (x.isLeaf()) {
                    leaf++;
                    rqt += x.getRqtCnt();
                    pages += x.getPages();
                }
            }
            g.setLeafCnt(leaf);
            g.setSubRqtCnt(rqt);
            g.setSubPages(pages);
        }
        return tocs;
    }

    @Override
    public List<TocDto> leaves(List<TocDto> tocs) {
        List<TocDto> r = new ArrayList<>();
        for (TocDto t : tocs) {
            if (t.isLeaf()) {
                r.add(t);
            }
        }
        return r;
    }

    @Override
    public int totalPages(List<TocDto> tocs) {
        int sum = 0;
        for (TocDto t : tocs) {
            if (t.isLeaf()) {
                sum += t.getPages();
            }
        }
        return sum;
    }

    @Override
    public int unassignedCount(List<TocDto> tocs) {
        int n = 0;
        for (TocDto t : tocs) {
            if (t.isLeaf() && t.getPicId() == null) {
                n++;
            }
        }
        return n;
    }

    /* ---------- 목차 양식 ---------- */

    @Override
    public List<TocFormDto> forms(Long teamSn, String tocSeCd) {
        if (teamSn == null) {
            return new ArrayList<>();
        }
        return mapper.findForms(teamSn, checkSe(tocSeCd));
    }

    @Transactional
    @Override
    public int importForm(Long pbancSn, String tocSeCd, Long tocFormSn, Long teamSn) {
        String se = checkSe(tocSeCd);
        if (tocFormSn == null) {
            throw new BusinessException("불러올 목차 양식을 골라 주세요.");
        }
        TocFormDto form = mapper.findForm(tocFormSn);
        if (form == null || !form.isInUse() || teamSn == null || !teamSn.equals(form.getTeamSn())) {
            throw new BusinessException("선택한 목차 양식을 찾을 수 없습니다. 주관팀이 저장한 양식만 불러올 수 있습니다.");
        }
        List<TocDto> items = mapper.findFormItems(tocFormSn);
        if (items.isEmpty()) {
            throw new BusinessException("'" + form.getTocFormNm() + "'에 목차 항목이 없습니다.");
        }
        if (mapper.countByBid(pbancSn, se) > 0) {
            if (mapper.countFilesByBid(pbancSn, se) > 0) {
                throw new BusinessException("이미 올린 파일(삭제한 파일 기록 포함)이 있어 양식을 바꿀 수 없습니다. 필요한 목차만 직접 수정하세요.");
            }
            removeAll(pbancSn, se);
        }
        Map<Long, Long> idMap = new HashMap<>();
        for (TocDto src : items) { // 부모가 먼저 나오므로 부모의 새 번호를 바로 찾을 수 있다
            TocDto t = new TocDto();
            t.setPbancSn(pbancSn);
            t.setTocSeCd(se);
            t.setUpTocSn(src.getUpTocSn() == null ? null : idMap.get(src.getUpTocSn()));
            t.setTocNo(src.getTocNo());
            t.setTocNm(src.getTocNm());
            t.setSortSeq(src.getSortSeq());
            t.setExpcPageCnt(src.isLeaf() ? src.getExpcPageCnt() : null);
            mapper.insert(t);
            idMap.put(src.getTocSn(), t.getTocSn());
        }
        return items.size();
    }

    @Transactional
    @Override
    public void saveAsForm(Long pbancSn, String tocSeCd, String formName, Long teamSn, String userId) {
        String se = checkSe(tocSeCd);
        String nm = formName == null ? "" : formName.trim();
        if (teamSn == null) {
            throw new BusinessException("이 사업건의 주관팀이 정해지지 않아 양식을 저장할 수 없습니다.");
        }
        if (nm.isEmpty()) {
            throw new BusinessException("저장할 양식 이름을 입력하세요.");
        }
        if (nm.length() > 100) {
            throw new BusinessException("양식 이름은 100자까지 입력할 수 있습니다.");
        }
        if (mapper.countFormName(teamSn, nm) > 0) {
            throw new BusinessException("우리 팀에 같은 이름의 양식이 있습니다. 다른 이름을 쓰세요.");
        }
        List<TocDto> tocs = mapper.findByBid(pbancSn, se);
        if (tocs.isEmpty()) {
            throw new BusinessException("저장할 목차가 없습니다.");
        }
        TocFormDto form = new TocFormDto();
        form.setTeamSn(teamSn);
        form.setTocFormNm(nm);
        form.setTocSeCd(se);
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
            item.setExpcPageCnt(src.isLeaf() ? src.getExpcPageCnt() : null);
            mapper.insertFormItem(item);
            idMap.put(src.getTocSn(), item.getTocSn());
        }
    }

    /* ---------- 목차 편집 ---------- */

    @Transactional
    @Override
    public void addTop(Long pbancSn, String tocSeCd, String label) {
        String se = checkSe(tocSeCd);
        String[] nl = cleanLabel(label);
        insert(pbancSn, se, null, mapper.nextSortSeq(pbancSn, se, null), nl);
    }

    @Transactional
    @Override
    public int addNear(Long pbancSn, Long baseTocSn, String mode, String label) {
        TocDto base = own(pbancSn, baseTocSn);
        String[] nl = cleanLabel(label);
        String se = base.getTocSeCd();
        if (MODE_SIBLING.equals(mode)) {
            mapper.shiftSortSeq(pbancSn, se, base.getUpTocSn(), base.getSortSeq());
            insert(pbancSn, se, base.getUpTocSn(), base.getSortSeq() + 1, nl);
            return 0;
        }
        if (!MODE_CHILD.equals(mode)) {
            throw new BusinessException("추가 방식이 올바르지 않습니다.");
        }
        int unmapped = 0;
        if (base.isLeaf()) { // 최하위 목차에 하위를 만들면 이 목차는 더 이상 최하위가 아니다
            if (mapper.countFiles(baseTocSn) > 0) {
                throw new BusinessException("'" + base.getLabel() + "'에 올린 파일(삭제한 파일 기록 포함)이 있어 하위 목차를 만들 수 없습니다.");
            }
            unmapped = mapper.countRequirements(baseTocSn);
            mapper.clearRequirements(baseTocSn);
            mapper.clearLeafInfo(baseTocSn);
        }
        insert(pbancSn, se, baseTocSn, mapper.nextSortSeq(pbancSn, se, baseTocSn), nl);
        return unmapped;
    }

    @Transactional
    @Override
    public void rename(Long pbancSn, Long tocSn, String label) {
        own(pbancSn, tocSn);
        String[] nl = cleanLabel(label);
        mapper.updateLabel(tocSn, nl[0], nl[1]);
    }

    @Transactional
    @Override
    public void changePages(Long pbancSn, Long tocSn, Integer pages) {
        TocDto t = own(pbancSn, tocSn);
        if (t.isGroup()) {
            throw new BusinessException("예상 쪽수는 최하위 목차에만 입력합니다.");
        }
        if (pages != null && (pages < 0 || pages > 9999)) {
            throw new BusinessException("예상 쪽수는 0~9999 사이로 입력해 주세요.");
        }
        mapper.updatePages(tocSn, pages);
    }

    @Transactional
    @Override
    public int delete(Long pbancSn, Long tocSn) {
        TocDto t = own(pbancSn, tocSn);
        List<TocDto> all = mapper.findByBid(pbancSn, t.getTocSeCd());
        List<TocDto> targets = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (tocSn.equals(all.get(i).getTocSn())) {
                targets.add(all.get(i));
                for (int j = i + 1; j < all.size() && all.get(j).getDepth() > all.get(i).getDepth(); j++) {
                    targets.add(all.get(j));
                }
                break;
            }
        }
        for (TocDto x : targets) {
            if (mapper.countFiles(x.getTocSn()) > 0) { // 삭제(숨김)한 파일 기록도 목차를 참조한다
                throw new BusinessException("'" + x.getLabel() + "'에 올린 파일(삭제한 파일 기록 포함)이 있어 지울 수 없습니다.");
            }
        }
        int unmapped = 0;
        for (int i = targets.size() - 1; i >= 0; i--) { // 깊은 하위부터 지운다
            unmapped += removeOne(targets.get(i).getTocSn());
        }
        return unmapped;
    }

    @Transactional
    @Override
    public void assign(Long pbancSn, Long tocSn, String picId) {
        TocDto t = own(pbancSn, tocSn);
        if (t.isGroup()) {
            throw new BusinessException("담당자는 최하위 목차에만 지정합니다.");
        }
        String pic = picId == null || picId.trim().isEmpty() ? null : picId.trim();
        if (pic != null && mapper.countAssignableMember(pbancSn, pic) == 0) {
            throw new BusinessException("담당자는 이 사업건의 제안팀원 중에서만 고를 수 있습니다.");
        }
        mapper.updatePic(tocSn, pic);
    }

    /* ---------- 내부 처리 ---------- */

    private void insert(Long pbancSn, String se, Long upTocSn, int sortSeq, String[] noAndName) {
        TocDto t = new TocDto();
        t.setPbancSn(pbancSn);
        t.setTocSeCd(se);
        t.setUpTocSn(upTocSn);
        t.setSortSeq(sortSeq);
        t.setTocNo(noAndName[0]);
        t.setTocNm(noAndName[1]);
        mapper.insert(t);
    }

    /** 사업건의 해당 구분 목차를 모두 지운다 (깊은 하위부터). 파일 확인은 호출하는 쪽에서 한다. */
    private void removeAll(Long pbancSn, String tocSeCd) {
        List<TocDto> all = mapper.findByBid(pbancSn, tocSeCd);
        for (int i = all.size() - 1; i >= 0; i--) {
            removeOne(all.get(i).getTocSn());
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

    /** 이 사업건의 목차인지 확인한다. 양식 항목(공고일련번호 없음)이나 다른 사업건 목차면 예외 */
    private TocDto own(Long pbancSn, Long tocSn) {
        TocDto t = tocSn == null ? null : mapper.findOne(tocSn);
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

    /** 번호 포함 이름을 확인하고 [번호, 이름]으로 나눈다. */
    private String[] cleanLabel(String label) {
        String[] nl = TocDto.splitLabel(label);
        if (nl[1].isEmpty()) {
            throw new BusinessException("목차 이름을 입력하세요.");
        }
        if (nl[0] != null && nl[0].length() > 50) {
            throw new BusinessException("목차 번호는 50자까지 입력할 수 있습니다.");
        }
        if (nl[1].length() > 200) {
            throw new BusinessException("목차 이름은 200자까지 입력할 수 있습니다.");
        }
        return nl;
    }
}
