package com.ojj.sujuro.proposal.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import com.ojj.sujuro.common.exception.BusinessException;
import com.ojj.sujuro.proposal.dto.RqtExtractDto;
import com.ojj.sujuro.proposal.dto.RqtExtractItemDto;

/**
 * 제안요청서 PDF에서 요구사항 ID·명칭을 뽑는다 (FR-050-1, AI 사용 안 함).
 * 1) PDFBox로 글자를 줄 단위로 뽑으면서 줄마다 쪽·세로 위치·낱말의 가로 위치를 같이 기록하고
 * 2) 정규식으로 "요구사항 총괄표"와 "요구사항 목록표"를 읽는다.
 *   총괄표 줄 : "기능 요구사항  SFR-00  7"   → 접두어 SFR 7개 / "합 계  111" → 합계
 *   목록표 줄 : "01  SFR  01  이용신청 일원화" → SFR-001 이용신청 일원화 (ID가 "SFR-001", "SFR01"로 붙어 있어도 읽는다)
 * 이름이 여러 줄인 칸: 같은 칸 안의 줄은 간격이 촘촘하고, 칸이 바뀌면 칸 여백만큼 간격이 벌어진다.
 *   그래서 두 요구사항 사이에 낀 이름 조각은 "가장 크게 벌어진 간격"을 칸 경계로 보고 위·아래 요구사항에 나눠 붙인다.
 *   표 칸이 위쪽·가운데·아래쪽 정렬이어도 맞게 붙는다. 간격이 같아 경계를 알 수 없으면 앞 요구사항에 붙이고 "확인 필요"로 표시한다.
 * 스캔한 이미지 PDF는 글자가 없어 읽지 못한다. 표 모양이 다르면 못 읽은 줄이 생기므로 사람이 확인한 뒤 등록한다.
 */
public final class RfpRqtParser {

    private static final Pattern SUMMARY = Pattern.compile("([A-Z]{2,4})-0+\\s+(\\d{1,4})\\s*$");
    /** 총괄표의 다른 모양 (11/10): "유지관리 수행 요구사항  MAR(Maintenance Requirement)  16" — ID 규칙 대신 접두어(영문 이름), 개수는 비어 있을 수 있다 */
    private static final Pattern SUMMARY_EN = Pattern.compile("^\\s*(\\S.*?)\\s+([A-Z]{2,4})\\s*\\(\\s*[A-Za-z][A-Za-z .&-]*\\)\\s*(\\d{1,4})?\\s*$");
    private static final Pattern TOTAL = Pattern.compile("^\\s*합\\s*계\\s+(\\d{1,4})\\s*$");
    // 이름이 없는 요구사항 줄도 받는다: 가운데 정렬 칸에서 번호가 이름 줄들 사이에 따로 찍히는 경우
    private static final Pattern ROW = Pattern.compile("^\\s*(\\d{1,4})\\s+([A-Z]{2,4})[\\s-]*(\\d{1,4})(?:\\s+(\\S.*?))?\\s*$");
    /** 목록표에 "구분" 칸이 번호와 ID 사이에 있는 모양 (11/10): "8 유지관리 수행 요구사항 MAR 8 예비부품 확보 및 지원 요건".
     *  가운데 글자는 구분명으로 쓰고, 나머지는 ROW 와 같은 모양으로 읽는다 */
    private static final Pattern ROW_MID = Pattern.compile("^\\s*(\\d{1,4})\\s+(\\S.*?)\\s+([A-Z]{2,4})[\\s-]*(\\d{1,4})(?:\\s+(\\S.*?))?\\s*$");
    private static final Pattern FOOTER = Pattern.compile("^\\s*-\\s*\\d+\\s*-\\s*$");
    private static final Pattern HEADER = Pattern.compile("요구사항\\s*명|^\\s*No\\b");
    private static final String WRAP_NOTE = "이름 여러 줄";
    private static final String WRAP_CHECK_NOTE = "이름 여러 줄 · 확인 필요";
    /** 칸 경계로 볼 간격: 다른 간격보다 이만큼 넓어야 한다 (8%) */
    private static final double GAP_RATIO = 1.08;

