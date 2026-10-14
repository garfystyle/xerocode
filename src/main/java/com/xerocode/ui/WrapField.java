package com.xerocode.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.xerocode.Env;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class WrapField {
    static final int LINE = 11;
    private static final int PAD_X = 7, PAD_Y = 5, BLINK = 530, BAR_W = 3;

    static final class Box extends EditBox {
        private int anchor;

        Box(Font tr, String text, String hint) {
            super(tr, 0, 0, 40, LINE, Component.literal(hint));
            setBordered(false);
            setTextShadow(false);
            setTextColor(Draw.opaque(Theme.TEXT));
            setMaxLength(Ui.TEXT_MAX);
            setValue(text);
            moveCursorToStart(false);
        }

        @Override
        public void setHighlightPos(int pos) {
            super.setHighlightPos(pos);
            anchor = Math.max(0, Math.min(getValue().length(), pos));
        }

        int anchor() { return Math.min(anchor, getValue().length()); }

        void select(int from, int to) {
            moveCursorTo(from, false);
            moveCursorTo(to, true);
        }
    }

    private final Font tr;
    private final Box box;
    private final Supplier<String> parsing;
    private final Map<Integer, Integer> widths = new HashMap<>();

    private int x, y, w, h, scroll, goalX = -1;
    private int[] starts = {0};
    private String laidText;
    private int laidW = -1;
    private String inkText, inkParsing;
    private int inkCursor = -2;
    private int[] ink = new int[0];
    private boolean[] mark = new boolean[0];
    private String seenText;
    private int seenCursor = -1;
    private long movedAt;
    private boolean dragging;

    WrapField(Font tr, Box box, Supplier<String> parsing) {
        this.tr = tr;
        this.box = box;
        this.parsing = parsing;
    }

    void place(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        if (box.getWidth() != textW()) box.setWidth(Math.max(20, textW()));
        lay();
        clamp();
        anchor();
    }

    static int height(int lines) { return lines * LINE + PAD_Y * 2; }

    int lines() {
        lay();
        return starts.length;
    }

    private int textW() { return w - PAD_X * 2 - BAR_W - 1; }

    private int viewH() { return h - PAD_Y * 2; }

    private int maxScroll() { return Math.max(0, starts.length * LINE - viewH()); }

    private void clamp() { scroll = Math.max(0, Math.min(maxScroll(), scroll)); }

    private int adv(int cp) {
        Integer known = widths.get(cp);
        if (known != null) return known;
        int width = tr.width(new String(Character.toChars(cp)));
        widths.put(cp, width);
        return width;
    }

    private int span(String s, int from, int to) {
        int out = 0;
        for (int i = from; i < to; ) {
            int cp = s.codePointAt(i);
            out += adv(cp);
            i += Character.charCount(cp);
        }
        return out;
    }

    private void lay() {
        String s = box.getValue();
        int width = Math.max(20, textW());
        if (s.equals(laidText) && width == laidW) return;
        laidText = s;
        laidW = width;
        int[] out = new int[8];
        int n = 1, start = 0, at = 0, soft = -1, i = 0;
        while (i < s.length()) {
            if (s.startsWith(TextLines.BREAK, i)) {
                i += TextLines.BREAK.length();
                if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
                out[n++] = i;
                start = i;
                at = 0;
                soft = -1;
                continue;
            }
            int cp = s.codePointAt(i);
            int cw = adv(cp);
            if (at + cw > width && i > start) {
                int cut = soft > start ? soft : i;
                if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
                out[n++] = cut;
                start = cut;
                at = span(s, cut, i);
                soft = -1;
            }
            at += cw;
            i += Character.charCount(cp);
            if (cp == ' ') soft = i;
        }
        starts = java.util.Arrays.copyOf(out, n);
    }

    private int lineOf(int p) {
        int line = 0;
        while (line + 1 < starts.length && starts[line + 1] <= p) line++;
        return line;
    }

    private int lineEnd(int line) {
        String s = box.getValue();
        if (line + 1 >= starts.length) return s.length();
        int next = starts[line + 1];
        int brk = next - TextLines.BREAK.length();
        if (brk >= starts[line] && s.startsWith(TextLines.BREAK, brk)) return brk;
        if (next > starts[line] && s.charAt(next - 1) == ' ') return next - 1;
        return next;
    }

    private int indexOnLine(int line, int px) {
        String s = box.getValue();
        int end = lineEnd(line), at = 0;
        for (int i = starts[line]; i < end; ) {
            int cp = s.codePointAt(i);
            int cw = adv(cp);
            if (px < at + (cw + 1) / 2) return i;
            at += cw;
            i += Character.charCount(cp);
        }
        return end;
    }

    private int indexAt(double mx, double my) {
        int line = (int) Math.floor((my - (y + PAD_Y) + scroll) / LINE);
        line = Math.max(0, Math.min(starts.length - 1, line));
        return indexOnLine(line, (int) Math.round(mx - (x + PAD_X)));
    }

    private void tint() {
        String s = box.getValue();
        int cursor = box.isFocused() ? box.getCursorPosition() : -1;
        String p = parsing.get();
        if (s.equals(inkText) && cursor == inkCursor && Objects.equals(p, inkParsing)) return;
        inkText = s;
        inkCursor = cursor;
        inkParsing = p;
        ink = Tint.markup(s, p);
        mark = Tint.placeholders(s, cursor, ink);
        if (McText.JSON.equals(p)) return;
        for (int i = s.indexOf(TextLines.BREAK); i >= 0; i = s.indexOf(TextLines.BREAK, i + 2)) {
            ink[i] = Theme.TEXT_FAINT;
            ink[i + 1] = Theme.TEXT_FAINT;
        }
    }

    private void reveal(int p) {
        int top = lineOf(p) * LINE;
        if (top < scroll) scroll = top;
        if (top + LINE > scroll + viewH()) scroll = top + LINE - viewH();
        clamp();
    }

    void follow() {
        lay();
        reveal(Math.min(box.getCursorPosition(), box.getValue().length()));
        anchor();
    }

    private void anchor() {
        String s = box.getValue();
        int cursor = Math.min(box.getCursorPosition(), s.length());
        int line = lineOf(cursor);
        int from = Complete.tokenStart(s, cursor);
        int at = from >= starts[line] ? from : cursor;
        box.setX(x + PAD_X + span(s, starts[line], at));
        box.setY(y + PAD_Y + line * LINE - scroll);
    }

    void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        lay();
        tint();
        String s = box.getValue();
        int cursor = Math.min(box.getCursorPosition(), s.length());
        if (cursor != seenCursor || !s.equals(seenText)) {
            seenCursor = cursor;
            seenText = s;
            movedAt = System.currentTimeMillis();
            reveal(cursor);
        }
        boolean focused = box.isFocused();
        Ui.input(ctx, x, y, w, h, focused);
        if (Ui.hit(mx, my, x, y, w, h)) ctx.requestCursor(CursorTypes.IBEAM);

        int left = x + PAD_X, top = y + PAD_Y;
        int a = Math.min(cursor, box.anchor()), b = Math.max(cursor, box.anchor());
        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int first = Math.max(0, scroll / LINE);
        int last = Math.min(starts.length - 1, (scroll + viewH()) / LINE);
        for (int line = first; line <= last; line++) {
            int ly = top + line * LINE - scroll;
            int from = starts[line];
            int to = line + 1 < starts.length ? starts[line + 1] : s.length();
            if (b > a && a < to && b > from) {
                int sx = left + span(s, from, Math.max(a, from));
                int ex = left + span(s, from, Math.min(b, to));
                if (b > to || (b == to && line + 1 < starts.length)) ex += 3;
                Draw.rect(ctx, sx, ly, Math.max(1, ex - sx), LINE, Draw.argb(0x66, Theme.ACCENT));
            }
            if (to > from)
                Draw.text(ctx, tr, Tint.paint(s.substring(from, to), from, ink, mark),
                        left, ly + 2, Theme.TEXT, false);
        }
        if (s.isEmpty() && !focused)
            Draw.textFit(ctx, tr, box.getMessage().getString(), left, top + 2, textW(),
                    Theme.TEXT_FAINT, false);
        if (focused && (System.currentTimeMillis() - movedAt) / BLINK % 2 == 0) {
            int line = lineOf(cursor);
            int cx = left + span(s, starts[line], cursor);
            Draw.rect(ctx, cx, top + line * LINE - scroll, 1, LINE, Draw.opaque(Theme.TEXT));
        }
        ctx.disableScissor();

        if (maxScroll() > 0) {
            int track = h - 6;
            int thumb = Math.max(10, track * viewH() / (starts.length * LINE));
            int ty = y + 3 + (track - thumb) * scroll / maxScroll();
            Draw.round(ctx, x + w - BAR_W - 2, ty, BAR_W, thumb, 1,
                    Draw.opaque(Ui.hit(mx, my, x, y, w, h) ? 0x8D9AB4 : 0x5A6478));
        }

        anchor();
        if (Env.browser()) {
            ctx.enableScissor(x, y, x, y);
            box.extractRenderState(ctx, -1, -1, delta);
            ctx.disableScissor();
        }
    }

    boolean press(MouseButtonEvent click, boolean doubled) {
        if (!Ui.hit(click.x(), click.y(), x, y, w, h)) return false;
        lay();
        goalX = -1;
        int p = indexAt(click.x(), click.y());
        if (doubled) {
            selectWord(p);
            dragging = false;
        } else {
            box.moveCursorTo(p, click.hasShiftDown());
            dragging = true;
        }
        return true;
    }

    private void selectWord(int p) {
        String s = box.getValue();
        int from = p, to = p;
        while (from > 0 && word(s.charAt(from - 1))) from--;
        while (to < s.length() && word(s.charAt(to))) to++;
        if (from == to && to < s.length()) to++;
        box.select(from, to);
    }

    private static boolean word(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    boolean drag(double mx, double my) {
        if (!dragging) return false;
        if (my < y + PAD_Y) scroll -= LINE / 2;
        else if (my > y + h - PAD_Y) scroll += LINE / 2;
        clamp();
        box.moveCursorTo(indexAt(mx, my), true);
        return true;
    }

    void release() { dragging = false; }

    boolean wheel(double mx, double my, double amount) {
        if (!Ui.hit(mx, my, x, y, w, h) || maxScroll() == 0) return false;
        scroll -= (int) Math.round(amount * LINE);
        clamp();
        return true;
    }

    boolean key(KeyEvent in) {
        int key = in.key();
        boolean shift = (in.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (in.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean vertical = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN;
        if (!vertical) goalX = -1;
        lay();
        String s = box.getValue();
        int p = Math.min(box.getCursorPosition(), s.length());
        int line = lineOf(p);
        if (vertical) {
            if (goalX < 0) goalX = span(s, starts[line], p);
            int target = line + (key == GLFW.GLFW_KEY_UP ? -1 : 1);
            int to = target < 0 ? 0 : target >= starts.length ? s.length()
                    : indexOnLine(target, goalX);
            box.moveCursorTo(to, shift);
            return true;
        }
        if (!ctrl && (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END)) {
            box.moveCursorTo(key == GLFW.GLFW_KEY_HOME ? starts[line] : lineEnd(line), shift);
            return true;
        }
        return false;
    }
}
