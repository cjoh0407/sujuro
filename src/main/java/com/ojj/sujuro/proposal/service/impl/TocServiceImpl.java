package com.ojj.sujuro.proposal.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.service.NotificationService;
import com.ojj.sujuro.proposal.dto.TocDto;
import com.ojj.sujuro.proposal.dto.TocFormDto;
import com.ojj.sujuro.proposal.mapper.ProposalMapper;
import com.ojj.sujuro.proposal.mapper.TocMapper;
import com.ojj.sujuro.proposal.service.TocService;

import lombok.RequiredArgsConstructor;

/**
 * 사업건 목차 (FR-046~049, FR-056, FR-063, 요구사항 v3.2).
 * 목차 양식은 읽기만 하고, 불러오면 사업건 전용 복사본이 만들어진다. 복사본을 고쳐도 저장된 양식은 바뀌지 않는다.
 * 목차 단계에는 제한이 없고 번호는 사용자가 입력한다(자동 번호 없음). 담당자·쪽수·요구사항은 최하위 목차에만 둔다.
 */
@Service
@RequiredArgsConstructor
public class TocServiceImpl implements TocService {

    private final TocMapper mapper;
    private final ProposalMapper proposalMapper;
    private final NotificationService notificationService;

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
            if (t.isLeaf() && !t.isAssigned()) {
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
        String se = checkFormSe(tocSeCd);
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
            syncForm(pbancSn, se); // 바꾸기 전에 지금 목차를 지금 양식에 한 번 더 맞춰 둔다 (고친 내용이 사라지지 않게)
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
        mapper.updateBidForm(pbancSn, tocFormSn); // 화면에 "현재 목차 양식"으로 보여 준다
        return items.size();
    }

    @Transactional
    @Override
    public boolean saveAsForm(Long pbancSn, String tocSeCd, String formName, Long teamSn, String userId) {
        String se = checkFormSe(tocSeCd);
        if (teamSn == null) {
            throw new BusinessException("이 사업건의 주관팀이 정해지지 않아 양식을 저장할 수 없습니다.");
        }
        String nm = cleanFormName(formName);
        List<TocDto> tocs = mapper.findByBid(pbancSn, se);
        if (tocs.isEmpty()) {
            throw new BusinessException("저장할 목차가 없습니다.");
        }

        Long formSn;
        TocFormDto same = mapper.findFormByName(teamSn, nm);
        boolean overwrite = same != null;
        if (overwrite) { // 같은 이름이면 그 양식을 지금 목차로 덮어쓴다 (FR-048 v4.0)
            if (!se.equals(same.getTocSeCd())) {
                throw new BusinessException("'" + nm + "'은(는) " + seName(same.getTocSeCd())
                        + " 양식 이름입니다. " + seName(se) + " 목차는 다른 이름으로 저장하세요.");
            }
            formSn = same.getTocFormSn();
            mapper.touchForm(formSn, userId);
        } else {
            TocFormDto form = new TocFormDto();
            form.setTeamSn(teamSn);
            form.setTocFormNm(nm);
            form.setTocSeCd(se);
            form.setRgtrId(userId);
            mapper.insertForm(form);
            formSn = form.getTocFormSn();
        }

        writeFormItems(formSn, se, tocs);
        mapper.updateBidForm(pbancSn, formSn); // 저장한 양식이 이 사업건의 현재 양식이 된다
        return overwrite;
    }

    @Transactional
    @Override
    public String renameForm(Long tocFormSn, String newName, Long teamSn) {
        TocFormDto form = ownForm(tocFormSn, teamSn);
        String nm = cleanFormName(newName);
        if (nm.equals(form.getTocFormNm())) {
            return nm;
        }
        if (mapper.findFormByName(teamSn, nm) != null) {
            throw new BusinessException("우리 팀에 '" + nm + "' 양식이 이미 있습니다. 다른 이름을 쓰세요.");
        }
        mapper.updateFormName(tocFormSn, nm);
        return nm;
    }

    @Transactional
    @Override
    public String deleteForm(Long tocFormSn, Long teamSn) {
        TocFormDto form = ownForm(tocFormSn, teamSn);
        mapper.hideForm(tocFormSn);
        return form.getTocFormNm();
    }

    @Override
    public List<String> coNames(Long pbancSn) {
        return mapper.findCoNames(pbancSn);
    }

    /* ---------- 목차 편집 ---------- */

