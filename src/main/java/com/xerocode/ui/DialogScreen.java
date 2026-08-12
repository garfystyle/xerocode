package com.xerocode.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import org.lwjgl.glfw.GLFW;

abstract class DialogScreen extends Screen {
    protected static final int PAD = 14;
    protected static final int HEAD_H = 26;
    protected static final int ROW = 12;
    protected static final int BTN_H = 22;
    protected static final int BAR_H = 10;
    protected static final int GAP = 7;

    private final int wantW;

    protected DialogScreen(int panelW) {
        super(Text.literal("XeroCode"));
        this.wantW = panelW;
    }

    protected int panelW() { return Ui.fitW(width, wantW); }

    @Override
    public boolean shouldPause() { return false; }

    protected abstract int bodyH();

    protected abstract String title();

    protected int accent() { return Theme.ACCENT; }

    protected int panelH() { return HEAD_H + PAD + bodyH() + PAD; }
    protected int panelX() { return Ui.midX(width, panelW()); }
    protected int panelY() { return Ui.midY(height, panelH()); }
    protected int bodyX()  { return panelX() + PAD; }
    protected int bodyY()  { return panelY() + HEAD_H + PAD; }
    protected int bodyW()  { return panelW() - PAD * 2; }
    protected int buttonY() { return panelY() + panelH() - PAD - BTN_H; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        Ui.dim(ctx, width, height);
        ctx.createNewRootLayer();

        int x = panelX(), y = panelY();
        Ui.panel(ctx, x, y, panelW(), panelH());
        Ui.headerStrip(ctx, x, y, panelW(), HEAD_H, accent());
        Draw.textFit(ctx, textRenderer, title(), x + PAD, y + (HEAD_H - Ui.TEXT_H) / 2 + 1,
                panelW() - PAD * 2, Theme.TEXT, false);
        Ui.hairline(ctx, x + 1, y + HEAD_H, panelW() - 2);

        drawBody(ctx, mouseX, mouseY, bodyX(), bodyY(), bodyW());
    }

    protected abstract void drawBody(DrawContext ctx, int mouseX, int mouseY, int x, int y, int w);

    protected void barTrack(DrawContext ctx, int x, int y, int w) {
        Draw.round(ctx, x, y, w, BAR_H, BAR_H / 2, Draw.opaque(Ui.WELL));
    }

    protected void barFill(DrawContext ctx, int x, int y, int from, int to) {
        if (to <= from) return;
        Draw.pillGrad(ctx, x + 1 + from, y + 1, to - from, BAR_H - 2,
                Draw.opaque(Draw.shade(Theme.ACCENT, 0.15f)), Draw.opaque(Theme.ACCENT));
    }

    protected int primaryW(String label) { return Math.max(90, Ui.buttonW(textRenderer, label)); }

    protected int ghostW(String label) { return Math.max(70, Ui.buttonW(textRenderer, label)); }

    protected void buttons(DrawContext ctx, int mouseX, int mouseY, int x, int w,
                           String primary, String ghost) {
        int y = buttonY();
        if (ghost != null) {
            int gw = ghostW(ghost);
            Ui.button(ctx, textRenderer, mouseX, mouseY, ghostX(x, w, primary, ghost), y, gw,
                    BTN_H, ghost, Ui.GHOST);
        }
        if (primary != null) {
            int pw = primaryW(primary);
            Ui.button(ctx, textRenderer, mouseX, mouseY, x + w - pw, y, pw, BTN_H,
                    primary, Ui.ACCENT);
        }
    }

    private int ghostX(int x, int w, String primary, String ghost) {
        int gw = ghostW(ghost);
        return primary == null ? x + w - gw : x + w - gw - 6 - primaryW(primary);
    }

    protected int paragraph(DrawContext ctx, String text, int x, int y, int w, int rgb) {
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(text), w);
        for (int i = 0; i < lines.size(); i++)
            Draw.text(ctx, textRenderer, lines.get(i), x, y + ROW * i, rgb, false);
        return lines.size();
    }

    protected int paragraphRows(String text, int w) {
        return Math.max(1, textRenderer.wrapLines(Text.literal(text), w).size());
    }

    private int[] rowX(int x, int w, String... labels) {
        int[] at = new int[labels.length];
        int bx = x + w;
        for (int i = labels.length - 1; i >= 0; i--) {
            bx -= ghostW(labels[i]);
            at[i] = bx;
            bx -= 6;
        }
        return at;
    }

    protected void rowButtons(DrawContext ctx, int mouseX, int mouseY, int x, int w,
                              int mainKind, String... labels) {
        int[] kinds = new int[labels.length];
        for (int i = 0; i < kinds.length; i++) kinds[i] = Ui.GHOST;
        if (kinds.length > 0) kinds[kinds.length - 1] = mainKind;
        rowButtons(ctx, mouseX, mouseY, x, w, kinds, labels);
    }

    protected void rowButtons(DrawContext ctx, int mouseX, int mouseY, int x, int w,
                              int[] kinds, String... labels) {
        int[] at = rowX(x, w, labels);
        int y = buttonY();
        for (int i = 0; i < labels.length; i++)
            Ui.button(ctx, textRenderer, mouseX, mouseY, at[i], y, ghostW(labels[i]), BTN_H,
                    labels[i], i < kinds.length ? kinds[i] : Ui.GHOST);
    }

    protected int hitRow(double mx, double my, int x, int w, String... labels) {
        int[] at = rowX(x, w, labels);
        int y = buttonY();
        for (int i = 0; i < labels.length; i++)
            if (Ui.hit(mx, my, at[i], y, ghostW(labels[i]), BTN_H)) return i;
        return -1;
    }

    protected boolean hitPrimary(double mx, double my, String primary) {
        int pw = primaryW(primary);
        return Ui.hit(mx, my, bodyX() + bodyW() - pw, buttonY(), pw, BTN_H);
    }

    protected boolean hitGhost(double mx, double my, String primary, String ghost) {
        return Ui.hit(mx, my, ghostX(bodyX(), bodyW(), primary, ghost), buttonY(),
                ghostW(ghost), BTN_H);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && onEnter()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) { onEscape(); return true; }
        return super.keyPressed(input);
    }

    protected boolean onEnter() { return false; }

    protected void onEscape() { close(); }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return onClick(click.x(), click.y()) || super.mouseClicked(click, doubled);
    }

    protected abstract boolean onClick(double mx, double my);
}
