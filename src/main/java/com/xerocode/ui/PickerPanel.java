package com.xerocode.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

abstract class PickerPanel {
    protected static final int PAD = 10;
    protected static final int HEAD_H = 28;
    protected static final int FOOT_H = 28;
    protected static final int DET_W = 176;

    protected final TextRenderer tr;
    protected final int screenW, screenH;
    protected final int accent;

    protected TextFieldWidget search;
    protected int x, y, w, h, railW, detW;
    protected int hovered = -1;
    protected boolean closed;

    protected PickerPanel(TextRenderer tr, int screenW, int screenH, int accent) {
        this.tr = tr;
        this.screenW = screenW;
        this.screenH = screenH;
        this.accent = accent;
    }

    protected void place(int w, int h, int railW, int detW, String searchHint) {
        this.w = w;
        this.h = h;
        this.railW = railW;
        this.detW = detW;
        this.x = (screenW - w) / 2;
        this.y = Math.max(6, (screenH - h) / 2);
        search = Ui.field(tr, searchX() + 16, y + 9, searchW() - 22, 10, searchHint);
        search.setMaxLength(48);
        search.setChangedListener(s -> refresh(true));
        search.setFocused(true);
    }

    public boolean isClosed() { return closed; }

    protected int searchMaxW() { return 210; }

    protected int searchW() { return Math.min(searchMaxW(), w / 2); }
    protected int searchX() { return x + w - PAD - 16 - searchW() - 6; }
    protected int bodyY()   { return y + HEAD_H + 1; }
    protected int railX()   { return x + 1; }
    protected int detX()    { return x + w - 1 - detW; }
    protected int footY()   { return y + h - FOOT_H; }