    @Transactional
    @Override
    public void addTop(Long pbancSn, String tocSeCd, String label) {
        String se = checkSe(tocSeCd);
        String[] nl = cleanLabel(label);
        if (TocDto.SE_PROPOSAL.equals(se) && mapper.countByBid(pbancSn, se) == 0) {
            mapper.updateBidForm(pbancSn, null); // 빈 목차에서 직접 만들기 시작: 양식 없이 만든 목차
        }
        insert(pbancSn, se, null, mapper.nextSortSeq(pbancSn, se, null), nl);
        syncForm(pbancSn, se);
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
            syncForm(pbancSn, se);
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
        syncForm(pbancSn, se);
        return unmapped;
    }

    @Transactional
    @Override
    public void rename(Long pbancSn, Long tocSn, String label) {
        TocDto t = own(pbancSn, tocSn);
        String[] nl = cleanLabel(label);
        mapper.updateLabel(tocSn, nl[0], nl[1]);
        syncForm(pbancSn, t.getTocSeCd());
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
        syncForm(pbancSn, t.getTocSeCd());
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
        syncForm(pbancSn, t.getTocSeCd());
        return unmapped;
    }

    @Transactional
    @Override
    public void assign(Long pbancSn, Long tocSn, String picId, String coName, String actorId) {
        TocDto t = own(pbancSn, tocSn);
        if (t.isGroup()) {
            throw new BusinessException("담당자는 최하위 목차에만 지정합니다.");
        }
        String pic = picId == null || picId.trim().isEmpty() ? null : picId.trim();
        String co = cleanCoName(pbancSn, coName);
        if (pic != null && co != null) {
            throw new BusinessException("담당자는 제안팀원과 외부 회사 중 하나만 지정할 수 있습니다.");
        }
        if (pic != null && mapper.countAssignableMember(pbancSn, pic) == 0) {
            throw new BusinessException("담당자는 이 사업건의 제안팀원 중에서만 고를 수 있습니다.");
        }
        mapper.updatePic(tocSn, pic, co);

        // 알림 (NT100 목차 담당): 새 담당자에게 지정, 이전 담당자에게 해제. 외부 회사와 처리한 본인에게는 보내지 않는다.
        String old = t.getPicId();
        if (pic == null ? old == null : pic.equals(old)) {
            return;
        }
        String bidNm = proposalMapper.findBid(pbancSn).getLabel();
        String where = "'" + bidNm + "' " + seName(t.getTocSeCd()) + " 목차 '" + t.getLabel() + "'";
        if (pic != null && !pic.equals(actorId)) {
            notificationService.send(pic, NotificationType.TOC_ASSIGNED, pbancSn,
                    where + " 담당자로 지정되었습니다. 제안서 파일 화면에서 파일을 올려 주세요.");
        }
        if (old != null && !old.equals(actorId)) {
            notificationService.send(old, NotificationType.TOC_ASSIGNED, pbancSn, where + " 담당에서 해제되었습니다.");
        }
    }

    @Override
    public TocFormDto currentForm(Long pbancSn) {
        return mapper.findBidForm(pbancSn);
    }

    /* ---------- 제안요약서 기본 목차 ---------- */

    @Transactional
    @Override
    public void applySummaryDefault(Long pbancSn, boolean reset) {
        String se = TocDto.SE_SUMMARY;
        if (mapper.countByBid(pbancSn, se) > 0) {
            if (!reset) {
                throw new BusinessException("이미 제안요약서 목차가 있습니다. 화면을 새로고침해 확인하세요.");
            }
            if (mapper.countFilesByBid(pbancSn, se) > 0) {
                throw new BusinessException("이미 올린 요약서 파일(삭제한 파일 기록 포함)이 있어 기본 목차로 되돌릴 수 없습니다. 필요한 목차만 직접 수정하세요.");
            }
            removeAll(pbancSn, se);
        }
        int top = 0;
        for (String[] group : SUMMARY_DEFAULT) {
            TocDto parent = insert(pbancSn, se, null, top++, TocDto.splitLabel(group[0]));
            for (int i = 1; i < group.length; i++) {
                insert(pbancSn, se, parent.getTocSn(), i - 1, TocDto.splitLabel(group[i]));
            }
        }
    }

    /* ---------- 내부 처리 ---------- */

