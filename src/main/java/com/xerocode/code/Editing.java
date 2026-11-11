package com.xerocode.code;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import xero.dialect.Ink;
import xero.dialect.Syntax;

public final class Editing {
    private static final String OPEN = "([{", CLOSE = ")]}";
    public static boolean pairs = true;
    private static final String STEP = " ".repeat(Doc.TAB);

    private Editing() {}

    private static boolean word(char c) { return Ink.wordPart(c); }

    private static char at(String s, int i) { return i >= 0 && i < s.length() ? s.charAt(i) : 0; }

    private static byte inkAt(Doc d, int line, int col) {
        byte[] ink = d.ink(line);
        return col >= 0 && col < ink.length ? ink[col] : Ink.PLAIN;
    }

    private static boolean literal(Doc d, int line, int col) {
        byte k = inkAt(d, line, col);
        return k == Ink.STRING || k == Ink.COMMENT || k == Ink.ESCAPE;
    }

    public static void type(Doc d, String typed) {
        if (typed.length() != 1) {
            d.insertAll(typed, Doc.TYPING);
            return;
        }
        char ch = typed.charAt(0);
        Syntax s = d.syntax();
        String quotes = s == null ? "\"" : s.quotes();
        boolean quote = quotes.indexOf(ch) >= 0;
        int open = OPEN.indexOf(ch);
        d.begin(Doc.TYPING);
        try {
            if (d.anySelection() && (open >= 0 || quote)) {
                wrap(d, ch, open >= 0 ? CLOSE.charAt(open) : ch);
                return;
            }
            boolean overtype = pairs && (CLOSE.indexOf(ch) >= 0 || quote) && allNext(d, ch);
            if (overtype) {
                for (Doc.Caret c : d.carets()) c.place(c.line, c.col + 1);
                return;
            }
            if (pairs && open >= 0 && canPair(d)) {
                insertPair(d, typed, String.valueOf(CLOSE.charAt(open)));
                return;
            }
            if (pairs && quote && canQuote(d)) {
                insertPair(d, typed, typed);
                return;
            }
            d.insertAll(typed, Doc.TYPING);
            if (ch == '}' || ch == ')' || ch == ']') outdent(d);
        } finally {
            d.end();
        }
    }

    private static boolean allNext(Doc d, char ch) {
        for (Doc.Caret c : d.carets()) if (c.selecting() || at(d.line(c.line), c.col) != ch) return false;
        return true;
    }

    private static boolean canPair(Doc d) {
        for (Doc.Caret c : d.carets()) {
            char next = at(d.line(c.line), c.col);
            if (literal(d, c.line, c.col - 1) && literal(d, c.line, c.col)) return false;
            if (next != 0 && next != ' ' && CLOSE.indexOf(next) < 0 && next != ',' && next != ';') return false;
        }
        return true;
    }

    private static boolean canQuote(Doc d) {
        for (Doc.Caret c : d.carets()) {
            String t = d.line(c.line);
            char prev = at(t, c.col - 1), next = at(t, c.col);
            if (word(prev) && !(c.col == 1 || !word(at(t, c.col - 2)))) return false;
            if (next != 0 && next != ' ' && CLOSE.indexOf(next) < 0 && next != ',' && next != ';') return false;
            if (literal(d, c.line, c.col - 1) && inkAt(d, c.line, c.col - 1) != Ink.ESCAPE) return false;
        }
        return true;
    }

    private static void insertPair(Doc d, String left, String right) {
        for (Doc.Caret c : d.reversed()) {
            d.put(c, left + right);
            c.place(c.line, c.col - right.length());
        }
    }

    private static void wrap(Doc d, char left, char right) {
        for (Doc.Caret c : d.reversed()) {
            int al = c.startLine(), ac = c.startCol(), bl = c.endLine(), bc = c.endCol();
            d.replace(bl, bc, bl, bc, String.valueOf(right));
            d.replace(al, ac, al, ac, String.valueOf(left));
            c.anchorLine = al;
            c.anchorCol = ac + 1;
            c.line = bl;
            c.col = bc + (al == bl ? 1 : 0);
        }
    }

