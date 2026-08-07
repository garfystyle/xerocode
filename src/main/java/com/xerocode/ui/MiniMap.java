package com.xerocode.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;

import java.util.List;

public final class MiniMap {
    private static final int MAX_W = 152, MAX_H = 108, MIN_W = 56, MIN_H = 40;
    private static final int EDGE = 10, PAD = 6, MAX_R = 6;
    private static final int CELL_ALPHA = 0xC0, VEIL_ALPHA = 0x66;

    private int x, y, w, h, offX, offY;
    private double scale, minX, minY;
    private int stamp = Integer.MIN_VALUE, place = Integer.MIN_VALUE;
    private int[] cells = new int[0];
    private int count;
    private boolean shown;

    public boolean shown() { return shown; }

    public void hide() {
        shown = false;
        place = Integer.MIN_VALUE;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return w; }
    public int height() { return h; }

    public void frame(Layout layout, int layoutStamp, int left, int top, int right, int bottom) {
        int key = ((left * 31 + top) * 31 + right) * 31 + bottom;
        if (layoutStamp == stamp && key == place) return;
        stamp = layoutStamp;
        place = key;
        shown = false;
        if (layout == null || layout.boxes.isEmpty()) return;

        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (Layout.Box b : layout.boxes) {
            x0 = Math.min(x0, b.x);
            y0 = Math.min(y0, b.y);
            x1 = Math.max(x1, b.x + b.w);
            y1 = Math.max(y1, b.bottom());
        }
        int maxW = Math.max(MIN_W, Math.min(MAX_W, (right - left) / 4));
        int maxH = Math.max(MIN_H, Math.min(MAX_H, (bottom - top) / 3));
        double cw = Math.max(1, x1 - x0), ch = Math.max(1, y1 - y0);
        scale = Math.min((maxW - PAD * 2) / cw, (maxH - PAD * 2) / ch);
        minX = x0;
        minY = y0;
        int needW = (int) Math.ceil(cw * scale) + PAD * 2;
        int needH = (int) Math.ceil(ch * scale) + PAD * 2;
        w = Math.max(MIN_W, Math.min(maxW, needW));
        h = Math.max(MIN_H, Math.min(maxH, needH));
        offX = Math.max(0, (w - needW) / 2);
        offY = Math.max(0, (h - needH) / 2);
        x = right - EDGE - w;
        y = bottom - EDGE - h;

        if (cells.length < layout.boxes.size() * 5) cells = new int[layout.boxes.size() * 5];
        count = 0;
        for (Layout.Box b : layout.boxes) {
            int at = count * 5;
            cells[at] = mapX(b.x);
            cells[at + 1] = mapY(b.y);
            cells[at + 2] = Math.max(1, (int) Math.round(b.w * scale));
            cells[at + 3] = Math.max(1, (int) Math.round(b.totalH * scale));
            cells[at + 4] = b.node.action.category == null ? 0x8A93A6 : b.node.action.category.color;
            count++;
        }
        shown = true;
    }

    private int mapX(double canvasX) {
        return x + PAD + offX + (int) Math.round((canvasX - minX) * scale);
    }

    private int mapY(double canvasY) {
        return y + PAD + offY + (int) Math.round((canvasY - minY) * scale);
    }

    private static int radius() { return Math.min(Ui.R_SM, MAX_R); }

    public double canvasX(double mx) { return minX + (mx - x - PAD - offX) / scale; }
    public double canvasY(double my) { return minY + (my - y - PAD - offY) / scale; }

    public boolean hit(double mx, double my) {
        return shown && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void draw(DrawContext ctx, double vx0, double vy0, double vx1, double vy1,
                     boolean hover) {
        if (!shown) return;
        int r = radius();
        int border = Draw.argb(hover ? 0xFF : 0xA0, hover ? Theme.ACCENT : Ui.BORDER);
        Draw.card(ctx, x, y, w, h, r, Draw.argb(hover ? 0xF2 : 0xD8, Ui.PANEL), border);
        ScreenRect area = new ScreenRect(x + 1, y + 1, w - 2, h - 2);
        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        Draw.batch(Batch.open(ctx, area, area, Math.max(256, count + 2 * h)));
        for (int i = 0, at = 0; i < count; i++, at += 5) {
            int argb = Draw.argb(CELL_ALPHA, cells[at + 4]);
            Draw.rect(ctx, cells[at], cells[at + 1], cells[at + 2], cells[at + 3], argb);
        }

        int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;
        int rx0 = Math.max(ix, mapX(vx0)), ry0 = Math.max(iy, mapY(vy0));
        int rx1 = Math.min(ix + iw, mapX(vx1)), ry1 = Math.min(iy + ih, mapY(vy1));
        boolean seen = rx1 > rx0 && ry1 > ry0;
        Draw.roundVeil(ctx, ix, iy, iw, ih, Math.max(0, r - 1),
                seen ? rx0 : 0, seen ? ry0 : 0, seen ? rx1 : 0, seen ? ry1 : 0,
                Draw.argb(VEIL_ALPHA, Theme.CANVAS));
        if (seen) {
            int edge = Draw.argb(0xF0, Theme.ACCENT);
            Draw.rect(ctx, rx0, ry0, rx1 - rx0, 1, edge);
            Draw.rect(ctx, rx0, ry1 - 1, rx1 - rx0, 1, edge);
            Draw.rect(ctx, rx0, ry0, 1, ry1 - ry0, edge);
            Draw.rect(ctx, rx1 - 1, ry0, 1, ry1 - ry0, edge);
        }
        Draw.batch(null);
        ctx.disableScissor();
        Draw.roundOutline(ctx, x, y, w, h, r, border);
    }

    public void marks(DrawContext ctx, List<Layout.Box> boxes, int rgb) {
        if (!shown || boxes.isEmpty()) return;
        ScreenRect area = new ScreenRect(x + 1, y + 1, w - 2, h - 2);
        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        Draw.batch(Batch.open(ctx, area, area, Math.max(64, boxes.size() * 2)));
        for (Layout.Box b : boxes) {
            int mx = mapX(b.x), my = mapY(b.y);
            int mw = Math.max(2, (int) Math.round(b.w * scale));
            int mh = Math.max(2, (int) Math.round(b.totalH * scale));
            Draw.rect(ctx, mx, my, mw, mh, Draw.argb(0xFF, rgb));
        }
        Draw.batch(null);
        ctx.disableScissor();
    }
}