    protected abstract int bodyH();

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);

        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent);
        Draw.round(ctx, x + PAD, y + 9, 3, 10, 1, Draw.opaque(accent));
        Draw.textFit(ctx, tr, title(), x + PAD + 9, y + 10, searchX() - x - PAD - 14,
                Theme.TEXT, false);
        Ui.input(ctx, searchX(), y + 6, searchW(), 16, search.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, searchX() + 6, y + 10, Theme.TEXT_FAINT);
        search.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, search);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 16, y + 6, 16);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);

        hovered = indexAt(mouseX, mouseY);
        drawRail(ctx, mouseX, mouseY);
        drawBody(ctx, mouseX, mouseY, delta);
        drawDetails(ctx);

        Ui.hairline(ctx, x + 1, footY() - 1, w - 2);
        drawFooter(ctx, mouseX, mouseY);
    }

    protected abstract String title();

    protected abstract void drawBody(DrawContext ctx, int mouseX, int mouseY, float delta);

    protected abstract void drawDetails(DrawContext ctx);

    protected record RailRow(ItemStack icon, String label, int count) {}

    protected abstract List<RailRow> railRows();

    protected abstract int railRowH();

    protected abstract int railActive();

    protected abstract void railChosen(int index);

    private void drawRail(DrawContext ctx, int mouseX, int mouseY) {
        int by = bodyY(), bh = bodyH(), rowH = railRowH();
        Draw.rect(ctx, railX(), by, railW, bh, Draw.opaque(Ui.RAIL));
        Ui.vline(ctx, railX() + railW, by, bh);
        List<RailRow> rows = railRows();
        int active = railActive();
        for (int i = 0; i < rows.size(); i++) {
            int ry = by + 4 + i * rowH;
            if (ry + rowH > by + bh) break;
            RailRow row = rows.get(i);
            boolean on = i == active;
            boolean hov = Ui.hit(mouseX, mouseY, railX() + 3, ry, railW - 6, rowH - 1);
            if (on) {
                Draw.round(ctx, railX() + 3, ry, railW - 6, rowH - 1, 3, Draw.opaque(Ui.BTN_ON));
                Draw.rect(ctx, railX() + 3, ry + 2, 2, rowH - 5, Draw.opaque(Theme.ACCENT));
            } else if (hov) {
                Draw.round(ctx, railX() + 3, ry, railW - 6, rowH - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            }
            boolean icon = row.icon() != null;
            if (icon) ctx.drawItem(row.icon(), railX() + 7, ry + 1);
            String n = String.valueOf(row.count());
            int ty = ry + (icon ? 5 : 4);
            Draw.textFit(ctx, tr, row.label(), railX() + (icon ? 26 : 9), ty,
                    railW - (icon ? 38 : 22) - tr.getWidth(n),
                    on || hov ? Theme.TEXT : Theme.TEXT_DIM, false);
            Draw.textRight(ctx, tr, n, railX() + railW - 8, ty,
                    on ? Theme.ACCENT : Theme.TEXT_FAINT, false);
        }
    }

    private int railIndexAt(double mx, double my) {
        int rowH = railRowH();
        List<RailRow> rows = railRows();
        for (int i = 0; i < rows.size(); i++) {
            int ry = bodyY() + 4 + i * rowH;
            if (ry + rowH > bodyY() + bodyH()) break;
            if (Ui.hit(mx, my, railX() + 3, ry, railW - 6, rowH - 1)) return i;
        }
        return -1;
    }

    protected boolean detailsFrame(DrawContext ctx) {
        if (detW == 0) return false;
        Draw.rect(ctx, detX(), bodyY(), detW, bodyH(), Draw.opaque(Ui.RAIL));
        Ui.vline(ctx, detX(), bodyY(), bodyH());
        return true;
    }

    protected int detailsInner() { return detW - 16; }
    protected int detailsX()     { return detX() + 8; }

    protected void detailsEmpty(DrawContext ctx, String message) {
        int at = bodyY() + 8;
        for (String line : Ui.wrap(tr, message, detailsInner(), 4)) {
            Draw.text(ctx, tr, line, detailsX(), at, Theme.TEXT_FAINT, false);
            at += 10;
        }
    }

    protected int detailsHead(DrawContext ctx, ItemStack icon, String name,
                              String subtitle, int subtitleColor) {
        int inner = detailsInner(), tx = detailsX(), at = bodyY() + 8;
        ctx.drawItem(icon, tx, at);
        List<String> lines = Ui.wrap(tr, name, inner - 22, 3);
        for (int i = 0; i < lines.size(); i++)
            Draw.text(ctx, tr, lines.get(i), tx + 22, at + 1 + i * 10, Theme.TEXT, false);
        at += Math.max(18, lines.size() * 10 + 2);
        Draw.textFit(ctx, tr, subtitle, tx, at, inner, subtitleColor, false);
        at += 12;
        Ui.hairline(ctx, tx, at, inner);
        return at + 6;
    }

    protected int detailsBottom() { return bodyY() + bodyH() - 6; }

    private void drawFooter(DrawContext ctx, int mouseX, int mouseY) {
        int fy = footY() + (FOOT_H - 16) / 2;
        Draw.textFit(ctx, tr, footerHint(), x + PAD, fy + 4, w - 2 * PAD - 146,
                Theme.TEXT_FAINT, false);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 72, fy, 72, 16, "Выбрать", Ui.ACCENT,
                canFinish());
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 134, fy, 56, 16, "Отмена", Ui.GHOST);
    }

    protected abstract String footerHint();

    protected abstract boolean canFinish();

    protected abstract void finish();

    protected abstract int indexAt(double mx, double my);

    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        if (Ui.hit(mx, my, x + w - PAD - 16, y + 6, 16, 16)) { closed = true; return true; }
        if (Ui.hit(mx, my, searchX(), y + 6, searchW(), 16)) {
            search.setFocused(true);
            if (!search.mouseClicked(click, doubled)) search.onClick(click, doubled);
            return true;
        }
        int rail = railIndexAt(mx, my);
        if (rail >= 0) { railChosen(rail); return true; }
        if (bodyClicked(click, doubled, mx, my)) return true;

        int fy = footY() + (FOOT_H - 16) / 2;
        if (Ui.hit(mx, my, x + w - PAD - 72, fy, 72, 16)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 134, fy, 56, 16)) { closed = true; return true; }
        return true;
    }

    protected abstract boolean bodyClicked(Click click, boolean doubled, int mx, int my);

    public boolean mouseDragged(Click click, double dx, double dy) {
        return search.mouseDragged(click, dx, dy);
    }

    public void mouseReleased() { }

    public boolean keyPressed(KeyInput in) {
        if (bodyKey(in)) return true;
        switch (in.key()) {
            case GLFW.GLFW_KEY_ESCAPE -> { closed = true; return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { finish(); return true; }
            default -> { }
        }
        return search.keyPressed(in);
    }

    protected abstract boolean bodyKey(KeyInput in);

    public boolean charTyped(CharInput in) {
        search.setFocused(true);
        return search.charTyped(in);
    }

    protected abstract void refresh(boolean resetScroll);
}