    private static void outdent(Doc d) {
        for (Doc.Caret c : d.reversed()) {
            String t = d.line(c.line);
            if (!t.substring(0, c.col - 1).isBlank() || c.selecting()) continue;
            int[] pair = d.matching(c.line, c.col);
            if (pair == null || pair[0] == c.line) continue;
            String want = indentOf(d.line(pair[0]));
            int have = c.col - 1;
            if (want.length() == have) continue;
            d.replace(c.line, 0, c.line, have, want);
        }
    }

    public static String indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    public static void enter(Doc d) {
        d.begin(Doc.OTHER);
        for (Doc.Caret c : d.reversed()) {
            int sl = c.startLine(), sc = c.startCol();
            d.replace(sl, sc, c.endLine(), c.endCol(), "");
            String t = d.line(sl);
            String lead = indentOf(t);
            if (sc < lead.length()) lead = lead.substring(0, sc);
            char prev = lastCode(t, sc), next = at(t, sc);
            boolean opens = prev != 0 && OPEN.indexOf(prev) >= 0;
            boolean between = opens && next != 0 && CLOSE.indexOf(next) >= 0;
            String inner = opens ? lead + STEP : lead;
            String piece = "\n" + inner + (between ? "\n" + lead : "");
            d.replace(sl, sc, sl, sc, piece);
            c.place(sl + 1, inner.length());
        }
        d.merge();
        d.end();
    }

    private static char lastCode(String t, int col) {
        for (int i = Math.min(col, t.length()) - 1; i >= 0; i--) if (t.charAt(i) != ' ') return t.charAt(i);
        return 0;
    }

    public static void newLineBelow(Doc d) {
        d.begin(Doc.OTHER);
        for (Doc.Caret c : d.reversed()) c.place(c.line, d.length(c.line));
        d.end();
        enter(d);
    }

    public static void newLineAbove(Doc d) {
        d.begin(Doc.OTHER);
        for (Doc.Caret c : d.reversed()) {
            int line = c.line;
            String lead = indentOf(d.line(line));
            d.replace(line, 0, line, 0, lead + "\n");
            c.place(line, lead.length());
        }
        d.end();
    }

    public static void backspace(Doc d, boolean wholeWord) {
        d.begin(Doc.DELETING);
        for (Doc.Caret c : d.reversed()) {
            if (c.selecting()) {
                d.replace(c.startLine(), c.startCol(), c.endLine(), c.endCol(), "");
                continue;
            }
            if (c.col == 0) {
                if (c.line > 0) d.replace(c.line - 1, d.length(c.line - 1), c.line, 0, "");
                continue;
            }
            String t = d.line(c.line);
            if (wholeWord) {
                d.replace(c.line, wordLeft(t, c.col), c.line, c.col, "");
                continue;
            }
            char prev = t.charAt(c.col - 1), next = at(t, c.col);
            int open = OPEN.indexOf(prev);
            boolean pair = pairs && (open >= 0 && next == CLOSE.charAt(open)
                    || d.syntax() != null && d.syntax().quotes().indexOf(prev) >= 0 && next == prev);
            if (pair) {
                d.replace(c.line, c.col - 1, c.line, c.col + 1, "");
                continue;
            }
            if (t.substring(0, c.col).isBlank()) {
                int to = (c.col - 1) / Doc.TAB * Doc.TAB;
                d.replace(c.line, to, c.line, c.col, "");
                continue;
            }
            int from = Character.isLowSurrogate(prev) && c.col > 1 ? c.col - 2 : c.col - 1;
            d.replace(c.line, from, c.line, c.col, "");
        }
        settle(d);
        d.end();
    }