    private RfpRqtParser() {
    }

    /** PDF 한 줄. 쪽, 세로 위치(아래로 갈수록 커짐), 낱말과 각 낱말의 가로·세로 위치 */
    public static final class Line {
        private final int page;
        /** 줄 간격을 잴 세로 위치. 요구사항 줄이면 이름 첫 낱말의 위치로 바꾼다 (번호는 칸 가운데에 찍혀 이름 줄과 어긋날 수 있다) */
        private float y;
        private final List<String> words = new ArrayList<>();
        private final List<Float> xs = new ArrayList<>();
        private final List<Float> ys = new ArrayList<>();

        public Line(int page, float y) {
            this.page = page;
            this.y = y;
        }

        public void add(String word, float x, float y) {
            words.add(word);
            xs.add(x);
            ys.add(y);
        }

        String text() {
            return String.join(" ", words);
        }

        float x() {
            return xs.isEmpty() ? 0 : xs.get(0);
        }
    }

    /** PDF 글자를 줄 단위로(위치와 함께) 뽑는다. 제안팀이 요구사항 총괄표·목록표 쪽만 골라 올리므로 쪽 범위는 받지 않는다 */
    public static List<Line> readLines(InputStream pdf) {
        try (PDDocument doc = PDDocument.load(pdf)) {
            LineStripper stripper = new LineStripper();
            stripper.setSortByPosition(true); // 표가 줄 단위로 나오도록 글자 위치 순서로 읽는다
            stripper.getText(doc);
            return stripper.lines;
        } catch (IOException e) {
            throw new BusinessException("PDF를 읽을 수 없습니다. 암호가 걸렸거나 손상된 파일인지 확인해 주세요.");
        }
    }

    /** PDFBox가 줄을 쓸 때 낱말마다 위치를 같이 모은다 (글자 출력 대신) */
    private static final class LineStripper extends PDFTextStripper {
        private final List<Line> lines = new ArrayList<>();
        private Line cur;

        LineStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (text == null || text.trim().isEmpty() || positions == null || positions.isEmpty()) {
                return;
            }
            TextPosition first = positions.get(0);
            if (cur == null) {
                cur = new Line(getCurrentPageNo(), first.getYDirAdj());
            }
            cur.add(text.trim(), first.getXDirAdj(), first.getYDirAdj());
        }

        @Override
        protected void writeWordSeparator() {
            // 낱말은 Line에 따로 모으므로 구분자를 쓰지 않는다
        }

        @Override
        protected void writeLineSeparator() {
            flush();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            flush();
            super.endPage(page);
        }

