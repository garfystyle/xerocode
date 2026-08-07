package com.xerocode.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

public final class ColorPick {
    public interface Done { void apply(int rgb); }

    private static final int WANT_W = 232, PAD = 10, ROW = 18, CAP = 11;
    private static final int WANT_SV_H = 84;
    private static final int HUE_H = 10;
    private static final int SWATCH = 20, SWATCH_GAP = 2;

    private final TextRenderer tr;
    private final String title;
    private final Done done;
    private final int screenW, screenH;
    private final TextFieldWidget hex;

    private float pickH, pickS, pickV;
    private int shown;
    private int dragging;
    private boolean closed, cancelled;

    private int x, y, h;
    private int svY, hueY, hexY, swatchY, footY;
    private final int W, SV_W;
    private final int SV_H;
    private final int rows;

    public ColorPick(TextRenderer tr, String title, int rgb, int anchorX, int anchorY,
                     int screenW, int screenH, Done done) {
        this.tr = tr;
        this.title = title;
        this.done = done;
        this.screenW = screenW;
        this.screenH = screenH;
        this.W = Ui.fitW(screenW, WANT_W);
        this.SV_W = W - PAD * 2;

        float[] hsv = McText.rgbHsv(rgb & 0xFFFFFF);
        pickH = hsv[0];
        pickS = hsv[1];
        pickV = hsv[2];

        this.hex = Ui.field(tr, 0, 0, 60, 10, "rrggbb");
        hex.setMaxLength(7);
        hex.setDrawsBackground(false);
        hex.setEditableColor(Draw.opaque(Theme.TEXT));
        hex.setText(hexOf(rgb));
        shown = rgb & 0xFFFFFF;
        hex.setChangedListener(this::hexTyped);

        int perRow = Math.max(1, (SV_W + SWATCH_GAP) / (SWATCH + SWATCH_GAP));
        this.rows = (McText.COLOURS.size() + perRow - 1) / perRow;

        int tail = 4 + HUE_H + 6 + ROW + 8 + CAP + rows * (SWATCH + SWATCH_GAP) + 8 + ROW + PAD;
        this.SV_H = Math.max(40, Math.min(WANT_SV_H, screenH - 8 - PAD - CAP - tail));

        svY = PAD + CAP;
        hueY = svY + SV_H + 4;
        hexY = hueY + HUE_H + 6;
        swatchY = hexY + ROW + 8 + CAP;
        footY = swatchY + rows * (SWATCH + SWATCH_GAP) + 8;
        h = footY + ROW + PAD;
        x = Ui.anchorX(screenW, anchorX, W);
        y = Ui.anchorY(screenH, anchorY, h);
    }

    public boolean isClosed() { return closed; }
    public boolean cancelled() { return cancelled; }

    public int rgb() { return McText.hsvRgb(pickH, pickS, pickV); }

    private static String hexOf(int rgb) {
        return "#" + String.format("%06x", rgb & 0xFFFFFF);
    }

    private void hexTyped(String text) {
        String normalised = McText.normaliseHex(text);
        if (normalised == null) return;
        float[] hsv = McText.rgbHsv(McText.hexRgb(normalised));
        pickH = hsv[0];
        pickS = hsv[1];
        pickV = hsv[2];
    }

    private void syncHex() {
        int now = rgb() & 0xFFFFFF;
        if (now == shown) return;
        shown = now;
        String text = hexOf(now);
        if (text.equalsIgnoreCase(hex.getText())) return;
        hex.setChangedListener(s -> { });
        hex.setText(text);
        hex.setChangedListener(this::hexTyped);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.card(ctx, x, y, W, h, Ui.R, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));
        Ui.caption(ctx, tr, title, x + PAD, y + PAD, W - PAD * 2);

        int sx = x + PAD, sy = y + svY;
        Ui.svSquare(ctx, sx, sy, SV_W, SV_H, pickH, pickS, pickV, 4);
        Ui.hueBar(ctx, sx, y + hueY, SV_W, HUE_H, pickH);

