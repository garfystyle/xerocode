package com.xerocode.ui;

import com.xerocode.Placeholders;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public final class Complete {
    private static final int ROW_H = 12, PAD = 3, ROWS = 7, MAX_HITS = 40;
    private static final int MIN_W = 132, MAX_W = 240, DESC_LINES = 3;

    private final List<Placeholders.Item> hits = new ArrayList<>();
    private final List<String> desc = new ArrayList<>();

    private EditBox field;
    private Font tr;
    private int screenW, screenH;
    private int start = -1;
    private int sel, scroll;
    private int dismissed = -1;
    private int x, y, w, h, listH;
    private boolean numbersOnly;

    public boolean active() { return start >= 0 && !hits.isEmpty(); }

    public void reset() {
        field = null;
        start = -1;
        dismissed = -1;
        hits.clear();
    }

    public void update(EditBox f, Font tr, int screenW, int screenH) {
        update(f, tr, screenW, screenH, false);
    }

    public void update(EditBox f, Font tr, int screenW, int screenH, boolean numbersOnly) {
        this.numbersOnly = numbersOnly;
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        rescan(f);
    }

    private void rescan(EditBox f) {
        field = f;
        hits.clear();
        start = -1;
        if (f == null || !f.isFocused()) { dismissed = -1; return; }

        String text = f.getValue();
        int cursor = Math.max(0, Math.min(f.getCursorPosition(), text.length()));
        int p = tokenStart(text, cursor);
        if (p < 0) { dismissed = -1; return; }
        if (dismissed == p) return;
        dismissed = -1;

        hits.addAll(Placeholders.match(text.substring(p + 1, cursor), MAX_HITS, numbersOnly));
        if (hits.isEmpty()) return;
        start = p;
        if (sel >= hits.size()) sel = 0;
        place();
    }

    private void wrapDesc(String text, int room, int most) {
        desc.clear();
        if (text.isBlank()) return;
        for (FormattedText line : tr.getSplitter().splitLines(text.trim(), room, Style.EMPTY)) {
            if (desc.size() < most) { desc.add(line.getString()); continue; }
            int last = most - 1;
            desc.set(last, Draw.fit(tr, desc.get(last) + " " + line.getString(), room));
            return;
        }
    }

    static int tokenStart(String text, int cursor) {
        if (text == null || cursor <= 0 || cursor > text.length()) return -1;
        int p = text.lastIndexOf('%', cursor - 1);
        if (p < 0 || closing(text, p)) return -1;
        for (int i = p + 1; i < cursor; i++) if (!word(text.charAt(i))) return -1;
        return p;
    }

    private static boolean word(char c) { return c == '_' || Character.isLetterOrDigit(c); }

    private static boolean closing(String text, int p) {
        int q = p - 1;
        while (q >= 0 && word(text.charAt(q))) q--;
        return q >= 0 && text.charAt(q) == '%';
    }

    private void place() {
        int textW = MIN_W;
        for (Placeholders.Item it : hits)
            textW = Math.max(textW, tr.width(it.insert())
                    + tr.width(Placeholders.categoryName(it.category())) + 22);
        w = Math.min(Math.min(MAX_W, Math.max(40, screenW - 4)), textW);

        int rows = Math.min(ROWS, hits.size());
        scroll = Math.max(Math.min(scroll, hits.size() - rows), 0);
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + rows) scroll = sel - rows + 1;
        listH = rows * ROW_H;

        int below = field.getY() + field.getHeight() + 2;
        int space = Math.max(screenH - 2 - below, field.getY() - 4) - PAD * 2 - listH - 2;
        wrapDesc(hits.get(sel).description(), w - PAD * 2 - 2, Math.max(DESC_LINES, space / 9));
        h = PAD * 2 + listH + (desc.isEmpty() ? 0 : 2 + desc.size() * 9);

        x = Math.max(2, Math.min(field.getX() - 3, screenW - w - 2));
        y = below + h > screenH - 2 ? Math.max(2, field.getY() - h - 2) : below;
    }

    public boolean keyPressed(KeyEvent input) {
        if (!active()) return false;
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            dismissed = start;
            start = -1;
            hits.clear();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER) {
            accept();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) { move(1); return true; }
        if (key == GLFW.GLFW_KEY_UP) { move(-1); return true; }
        return false;
    }

    private void move(int by) {
        sel = Math.floorMod(sel + by, hits.size());
        place();
    }

    private void accept() {
        Placeholders.Item pick = hits.get(sel);
        String text = field.getValue();
        int cursor = Math.max(0, Math.min(field.getCursorPosition(), text.length()));
        String made = text.substring(0, start) + pick.insert() + text.substring(cursor);
        field.setValue(made);
        if (!field.getValue().equals(made)) {
            field.setValue(text);
            field.moveCursorTo(cursor, false);
            return;
        }
        field.moveCursorTo(start + pick.caret(), false);
        start = -1;
        hits.clear();
        rescan(field);
    }

    public boolean mouseClicked(double mx, double my) {
        if (!active()) return false;
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int i = rowAt(my);
        if (i >= 0) { sel = i; accept(); }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!active() || mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int rows = Math.min(ROWS, hits.size());
        scroll = Math.max(0, Math.min(hits.size() - rows, scroll - (int) Math.signum(amount)));
        return true;
    }

    private int rowAt(double my) {
        int rows = Math.min(ROWS, hits.size());
        int rel = (int) (my - (y + PAD));
        if (rel < 0 || rel >= rows * ROW_H) return -1;
        return scroll + rel / ROW_H;
    }

    public void render(GuiGraphicsExtractor ctx, Font tr, int mouseX, int mouseY) {
        if (!active()) return;
        Draw.shadow(ctx, x, y, w, h, 4);
        Draw.card(ctx, x, y, w, h, 4, Draw.opaque(Ui.PANEL), Draw.opaque(Theme.ACCENT));

        int rows = Math.min(ROWS, hits.size());
        int hover = mouseX >= x && mouseX < x + w ? rowAt(mouseY) : -1;
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i >= hits.size()) break;
            Placeholders.Item it = hits.get(i);
            int ry = y + PAD + r * ROW_H;
            boolean on = i == sel;
            if (on) Draw.round(ctx, x + 2, ry, w - 4, ROW_H - 1, 3,
                    Draw.opaque(Draw.mix(Ui.BTN_HOVER, Theme.ACCENT, 0.35f)));
            else if (i == hover) Draw.round(ctx, x + 2, ry, w - 4, ROW_H - 1, 3,
                    Draw.opaque(Ui.BTN_HOVER));
            int right = x + w - 6;
            boolean firstOfKind = r == 0
                    || !hits.get(i - 1).category().equals(it.category());
            int tagW = 0;
            if (firstOfKind) {
                String tag = Placeholders.categoryName(it.category());
                tagW = tr.width(tag);
                Draw.text(ctx, tr, Draw.ordered(tag), right - tagW, ry + 2,
                        on ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
            }
            Draw.textFit(ctx, tr, it.insert(), x + 6, ry + 2,
                    right - (tagW == 0 ? 0 : tagW + 8) - (x + 6),
                    on ? Theme.TEXT : Theme.TEXT_DIM, false);
        }

        if (hits.size() > rows) {
            int barH = Math.max(6, listH * rows / hits.size());
            int barY = y + PAD + (listH - barH) * scroll / Math.max(1, hits.size() - rows);
            Draw.round(ctx, x + w - 3, barY, 2, barH, 1, Draw.opaque(Ui.BORDER));
        }

        if (desc.isEmpty()) return;
        int dy = y + PAD + listH + 2;
        Draw.rect(ctx, x + PAD, dy - 1, w - PAD * 2, 1, Draw.opaque(Ui.LINE));
        for (String line : desc) {
            Draw.text(ctx, tr, line, x + PAD + 1, dy + 1, Theme.TEXT_FAINT, false);
            dy += 9;
        }
    }
}