        private void flush() {
            if (cur != null) {
                lines.add(cur);
                cur = null;
            }
        }
    }

    /**
     * 위치 정보 없이 글자만 있을 때 (단위 테스트·예비용). 줄 간격을 모두 같게 보고, 빈 줄은 간격을 넓게 본다.
     * 간격이 같으면 칸 경계를 알 수 없으므로 여러 줄 이름은 앞 요구사항에 붙이고 "확인 필요"로 표시된다.
     */
    public static RqtExtractDto parse(String text) {
        List<Line> lines = new ArrayList<>();
        float y = 0;
        for (String t : (text == null ? "" : text).split("\\r?\\n")) {
            y += 10;
            if (t.trim().isEmpty()) {
                y += 10;
                continue;
            }
            Line l = new Line(1, y);
            for (String w : t.trim().split("\\s+")) {
                l.add(w, 0, y);
            }
            lines.add(l);
        }
        return parse(lines);
    }

    /** 줄 목록에서 총괄표·목록표를 읽고 개수를 검사한다 */
    public static RqtExtractDto parse(List<Line> lines) {
        RqtExtractDto r = new RqtExtractDto();
        int n = lines.size();
        Matcher[] rows = new Matcher[n];
        String[] mids = new String[n]; // 목록표 "구분" 칸 글자 (번호와 ID 사이에 있을 때)
        boolean[] other = new boolean[n]; // 총괄표·합계·머리·쪽 번호 줄
        List<Float> nameXs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Line l = lines.get(i);
            String t = l.text();
            Matcher row = ROW.matcher(t);
            boolean ok = row.matches();
            if (!ok) {
                Matcher mid = ROW_MID.matcher(t);
                if (mid.matches()) {
                    mids[i] = mid.group(2);
                    String name = mid.group(5);
                    // 구분 칸을 뺀 모양으로 다시 맞춰 아래에서는 보통 줄과 똑같이 다룬다
                    row = ROW.matcher(mid.group(1) + " " + mid.group(3) + " " + mid.group(4) + (name != null ? " " + name : ""));
                    ok = row.matches();
                }
            }
            if (ok) {
                rows[i] = row;
                // 이름 첫 낱말 위치 = 이름 앞 글자의 낱말 수 (낱말은 한 칸 띄어 이어 붙였으므로 그대로 센다)
                int k = l.words.size();
                Matcher whole = ROW.matcher(t).matches() ? ROW.matcher(t) : ROW_MID.matcher(t);
                if (whole.matches()) {
                    int g = whole.pattern() == ROW ? 4 : 5;
                    if (whole.group(g) != null) {
                        String before = t.substring(0, whole.start(g)).trim();
                        k = before.isEmpty() ? 0 : before.split("\\s+").length;
                    }
                }
                if (l.words.size() > k) {
                    nameXs.add(l.xs.get(k));
                    l.y = l.ys.get(k); // 간격은 이름 줄 기준으로 잰다
                }
                continue;
            }
            Matcher sum = SUMMARY.matcher(t);
            Matcher sumEn = SUMMARY_EN.matcher(t);
            Matcher total = TOTAL.matcher(t);
            if (sumEn.matches()) {
                r.setSummaryFound(true);
                if (sumEn.group(3) != null) {
                    r.getExpected().merge(sumEn.group(2), Integer.parseInt(sumEn.group(3)), Integer::sum);
                }
                String gn = groupName(sumEn.group(1));
                if (!gn.isEmpty()) {
                    r.getGroupNames().putIfAbsent(sumEn.group(2), gn);
                }
                other[i] = true;
            } else if (sum.find()) {
                r.setSummaryFound(true);
                r.getExpected().merge(sum.group(1), Integer.parseInt(sum.group(2)), Integer::sum);
                String gn = groupName(t.substring(0, sum.start(1)));
                if (!gn.isEmpty()) {
                    r.getGroupNames().putIfAbsent(sum.group(1), gn); // 접두어별 구분명 (예: SFR → 기능 요구사항)
                }
                other[i] = true;
            } else if (total.matches()) {
                r.setDeclaredTotal(Integer.parseInt(total.group(1)));
                other[i] = true;
            } else if (FOOTER.matcher(t).matches() || HEADER.matcher(t).find()) {
                other[i] = true;
            }
        }
        // 이름 칸의 왼쪽 위치. 이보다 왼쪽에서 시작하는 줄(제목·본문)은 이름 조각이 아니다
        float nameX = 0;
        if (!nameXs.isEmpty()) {
            nameXs.sort(null);
            nameX = nameXs.get(nameXs.size() / 2) - 3;
        }
        boolean[] frag = new boolean[n];
        for (int i = 0; i < n; i++) {
            frag[i] = rows[i] == null && !other[i] && lines.get(i).x() >= nameX;
        }
        groupNamesFromList(r, lines, rows, mids, other, nameX);

        // 이름 조각을 위·아래 요구사항에 나눠 붙인다 (owner[i] = 그 조각이 붙을 요구사항 줄 번호)
        int[] owner = new int[n];
        java.util.Arrays.fill(owner, -1);
        Set<Integer> unsure = new HashSet<>();
        int i = 0;
        while (i < n) {
            if (!frag[i]) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && frag[i] && lines.get(i).page == lines.get(s).page) {
                i++;
            }
            int e = i; // 조각 s..e-1
            int prev = s > 0 && lines.get(s - 1).page == lines.get(s).page && rows[s - 1] != null ? s - 1 : -1;
            int next = e < n && lines.get(e).page == lines.get(s).page && rows[e] != null ? e : -1;
            assign(lines, s, e, prev, next, owner, unsure);
        }

        Set<String> seenIds = new HashSet<>();
        List<Integer> nos = new ArrayList<>();
        Map<Integer, List<String>> before = new HashMap<>();
        Map<Integer, List<String>> after = new HashMap<>();
        for (int k = 0; k < n; k++) {
            if (owner[k] >= 0) {
                (k < owner[k] ? before : after).computeIfAbsent(owner[k], x -> new ArrayList<>()).add(lines.get(k).text());
            }
        }
        int unsureCnt = 0;
        for (int k = 0; k < n; k++) {
            if (rows[k] == null) {
                continue;
            }
            Matcher row = rows[k];
            List<String> parts = new ArrayList<>(before.getOrDefault(k, new ArrayList<>()));
            if (row.group(4) != null) {
                parts.add(row.group(4));
            }
            parts.addAll(after.getOrDefault(k, new ArrayList<>()));
            RqtExtractItemDto item = new RqtExtractItemDto();
            item.setNo(row.group(1));
            item.setRqtMttrNo(row.group(2) + "-" + String.format("%03d", Integer.parseInt(row.group(3))));
            item.setRqtMttrNm(String.join(" ", parts));
            if (!seenIds.add(item.getRqtMttrNo())) {
                item.setNote("ID가 목록에 두 번 나옵니다");
            } else if (unsure.contains(k)) {
                item.setNote(WRAP_CHECK_NOTE);
                unsureCnt++;
            } else if (parts.size() > 1 || row.group(4) == null) {
                item.setNote(WRAP_NOTE);
            }
            r.getItems().add(item);
            nos.add(Integer.parseInt(row.group(1)));
        }
        check(r, nos);
        if (unsureCnt > 0) {
            r.getWarnings().add("이름이 여러 줄인 요구사항 " + unsureCnt + "건은 줄 간격으로 칸을 구분하지 못해 앞 요구사항에 이어 붙였습니다. \""
                    + WRAP_CHECK_NOTE + "\" 표시된 줄을 원본과 비교해 주세요.");
        }
        return r;
    }

    /**
     * 총괄표가 없고 목록표에 "구분" 칸이 있는 제안요청서 (11/10): 구분 칸 글자를 접두어별로 모아 구분명으로 쓴다.
     * 구분 칸은 여러 줄을 합친 칸이라 글자가 번호·ID 사이에 끼거나(mids) 요구사항 줄 사이에 따로 찍힌다(이름 칸보다 왼쪽 줄).
     * 따로 찍힌 줄은 위·아래 요구사항의 접두어가 같을 때만 그 구분에 붙인다. 영문 설명 "(Maintenance Requirement)"은 뺀다.
     * 총괄표에서 이미 읽은 구분명이 있으면 그대로 둔다.
     */
    private static void groupNamesFromList(RqtExtractDto r, List<Line> lines, Matcher[] rows, String[] mids, boolean[] other, float nameX) {
        int n = lines.size();
        Map<String, StringBuilder> acc = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String p = null;
            String text = null;
            if (rows[i] != null && mids[i] != null) {
                p = rows[i].group(2);
                text = mids[i];
            } else if (rows[i] == null && !other[i] && lines.get(i).x() < nameX) {
                int a = i - 1;
                while (a >= 0 && rows[a] == null && !other[a] && lines.get(a).page == lines.get(i).page) {
                    a--;
                }
                int b = i + 1;
                while (b < n && rows[b] == null && !other[b] && lines.get(b).page == lines.get(i).page) {
                    b++;
                }
                if (a >= 0 && b < n && rows[a] != null && rows[b] != null && lines.get(a).page == lines.get(i).page
                        && lines.get(b).page == lines.get(i).page && rows[a].group(2).equals(rows[b].group(2))) {
                    p = rows[a].group(2);
                    text = lines.get(i).text();
                }
            }
            if (p != null && text != null) {
                acc.computeIfAbsent(p, x -> new StringBuilder()).append(' ').append(text);
            }
        }
        for (Map.Entry<String, StringBuilder> e : acc.entrySet()) {
            String nm = e.getValue().toString()
                    .replaceAll("\\([^)]*\\)?", " ")      // (영문 설명)
                    .replaceAll("[A-Za-z()]+", " ")         // 줄이 나뉘어 남은 영문 조각
                    .replaceAll("\\s+", " ").trim();
            if (!nm.isEmpty()) {
                r.getGroupNames().putIfAbsent(e.getKey(), nm);
            }
        }
    }

    /**
     * 총괄표 줄에서 ID 앞의 글자를 구분명으로 쓴다 (11/09). 큰 분류(기능·비기능) 칸이 같은 줄에 붙어 나오면 뗀다.
     *   "기능 기능 요구사항" → "기능 요구사항", "비기능 보안 요구사항" → "보안 요구사항", "기능 요구사항" → 그대로
     */
    static String groupName(String raw) {
        String s = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (s.startsWith("비기능 ")) {
            s = s.substring(4).trim();
        } else if (s.startsWith("기능 ") && !"기능 요구사항".equals(s)) {
            s = s.substring(3).trim();
        }
        return s;
    }

    /**
     * 조각 s..e-1을 앞 요구사항(prev)·뒤 요구사항(next)에 나눈다. 없는 쪽은 -1.
     * 둘 다 있으면 가장 크게 벌어진 간격을 칸 경계로 본다. 한쪽만 있으면 그쪽에 촘촘히 붙어 있는 조각만 붙인다.
     */
    private static void assign(List<Line> lines, int s, int e, int prev, int next, int[] owner, Set<Integer> unsure) {
        if (prev < 0 && next < 0) {
            return; // 요구사항과 상관없는 글 (제목·본문 등)
        }
        int from = prev >= 0 ? prev : s;
        int to = next >= 0 ? next : e - 1;
        float[] gaps = new float[to - from]; // gaps[j] = (from+j) 줄과 (from+j+1) 줄 사이 간격
        for (int j = 0; j < gaps.length; j++) {
            gaps[j] = lines.get(from + j + 1).y - lines.get(from + j).y;
        }
        if (prev >= 0 && next >= 0) {
            int cut = 0;
            for (int j = 1; j < gaps.length; j++) {
                if (gaps[j] > gaps[cut]) {
                    cut = j;
                }
            }
            boolean clear = true;
            for (int j = 0; j < gaps.length; j++) {
                if (j != cut && gaps[cut] < gaps[j] * GAP_RATIO) {
                    clear = false;
                }
            }
            if (!clear) { // 간격이 모두 비슷하면 경계를 알 수 없다: 앞 요구사항에 붙이고 확인 표시
                for (int k = s; k < e; k++) {
                    owner[k] = prev;
                }
                unsure.add(prev);
                return;
            }
            for (int k = s; k < e; k++) {
                owner[k] = k <= from + cut ? prev : next; // 경계(cut) 위까지는 앞, 아래는 뒤 요구사항
            }
        } else if (prev >= 0) { // 뒤에 요구사항이 없음(목록 끝·쪽 끝): 앞 줄과 같은 간격으로 이어진 조각만
            float base = gaps[0];
            for (int k = s; k < e; k++) {
                if (k > s && gaps[k - from - 1] > base * GAP_RATIO) {
                    break;
                }
                owner[k] = prev;
            }
        } else { // 앞이 표 머리 등: 뒤 요구사항 바로 위에 같은 간격으로 붙은 조각만
            float base = gaps[gaps.length - 1];
            for (int k = e - 1; k >= s; k--) {
                if (k < e - 1 && gaps[k - from] > base * GAP_RATIO) {
                    break;
                }
                owner[k] = next;
            }
        }
    }

    /** 총괄표와 목록의 개수, 번호가 빠지거나 겹친 곳을 경고로 남긴다 (등록은 막지 않는다) */
    private static void check(RqtExtractDto r, List<Integer> nos) {
        List<String> w = r.getWarnings();
        int found = r.getFoundCount();
        if (found == 0) {
            w.add("요구사항 목록을 찾지 못했습니다. 스캔한 이미지 PDF이거나 표 모양이 달라 읽지 못했을 수 있습니다. 요구사항 목록표가 들어 있는 PDF인지 확인하거나 수기로 등록해 주세요.");
            return;
        }
        if (r.getExpected().isEmpty() && r.isSummaryFound()) {
            w.add("요구사항 총괄표에 구분별 요구사항 수가 적혀 있지 않아 개수를 비교하지 못했습니다. 목록을 직접 확인해 주세요.");
        } else if (r.getExpected().isEmpty()) {
            w.add("요구사항 총괄표(ID 부여규칙별 개수)를 찾지 못해 개수를 비교하지 못했습니다. 목록을 직접 확인해 주세요.");
        } else {
            Map<String, Integer> got = r.getFound();
            Set<String> prefixes = new TreeSet<>(r.getExpected().keySet());
            prefixes.addAll(got.keySet());
            for (String p : prefixes) {
                int exp = r.getExpected().getOrDefault(p, 0);
                int act = got.getOrDefault(p, 0);
                if (exp != act) {
                    w.add(p + " 요구사항 개수가 다릅니다: 총괄표 " + exp + "개 · 목록 " + act + "개. 확인해 주세요.");
                }
            }
            if (r.getExpectedSum() != found) {
                w.add("총괄표 항목별 합(" + r.getExpectedSum() + "개)과 목록에서 읽은 수(" + found + "개)가 다릅니다. 확인해 주세요.");
            }
        }
        if (r.getDeclaredTotal() != null && r.getDeclaredTotal() != found) {
            w.add("총괄표 합계(" + r.getDeclaredTotal() + "개)와 목록에서 읽은 수(" + found + "개)가 다릅니다."
                    + (r.getDeclaredTotal() != r.getExpectedSum() && r.getExpectedSum() == found
                        ? " 항목별 개수를 더하면 " + found + "개라 원본의 합계 숫자가 틀렸을 수 있습니다." : " 확인해 주세요."));
        }
        // 목록 No가 1부터 차례대로인지
        List<Integer> missing = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        List<Integer> dup = new ArrayList<>();
        int max = 0;
        for (int n : nos) {
            if (!seen.add(n)) {
                dup.add(n);
            }
            max = Math.max(max, n);
        }
        for (int n = 1; n <= max; n++) {
            if (!seen.contains(n)) {
                missing.add(n);
            }
        }
        if (!missing.isEmpty()) {
            w.add("목록 번호(No) 중 읽지 못한 번호가 있습니다: " + shortList(missing) + ". 이름이 여러 줄이거나 표 모양이 달라 빠졌을 수 있습니다.");
        }
        if (!dup.isEmpty()) {
            w.add("목록 번호(No)가 겹칩니다: " + shortList(dup));
        }
        for (RqtExtractItemDto i : r.getItems()) {
            if (i.getNote() != null && !i.getNote().startsWith(WRAP_NOTE)) {
                w.add(i.getRqtMttrNo() + ": " + i.getNote());
            }
        }
    }

    private static String shortList(List<Integer> ns) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < ns.size() && k < 10; k++) {
            sb.append(k == 0 ? "" : ", ").append(ns.get(k));
        }
        return ns.size() > 10 ? sb + " 외 " + (ns.size() - 10) + "개" : sb.toString();
    }
}
