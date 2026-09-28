package com.xerocode.ui;

import com.xerocode.Values;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

public final class Tint {
    public static final int FUNCTION = 0x6FA8FF;
    public static final int PLACEHOLDER = 0x4FD1C5;
    public static final int BAD = 0xFF5C5C;
    private static final int[] DEPTH = {0xE5C07B, 0xC678DD, 0x56B6C2};
    private static final int TAG = 0xB08CFF, DIM = 0x707A8C, JSON = 0x9CDCFE;

    public static int[] markup(String s, String parsing) {
        int[] out = new int[s.length()];
        Arrays.fill(out, Theme.TEXT);
        if (parsing == null) return out;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int len = 0, ink = Theme.TEXT;
            if (McText.LEGACY.equals(parsing) && (c == '&' || c == '§') && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                String h6 = i + 8 <= s.length() && next == '#'
                        ? McText.normaliseHex(s.substring(i + 2, i + 8)) : null;
                if (h6 != null) {
                    len = 8;
                    ink = McText.hexRgb(h6);
                } else {
                    ChatFormatting f = ChatFormatting.getByCode(next);
                    if (f != null) {
                        len = 2;
                        ink = McText.rgb(f) != null ? McText.rgb(f) : TAG;
                    }
                }
            } else if (McText.MINI.equals(parsing) && c == '<') {
                int end = s.indexOf('>', i);
                if (end > i) {
                    len = end - i + 1;
                    String body = s.substring(i + 1, end);
                    boolean closing = body.startsWith("/");
                    String name = (closing ? body.substring(1) : body).toLowerCase();
                    ink = TAG;
                    if (name.startsWith("#")) {
                        String h6 = McText.normaliseHex(name);
                        if (h6 != null) ink = McText.hexRgb(h6);
                    } else {
                        for (McText.Colour col : McText.COLOURS)
                            if (col.name().equals(name)) { ink = col.rgb(); break; }
                    }
                    if (closing) ink = Draw.shade(ink, -0.35f);
                }
            } else if (McText.JSON.equals(parsing)) {
                if (c == '"') {
                    int end = i + 1;
                    while (end < s.length() && (s.charAt(end) != '"' || s.charAt(end - 1) == '\\')) end++;
                    len = Math.min(s.length(), end + 1) - i;
                    ink = JSON;
                } else if ("{}[],:".indexOf(c) >= 0) {
                    len = 1;
                    ink = DIM;
                }
            }
            if (len == 0) { i++; continue; }
            for (int k = i; k < i + len && k < out.length; k++) out[k] = ink;
            i += len;
        }
        return out;
    }

    private record Frame(int open, int ink, int soft) {}

    public static boolean[] placeholders(String s, int cursor, int[] ink) {
        int n = s.length();
        boolean[] claimed = new boolean[n];
        boolean[] mark = new boolean[n];
        Deque<Frame> open = new ArrayDeque<>();
        List<int[]> pairs = new ArrayList<>();
        int plain = 0;
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '%') {
                int end = i + 1;
                while (end < n && word(s.charAt(end))) end++;
                String name = s.substring(i + 1, end);
                if (!name.isEmpty() && end < n && s.charAt(end) == '(') {
                    int color = callInk(name);
                    for (int k = i; k <= end; k++) { ink[k] = color; claimed[k] = true; }
                    open.push(new Frame(end, color, variable(name) ? soft(color) : -1));
                    i = end + 1;
                    continue;
                }
                if (!name.isEmpty() && end < n && s.charAt(end) == '%') {
                    for (int k = i; k <= end; k++) { ink[k] = PLACEHOLDER; claimed[k] = true; }
                    i = end + 1;
                    continue;
                }
                i++;
                continue;
            }
            if (c == '(') {
                int color = DEPTH[plain++ % DEPTH.length];
                ink[i] = color;
                claimed[i] = true;
                open.push(new Frame(i, color, -1));
            } else if (c == ')') {
                claimed[i] = true;
                if (open.isEmpty()) {
                    ink[i] = BAD;
                } else {
                    Frame f = open.pop();
                    ink[i] = f.ink();
                    if (f.soft() >= 0)
                        for (int k = f.open() + 1; k < i; k++) if (!claimed[k]) { ink[k] = f.soft(); claimed[k] = true; }
                    pairs.add(new int[]{f.open(), i});
                    if (f.soft() < 0 && plain > 0 && s.charAt(f.open()) == '(' && !callOpen(s, f.open())) plain--;
                }
            }
            i++;
        }
        int[] best = null;
        for (int[] p : pairs)
            if (p[0] < cursor && cursor <= p[1] + 1 && (best == null || p[0] > best[0])) best = p;
        if (best != null) {
            mark[best[0]] = true;
            mark[best[1]] = true;
        }
        return mark;
    }

    private static boolean callOpen(String s, int paren) {
        int k = paren - 1;
        while (k >= 0 && word(s.charAt(k))) k--;
        return k >= 0 && k < paren - 1 && s.charAt(k) == '%';
    }

    private static boolean word(char c) { return c == '_' || Character.isLetterOrDigit(c); }

    private static boolean variable(String name) {
        return family(name, "var") || family(name, "length") || family(name, "index")
                || family(name, "entry");
    }

    private static boolean family(String name, String base) {
        return name.equals(base) || name.startsWith(base + "_");
    }

    private static int callInk(String name) {
        if (!variable(name)) return FUNCTION;
        String scope = name.endsWith("_local") ? "local" : name.endsWith("_save") ? "save"
                : name.endsWith("_line") ? "line" : "game";
        Values.Scope sc = Values.scope(scope);
        return sc == null ? FUNCTION : sc.color();
    }

    private static int soft(int color) { return Draw.mix(color, 0xFFFFFF, 0.35f); }

    private static final class Memo {
        String text, parsing;
        int cursor = -1;
        int[] ink = new int[0];
        boolean[] mark = new boolean[0];
    }

    public static EditBox.TextFormatter of(EditBox field, Supplier<String> parsing) {
        Memo memo = new Memo();
        return (visible, offset) -> {
            String all = field.getValue();
            int cursor = field.isFocused() ? field.getCursorPosition() : -1;
            String p = parsing == null ? null : parsing.get();
            if (!all.equals(memo.text) || cursor != memo.cursor || !Objects.equals(p, memo.parsing)) {
                memo.text = all;
                memo.cursor = cursor;
                memo.parsing = p;
                memo.ink = markup(all, p);
                memo.mark = placeholders(all, cursor, memo.ink);
            }
            return paint(visible, offset, memo.ink, memo.mark);
        };
    }

    public static FormattedCharSequence paint(String visible, int offset, int[] ink, boolean[] mark) {
        List<FormattedCharSequence> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int run = Integer.MIN_VALUE;
        boolean under = false;
        for (int i = 0; i < visible.length(); i++) {
            int idx = offset + i;
            int color = idx >= 0 && idx < ink.length ? ink[idx] : Theme.TEXT;
            boolean m = idx >= 0 && idx < mark.length && mark[idx];
            if (m) color = Draw.mix(color, 0xFFFFFF, 0.45f);
            if ((color != run || m != under) && !buf.isEmpty()) flush(out, buf, run, under);
            run = color;
            under = m;
            buf.append(visible.charAt(i));
        }
        flush(out, buf, run, under);
        return out.isEmpty() ? FormattedCharSequence.EMPTY : FormattedCharSequence.composite(out);
    }

    private static void flush(List<FormattedCharSequence> out, StringBuilder buf, int rgb, boolean under) {
        if (buf.isEmpty()) return;
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF));
        if (under) style = style.withUnderlined(true);
        out.add(FormattedCharSequence.forward(buf.toString(), style));
        buf.setLength(0);
    }

    private Tint() {}
}