    public static void delete(Doc d, boolean wholeWord) {
        d.begin(Doc.DELETING);
        for (Doc.Caret c : d.reversed()) {
            if (c.selecting()) {
                d.replace(c.startLine(), c.startCol(), c.endLine(), c.endCol(), "");
                continue;
            }
            String t = d.line(c.line);
            if (c.col >= t.length()) {
                if (c.line + 1 < d.lines()) d.replace(c.line, c.col, c.line + 1, 0, "");
                continue;
            }
            int to = wholeWord ? wordRight(t, c.col)
                    : Character.isHighSurrogate(t.charAt(c.col)) && c.col + 1 < t.length() ? c.col + 2 : c.col + 1;
            d.replace(c.line, c.col, c.line, to, "");
        }
        settle(d);
        d.end();
    }

    private static void settle(Doc d) {
        for (Doc.Caret c : d.carets()) {
            c.anchorLine = c.line;
            c.anchorCol = c.col;
            c.goalX = -1;
        }
        d.merge();
    }

    public static int wordLeft(String t, int col) {
        int i = col;
        while (i > 0 && t.charAt(i - 1) == ' ') i--;
        if (i == 0) return 0;
        if (word(t.charAt(i - 1))) {
            while (i > 0 && word(t.charAt(i - 1))) i--;
        } else {
            while (i > 0 && !word(t.charAt(i - 1)) && t.charAt(i - 1) != ' ') i--;
        }
        return i;
    }

    public static int wordRight(String t, int col) {
        int i = col, n = t.length();
        if (i < n && word(t.charAt(i))) {
            while (i < n && word(t.charAt(i))) i++;
        } else {
            while (i < n && !word(t.charAt(i)) && t.charAt(i) != ' ') i++;
        }
        while (i < n && t.charAt(i) == ' ') i++;
        return i;
    }

    public static void tab(Doc d) {
        boolean block = false;
        for (Doc.Caret c : d.carets()) if (c.startLine() != c.endLine()) block = true;
        if (block) {
            shift(d, 1);
            return;
        }
        d.begin(Doc.TYPING);
        for (Doc.Caret c : d.reversed()) {
            d.put(c, " ".repeat(Doc.TAB - c.startCol() % Doc.TAB));
        }
        d.merge();
        d.end();
    }

    public static void shift(Doc d, int direction) {
        d.begin(Doc.OTHER);
        for (int line : touched(d)) {
            String t = d.line(line);
            if (direction > 0) {
                if (!t.isBlank()) d.replace(line, 0, line, 0, STEP);
                continue;
            }
            int n = 0;
            while (n < Doc.TAB && n < t.length() && t.charAt(n) == ' ') n++;
            if (n == 0 && t.startsWith("\t")) n = 1;
            if (n > 0) d.replace(line, 0, line, n, "");
        }
        d.end();
    }

    private static List<Integer> touched(Doc d) {
        TreeSet<Integer> lines = new TreeSet<>();
        for (Doc.Caret c : d.carets()) {
            int last = c.endLine();
            if (c.selecting() && c.endCol() == 0 && last > c.startLine()) last--;
            for (int l = c.startLine(); l <= last; l++) lines.add(l);
        }
        List<Integer> out = new ArrayList<>(lines.descendingSet());
        return out;
    }

    public static void toggleComment(Doc d) {
        Syntax s = d.syntax();
        if (s == null || s.lineComment() == null) return;
        String mark = s.lineComment();
        List<Integer> lines = touched(d);
        boolean all = true;
        int low = Integer.MAX_VALUE;
        for (int l : lines) {
            String t = d.line(l);
            if (t.isBlank()) continue;
            int lead = indentOf(t).length();
            low = Math.min(low, lead);
            if (!t.startsWith(mark, lead)) all = false;
        }
        if (low == Integer.MAX_VALUE) return;
        d.begin(Doc.OTHER);
        for (int l : lines) {
            String t = d.line(l);
            if (t.isBlank()) continue;
            int lead = indentOf(t).length();
            if (all) {
                int to = lead + mark.length();
                if (at(t, to) == ' ') to++;
                d.replace(l, lead, l, to, "");
            } else d.replace(l, low, l, low, mark + " ");
        }
        d.end();
    }

