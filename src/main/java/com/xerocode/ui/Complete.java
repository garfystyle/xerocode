package com.xerocode.ui;

import com.xerocode.Placeholders;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class Complete {
    private static final int ROW_H = 12, PAD = 3, ROWS = 7, MAX_HITS = 40;
    private static final int MIN_W = 132, MAX_W = 240, DESC_LINES = 3;

    private final List<Placeholders.Item> hits = new ArrayList<>();
    private final List<String> desc = new ArrayList<>();

    private TextFieldWidget field;
    private TextRenderer tr;
    private int screenW, screenH;
    private int start = -1;
    private int sel, scroll;
    private int dismissed = -1;
    private int x, y, w, h, listH;

    public boolean active() { return start >= 0 && !hits.isEmpty(); }

    public void reset() {
        field = null;
        start = -1;
        dismissed = -1;
        hits.clear();
    }

    public void update(TextFieldWidget f, TextRenderer tr, int screenW, int screenH) {
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        rescan(f);
    }

    private void rescan(TextFieldWidget f) {
        field = f;
        hits.clear();
        start = -1;
        if (f == null || !f.isFocused()) { dismissed = -1; return; }

        String text = f.getText();
        int cursor = Math.max(0, Math.min(f.getCursor(), text.length()));
        int p = tokenStart(text, cursor);
        if (p < 0) { dismissed = -1; return; }
        if (dismissed == p) return;
        dismissed = -1;

        hits.addAll(Placeholders.match(text.substring(p + 1, cursor), MAX_HITS));
        if (hits.isEmpty()) return;
        start = p;
        if (sel >= hits.size()) sel = 0;
        place();
    }

    private void wrapDesc(String text, int room) {
        desc.clear();
        if (text.isBlank()) return;
        for (StringVisitable line : tr.getTextHandler().wrapLines(text.trim(), room, Style.EMPTY)) {
            if (desc.size() < DESC_LINES) { desc.add(line.getString()); continue; }
            int last = DESC_LINES - 1;
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
            textW = Math.max(textW, tr.getWidth(it.insert())
                    + tr.getWidth(Placeholders.categoryName(it.category())) + 22);
        w = Math.min(Math.min(MAX_W, Math.max(40, screenW - 4)), textW);

        int rows = Math.min(ROWS, hits.size());
        scroll = Math.max(Math.min(scroll, hits.size() - rows), 0);
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + rows) scroll = sel - rows + 1;
        listH = rows * ROW_H;

        wrapDesc(hits.get(sel).description(), w - PAD * 2 - 2);
        h = PAD * 2 + listH + (desc.isEmpty() ? 0 : 2 + desc.size() * 9);

        x = Math.max(2, Math.min(field.getX() - 3, screenW - w - 2));
        int below = field.getY() + field.getHeight() + 2;
        y = below + h > screenH - 2 ? Math.max(2, field.getY() - h - 2) : below;
    }

    public boolean keyPressed(KeyInput input) {
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
        String text = field.getText();
        int cursor = Math.max(0, Math.min(field.getCursor(), text.length()));
        String made = text.substring(0, start) + pick.insert() + text.substring(cursor);
        field.setText(made);
        if (!field.getText().equals(made)) {
            field.setText(text);
            field.setCursor(cursor, false);
            return;
        }
        field.setCursor(start + pick.caret(), false);
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

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
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
                tagW = tr.getWidth(tag);
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
