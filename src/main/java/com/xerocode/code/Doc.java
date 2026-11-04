package com.xerocode.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import xero.dialect.Ink;
import xero.dialect.Syntax;

public final class Doc {
    public static final int OTHER = 0, TYPING = 1, DELETING = 2;
    public static final int TAB = 4;

    private static final long MERGE_MS = 900;
    private static final int UNDO_MAX = 400;

    public static final class Row {
        public final String text;
        public byte[] ink;
        public int[] overlay;
        public int[] xs;
        public int xsStamp;
        public Object spots;
        public byte[] spotsInk;
        public Object baked;
        public int bakedStamp;
        public boolean folded;
        int startState = -1, endState;

        Row(String text) { this.text = text; }
    }

    public static final class Caret {
        public int line, col, anchorLine, anchorCol;
        public int goalX = -1;

        public Caret(int line, int col) {
            this.line = line;
            this.col = col;
            this.anchorLine = line;
            this.anchorCol = col;
        }

        Caret copy() {
            Caret c = new Caret(line, col);
            c.anchorLine = anchorLine;
            c.anchorCol = anchorCol;
            return c;
        }

        public boolean selecting() { return line != anchorLine || col != anchorCol; }

        public int startLine() { return before() ? line : anchorLine; }

        public int startCol() { return before() ? col : anchorCol; }

        public int endLine() { return before() ? anchorLine : line; }

        public int endCol() { return before() ? anchorCol : col; }

        private boolean before() { return line < anchorLine || line == anchorLine && col < anchorCol; }

        public void place(int l, int c) {
            line = l;
            col = c;
            anchorLine = l;
            anchorCol = c;
            goalX = -1;
        }

        public void drag(int l, int c) {
            line = l;
            col = c;
            goalX = -1;
        }
    }

    private record Edit(int line, int col, String removed, String inserted) {}

    private static final class Step {
        final List<Edit> edits = new ArrayList<>();
        final List<Caret> before;
        List<Caret> after;
        final int kind;
        long at;

        Step(List<Caret> before, int kind) {
            this.before = before;
            this.kind = kind;
        }
    }

    private final ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<Caret> carets = new ArrayList<>();
    private final ArrayList<Step> undo = new ArrayList<>();
    private final ArrayList<Step> redo = new ArrayList<>();

    private Step open;
    private int depth;
    private boolean chain;
    private int version, savedVersion;
    private int textVersion = -1;
    private String text;
    private int startsVersion = -1;
    private int[] starts = new int[0];
    private int cleanUntil;
    private Syntax syntax;
    private Map<String, Byte> lexicon = Map.of();
    private int firstChanged = Integer.MAX_VALUE;
    private int[] visible;
    private int visibleVersion = -1;
    private int foldStamp;

    public Doc(String content) {
        set(content);
        carets.add(new Caret(0, 0));
    }

    private void set(String content) {
        rows.clear();
        String clean = content.replace("\r\n", "\n").replace('\r', '\n');
        int from = 0;
        while (true) {
            int nl = clean.indexOf('\n', from);
            if (nl < 0) {
                rows.add(new Row(clean.substring(from)));
                break;
            }
            rows.add(new Row(clean.substring(from, nl)));
            from = nl + 1;
        }
        cleanUntil = 0;
    }

    public void language(Syntax s, Map<String, Byte> words) {
        syntax = s;
        lexicon = words;
        for (Row r : rows) {
            r.ink = null;
            r.baked = null;
            r.startState = -1;
        }
        cleanUntil = 0;
    }

    public Syntax syntax() { return syntax; }

    public int version() { return version; }

    public boolean dirty() { return version != savedVersion; }

    public void saved() { savedVersion = version; }

    public int lines() { return rows.size(); }

    public String line(int i) { return rows.get(i).text; }

    public Row row(int i) { return rows.get(i); }

    public int length(int i) { return rows.get(i).text.length(); }