    public static void moveRows(Doc d, int direction) {
        List<Integer> lines = touched(d);
        if (lines.isEmpty()) return;
        int first = lines.get(lines.size() - 1), last = lines.get(0);
        if (direction < 0 ? first == 0 : last + 1 >= d.lines()) return;
        d.begin(Doc.OTHER);
        List<int[]> keep = new ArrayList<>();
        for (Doc.Caret c : d.carets()) keep.add(new int[]{c.line, c.col, c.anchorLine, c.anchorCol});
        if (direction < 0) {
            String above = d.line(first - 1);
            d.replace(first - 1, 0, first, 0, "");
            int end = last - 1;
            d.replace(end, d.length(end), end, d.length(end), "\n" + above);
        } else {
            String below = d.line(last + 1);
            d.replace(last, d.length(last), last + 1, d.length(last + 1), "");
            d.replace(first, 0, first, 0, below + "\n");
        }
        int i = 0;
        for (Doc.Caret c : d.carets()) {
            int[] k = keep.get(i++);
            c.line = k[0] + direction;
            c.col = k[1];
            c.anchorLine = k[2] + direction;
            c.anchorCol = k[3];
        }
        d.end();
    }

    public static void duplicateRows(Doc d, boolean up) {
        List<Integer> lines = touched(d);
        if (lines.isEmpty()) return;
        int first = lines.get(lines.size() - 1), last = lines.get(0), count = last - first + 1;
        d.begin(Doc.OTHER);
        String copy = d.slice(first, 0, last, d.length(last));
        List<int[]> keep = new ArrayList<>();
        for (Doc.Caret c : d.carets()) keep.add(new int[]{c.line, c.col, c.anchorLine, c.anchorCol});
        d.replace(last, d.length(last), last, d.length(last), "\n" + copy);
        int i = 0, shift = up ? 0 : count;
        for (Doc.Caret c : d.carets()) {
            int[] k = keep.get(i++);
            c.line = k[0] + shift;
            c.col = k[1];
            c.anchorLine = k[2] + shift;
            c.anchorCol = k[3];
        }
        d.end();
    }

    public static void deleteRows(Doc d) {
        List<Integer> lines = touched(d);
        d.begin(Doc.OTHER);
        for (int l : lines) {
            if (d.lines() == 1) d.replace(0, 0, 0, d.length(0), "");
            else if (l + 1 < d.lines()) d.replace(l, 0, l + 1, 0, "");
            else d.replace(l - 1, d.length(l - 1), l, d.length(l), "");
        }
        settle(d);
        d.end();
    }

    public static void selectWordAt(Doc d, int line, int col) {
        String t = d.line(line);
        int a = Math.min(col, t.length()), b = a;
        if (a > 0 && (a == t.length() || !word(t.charAt(a))) && word(t.charAt(a - 1))) a--;
        if (a < t.length() && word(t.charAt(a))) {
            while (a > 0 && word(t.charAt(a - 1))) a--;
            b = a;
            while (b < t.length() && word(t.charAt(b))) b++;
        } else if (a < t.length()) {
            b = a + 1;
        }
        d.select(line, a, line, b);
    }

    public static boolean selectNext(Doc d) {
        Doc.Caret main = d.primary();
        if (!main.selecting()) {
            selectWordAt(d, main.line, main.col);
            return true;
        }
        String wanted = d.selected(main);
        if (wanted.isEmpty() || wanted.indexOf('\n') >= 0) return false;
        int line = main.endLine(), col = main.endCol(), n = d.lines();
        for (int k = 0; k <= n; k++) {
            int l = (line + k) % n;
            int from = k == 0 ? col : 0;
            int found = d.line(l).indexOf(wanted, from);
            if (found < 0) continue;
            if (taken(d, l, found)) continue;
            Doc.Caret c = d.addCaret(l, found + wanted.length());
            c.anchorLine = l;
            c.anchorCol = found;
            return true;
        }
        return false;
    }