        syncHex();
        int fy = y + hexY;
        Ui.input(ctx, sx, fy, 74, ROW, hex.isFocused());
        hex.setX(sx + 6);
        hex.setY(fy + (ROW - 8) / 2);
        Ui.width(hex, 62);
        hex.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, hex);
        Ui.swatch(ctx, sx + 78, fy, SV_W - 78, ROW, rgb(), true, false);

        Ui.caption(ctx, tr, "ГОТОВЫЕ", sx, y + swatchY - CAP, SV_W);
        int i = 0;
        for (McText.Colour c : McText.COLOURS) {
            int[] p = swatchAt(i++);
            boolean hover = Ui.hit(mouseX, mouseY, p[0], p[1], SWATCH, SWATCH);
            Ui.swatch(ctx, p[0], p[1], SWATCH, SWATCH, c.rgb(), true, hover);
        }

        int by = y + footY;
        Ui.button(ctx, tr, mouseX, mouseY, sx, by, 70, ROW, "Отмена", Ui.GHOST, true);
        Ui.button(ctx, tr, mouseX, mouseY, sx + SV_W - 74, by, 74, ROW, "Готово", Ui.ACCENT, true);
    }

    private int[] swatchAt(int i) {
        int perRow = (SV_W + SWATCH_GAP) / (SWATCH + SWATCH_GAP);
        int row = i / perRow, col = i % perRow;
        return new int[]{x + PAD + col * (SWATCH + SWATCH_GAP),
                y + swatchY + row * (SWATCH + SWATCH_GAP)};
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + h;
    }

    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        hex.setFocused(false);
        if (!contains(mx, my)) {
            commit();
            return true;
        }
        int sx = x + PAD;
        if (Ui.hit(mx, my, sx, y + svY, SV_W, SV_H)) {
            dragging = 1;
            drag(mx, my);
            return true;
        }
        if (Ui.hit(mx, my, sx, y + hueY, SV_W, HUE_H)) {
            dragging = 2;
            drag(mx, my);
            return true;
        }
        if (Ui.hit(mx, my, sx, y + hexY, 74, ROW)) {
            hex.setFocused(true);
            if (!hex.mouseClicked(click, doubled)) hex.onClick(click, doubled);
            grab.take(hex);
            return true;
        }
        for (int i = 0; i < McText.COLOURS.size(); i++) {
            int[] p = swatchAt(i);
            if (!Ui.hit(mx, my, p[0], p[1], SWATCH, SWATCH)) continue;
            float[] hsv = McText.rgbHsv(McText.COLOURS.get(i).rgb());
            pickH = hsv[0];
            pickS = hsv[1];
            pickV = hsv[2];
            return true;
        }
        int by = y + footY;
        if (Ui.hit(mx, my, sx, by, 70, ROW)) { cancelled = true; closed = true; return true; }
        if (Ui.hit(mx, my, sx + SV_W - 74, by, 74, ROW)) { commit(); return true; }
        return true;
    }

    private final Ui.Grab grab = new Ui.Grab();

    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != 0) { drag(click.x(), click.y()); return true; }
        return grab.drag(click, dx, dy);
    }

    public void mouseReleased() {
        dragging = 0;
        grab.release();
    }

    private void drag(double mx, double my) {
        int sx = x + PAD;
        if (dragging == 1) {
            pickS = clamp01((mx - sx) / (SV_W - 1.0));
            pickV = 1f - clamp01((my - (y + svY)) / (SV_H - 1.0));
        } else if (dragging == 2) {
            pickH = clamp01((mx - sx) / (SV_W - 1.0));
        }
    }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { cancelled = true; closed = true; return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commit(); return true; }
        if (hex.isFocused()) return hex.keyPressed(input);
        return false;
    }

    public boolean charTyped(CharInput input) {
        return hex.isFocused() && hex.charTyped(input);
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        if (Ui.hit(mx, my, x + PAD, y + hueY, SV_W, HUE_H)) {
            pickH = clamp01(pickH + amount * 0.01);
            return true;
        }
        return true;
    }

    private void commit() {
        done.apply(rgb());
        closed = true;
    }
}