    public List<Caret> carets() { return carets; }

    public Caret primary() { return carets.get(carets.size() - 1); }

    public int takeChanged() {
        int f = firstChanged;
        firstChanged = Integer.MAX_VALUE;
        return f;
    }

    public String text() {
        if (textVersion == version && text != null) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(rows.get(i).text);
        }
        text = sb.toString();
        textVersion = version;
        return text;
    }

    private int[] starts() {
        if (startsVersion == version && starts.length == rows.size()) return starts;
        starts = new int[rows.size()];
        int at = 0;
        for (int i = 0; i < rows.size(); i++) {
            starts[i] = at;
            at += rows.get(i).text.length() + 1;
        }
        startsVersion = version;
        return starts;
    }

    public int offset(int line, int col) {
        int l = clampLine(line);
        return starts()[l] + Math.min(col, length(l));
    }

    public int lineAt(int offset) {
        int[] s = starts();
        int lo = 0, hi = s.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (s[mid] <= offset) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    public int colAt(int offset) {
        int l = lineAt(offset);
        return Math.max(0, Math.min(length(l), offset - starts()[l]));
    }

    public int clampLine(int line) { return Math.max(0, Math.min(rows.size() - 1, line)); }

    public int clampCol(int line, int col) { return Math.max(0, Math.min(length(clampLine(line)), col)); }

    public byte[] ink(int line) {
        if (syntax == null) {
            Row r = rows.get(line);
            if (r.ink == null) r.ink = new byte[r.text.length()];
            return r.ink;
        }
        int from = Math.min(cleanUntil, line);
        for (int i = from; i <= line; i++) {
            Row r = rows.get(i);
            int state = i == 0 ? Ink.NORMAL : rows.get(i - 1).endState;
            if (r.ink != null && r.startState == state) continue;
            r.ink = new byte[r.text.length()];
            r.startState = state;
            r.endState = Ink.scan(syntax, lexicon, r.text, state, r.ink);
            r.baked = null;
        }
        cleanUntil = Math.max(cleanUntil, line + 1);
        return rows.get(line).ink;
    }

    public void paintAll() { if (!rows.isEmpty()) ink(rows.size() - 1); }

    public static int compare(int l1, int c1, int l2, int c2) {
        return l1 != l2 ? Integer.compare(l1, l2) : Integer.compare(c1, c2);
    }

    public String slice(int l0, int c0, int l1, int c1) {
        if (l0 == l1) return rows.get(l0).text.substring(c0, c1);
        StringBuilder sb = new StringBuilder(rows.get(l0).text.substring(c0));
        for (int i = l0 + 1; i < l1; i++) sb.append('\n').append(rows.get(i).text);
        sb.append('\n').append(rows.get(l1).text, 0, c1);
        return sb.toString();
    }

    public String selected(Caret c) { return slice(c.startLine(), c.startCol(), c.endLine(), c.endCol()); }

    public String selectedAll() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Caret c : sorted()) {
            if (!c.selecting()) continue;
            if (!first) sb.append('\n');
            first = false;
            sb.append(selected(c));
        }
        return sb.toString();
    }

    public boolean anySelection() {
        for (Caret c : carets) if (c.selecting()) return true;
        return false;
    }

    public List<Caret> sorted() {
        List<Caret> out = new ArrayList<>(carets);
        out.sort(Comparator.<Caret>comparingInt(Caret::startLine).thenComparingInt(Caret::startCol));
        return out;
    }

    public void single(int line, int col) {
        Caret c = primary();
        carets.clear();
        carets.add(c);
        c.place(clampLine(line), clampCol(line, col));
        broke();
    }

    public void singleKeep() {
        Caret c = primary();
        carets.clear();
        carets.add(c);
    }

    public Caret addCaret(int line, int col) {
        Caret c = new Caret(clampLine(line), clampCol(line, col));
        carets.add(c);
        merge();
        broke();
        return primary();
    }

    public void select(int l0, int c0, int l1, int c1) {
        singleKeep();
        Caret c = primary();
        c.anchorLine = clampLine(l0);
        c.anchorCol = clampCol(l0, c0);
        c.line = clampLine(l1);
        c.col = clampCol(l1, c1);
        c.goalX = -1;
        broke();
    }

    public void broke() { chain = false; }

    public void merge() {
        if (carets.size() < 2) return;
        Caret main = primary();
        List<Caret> order = sorted();
        List<Caret> keep = new ArrayList<>();
        for (Caret c : order) {
            if (!keep.isEmpty()) {
                Caret p = keep.get(keep.size() - 1);
                int cmp = compare(c.startLine(), c.startCol(), p.endLine(), p.endCol());
                boolean touching = cmp < 0 || cmp == 0 && (!c.selecting() || !p.selecting());
                if (touching) {
                    if (compare(c.endLine(), c.endCol(), p.endLine(), p.endCol()) > 0) {
                        boolean forward = p.line == p.endLine() && p.col == p.endCol();
                        int sl = p.startLine(), sc = p.startCol();
                        p.anchorLine = sl;
                        p.anchorCol = sc;
                        p.line = c.endLine();
                        p.col = c.endCol();
                        if (!forward && !p.selecting()) p.goalX = -1;
                    }
                    if (c == main) {
                        keep.remove(keep.size() - 1);
                        keep.add(p);
                        main = p;
                    }
                    continue;
                }
            }
            keep.add(c);
        }
        keep.remove(main);
        keep.add(main);
        carets.clear();
        carets.addAll(keep);
    }

    public void begin(int kind) {
        if (depth++ > 0) return;
        Step last = undo.isEmpty() ? null : undo.get(undo.size() - 1);
        long now = System.currentTimeMillis();
        if (chain && kind != OTHER && last != null && last.kind == kind && now - last.at < MERGE_MS) {
            open = last;
            undo.remove(undo.size() - 1);
            return;
        }
        open = new Step(snapshot(), kind);
    }

    public void end() {
        if (--depth > 0) return;
        Step s = open;
        open = null;
        if (s == null || s.edits.isEmpty()) return;
        s.after = snapshot();
        s.at = System.currentTimeMillis();
        undo.add(s);
        if (undo.size() > UNDO_MAX) undo.remove(0);
        redo.clear();
        chain = s.kind != OTHER;
    }

    private List<Caret> snapshot() {
        List<Caret> out = new ArrayList<>(carets.size());
        for (Caret c : carets) out.add(c.copy());
        return out;
    }

    private void restore(List<Caret> saved) {
        carets.clear();
        for (Caret c : saved) carets.add(c.copy());
    }

    public boolean canUndo() { return !undo.isEmpty(); }

    public boolean canRedo() { return !redo.isEmpty(); }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        Step s = undo.remove(undo.size() - 1);
        for (int i = s.edits.size() - 1; i >= 0; i--) {
            Edit e = s.edits.get(i);
            int[] end = endOf(e.line, e.col, e.inserted);
            splice(e.line, e.col, end[0], end[1], e.removed);
        }
        restore(s.before);
        redo.add(s);
        chain = false;
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        Step s = redo.remove(redo.size() - 1);
        for (Edit e : s.edits) {
            int[] end = endOf(e.line, e.col, e.removed);
            splice(e.line, e.col, end[0], end[1], e.inserted);
        }
        restore(s.after);
        undo.add(s);
        chain = false;
        return true;
    }

    private static int[] endOf(int line, int col, String s) {
        int nl = s.lastIndexOf('\n');
        if (nl < 0) return new int[]{line, col + s.length()};
        int count = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') count++;
        return new int[]{line + count, s.length() - nl - 1};
    }

    public void replace(int l0, int c0, int l1, int c1, String inserted) {
        String clean = inserted.indexOf('\r') < 0 ? inserted : inserted.replace("\r\n", "\n").replace('\r', '\n');
        String removed = slice(l0, c0, l1, c1);
        if (removed.isEmpty() && clean.isEmpty()) return;
        boolean own = depth == 0;
        if (own) begin(OTHER);
        open.edits.add(new Edit(l0, c0, removed, clean));
        splice(l0, c0, l1, c1, clean);
        int[] end = endOf(l0, c0, clean);
        for (Caret c : carets) {
            int[] head = moved(c.line, c.col, l0, c0, l1, c1, end);
            int[] tail = moved(c.anchorLine, c.anchorCol, l0, c0, l1, c1, end);
            c.line = head[0];
            c.col = head[1];
            c.anchorLine = tail[0];
            c.anchorCol = tail[1];
        }
        if (own) end();
    }

    private static int[] moved(int line, int col, int l0, int c0, int l1, int c1, int[] end) {
        if (compare(line, col, l0, c0) <= 0) return new int[]{line, col};
        if (compare(line, col, l1, c1) < 0) return end;
        if (line == l1) return new int[]{end[0], end[1] + col - c1};
        return new int[]{line + end[0] - l1, col};
    }

    private void splice(int l0, int c0, int l1, int c1, String inserted) {
        String head = rows.get(l0).text.substring(0, c0);
        String tail = rows.get(l1).text.substring(c1);
        boolean folded = rows.get(l0).folded && l0 == l1 && inserted.indexOf('\n') < 0;
        List<Row> fresh = new ArrayList<>();
        int from = 0;
        StringBuilder first = new StringBuilder(head);
        while (true) {
            int nl = inserted.indexOf('\n', from);
            if (nl < 0) break;
            first.append(inserted, from, nl);
            fresh.add(new Row(first.toString()));
            first.setLength(0);
            from = nl + 1;
        }
        first.append(inserted, from, inserted.length()).append(tail);
        fresh.add(new Row(first.toString()));
        fresh.get(0).folded = folded;
        int same = Math.min(l1 - l0 + 1, fresh.size());
        for (int i = 0; i < same; i++) rows.set(l0 + i, fresh.get(i));
        if (l1 - l0 + 1 > fresh.size()) rows.subList(l0 + same, l1 + 1).clear();
        else if (fresh.size() > same) rows.addAll(l0 + same, fresh.subList(same, fresh.size()));
        cleanUntil = Math.min(cleanUntil, l0);
        firstChanged = Math.min(firstChanged, l0);
        version++;
    }

    public void put(Caret c, String s) {
        int line = c.startLine(), col = c.startCol();
        replace(line, col, c.endLine(), c.endCol(), s);
        int[] end = endOf(line, col, s.indexOf('\r') < 0 ? s : s.replace("\r\n", "\n").replace('\r', '\n'));
        c.place(end[0], end[1]);
    }

    public void insertAll(String s, int kind) {
        begin(kind);
        for (Caret c : reversed()) put(c, s);
        merge();
        end();
    }

    public List<Caret> reversed() {
        List<Caret> out = sorted();
        java.util.Collections.reverse(out);
        return out;
    }

    public void setAll(String content) {
        Caret c = primary();
        int line = c.line, col = c.col;
        begin(OTHER);
        int last = rows.size() - 1;
        replace(0, 0, last, length(last), content);
        single(line, col);
        end();
    }

    public int foldStamp() { return foldStamp; }

    public void foldChanged() {
        foldStamp++;
        visibleVersion = -1;
    }

    public int[] visible() {
        if (visible != null && visibleVersion == version) return visible;
        boolean any = false;
        for (Row r : rows) {
            if (r.folded) {
                any = true;
                break;
            }
        }
        int n = rows.size();
        if (!any) {
            if (visible == null || visible.length != n || visible.length > 0 && visible[n - 1] != n - 1) {
                visible = new int[n];
                for (int i = 0; i < n; i++) visible[i] = i;
            }
            visibleVersion = version;
            return visible;
        }
        int[] out = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            out[count++] = i;
            if (rows.get(i).folded) {
                int end = foldEnd(i);
                if (end > i) i = end;
                else rows.get(i).folded = false;
            }
        }
        visible = java.util.Arrays.copyOf(out, count);
        visibleVersion = version;
        return visible;
    }

    public boolean foldable(int line) {
        String t = rows.get(line).text;
        byte[] ink = ink(line);
        int depthHere = 0;
        for (int i = 0; i < t.length() && i < ink.length; i++) {
            if (ink[i] != Ink.PUNCT) continue;
            char c = t.charAt(i);
            if (c == '{' || c == '[' || c == '(') depthHere++;
            else if ((c == '}' || c == ']' || c == ')') && depthHere > 0) depthHere--;
        }
        return depthHere > 0;
    }

    public int foldEnd(int line) {
        int depthHere = 0;
        boolean opened = false;
        for (int l = line; l < rows.size(); l++) {
            String t = rows.get(l).text;
            byte[] ink = ink(l);
            for (int i = 0; i < t.length() && i < ink.length; i++) {
                if (ink[i] != Ink.PUNCT) continue;
                char c = t.charAt(i);
                if (c == '{' || c == '[' || c == '(') {
                    depthHere++;
                    opened = true;
                } else if (c == '}' || c == ']' || c == ')') {
                    depthHere--;
                    if (opened && depthHere <= 0) return l == line ? line : l - 1 > line ? l - 1 : line;
                }
            }
        }
        return line;
    }

    public void reveal(int line) {
        boolean changed = false;
        for (int l = 0; l < line && l < rows.size(); l++) {
            if (!rows.get(l).folded) continue;
            int end = foldEnd(l);
            if (end >= line) {
                rows.get(l).folded = false;
                changed = true;
            } else if (end > l) l = end;
        }
        if (changed) foldChanged();
    }

    public int[] matching(int line, int col) {
        for (int k = 0; k <= 1; k++) {
            int at = col - 1 + k;
            if (at < 0 || at >= length(line)) continue;
            byte[] ink = ink(line);
            if (at >= ink.length || ink[at] != Ink.PUNCT) continue;
            char c = rows.get(line).text.charAt(at);
            int open = "([{".indexOf(c), close = ")]}".indexOf(c);
            if (open >= 0) {
                int[] found = forward(line, at + 1, c, ")]}".charAt(open));
                if (found != null) return new int[]{line, at, found[0], found[1]};
            } else if (close >= 0) {
                int[] found = backward(line, at - 1, c, "([{".charAt(close));
                if (found != null) return new int[]{found[0], found[1], line, at};
            }
        }
        return null;
    }

    private int[] forward(int line, int col, char open, char close) {
        int depthHere = 0, limit = Math.min(rows.size(), line + 3000);
        for (int l = line; l < limit; l++) {
            String t = rows.get(l).text;
            byte[] ink = ink(l);
            for (int i = l == line ? col : 0; i < t.length() && i < ink.length; i++) {
                if (ink[i] != Ink.PUNCT) continue;
                char c = t.charAt(i);
                if (c == open) depthHere++;
                else if (c == close && depthHere-- == 0) return new int[]{l, i};
            }
        }
        return null;
    }

    private int[] backward(int line, int col, char close, char open) {
        int depthHere = 0, limit = Math.max(0, line - 3000);
        for (int l = line; l >= limit; l--) {
            String t = rows.get(l).text;
            byte[] ink = ink(l);
            for (int i = l == line ? col : t.length() - 1; i >= 0; i--) {
                if (i >= ink.length || ink[i] != Ink.PUNCT) continue;
                char c = t.charAt(i);
                if (c == close) depthHere++;
                else if (c == open && depthHere-- == 0) return new int[]{l, i};
            }
        }
        return null;
    }
}