    private static boolean taken(Doc d, int line, int col) {
        for (Doc.Caret c : d.carets()) if (c.startLine() == line && c.startCol() == col) return true;
        return false;
    }

    public static void selectAllMatches(Doc d) {
        while (selectNext(d)) {
            if (d.carets().size() > 2000) return;
            Doc.Caret main = d.primary();
            String w = d.selected(main);
            int count = 0;
            for (int l = 0; l < d.lines(); l++) {
                int at = -1;
                while ((at = d.line(l).indexOf(w, at + 1)) >= 0) count++;
            }
            if (d.carets().size() >= count) return;
        }
    }

    public static void addCaretVertical(Doc d, int direction) {
        Doc.Caret main = d.primary();
        int line = main.line + direction;
        if (line < 0 || line >= d.lines()) return;
        d.addCaret(line, main.col);
    }

    public static void paste(Doc d, String text) {
        if (text == null || text.isEmpty()) return;
        String clean = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = clean.split("\n", -1);
        int n = d.carets().size();
        if (n > 1 && parts.length == n) {
            d.begin(Doc.OTHER);
            List<Doc.Caret> order = d.sorted();
            for (int i = n - 1; i >= 0; i--) d.put(order.get(i), parts[i]);
            d.merge();
            d.end();
            return;
        }
        d.begin(Doc.OTHER);
        for (Doc.Caret c : d.reversed()) {
            String before = d.line(c.startLine()).substring(0, c.startCol());
            d.put(c, parts.length > 1 && before.isBlank() ? reindent(parts, before) : clean);
        }
        d.merge();
        d.end();
    }

