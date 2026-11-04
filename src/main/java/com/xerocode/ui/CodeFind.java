package com.xerocode.ui;

import com.xerocode.Match;
import com.xerocode.code.Doc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

final class CodeFind {
    private static final int W = 300, ROW = 18, PAD = 4, TOGGLE = 16, COUNT_W = 52;
    private static final int LIMIT = 20000;
    private static final String[] MODES = {"Аа", "аб", ".*"};

    private final Font tr;
    private final CodeArea area;
    private final EditBox find, swap;
    private final boolean[] modes = new boolean[3];
    private boolean replacing;
    private int x, y, w, h;
    private List<CodeArea.Found> results = List.of();
    private int current = -1;
    private int searchedVersion = -1;
    private Doc searched;
    private String error;

    CodeFind(Font tr, CodeArea area) {
        this.tr = tr;
        this.area = area;
        find = field("найти");
        swap = field("заменить на");
        find.setResponder(s -> {
            searchedVersion = -1;
            refresh();
            pickNearest();
        });
    }

    private EditBox field(String hint) {
        EditBox f = Ui.field(tr, 0, 0, 100, 10, hint);
        f.setBordered(false);
        f.setMaxLength(Ui.TEXT_MAX);
        f.setTextColor(Draw.opaque(Theme.TEXT));
        return f;
    }

    void open(boolean withReplace) {
        replacing = withReplace;
        Doc d = area.doc();
        if (d != null && d.primary().selecting()) {
            String s = d.selected(d.primary());
            if (s.indexOf('\n') < 0) find.setValue(s);
        }
        focusFind();
        searchedVersion = -1;
        refresh();
    }

    void focusFind() {
        swap.setFocused(false);
        find.setFocused(true);
        find.moveCursorToEnd(false);
        find.setHighlightPos(0);
        area.focus(false);
    }

    boolean focused() { return find.isFocused() || swap.isFocused(); }

    void blur() {
        find.setFocused(false);
        swap.setFocused(false);
    }

    private void refresh() {
        Doc d = area.doc();
        if (d == null) return;
        if (d == searched && searchedVersion == d.version()) return;
        searched = d;
        searchedVersion = d.version();
        error = null;
        String q = find.getValue();
        List<CodeArea.Found> out = new ArrayList<>();
        if (!q.isEmpty()) {
            Match m = Match.of(q, modes[0], modes[1], modes[2]);
            if (m.error != null) error = m.error;
            else {
                for (int l = 0; l < d.lines() && out.size() < LIMIT; l++) {
                    String t = d.line(l);
                    for (int[] hit : m.ranges(t)) out.add(new CodeArea.Found(l, hit[0], hit[1] - hit[0]));
                }
            }
        }
        results = out;
        if (current >= results.size()) current = results.isEmpty() ? -1 : 0;
        area.found(results, current);
    }

    void frame() {
        refresh();
        area.found(results, current);
    }

    private void pickNearest() {
        Doc d = area.doc();
        if (d == null || results.isEmpty()) {
            current = -1;
            return;
        }
        Doc.Caret c = d.primary();
        current = 0;
        for (int i = 0; i < results.size(); i++) {
            CodeArea.Found f = results.get(i);
            if (Doc.compare(f.line(), f.col(), c.startLine(), c.startCol()) >= 0) {
                current = i;
                break;
            }
        }
        show();
    }

    private void show() {
        if (current < 0 || current >= results.size()) return;
        CodeArea.Found f = results.get(current);
        area.select(f.line(), f.col(), f.line(), f.col() + f.length());
        area.center(f.line());
    }

    void step(int dir) {
        refresh();
        if (results.isEmpty()) return;
        current = current < 0 ? 0 : (current + dir + results.size()) % results.size();
        show();
    }

    private void replaceOne() {
        refresh();
        if (current < 0 || current >= results.size()) return;
        CodeArea.Found f = results.get(current);
        Doc d = area.doc();
        d.replace(f.line(), f.col(), f.line(), f.col() + f.length(), swap.getValue());
        area.edited();
        refresh();
        if (!results.isEmpty()) {
            current = Math.min(current, results.size() - 1);
            show();
        }
    }

    private void replaceAll() {
        refresh();
        if (results.isEmpty()) return;
        Doc d = area.doc();
        d.begin(Doc.OTHER);
        for (int i = results.size() - 1; i >= 0; i--) {
            CodeArea.Found f = results.get(i);
            d.replace(f.line(), f.col(), f.line(), f.col() + f.length(), swap.getValue());
        }
        d.end();
        area.edited();
        refresh();
    }

    int height() { return replacing ? ROW * 2 + PAD * 3 : ROW + PAD * 2; }

    private int toggleX(int i) { return x + w - PAD - COUNT_W - 32 - (MODES.length - i) * (TOGGLE + 1); }

