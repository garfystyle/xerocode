package com.xerocode.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;

public final class CodeStage {
    private final double min, max, fitMax;

    private int x, y, w, h;
    private double zoom = 1, panX, panY;
    private boolean panning;

    public CodeStage(double min, double max, double fitMax) {
        this.min = min;
        this.max = max;
        this.fitMax = fitMax;
    }

    public String zoomText() { return Math.round(zoom * 100) + "%"; }

    public boolean atMin() { return zoom <= min; }

    public boolean atMax() { return zoom >= max; }

    public boolean panning() { return panning; }

    public void grab() { panning = true; }

    public void release() { panning = false; }

    private boolean fitted;

    public void place(int px, int py, int pw, int ph) {
        if (px == x && py == y && pw == w && ph == h) return;
        x = px;
        y = py;
        w = pw;
        h = ph;
        fitted = false;
    }

    public boolean needsFit() { return !fitted; }

    public void fit(Layout layout, int pad) {
        if (layout == null || layout.boxes.isEmpty() || w <= 0) return;
        int[] b = layout.bounds(2);
        double room = Math.max(40, w - pad * 2), tall = Math.max(40, h - pad * 2);
        zoom = Math.max(min, Math.min(fitMax, Math.min(room / b[2], tall / b[3])));
        panX = x + (w - b[2] * zoom) / 2 - b[0] * zoom;
        panY = y + Math.max(pad, (h - b[3] * zoom) / 2) - b[1] * zoom;
        fitted = true;
    }

    public boolean over(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void drag(double dx, double dy) {
        panX += dx;
        panY += dy;
    }

    public void wheel(double aroundX, double aroundY, double amount) {
        zoomTo(zoom * (amount > 0 ? 1.12 : 1 / 1.12), aroundX, aroundY);
    }

    public void step(double factor) {
        zoomTo(zoom * factor, x + w / 2.0, y + h / 2.0);
    }

    private void zoomTo(double target, double aroundX, double aroundY) {
        double next = Math.max(min, Math.min(max, target));
        double cx = (aroundX - panX) / zoom, cy = (aroundY - panY) / zoom;
        zoom = next;
        panX = aroundX - cx * zoom;
        panY = aroundY - cy * zoom;
    }

    public void keepOnScreen(Layout layout, double keep) {
        if (layout == null || layout.boxes.isEmpty()) return;
        int[] b = layout.bounds(2);
        double left = panX + b[0] * zoom, top = panY + b[1] * zoom;
        double cw = b[2] * zoom, ch = b[3] * zoom;
        double nl = Math.max(x + keep - cw, Math.min(x + w - keep, left));
        double nt = Math.max(y + keep - ch, Math.min(y + h - keep, top));
        panX += nl - left;
        panY += nt - top;
    }

    public Layout.Box boxAt(Layout layout, double mx, double my) {
        if (layout == null || !over(mx, my)) return null;
        return layout.at((mx - panX) / zoom, (my - panY) / zoom);
    }

    public void draw(DrawContext ctx, TextRenderer tr, Layout layout, BlockView.Look look) {
        if (layout == null) return;
        ScreenRect area = new ScreenRect(x, y, w, h);
        ctx.enableScissor(x, y, x + w, y + h);
        SmoothText.clip(area);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) panX, (float) panY);
        m.scale((float) zoom, (float) zoom);
        BlockView.paint(ctx, tr, layout, area, look);
        m.popMatrix();
        SmoothText.clip(null);
        ctx.disableScissor();
    }
}
