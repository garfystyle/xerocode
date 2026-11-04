package com.xerocode.ui;

import com.xerocode.Env;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

final class CodeQuick {
    record Entry(String label, String note, String mark, int color, Runnable run) {}

    private static final int WANT_W = 420, ROWS = 12, HEAD = 22, PAD = 4;

    private final Font tr;
    private final EditBox field;
    private final String title;
    private final List<Entry> all;
    private final Consumer<String> typed;
    private final java.util.function.Function<String, List<Entry>> source;
    private List<Entry> shown = new ArrayList<>();
    private int selected, top;
    private int x, y, w, h;
    private boolean finished;

    private static int rowH() { return Env.touch() ? 22 : 14; }

    private CodeQuick(Font tr, String title, String hint, String text, List<Entry> all,
                      java.util.function.Function<String, List<Entry>> source, Consumer<String> typed) {
        this.tr = tr;
        this.title = title;
        this.all = all;
        this.source = source;
        this.typed = typed;
        field = Ui.field(tr, 0, 0, 100, 12, hint);
        field.setBordered(false);
        field.setMaxLength(Ui.TEXT_MAX);
        field.setTextColor(Draw.opaque(Theme.TEXT));
        field.setValue(text);
        field.setFocused(true);
        field.setResponder(s -> filter());
        field.moveCursorToEnd(false);
        field.setHighlightPos(0);
        filter();
    }

    static CodeQuick list(Font tr, String hint, List<Entry> entries) {
        return new CodeQuick(tr, null, hint, "", entries, null, null);
    }

    static CodeQuick dynamic(Font tr, String hint, java.util.function.Function<String, List<Entry>> source) {
        return new CodeQuick(tr, null, hint, "", List.of(), source, null);
    }

    static CodeQuick ask(Font tr, String title, String hint, String text, Consumer<String> done) {
        return new CodeQuick(tr, title, hint, text, List.of(), null, done);
    }

    boolean done() { return finished; }

    private void filter() {
        String q = field.getValue().toLowerCase(Locale.ROOT).strip();
        List<Entry> out = new ArrayList<>();
        if (source != null) out.addAll(source.apply(field.getValue()));
        else if (typed == null) {
            for (Entry e : all) if (q.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(q)) out.add(e);
            out.sort((a, b) -> rank(a, q) - rank(b, q));
        }
        shown = out;
        selected = 0;
        top = 0;
    }

    private static int rank(Entry e, String q) {
        if (q.isEmpty()) return 0;
        return e.label.toLowerCase(Locale.ROOT).startsWith(q) ? 0 : 1;
    }

    private void layout(int screenW, int screenH) {
        w = Ui.fitW(screenW, WANT_W);
        x = Ui.midX(screenW, w);
        y = Math.min(44, Math.max(2, screenH / 8));
        int rows = Math.min(Math.min(ROWS, shown.size()), Math.max(1, (screenH - y - HEAD - 8) / rowH()));
        h = HEAD + (title == null ? 0 : 12) + (shown.isEmpty() ? 0 : rows * rowH() + PAD * 2);
        int fy = y + (title == null ? 0 : 12) + 6;
        field.setX(x + 8);
        field.setY(fy);
        Ui.width(field, w - 16);
    }

    void draw(GuiGraphicsExtractor ctx, int mx, int my, float delta, int screenW, int screenH) {
        layout(screenW, screenH);
        Draw.shadow(ctx, x, y, w, h, 4);
        Draw.round(ctx, x, y, w, h, 4, Draw.opaque(Theme.PANEL_RAISED));
        Draw.roundOutline(ctx, x, y, w, h, 4, Draw.opaque(Theme.LINE));
        if (title != null) Draw.textFit(ctx, tr, title, x + 8, y + 4, w - 16, Theme.TEXT_DIM, false);
        int fieldTop = y + (title == null ? 0 : 12) + 2;
        Ui.input(ctx, x + 4, fieldTop, w - 8, HEAD - 5, true);
        Ui.placeholder(ctx, tr, field);
        field.extractRenderState(ctx, mx, my, delta);
        if (shown.isEmpty()) return;
        int rh = rowH(), base = y + HEAD + (title == null ? 0 : 12) + PAD;
        int rows = (h - HEAD - (title == null ? 0 : 12) - PAD * 2) / rh;
        for (int i = 0; i < rows && top + i < shown.size(); i++) {
            Entry e = shown.get(top + i);
            int ry = base + i * rh, ty = ry + (rh - 8) / 2;
            boolean hover = Ui.hit(mx, my, x + 2, ry, w - 4, rh);
            if (top + i == selected) Draw.round(ctx, x + 2, ry, w - 4, rh, 2, Draw.argb(0x55, Theme.ACCENT));
            else if (hover) Draw.round(ctx, x + 2, ry, w - 4, rh, 2, Draw.opaque(Theme.SURFACE_HOVER));
            int tx = x + 8;
            if (e.mark != null) {
                Draw.text(ctx, tr, e.mark, tx, ty, e.color, false);
                tx += 12;
            }
            int noteW = e.note == null ? 0 : Math.min(w / 2, tr.width(e.note));
            Draw.textFit(ctx, tr, e.label, tx, ty, x + w - 10 - tx - noteW - 6, Theme.TEXT, false);
            if (e.note != null) Draw.textFit(ctx, tr, e.note, x + w - 8 - noteW, ty, noteW, Theme.TEXT_FAINT, false);
        }
    }

    boolean keyPressed(KeyEvent e) {
        int key = e.key();
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                finished = true;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                accept();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                move(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                move(1);
                return true;
            }
            default -> {
                return field.keyPressed(e);
            }
        }
    }

    private void accept() {
        finished = true;
        if (typed != null) {
            typed.accept(field.getValue());
            return;
        }
        if (selected < shown.size()) shown.get(selected).run.run();
    }

    private void move(int by) {
        if (shown.isEmpty()) return;
        selected = (selected + by + shown.size()) % shown.size();
        if (selected < top) top = selected;
        if (selected >= top + ROWS) top = selected - ROWS + 1;
    }

    boolean charTyped(CharacterEvent e) { return field.charTyped(e); }

    boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (!Ui.hit(mx, my, x, y, w, h)) {
            finished = true;
            return true;
        }
        int rh = rowH(), base = y + HEAD + (title == null ? 0 : 12) + PAD;
        if (my >= base && !shown.isEmpty()) {
            int row = (int) ((my - base) / rh) + top;
            if (row >= 0 && row < shown.size()) {
                selected = row;
                accept();
            }
            return true;
        }
        field.mouseClicked(click, doubled);
        return true;
    }

    boolean mouseScrolled(double amount) {
        top = Math.max(0, Math.min(Math.max(0, shown.size() - ROWS), top - (int) Math.signum(amount)));
        return true;
    }

    void close() { field.setFocused(false); }
}