    /**
     * 제안서 목차를 고칠 때마다 지금 양식(TB_BID_PBANC.TOC_FORM_SN)에도 바로 반영한다 (v4.0, 2026-10-31 결정).
     * 그래서 다른 양식으로 바꿨다가 돌아와도 고친 목차가 남아 있다. 담당자·요구사항 연결은 양식에 넣지 않는다.
     * 직접 만든 목차(양식 없음), 삭제한 양식, 요약서 목차, 목차를 모두 지운 경우는 건드리지 않는다.
     */
    private void syncForm(Long pbancSn, String se) {
        if (!TocDto.SE_PROPOSAL.equals(se)) {
            return;
        }
        TocFormDto form = mapper.findBidForm(pbancSn);
        if (form == null || !form.isInUse()) {
            return;
        }
        List<TocDto> tocs = mapper.findByBid(pbancSn, se);
        if (!tocs.isEmpty()) {
            writeFormItems(form.getTocFormSn(), se, tocs);
        }
    }

    /** 양식의 항목을 지우고 사업건 목차(tocs, 부모가 먼저 나오는 순서)로 다시 채운다. 번호·이름·순서·최하위 쪽수만 옮긴다 */
    private void writeFormItems(Long formSn, String se, List<TocDto> tocs) {
        List<TocDto> old = mapper.findFormItems(formSn);
        for (int i = old.size() - 1; i >= 0; i--) { // 깊은 하위부터 지운다
            mapper.deleteFormItem(old.get(i).getTocSn());
        }
        Map<Long, Long> idMap = new HashMap<>();
        for (TocDto src : tocs) { // 부모가 먼저 나오므로 부모의 새 번호를 바로 찾을 수 있다
            TocDto item = new TocDto();
            item.setTocFormSn(formSn);
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

    private TocDto insert(Long pbancSn, String se, Long upTocSn, int sortSeq, String[] noAndName) {
        TocDto t = new TocDto();
        t.setPbancSn(pbancSn);
        t.setTocSeCd(se);
        t.setUpTocSn(upTocSn);
        t.setSortSeq(sortSeq);
        t.setTocNo(noAndName[0]);
        t.setTocNm(noAndName[1]);
        mapper.insert(t);
        return t;
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

    /** 이 주관팀의 사용 중인 양식인지 확인한다. 아니면 예외 */
    private TocFormDto ownForm(Long tocFormSn, Long teamSn) {
        TocFormDto f = tocFormSn == null ? null : mapper.findForm(tocFormSn);
        if (f == null || !f.isInUse() || teamSn == null || !teamSn.equals(f.getTeamSn())) {
            throw new BusinessException("선택한 목차 양식을 찾을 수 없습니다. 주관팀이 저장한 양식만 고칠 수 있습니다.");
        }
        return f;
    }

    private String cleanFormName(String formName) {
        String nm = formName == null ? "" : formName.trim().replaceAll("\\s+", " ");
        if (nm.isEmpty()) {
            throw new BusinessException("양식 이름을 입력하세요.");
        }
        if (nm.length() > 100) {
            throw new BusinessException("양식 이름은 100자까지 입력할 수 있습니다.");
        }
        return nm;
    }

    /**
     * 외부 회사명을 정리한다. 비어 있으면 null.
     * 이 사업건에 이미 쓴 회사명과 공백·대소문자·(주)·㈜·주식회사 표기만 다르면 기존 이름을 그대로 쓴다. (FR-043-1)
     */
    private String cleanCoName(Long pbancSn, String coName) {
        String nm = coName == null ? "" : coName.trim().replaceAll("\\s+", " ");
        if (nm.isEmpty()) {
            return null;
        }
        if (nm.length() > 200) {
            throw new BusinessException("외부 회사명은 200자까지 입력할 수 있습니다.");
        }
        String key = coKey(nm);
        if (key.isEmpty()) {
            throw new BusinessException("외부 회사명을 입력하세요.");
        }
        for (String used : mapper.findCoNames(pbancSn)) {
            if (coKey(used).equals(key)) {
                return used;
            }
        }
        return nm;
    }

    /** 회사명 비교용 키: (주)·㈜·주식회사와 공백을 빼고 소문자로 */
    static String coKey(String name) {
        return name.replace("(주)", "").replace("㈜", "").replace("주식회사", "")
                .replaceAll("\\s+", "").toLowerCase();
    }

    private static String seName(String tocSeCd) {
        return TocDto.SE_SUMMARY.equals(tocSeCd) ? "제안요약서" : "제안서";
    }

    /** 주관팀 양식은 제안서 목차에만 쓴다. 제안요약서는 기본 목차로 시작한다 */
    private String checkFormSe(String tocSeCd) {
        String se = checkSe(tocSeCd);
        if (TocDto.SE_SUMMARY.equals(se)) {
            throw new BusinessException("제안요약서는 목차 양식을 쓰지 않습니다. [기본 목차로 되돌리기]를 쓰거나 목차를 직접 수정하세요.");
        }
        return se;
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
