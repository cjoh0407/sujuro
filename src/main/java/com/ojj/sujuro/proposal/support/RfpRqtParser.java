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
    /** 번호 없이 ID부터 나오는 목록 (11/10): "MAR-001 유지관리 수행 방안 제시", 앞에 구분 칸 글자, 뒤에 구분별 개수가 붙을 수 있다.
     *  "유지관리 수행 MAR-012 사이버보안 실태 평가 지원" / "요구사항 MAR-013 정보화 평가 지원 23". 번호는 읽은 순서대로 매긴다.
     *  ID는 붙임표(-)가 있는 모양만 받는다 (본문 글자를 요구사항으로 잘못 읽지 않게) */
    private static final Pattern ROW_ID = Pattern.compile("^\\s*(?:(\\S.*?)\\s+)?([A-Z]{2,4})-(\\d{1,4})(?:\\s+(\\S.*?))?\\s*$");
    /** 가운데에 영문이 더 붙은 ID (11/10): "FUN-QMS01" → 접두어 FUN, 번호 01. ID는 원래 모양 그대로 저장한다 */
    private static final Pattern INFIX_ID = Pattern.compile("\\b([A-Z]{2,4})-([A-Z]{1,4})(\\d{1,4})\\b");
    /** ROW_ID 앞 글자가 이런 말이면 목록이 아니라 상세 설명 표("요구사항 고유번호 MAR-001")이므로 읽지 않는다 */
    private static final Pattern NOT_LIST = Pattern.compile("고유\\s*번호|요구사항\\s*ID|관련|번호|분류");
    /** 숫자만 있는 낱말·줄 (구분별 요구사항 수) */
    private static final Pattern COUNT_ONLY = Pattern.compile("^\\s*(\\d{1,3})\\s*$");
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
            // PDFBox가 띄어쓰기가 든 덩어리("데이터 구조 관리 4")를 한 번에 넘길 때가 있다 (11/10).
            // 낱말마다 위치를 알아야 이름 칸 위치·멀리 떨어진 개수 숫자를 가릴 수 있으므로 글자 위치로 낱말을 나눈다
            if (positions.size() == text.length()) {
                int start = -1;
                for (int c = 0; c <= text.length(); c++) {
                    boolean end = c == text.length() || Character.isWhitespace(text.charAt(c));
                    if (!end && start < 0) {
                        start = c;
                    } else if (end && start >= 0) {
                        TextPosition tp = positions.get(start);
                        cur.add(text.substring(start, c), tp.getXDirAdj(), tp.getYDirAdj());
                        start = -1;
                    }
                }
                return;
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

    /**
     * PDF가 낱말 사이에 빈칸을 끼워 나눠 놓은 것을 붙인다 (11/11).
     * ID: "SFR- 001", "SFR -001", "SFR - 001" → "SFR-001" / 영문 약어: "H/ W", "S/ W" → "H/W", "S/W".
     * 위치(x·y)는 앞 낱말의 것을 그대로 쓴다. ID 모양이 아닌 말("SFR-00 06" 같은 개수 표기)은 건드리지 않는다.
     */
    private static void joinSplitTokens(List<Line> lines) {
        for (Line l : lines) {
            for (int i = 0; i + 1 < l.words.size(); i++) {
                String a = l.words.get(i);
                String b = l.words.get(i + 1);
                String joined = null;
                int take = 0;
                if (a.matches("[A-Z]{2,4}-") && b.matches("\\d{1,4}")) {
                    joined = a + b;
                    take = 2;
                } else if (a.matches("[A-Z]{2,4}") && b.matches("-\\d{1,4}")) {
                    joined = a + b;
                    take = 2;
                } else if (a.matches("[A-Z]{2,4}") && "-".equals(b) && i + 2 < l.words.size()
                        && l.words.get(i + 2).matches("\\d{1,4}")) {
                    joined = a + "-" + l.words.get(i + 2);
                    take = 3;
                } else if (a.matches("[A-Za-z]/") && b.matches("[A-Za-z]")) {
                    joined = a + b;
                    take = 2;
                }
                if (joined != null) {
                    l.words.set(i, joined);
                    for (int k = 1; k < take; k++) {
                        l.words.remove(i + 1);
                        l.xs.remove(i + 1);
                        l.ys.remove(i + 1);
                    }
                }
            }
        }
    }

    /**
     * 목록표 머리글에 "비고" 칸이 있으면 그 칸(머리글 "비고" 위치부터 오른쪽)에 찍힌 글자를 뺀다 (11/11).
     * 비고 칸은 여러 줄을 합친 칸이라 "SIMS"·"G4B"처럼 요구사항 이름 줄 끝이나 줄 사이에 따로 찍혀 이름 조각으로 오해받는다.
     * 비고 머리글이 나온 줄부터만 적용하므로 그 앞의 총괄표(개수 칸이 오른쪽 끝에 있다)는 그대로 읽는다.
     * 머리글에서 "비고"를 못 찾으면 줄을 그대로 둔다.
     */
    private static List<Line> dropRemarks(List<Line> lines) {
        List<Line> out = new ArrayList<>(lines.size());
        float remarkX = -1;
        for (Line l : lines) {
            float hx = remarksHeaderX(l);
            if (hx >= 0) {
                remarkX = remarkX < 0 ? hx : Math.min(remarkX, hx);
                out.add(l); // 머리글 줄은 아래에서 "머리"로 걸러진다
                continue;
            }
            if (remarkX < 0) {
                out.add(l);
                continue;
            }
            for (int k = l.words.size() - 1; k >= 0; k--) {
                if (l.xs.get(k) >= remarkX - 15f) {
                    l.words.remove(k);
                    l.xs.remove(k);
                    l.ys.remove(k);
                }
            }
            if (!l.words.isEmpty()) {
                out.add(l);
            }
        }
        return out;
    }

    /** "요구사항" 낱말이 있는 머리글 줄에서 "비고"(또는 "비 고") 낱말의 가로 위치. 없으면 -1 */
    private static float remarksHeaderX(Line l) {
        boolean head = false;
        for (String w : l.words) {
            if (w.contains("요구사항")) {
                head = true;
            }
        }
        if (!head) {
            return -1;
        }
        for (int k = 0; k < l.words.size(); k++) {
            String w = l.words.get(k);
            if ("비고".equals(w) || ("비".equals(w) && k + 1 < l.words.size() && "고".equals(l.words.get(k + 1)))) {
                return l.xs.get(k) > 0 ? l.xs.get(k) : -1; // 위치를 모르는(글자만 있는) 줄은 0이라 칸을 가를 수 없다
            }
        }
        return -1;
    }

    /** 줄 목록에서 총괄표·목록표를 읽고 개수를 검사한다 */
    public static RqtExtractDto parse(List<Line> lines) {
        joinSplitTokens(lines);
        lines = dropRemarks(lines);
        RqtExtractDto r = new RqtExtractDto();
        int n = lines.size();
        Matcher[] rows = new Matcher[n];
        String[] mids = new String[n]; // 목록표 "구분" 칸 글자 (번호와 ID 사이에 있을 때)
        Integer[] counts = new Integer[n]; // 목록표에 구분별 요구사항 수가 같이 적힌 경우 그 숫자 (ROW_ID 줄 끝·숫자만 있는 줄)
        int seq = 0; // 번호 없는 목록(ROW_ID)에 매기는 번호
        String[] rawIds = new String[n]; // "FUN-QMS01"처럼 원래 ID를 그대로 둘 줄
        boolean[] other = new boolean[n]; // 총괄표·합계·머리·쪽 번호 줄
        List<Float> nameXs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Line l = lines.get(i);
            String t = l.text();
            // "FUN-QMS01"은 "FUN-01"로 바꿔 읽고(낱말 수는 그대로) 원래 ID는 따로 기억한다. 총괄표 "FUN-QMS00 20"도 같은 방법으로 읽힌다
            String rawId = null;
            Matcher im = INFIX_ID.matcher(t);
            if (im.find()) {
                rawId = im.group();
                t = t.substring(0, im.start()) + im.group(1) + "-" + im.group(3) + t.substring(im.end());
            }
            Matcher row = ROW.matcher(t);
            boolean ok = row.matches();
            int k = l.words.size(); // 이름 첫 낱말 위치 = 이름 앞 글자의 낱말 수 (낱말은 한 칸 띄어 이어 붙였으므로 그대로 센다)
            if (ok && row.group(4) != null) {
                k = words(t.substring(0, row.start(4)));
            }
            if (!ok) {
                Matcher mid = ROW_MID.matcher(t);
                if (mid.matches()) {
                    mids[i] = mid.group(2);
                    String name = mid.group(5);
                    // 구분 칸을 뺀 모양으로 다시 맞춰 아래에서는 보통 줄과 똑같이 다룬다
                    row = ROW.matcher(mid.group(1) + " " + mid.group(3) + " " + mid.group(4) + (name != null ? " " + name : ""));
                    ok = row.matches();
                    if (name != null) {
                        k = words(t.substring(0, mid.start(5)));
                    }
                }
            }
            if (!ok) {
                Matcher idr = ROW_ID.matcher(t);
                // 총괄표 줄("기능 요구사항 SFR-00 7")·번호가 0뿐인 ID·숫자만 있는 이름은 목록이 아니다
                if (idr.matches() && (idr.group(1) == null || !NOT_LIST.matcher(idr.group(1)).find())
                        && Integer.parseInt(idr.group(3)) > 0 && (idr.group(4) == null || !idr.group(4).matches("[\\d\\s]+"))
                        && (idr.group(4) != null || idr.group(1) != null || t.trim().equals(idr.group(2) + "-" + idr.group(3)))
                        && !SUMMARY.matcher(t).find() && !SUMMARY_EN.matcher(t).matches()) {
                    // 이름이 위·아래 줄로 나뉘어 ID 줄에 이름이 없을 수도 있다 (그때는 아래에서 이름 조각을 붙인다)
                    List<String> nm = idr.group(4) == null ? new ArrayList<>()
                            : new ArrayList<>(java.util.Arrays.asList(idr.group(4).split("\\s+")));
                    // 줄 끝 숫자가 멀리 떨어져 있으면(개수 칸) 이름이 아니라 구분별 요구사항 수다. 위치를 모르면(글자만) 숫자만 있는 끝 낱말을 개수로 본다
                    int last = l.words.size() - 1;
                    boolean farNum = nm.size() > 1 && COUNT_ONLY.matcher(nm.get(nm.size() - 1)).matches()
                            && (l.xs.get(last) == 0f || l.xs.get(last) - l.xs.get(last - 1) > 60f);
                    if (farNum) {
                        counts[i] = Integer.parseInt(nm.remove(nm.size() - 1));
                    }
                    if (idr.group(1) != null) {
                        mids[i] = idr.group(1);
                    }
                    row = ROW.matcher((++seq) + " " + idr.group(2) + " " + idr.group(3) + (nm.isEmpty() ? "" : " " + String.join(" ", nm)));
                    ok = row.matches();
                    if (idr.group(4) != null) {
                        k = words(t.substring(0, idr.start(4)));
                    }
                }
            }
            if (ok) {
                rows[i] = row;
                rawIds[i] = rawId;
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
                String gn = groupName(korean(t.substring(0, sum.start(1))));
                if (gn.isEmpty()) { // 구분명이 위 줄에 따로 있는 총괄표: "성능요구사항" / "Performance Requirement PER-000 5" / "(PER)"
                    gn = groupName(nameAbove(lines, other, i));
                }
                if (!gn.isEmpty()) {
                    r.getGroupNames().putIfAbsent(sum.group(1), gn); // 접두어별 구분명 (예: SFR → 기능 요구사항)
                }
                other[i] = true;
            } else if (total.matches()) {
                r.setDeclaredTotal(Integer.parseInt(total.group(1)));
                other[i] = true;
            } else if (FOOTER.matcher(t).matches() || HEADER.matcher(t).find()) {
                other[i] = true;
            } else if (COUNT_ONLY.matcher(t).matches()) {
                counts[i] = Integer.parseInt(t.trim()); // 구분 칸 옆 "요구사항 수" 칸만 따로 찍힌 줄 (이름 조각이 아니다)
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
        groupNamesFromList(r, lines, rows, mids, counts, other, nameX);

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
            item.setRqtMttrNo(rawIds[k] != null ? rawIds[k] : row.group(2) + "-" + String.format("%03d", Integer.parseInt(row.group(3))));
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
    private static void groupNamesFromList(RqtExtractDto r, List<Line> lines, Matcher[] rows, String[] mids, Integer[] counts,
                                           boolean[] other, float nameX) {
        int n = lines.size();
        Map<String, StringBuilder> acc = new java.util.LinkedHashMap<>();
        Map<String, Integer> cnt = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String p = null;
            String text = null;
            if (rows[i] != null) {
                p = rows[i].group(2);
                text = mids[i];
            } else if (counts[i] != null || (!other[i] && lines.get(i).x() < nameX)) {
                int a = i - 1;
                while (a >= 0 && rows[a] == null && lines.get(a).page == lines.get(i).page) { // 위로 가장 가까운 요구사항 줄
                    a--;
                }
                int b = i + 1;
                while (b < n && rows[b] == null && lines.get(b).page == lines.get(i).page) { // 아래로 가장 가까운 요구사항 줄
                    b++;
                }
                if (a >= 0 && b < n && rows[a] != null && rows[b] != null && lines.get(a).page == lines.get(i).page
                        && lines.get(b).page == lines.get(i).page) {
                    // 위·아래 접두어가 같으면 그 구분, 다르면 세로로 더 가까운 요구사항의 구분 (한 줄짜리 구분 칸이 두 줄로 찍힌 경우)
                    float ya = lines.get(a).y, yb = lines.get(b).y, yi = lines.get(i).y;
                    p = rows[a].group(2).equals(rows[b].group(2)) || yi - ya <= yb - yi ? rows[a].group(2) : rows[b].group(2);
                    text = counts[i] != null ? null : lines.get(i).text();
                }
            }
            if (p == null) {
                continue;
            }
            // 구분 칸 줄 끝의 숫자는 그 구분의 요구사항 수 ("요구사항 2")
            if (text != null) {
                Matcher tail = Pattern.compile("^(.*?)\\s+(\\d{1,3})\\s*$").matcher(text);
                if (tail.matches() && rows[i] == null) {
                    text = tail.group(1);
                    cnt.putIfAbsent(p, Integer.parseInt(tail.group(2)));
                }
                acc.computeIfAbsent(p, x -> new StringBuilder()).append(' ').append(text);
            }
            if (counts[i] != null) {
                cnt.putIfAbsent(p, counts[i]);
            }
        }
        // 목록표에 구분별 요구사항 수가 적혀 있으면 총괄표처럼 개수를 비교한다 (총괄표에서 이미 읽었으면 그대로)
        if (!cnt.isEmpty() && r.getExpected().isEmpty()) {
            r.getExpected().putAll(cnt);
            r.setSummaryFound(true);
        }
        for (Map.Entry<String, StringBuilder> e : acc.entrySet()) {
            String nm = e.getValue().toString()
                    .replaceAll("\\([^)]*\\)?", " ")      // (영문 설명)
                    .replaceAll("[A-Za-z()]+", " ")         // 줄이 나뉘어 남은 영문 조각
                    .replaceAll("\\s+", " ").trim();
            if (!nm.isEmpty()) {
                r.getGroupNames().putIfAbsent(e.getKey(), joinGroupWords(nm));
            }
        }
    }

    /** 한글 구분명만 남긴다: 괄호 속 접두어·영문 설명과 영문 낱말을 뺀다 ("(PER) Performance Requirement" → "") */
    static String korean(String s) {
        return (s == null ? "" : s).replaceAll("\\([^)]*\\)?", " ").replaceAll("[A-Za-z()]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** 총괄표 줄 바로 위 줄의 한글 구분명 (요구사항·총괄표 줄이나 표 머리글이면 빈 글자) */
    private static String nameAbove(List<Line> lines, boolean[] other, int i) {
        if (i == 0 || other[i - 1] || lines.get(i - 1).page != lines.get(i).page) {
            return "";
        }
        String t = lines.get(i - 1).text();
        if (SUMMARY.matcher(t).find() || ROW.matcher(t).matches()) {
            return "";
        }
        String k = korean(t);
        return k.matches(".*(구분|부여|사항수|총괄표).*") || k.length() < 2 ? "" : k;
    }

    /** 글자의 낱말 수 (빈 글자는 0) */
    private static int words(String s) {
        String t = s.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    /**
     * 총괄표 줄에서 ID 앞의 글자를 구분명으로 쓴다 (11/09). 큰 분류(기능·비기능) 칸이 같은 줄에 붙어 나오면 뗀다.
     *   "기능 기능 요구사항" → "기능 요구사항", "비기능 보안 요구사항" → "보안 요구사항", "기능 요구사항" → 그대로
     */
    /** 칸 폭 때문에 줄이 나뉜 "요구 사항"은 "요구사항"으로 붙인다 (11/11) */
    private static String joinGroupWords(String s) {
        return s.replaceAll("요구\\s+사항", "요구사항");
    }

    static String groupName(String raw) {
        String s = raw == null ? "" : joinGroupWords(raw).replaceAll("\\s+", " ").trim();
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