    void draw(GuiGraphicsExtractor ctx, int mx, int my, float delta, int right, int top) {
        w = Math.min(W + 110, Math.max(180, right - area.left() - 8));
        h = height();
        x = right - w - 12;
        y = top + 2;
        Draw.shadow(ctx, x, y, w, h, 4);
        Draw.round(ctx, x, y, w, h, 4, Draw.opaque(Theme.PANEL_RAISED));
        Draw.roundOutline(ctx, x, y, w, h, 4, Draw.opaque(Theme.LINE));
        int fieldW = toggleX(0) - x - PAD * 2 - 2;
        Ui.input(ctx, x + PAD, y + PAD, fieldW, ROW, find.isFocused());
        place(find, x + PAD + 5, y + PAD + 5, fieldW - 8);
        Ui.placeholder(ctx, tr, find);
        find.extractRenderState(ctx, mx, my, delta);
        for (int i = 0; i < MODES.length; i++) {
            int bx = toggleX(i), by = y + PAD + 1;
            boolean over = Ui.hit(mx, my, bx, by, TOGGLE, TOGGLE);
            if (modes[i]) Draw.round(ctx, bx, by, TOGGLE, TOGGLE, 3, Draw.argb(0x77, Theme.ACCENT));
            else if (over) Draw.round(ctx, bx, by, TOGGLE, TOGGLE, 3, Draw.opaque(Theme.SURFACE_HOVER));
            Draw.text(ctx, tr, MODES[i], bx + (TOGGLE - tr.width(MODES[i])) / 2, by + 4,
                    modes[i] ? Theme.TEXT : Theme.TEXT_DIM, false);
        }
        String count = error != null ? "ошибка"
                : find.getValue().isEmpty() ? "" : results.isEmpty() ? "нет"
                : (current + 1) + " из " + results.size();
        Draw.textFit(ctx, tr, count, x + w - PAD - COUNT_W - 30, y + PAD + 5, COUNT_W, error != null ? Theme.DANGER : Theme.TEXT_DIM, false);
        button(ctx, mx, my, x + w - 32, y + PAD + 1, Draw.CARET_UP);
        button(ctx, mx, my, x + w - 17, y + PAD + 1, Draw.CARET_DOWN);
        if (!replacing) return;
        int ry = y + PAD * 2 + ROW;
        Ui.input(ctx, x + PAD, ry, fieldW, ROW, swap.isFocused());
        place(swap, x + PAD + 5, ry + 5, fieldW - 8);
        Ui.placeholder(ctx, tr, swap);
        swap.extractRenderState(ctx, mx, my, delta);
        text(ctx, mx, my, "Заменить", x + PAD + fieldW + 3, ry + 1);
        text(ctx, mx, my, "Все", x + w - 28, ry + 1);
    }

    private void place(EditBox f, int fx, int fy, int fw) {
        f.setX(fx);
        f.setY(fy);
        Ui.width(f, fw);
    }

    private void button(GuiGraphicsExtractor ctx, int mx, int my, int bx, int by, String[] icon) {
        if (Ui.hit(mx, my, bx, by, 14, TOGGLE)) Draw.round(ctx, bx, by, 14, TOGGLE, 3, Draw.opaque(Theme.SURFACE_HOVER));
        Draw.glyph(ctx, icon, bx + (14 - Draw.glyphW(icon)) / 2, by + (TOGGLE - Draw.glyphH(icon)) / 2, Theme.TEXT_DIM);
    }

    private void text(GuiGraphicsExtractor ctx, int mx, int my, String s, int bx, int by) {
        int bw = tr.width(s) + 8;
        boolean over = Ui.hit(mx, my, bx, by, bw, TOGGLE);
        Draw.round(ctx, bx, by, bw, TOGGLE, 3, Draw.opaque(over ? Theme.SURFACE_HOVER : Theme.SURFACE));
        Draw.text(ctx, tr, s, bx + 4, by + 4, Theme.TEXT, false);
    }

    boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, w, h); }

    boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) return false;
        for (int i = 0; i < MODES.length; i++) {
            if (Ui.hit(mx, my, toggleX(i), y + PAD + 1, TOGGLE, TOGGLE)) {
                modes[i] = !modes[i];
                searchedVersion = -1;
                refresh();
                pickNearest();
                return true;
            }
        }
        if (Ui.hit(mx, my, x + w - 32, y + PAD + 1, 14, TOGGLE)) {
            step(-1);
            return true;
        }
        if (Ui.hit(mx, my, x + w - 17, y + PAD + 1, 14, TOGGLE)) {
            step(1);
            return true;
        }
        int ry = y + PAD * 2 + ROW;
        if (replacing) {
            int fieldW = toggleX(0) - x - PAD * 2 - 2;
            int bw = tr.width("Заменить") + 8, bx = x + PAD + fieldW + 3;
            if (Ui.hit(mx, my, bx, ry + 1, bw, TOGGLE)) {
                replaceOne();
                return true;
            }
            if (Ui.hit(mx, my, x + w - 28, ry + 1, tr.width("Все") + 8, TOGGLE)) {
                replaceAll();
                return true;
            }
            if (my >= ry) {
                find.setFocused(false);
                swap.setFocused(true);
                area.focus(false);
                swap.mouseClicked(click, doubled);
                return true;
            }
        }
        swap.setFocused(false);
        find.setFocused(true);
        area.focus(false);
        find.mouseClicked(click, doubled);
        return true;
    }

    boolean keyPressed(KeyEvent e) {
        int key = e.key();
        boolean shift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (swap.isFocused()) {
                if (ctrl) replaceAll();
                else replaceOne();
            } else step(shift ? -1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (replacing) {
                boolean toSwap = find.isFocused();
                find.setFocused(!toSwap);
                swap.setFocused(toSwap);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F3) {
            step(shift ? -1 : 1);
            return true;
        }
        EditBox f = swap.isFocused() ? swap : find;
        return f.keyPressed(e);
    }

    boolean charTyped(CharacterEvent e) {
        EditBox f = swap.isFocused() ? swap : find;
        return f.charTyped(e);
    }

    void close() {
        blur();
        results = List.of();
        current = -1;
        area.found(results, -1);
    }
}
