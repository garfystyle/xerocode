package com.xerocode.ui;

import com.xerocode.Settings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class BlockMenu {
    public static final class Row {
        final String[] icon;
        final String label;
        final Settings.Hot hot;
        final Runnable act;
        String note = "";
        boolean danger, caret, enabled = true, gap;
        int top;

        Row(String[] icon, String label, Settings.Hot hot, Runnable act) {
            this.icon = icon; this.label = label; this.hot = hot; this.act = act;
        }

        public Row note(String s)  { note = s == null ? "" : s; return this; }
        public Row danger()        { danger = true; return this; }
        public Row caret()         { caret = true; return this; }
        public Row on(boolean yes) { enabled = yes; return this; }

        String right() {
            if (!note.isEmpty()) return note;
            return hot == null ? "" : Settings.get().label(hot);
        }
    }

    private static final int HEAD_H = 30, ROW_H = 15, GAP_H = 7, PAD = 4;
    private static final int ICON_X = 9, ICON_W = 11, INK_X = ICON_X + ICON_W + 4;
    private static final int EDGE = 9, CARET_W = 8;
    private static final int MIN_W = 176, MAX_W = 340;

    private final TextRenderer tr;
    private final int screenW, screenH, wantX, wantY;
    private final ItemStack stack;
    private final String title, subtitle;
    private final int accent;
    private final List<Row> rows = new ArrayList<>();
    private boolean pendingGap;

    private int x, y, w, h, listH, total;
    private double scroll;
    private int sel = -1;
    private int lastMx = Integer.MIN_VALUE, lastMy = Integer.MIN_VALUE;
    private boolean closed;
    private final Ui.Bar bar = new Ui.Bar();

    public BlockMenu(int screenW, int screenH, int x, int y, TextRenderer tr,
                     ItemStack stack, String title, String subtitle, int accent) {
        this.screenW = screenW; this.screenH = screenH;
        this.wantX = x; this.wantY = y;
        this.tr = tr;
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.accent = accent;
    }

    public BlockMenu gap() { pendingGap = true; return this; }

    public Row row(String[] icon, String label, Settings.Hot hot, Runnable act) {
        Row r = new Row(icon, label, hot, act);
        r.gap = pendingGap && !rows.isEmpty();
        pendingGap = false;
        rows.add(r);
        return r;
    }

    public BlockMenu open() {
        int need = 16 + 6 + Math.max(tr.getWidth(title), tr.getWidth(subtitle)) + EDGE + ICON_X;
        for (Row r : rows) {
            String right = r.right();
            need = Math.max(need, INK_X + tr.getWidth(r.label) + 14
                    + (right.isEmpty() ? 0 : tr.getWidth(right))
                    + (r.caret ? CARET_W : 0) + EDGE);
        }
        w = Math.max(Math.min(MIN_W, screenW - 6), Math.min(Math.min(MAX_W, screenW - 6), need));

        total = 0;
        for (Row r : rows) {
            if (r.gap) total += GAP_H;
            r.top = total;
            total += ROW_H;
        }
        h = Math.min(screenH - 6, HEAD_H + PAD * 2 + total);
        listH = h - HEAD_H - PAD * 2;

        x = Math.max(2, Math.min(wantX, screenW - w - 2));
        y = wantY + h > screenH - 2 ? Math.max(2, screenH - h - 2) : wantY;
        return this;
    }

    public boolean isClosed() { return closed; }
    public void close() { closed = true; }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int listTop() { return y + HEAD_H + PAD; }
    private double maxScroll() { return Math.max(0, total - listH); }

    private int indexAt(double mx, double my) {
        if (mx < x || mx >= x + w) return -1;
        int top = listTop();
        if (my < top || my >= top + listH) return -1;
        double rel = my - top + scroll;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (rel >= r.top && rel < r.top + ROW_H) return r.enabled ? i : -1;
        }
        return -1;
    }

    private void reveal(int i) {
        if (i < 0 || i >= rows.size()) return;
        Row r = rows.get(i);
        if (r.top < scroll) scroll = r.top;
        else if (r.top + ROW_H > scroll + listH) scroll = r.top + ROW_H - listH;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private void move(int step) {
        if (rows.isEmpty()) return;
        int i = sel;
        for (int n = 0; n < rows.size(); n++) {
            i = i < 0 ? (step > 0 ? 0 : rows.size() - 1)
                    : (i + step + rows.size()) % rows.size();
            if (rows.get(i).enabled) { sel = i; reveal(i); return; }
        }
    }

    private void run(Row r) {
        if (!r.enabled) return;
        closed = true;
        r.act.run();
    }

    public void render(DrawContext ctx, int mouseX, int mouseY) {
        if (mouseX != lastMx || mouseY != lastMy) {
            lastMx = mouseX;
            lastMy = mouseY;
            int over = indexAt(mouseX, mouseY);
            if (over >= 0) sel = over;
            else if (contains(mouseX, mouseY)) sel = -1;
        }

        Draw.shadow(ctx, x, y, w, h, Ui.R);
        Draw.card(ctx, x, y, w, h, Ui.R, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));
        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent);
        Draw.rect(ctx, x + 1, y + HEAD_H - 1, w - 2, 1,
                Draw.opaque(Draw.mix(Ui.LINE, accent, 0.55f)));

        int textX = x + ICON_X;
        if (!stack.isEmpty()) {
            ctx.drawItem(stack, x + ICON_X - 1, y + (HEAD_H - 16) / 2);
            textX = x + ICON_X + 16 + 6;
        }
        int room = x + w - EDGE - textX;
        if (subtitle.isEmpty() || subtitle.equals(title)) {
            Draw.textFit(ctx, tr, title, textX, y + (HEAD_H - Ui.TEXT_H) / 2 - 1, room,
                    Theme.TEXT, false);
        } else {
            Draw.textFit(ctx, tr, title, textX, y + 6, room, Theme.TEXT, false);
            Draw.textFit(ctx, tr, subtitle, textX, y + 16, room, Theme.TEXT_FAINT, false);
        }

        int top = listTop();
        ctx.enableScissor(x + 1, top, x + w - 1, top + listH);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = top + r.top - (int) Math.round(scroll);
            if (ry + ROW_H < top || ry > top + listH) continue;
            if (r.gap) Ui.hairline(ctx, x + 8, ry - GAP_H / 2 - 1, w - 16);
            boolean hot = i == sel && r.enabled;
            if (hot) Draw.round(ctx, x + 4, ry, w - 8, ROW_H - 1, Ui.R_SM,
                    Draw.opaque(r.danger ? Ui.DANGER_BG : Ui.BTN_HOVER));
            int ink = !r.enabled ? Theme.TEXT_FAINT
                    : r.danger ? Theme.DANGER : hot ? Theme.TEXT : Theme.TEXT_DIM;
            int mark = !r.enabled ? Theme.TEXT_FAINT
                    : r.danger ? Theme.DANGER : hot ? Theme.TEXT_DIM : Theme.TEXT_FAINT;
            int side = !r.enabled || !hot ? Theme.TEXT_FAINT : Theme.TEXT_DIM;
            if (r.icon != null)
                Draw.glyph(ctx, r.icon, x + ICON_X + (ICON_W - Draw.glyphW(r.icon)) / 2,
                        ry + (ROW_H - Draw.glyphH(r.icon)) / 2, mark);
            int right = x + w - EDGE;
            if (r.caret) {
                Draw.glyph(ctx, Draw.CARET_RIGHT, right - Draw.glyphW(Draw.CARET_RIGHT),
                        ry + (ROW_H - Draw.glyphH(Draw.CARET_RIGHT)) / 2, mark);
                right -= CARET_W;
            }
            String note = r.right();
            if (!note.isEmpty()) {
                String cut = Draw.fit(tr, note, Math.max(0, (right - x - INK_X) / 2));
                Draw.textRight(ctx, tr, cut, right, ry + 4, side, false);
                right -= tr.getWidth(cut) + 10;
            }
            Draw.textFit(ctx, tr, r.label, x + INK_X, ry + 4, right - x - INK_X, ink, false);
        }
        ctx.disableScissor();

        if (maxScroll() > 0)
            bar.draw(ctx, x + w - 5, top, listH, total, listH, (int) Math.round(scroll),
                    mouseX, mouseY);
    }

    public boolean mouseClicked(double mx, double my) {
        if (!contains(mx, my)) { closed = true; return false; }
        if (bar.grabbed(mx, my, 1, (int) maxScroll(), v -> scroll = v)) return true;
        int i = indexAt(mx, my);
        if (i >= 0) run(rows.get(i));
        return true;
    }

    public boolean mouseDragged(double my) {
        return bar.dragged(my, 1, (int) maxScroll(), v -> scroll = v);
    }

    public void mouseReleased() { bar.release(); }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my) || maxScroll() <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * ROW_H * 2));
        return true;
    }

    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { closed = true; return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { move(1); return true; }
        if (key == GLFW.GLFW_KEY_UP) { move(-1); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { sel = -1; move(1); return true; }
        if (key == GLFW.GLFW_KEY_END) { sel = -1; move(-1); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (sel >= 0 && sel < rows.size()) run(rows.get(sel));
            return true;
        }
        Settings.Hot hot = Settings.get().match(key, input.modifiers());
        if (hot == null) return true;
        for (Row r : rows) if (r.hot == hot && r.enabled) { run(r); return true; }
        closed = true;
        return false;
    }
}