    private static String reindent(String[] parts, String lead) {
        int low = Integer.MAX_VALUE;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isBlank()) continue;
            low = Math.min(low, indentOf(parts[i]).length());
        }
        if (low == Integer.MAX_VALUE) low = 0;
        StringBuilder sb = new StringBuilder(parts[0].stripLeading());
        for (int i = 1; i < parts.length; i++) {
            sb.append('\n');
            if (parts[i].isBlank()) continue;
            sb.append(lead).append(parts[i].substring(Math.min(low, parts[i].length())));
        }
        return sb.toString();
    }

    public static String cut(Doc d) {
        String copied = copy(d);
        if (d.anySelection()) {
            d.insertAll("", Doc.OTHER);
            return copied;
        }
        deleteRows(d);
        return copied;
    }

    public static String copy(Doc d) {
        if (d.anySelection()) return d.selectedAll();
        StringBuilder sb = new StringBuilder();
        for (int l : new TreeSet<>(touched(d))) sb.append(d.line(l)).append('\n');
        return sb.toString();
    }

    public static void toggleCase(Doc d, boolean upper) {
        d.begin(Doc.OTHER);
        for (Doc.Caret c : d.reversed()) {
            if (!c.selecting()) continue;
            String s = d.selected(c);
            int al = c.anchorLine, ac = c.anchorCol, l = c.line, k = c.col;
            d.replace(c.startLine(), c.startCol(), c.endLine(), c.endCol(),
                    upper ? s.toUpperCase(java.util.Locale.ROOT) : s.toLowerCase(java.util.Locale.ROOT));
            c.anchorLine = al;
            c.anchorCol = ac;
            c.line = l;
            c.col = k;
        }
        d.end();
    }

    public static void trimTrailing(Doc d) {
        d.begin(Doc.OTHER);
        for (int l = d.lines() - 1; l >= 0; l--) {
            String t = d.line(l);
            int end = t.length();
            while (end > 0 && t.charAt(end - 1) == ' ') end--;
            if (end < t.length()) d.replace(l, end, l, t.length(), "");
        }
        d.end();
    }

    private static final int CALLS_MAX = 6, LOOK_BACK = 20000;

    public static List<int[]> callsAround(Doc d, int line, int col) {
        String text = d.text();
        boolean[] skip = skipped(d, text.length());
        List<int[]> out = new ArrayList<>();
        int at = d.offset(line, col);
        int from = at, to = at;
        while (from > 0 && head(text.charAt(from - 1)) && !skip[from - 1]) from--;
        while (to < text.length() && head(text.charAt(to)) && !skip[to]) to++;
        int open = to > from ? openAfter(text, skip, to) : -1;
        if (open >= 0) call(out, text, skip, headStart(text, skip, open), open);
        int depth = 0;
        for (int i = Math.min(at, text.length()) - 1; i >= Math.max(0, at - LOOK_BACK) && out.size() < CALLS_MAX; i--) {
            if (skip[i]) continue;
            char c = text.charAt(i);
            if (CLOSE.indexOf(c) >= 0) depth++;
            else if (OPEN.indexOf(c) >= 0) {
                if (depth > 0) depth--;
                else if (c == '(' && i != open) call(out, text, skip, headStart(text, skip, i), i);
            }
        }
        return out;
    }

    private static boolean[] skipped(Doc d, int length) {
        boolean[] out = new boolean[length];
        int at = 0;
        for (int l = 0; l < d.lines(); l++) {
            byte[] ink = d.ink(l);
            int n = d.line(l).length();
            for (int c = 0; c < n && at + c < length; c++) {
                byte k = c < ink.length ? ink[c] : Ink.PLAIN;
                out[at + c] = k == Ink.STRING || k == Ink.ESCAPE || k == Ink.PLACEHOLDER || k == Ink.COMMENT;
            }
            at += n + 1;
        }
        return out;
    }

    private static boolean head(char c) { return word(c) || c == ':' || c == '.'; }

    private static int openAfter(String text, boolean[] skip, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        if (i < text.length() && text.charAt(i) == '<') {
            int angle = 0;
            for (; i < text.length(); i++) {
                if (skip[i]) continue;
                char c = text.charAt(i);
                if (c == '<') angle++;
                else if (c == '>' && --angle == 0) break;
                else if (c == '\n') return -1;
            }
            i++;
            while (i < text.length() && text.charAt(i) == ' ') i++;
        }
        return i < text.length() && text.charAt(i) == '(' && !skip[i] ? i : -1;
    }

    private static int headStart(String text, boolean[] skip, int open) {
        int j = open;
        while (j > 0 && text.charAt(j - 1) == ' ') j--;
        if (j > 0 && text.charAt(j - 1) == '>') {
            int angle = 0;
            for (j--; j >= 0; j--) {
                if (skip[j]) continue;
                char c = text.charAt(j);
                if (c == '>') angle++;
                else if (c == '<' && --angle == 0) break;
                else if (c == '\n') return -1;
            }
            if (j < 0) return -1;
            while (j > 0 && text.charAt(j - 1) == ' ') j--;
        }
        int end = j;
        while (j > 0 && head(text.charAt(j - 1)) && !skip[j - 1]) j--;
        if (j == end) return -1;
        int k = j;
        while (k > 0 && text.charAt(k - 1) == ' ') k--;
        int target = k;
        while (target > 0 && word(text.charAt(target - 1))) target--;
        return target < k && target > 0 && text.charAt(target - 1) == '@' ? target - 1 : j;
    }

    private static void call(List<int[]> out, String text, boolean[] skip, int start, int open) {
        if (start < 0) return;
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (skip[i]) continue;
            char c = text.charAt(i);
            if (OPEN.indexOf(c) >= 0) depth++;
            else if (CLOSE.indexOf(c) >= 0 && --depth == 0) {
                out.add(new int[]{start, i + 1});
                return;
            }
        }
    }
}
