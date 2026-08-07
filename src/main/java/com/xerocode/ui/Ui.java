package com.xerocode.ui;

import com.xerocode.Settings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class Ui {
    public static int PANEL   = 0x171A21;
    public static int HEAD    = 0x1C212B;
    public static int RAIL    = 0x13161D;
    public static int WELL    = 0x11141A;
    public static int INPUT   = 0x0E1116;
    public static int LINE    = 0x272C37;
    public static int LINE_IN = 0x333B49;
    public static int BORDER  = 0x3D4757;

    public static int BTN       = 0x1E232D;
    public static int BTN_HOVER = 0x2C3441;
    public static int BTN_ON    = 0x2F4E76;
    public static int PRIMARY   = 0x38506E;
    public static int PRIMARY_H = 0x4E8FE0;
    public static int DANGER_BG = 0x4A2A32;

    public static final int GHOST = 0, ACCENT = 1, DANGER = 2, ACTIVE = 3;

    public static int R = 6;
    public static int R_SM = 4;
    public static final int TEXT_H = 8;
    public static final int TEXT_MAX = 32000;

    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static int margin(int screen) { return screen < 420 ? 5 : 12; }

    public static int fitW(int screenW, int want) {
        return Math.max(60, Math.min(want, screenW - margin(screenW) * 2));
    }

    public static int fitH(int screenH, int want) {
        return Math.max(60, Math.min(want, screenH - margin(screenH) * 2));
    }

    public static int midX(int screenW, int w) {
        return Math.max(margin(screenW), (screenW - w) / 2);
    }

    public static int midY(int screenH, int h) {
        return Math.max(margin(screenH), (screenH - h) / 2);
    }

    public static int anchorX(int screenW, int wanted, int w) {
        return Math.max(4, Math.min(wanted, screenW - w - 4));
    }

    public static int anchorY(int screenH, int wanted, int h) {
        return Math.max(4, Math.min(wanted, screenH - h - 4));
    }

    private static int scrolled(int scroll, int contentH, int viewH, double amount) {
        return Math.max(0, Math.min(Math.max(0, contentH - viewH),
                scroll - (int) Math.round(amount * 18)));
    }

    public static void dim(DrawContext ctx, int screenW, int screenH) {
        Draw.rect(ctx, 0, 0, screenW, screenH, Theme.SCRIM);
    }

    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        Draw.shadow(ctx, x, y, w, h, R);
        Draw.card(ctx, x, y, w, h, R, Draw.opaque(PANEL), Draw.opaque(BORDER));
        Draw.rect(ctx, x + 1 + R - 1, y + 1, w - 2 * R, 1, sheen());
    }

    static int sheen() {
        return Theme.LIGHT ? Draw.argb(0x14, 0xFFFFFF) : Draw.argb(0x22, 0xFFFFFF);
    }

    public static void headerStrip(DrawContext ctx, int x, int y, int w, int h, int accent) {
        Draw.roundRectGrad(ctx, x + 1, y + 1, w - 2, h - 1, R - 1, R - 1, 0, 0,
                Draw.opaque(Draw.mix(HEAD, accent, 0.10f)), Draw.opaque(HEAD));
        Draw.rect(ctx, x + 1 + R - 1, y + 1, w - 2 * R, 1, sheen());
    }

    public static void well(DrawContext ctx, int x, int y, int w, int h) {
        Draw.card(ctx, x, y, w, h, R_SM, Draw.opaque(WELL), Draw.opaque(LINE_IN));
    }

    public static void hairline(DrawContext ctx, int x, int y, int w) {
        Draw.rect(ctx, x, y, w, 1, Draw.opaque(LINE));
    }

    public static void vline(DrawContext ctx, int x, int y, int h) {
        Draw.rect(ctx, x, y, 1, h, Draw.opaque(LINE));
    }

    public static void caption(DrawContext ctx, TextRenderer tr, String s, int x, int y, int w) {
        Draw.textFit(ctx, tr, s, x, y, w, Theme.TEXT_FAINT, false);
    }

    public static void caption(DrawContext ctx, TextRenderer tr, String s, int x, int y,
                               int w, String note) {
        Draw.textFit(ctx, tr, s, x, y, w - tr.getWidth(note) - 6, Theme.TEXT_FAINT, false);
        if (!note.isEmpty()) Draw.textRight(ctx, tr, note, x + w, y, Theme.TEXT_FAINT, false);
    }

    public static TextFieldWidget field(TextRenderer tr, int x, int y, int w, int h, String hint) {
        TextFieldWidget f = new TextFieldWidget(tr, x, y, w, h, Text.literal(hint));
        f.setDrawsBackground(false);
        f.setTextShadow(false);
        f.setEditableColor(Draw.opaque(Theme.TEXT));
        return f;
    }

    public static TextFieldWidget field(TextRenderer tr, String text, String hint, int maxLength) {
        TextFieldWidget f = field(tr, 0, 0, 40, 12, hint);
        f.setMaxLength(maxLength);
        f.setText(text);
        f.setCursorToStart(false);
        return f;
    }

    public static void width(TextFieldWidget f, int w) {
        if (f == null || f.getWidth() == w) return;
        f.setWidth(w);
        f.setCursor(f.getCursor(), false);
    }

    public static void placeholder(DrawContext ctx, TextRenderer tr, TextFieldWidget f) {
        if (f == null || !f.getText().isEmpty() || f.isFocused()) return;
        String hint = f.getMessage().getString();
        if (hint.isEmpty()) return;
        Draw.textFit(ctx, tr, hint, f.getX(), f.getY(), f.getWidth() - 2, Theme.TEXT_FAINT, false);
    }

    public static void input(DrawContext ctx, int x, int y, int w, int h, boolean focused) {
        Audit.role("input");
        Draw.card(ctx, x, y, w, h, R_SM, Draw.opaque(INPUT),
                Draw.opaque(focused ? Theme.ACCENT : LINE_IN));
        Audit.clearRole();
    }

    private static int face(int kind, boolean hov, boolean enabled) {
        if (!enabled) return BTN;
        return switch (kind) {
            case ACCENT -> hov ? PRIMARY_H : PRIMARY;
            case DANGER -> hov ? DANGER_BG : BTN;
            case ACTIVE -> hov ? Draw.shade(BTN_ON, 0.10f) : BTN_ON;
            default -> hov ? BTN_HOVER : BTN;
        };
    }

    private static int ink(int kind, boolean hov, boolean enabled) {
        if (!enabled) return Theme.TEXT_FAINT;
        return switch (kind) {
            case ACCENT, ACTIVE -> Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT;
            case DANGER -> hov ? Theme.DANGER : Theme.TEXT_DIM;
            default -> hov ? Theme.TEXT : Theme.TEXT_DIM;
        };
    }

    private static void controlFace(DrawContext ctx, int x, int y, int w, int h,
                                    int fill, boolean hov, boolean strong) {
        int r = Settings.radius(h);
        if (!Settings.outlined()) {
            Draw.round(ctx, x, y, w, h, r, Draw.opaque(fill));
            return;
        }
        Draw.round(ctx, x, y, w, h, r, Draw.argb(hov ? 0x66 : 0x28, fill));
        Draw.roundOutline(ctx, x, y, w, h, r,
                Draw.opaque(Draw.shade(fill, strong || hov ? 0.62f : 0.42f)));
    }

    public static boolean button(DrawContext ctx, TextRenderer tr, int mx, int my,
                                 int x, int y, int w, int h, String label, int kind,
                                 boolean enabled) {
        boolean hov = enabled && hit(mx, my, x, y, w, h);
        Audit.role("button");
        controlFace(ctx, x, y, w, h, face(kind, hov, enabled), hov, kind != GHOST);
        Draw.textCenter(ctx, tr, label, x, y + (h - TEXT_H) / 2, w, w - 6,
                ink(kind, hov, enabled), false);
        Audit.clearRole();
        return hov;
    }

    public static boolean button(DrawContext ctx, TextRenderer tr, int mx, int my,
                                 int x, int y, int w, int h, String label, int kind) {
        return button(ctx, tr, mx, my, x, y, w, h, label, kind, true);
    }

    public static boolean glyphButton(DrawContext ctx, TextRenderer tr, int mx, int my,
                                      int x, int y, int w, int h, String[] glyph, String label,
                                      int kind, boolean enabled) {
        boolean hov = enabled && hit(mx, my, x, y, w, h);
        Audit.role("button");
        controlFace(ctx, x, y, w, h, face(kind, hov, enabled), hov, kind != GHOST);
        int gw = Draw.glyphW(glyph);
        String text = Draw.fit(tr, label, w - 12 - gw);
        int lw = tr.getWidth(text);
        int at = x + (w - gw - 3 - lw) / 2;
        int color = ink(kind, hov, enabled);
        Draw.glyph(ctx, glyph, at, y + (h - Draw.glyphH(glyph)) / 2, color);
        Draw.text(ctx, tr, text, at + gw + 3, y + (h - TEXT_H) / 2, color, false);
        Audit.clearRole();
        return hov;
    }

    public static boolean iconButton(DrawContext ctx, int mx, int my, int x, int y, int size,
                                     String[] glyph, int kind, boolean enabled) {
        boolean hov = enabled && hit(mx, my, x, y, size, size);
        Audit.role("icon");
        controlFace(ctx, x, y, size, size, face(kind, hov, enabled), hov, kind != GHOST);
        Draw.glyph(ctx, glyph, x + (size - Draw.glyphW(glyph)) / 2,
                y + (size - Draw.glyphH(glyph)) / 2, ink(kind, hov, enabled));
        Audit.clearRole();
        return hov;
    }

    public static boolean closeButton(DrawContext ctx, int mx, int my, int x, int y, int size) {
        boolean hov = hit(mx, my, x, y, size, size);
        Audit.role("icon");
        controlFace(ctx, x, y, size, size, hov ? DANGER_BG : Theme.SURFACE, hov, hov);
        Draw.glyph(ctx, Draw.CROSS, x + (size - Draw.glyphW(Draw.CROSS)) / 2,
                y + (size - Draw.glyphH(Draw.CROSS)) / 2, hov ? Theme.DANGER : Theme.TEXT_DIM);
        Audit.clearRole();
        return hov;
    }

    public static void chip(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
                            String label, boolean on, boolean hov, int accent) {
        Audit.role("chip");
        controlFace(ctx, x, y, w, h,
                on ? Draw.mix(BTN_ON, accent, 0.25f) : (hov ? BTN_HOVER : BTN), hov, on);
        if (on) Draw.rect(ctx, x + 4, y + h - 2, w - 8, 1, Draw.opaque(accent));
        Draw.textCenter(ctx, tr, label, x, y + (h - TEXT_H) / 2, w, w - 6,
                on ? (Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT)
                        : hov ? Theme.TEXT : Theme.TEXT_DIM, false);
        Audit.clearRole();
    }

    public static int segmented(DrawContext ctx, TextRenderer tr, int mx, int my,
                                int x, int y, int w, int h, List<String> labels, int active,
                                int accent) {
        Draw.round(ctx, x, y, w, h, Settings.radius(h), Draw.opaque(WELL));
        int n = Math.max(1, labels.size());
        int hovered = -1;
        int r = Math.max(0, Settings.radius(h - 2));
        for (int i = 0; i < labels.size(); i++) {
            int cx = x + 1 + (w - 2) * i / n;
            int cw = x + 1 + (w - 2) * (i + 1) / n - cx;
            boolean hov = hit(mx, my, cx, y, cw, h);
            if (hov) hovered = i;
            if (i == active) {
                Draw.round(ctx, cx, y + 1, cw, h - 2, r,
                        Draw.opaque(Draw.mix(BTN_ON, accent, 0.25f)));
                Draw.rect(ctx, cx + 3, y + h - 3, cw - 6, 1, Draw.opaque(accent));
            } else if (hov) {
                Draw.round(ctx, cx, y + 1, cw, h - 2, r, Draw.opaque(BTN_HOVER));
            }
            Draw.textCenter(ctx, tr, labels.get(i), cx, y + (h - TEXT_H) / 2, cw, cw - 3,
                    i == active ? (Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT)
                            : hov ? Theme.TEXT : Theme.TEXT_DIM, false);
        }
        return hovered;
    }

    public static int segmentAt(double mx, double my, int x, int y, int w, int h, int n) {
        if (n <= 0 || !hit(mx, my, x, y, w, h)) return -1;
        for (int i = 0; i < n; i++) {
            int cx = x + 1 + (w - 2) * i / n;
            int cw = x + 1 + (w - 2) * (i + 1) / n - cx;
            if (mx >= cx && mx < cx + cw) return i;
        }
        return n - 1;
    }

    public static boolean toggle(DrawContext ctx, TextRenderer tr, int mx, int my,
                                 int x, int y, int w, int h, String label, boolean on) {
        boolean hov = hit(mx, my, x, y, w, h);
        controlFace(ctx, x, y, w, h, hov ? BTN_HOVER : BTN, hov, false);
        int kw = 16, kh = 8, kx = x + w - kw - 5, ky = y + (h - kh) / 2;
        Draw.pill(ctx, kx, ky, kw, kh, Draw.opaque(on ? Theme.ACCENT : 0x39414F));
        Draw.round(ctx, on ? kx + kw - 7 : kx + 1, ky + 1, 6, kh - 2, 3,
                Draw.opaque(on ? 0xFFFFFF : 0x8A93A6));
        Draw.textFit(ctx, tr, label, x + 7, y + (h - TEXT_H) / 2, w - kw - 16,
                on ? Theme.TEXT : Theme.TEXT_DIM, false);
        return hov;
    }

    public static final class Pane {
        public int scroll, contentH;
        private int top, bottom;

        public void fit(int top, int bottom, int contentH) {
            this.top = top;
            this.bottom = bottom;
            this.contentH = contentH;
            clamp();
        }

        public int top()    { return top; }
        public int bottom() { return bottom; }
        public int viewH()  { return bottom - top; }
        public int max()    { return Math.max(0, contentH - bottom); }

        public void clamp() { scroll = Math.max(0, Math.min(max(), scroll)); }

        public int at(int rel) { return rel - scroll; }

        public boolean inBody(double my, int panelY) {
            return my >= panelY + top && my < panelY + bottom;
        }

        public void wheel(double amount) {
            scroll = scrolled(scroll, contentH, bottom, amount);
        }

        public void drawBar(DrawContext ctx, Bar bar, int barX, int panelY, double mx, double my) {
            bar.draw(ctx, barX, panelY + top + 2, viewH() - 4, contentH - top,
                    viewH() - 4, scroll, mx, my);
        }
    }

    public static final class Grab {
        private net.minecraft.client.gui.widget.ClickableWidget held;

        public void take(net.minecraft.client.gui.widget.ClickableWidget field) {
            held = field;
        }

        public boolean drag(Click click, double dx, double dy) {
            return held != null && held.mouseDragged(click, dx, dy);
        }

        public boolean has() { return held != null; }

        public void release() { held = null; }
    }

    private static final int BAR_GRAB = 4, BAR_W = 3, THUMB_MIN = 14;

    public static final class Bar {
        private int x, y, trackH, thumbH, thumbY, span;
        private boolean dragging;
        private int grab;

        public void draw(DrawContext ctx, int x, int y, int trackH,
                         int contentH, int viewH, int scroll, double mx, double my) {
            this.x = x;
            this.y = y;
            this.trackH = trackH;
            this.span = Math.max(0, contentH - viewH);
            if (contentH <= viewH || trackH <= 0) { thumbH = 0; return; }
            thumbH = Math.min(trackH, Math.max(THUMB_MIN, trackH * viewH / contentH));
            thumbY = y + (trackH - thumbH) * Math.max(0, Math.min(span, scroll)) / Math.max(1, span);
            boolean hot = dragging || over(mx, my);
            Draw.rect(ctx, x, y, BAR_W, trackH, Draw.argb(0x30, 0x000000));
            Draw.round(ctx, x, thumbY, BAR_W, thumbH, 1,
                    Draw.argb(hot ? 0xFF : 0xB4, hot ? 0x8D9AB4 : 0x5A6478));
        }

        private boolean over(double mx, double my) {
            return thumbH > 0 && mx >= x - BAR_GRAB && mx < x + BAR_W + BAR_GRAB
                    && my >= y && my < y + trackH;
        }

        public boolean press(double mx, double my) {
            if (!over(mx, my)) return false;
            dragging = true;
            grab = my >= thumbY && my < thumbY + thumbH ? (int) (my - thumbY) : thumbH / 2;
            return true;
        }

        private int at(double my) {
            int free = trackH - thumbH;
            if (free <= 0) return 0;
            int want = Math.max(0, Math.min(free, (int) Math.round(my - grab) - y));
            return (int) Math.round(want * (double) span / free);
        }

        public int follow(double my, int step, int max) {
            return Math.max(0, Math.min(max, at(my) / Math.max(1, step)));
        }

        public boolean grabbed(double mx, double my, int step, int max, IntConsumer to) {
            if (!press(mx, my)) return false;
            to.accept(follow(my, step, max));
            return true;
        }

        public boolean dragged(double my, int step, int max, IntConsumer to) {
            if (!dragging) return false;
            to.accept(follow(my, step, max));
            return true;
        }

        public boolean dragging() { return dragging; }

        public void release() { dragging = false; }
    }

    public static void svSquare(DrawContext ctx, int x, int y, int w, int h,
                                float hue, float s, float v, int ring) {
        for (int i = 0; i < w; i++)
            ctx.fillGradient(x + i, y, x + i + 1, y + h,
                    Draw.opaque(McText.hsvRgb(hue, i / (float) (w - 1), 1f)),
                    Draw.opaque(0x000000));
        Draw.roundOutline(ctx, x - 1, y - 1, w + 2, h + 2, 1, Draw.opaque(LINE_IN));
        int kx = x + (int) (s * (w - 1)), ky = y + (int) ((1 - v) * (h - 1));
        Draw.roundOutline(ctx, kx - ring, ky - ring, ring * 2 + 1, ring * 2 + 1, ring,
                Draw.opaque(0x000000));
        Draw.roundOutline(ctx, kx - ring + 1, ky - ring + 1, ring * 2 - 1, ring * 2 - 1, ring - 1,
                Draw.opaque(0xFFFFFF));
    }

    public static void hueBar(DrawContext ctx, int x, int y, int w, int h, float hue) {
        for (int i = 0; i < w; i++)
            Draw.rect(ctx, x + i, y, 1, h,
                    Draw.opaque(McText.hsvRgb(i / (float) (w - 1), 1f, 1f)));
        Draw.roundOutline(ctx, x - 1, y - 1, w + 2, h + 2, 1, Draw.opaque(LINE_IN));
        int hx = x + (int) (hue * (w - 1));
        Draw.rect(ctx, hx - 1, y - 2, 3, h + 4, Draw.opaque(0x000000));
        Draw.rect(ctx, hx, y - 1, 1, h + 2, Draw.opaque(0xFFFFFF));
    }

    public static final int SL_HUE = 0, SL_SAT = 1, SL_VAL = 2;

    public static void hsvSlider(DrawContext ctx, int x, int y, int w, int h, int kind,
                                 float hue, float s, float v, boolean hot) {
        if (w <= 1 || h <= 0) return;
        for (int i = 0; i < w; i++) {
            float t = i / (float) (w - 1);
            int rgb = switch (kind) {
                case SL_HUE -> McText.hsvRgb(t, 1f, 1f);
                case SL_SAT -> McText.hsvRgb(hue, t, Math.max(0.15f, v));
                default -> McText.hsvRgb(hue, s, t);
            };
            Draw.rect(ctx, x + i, y, 1, h, Draw.opaque(rgb));
        }
        Draw.roundOutline(ctx, x - 1, y - 1, w + 2, h + 2, 1, Draw.opaque(LINE_IN));
        float t = kind == SL_HUE ? hue : kind == SL_SAT ? s : v;
        int kx = x + Math.round(t * (w - 1));
        Draw.rect(ctx, kx - 2, y - 2, 5, h + 4, Draw.opaque(0x000000));
        Draw.rect(ctx, kx - 1, y - 1, 3, h + 2, Draw.opaque(hot ? Theme.ACCENT : 0xFFFFFF));
    }

    public static void swatch(DrawContext ctx, int x, int y, int w, int h, int rgb,
                             boolean enabled, boolean hov) {
        Draw.round(ctx, x, y, w, h, 3, Draw.opaque(Draw.shade(rgb, -0.55f)));
        Draw.round(ctx, x + 1, y + 1, w - 2, h - 2, 2,
                enabled ? Draw.opaque(rgb) : Draw.argb(0x55, rgb));
        if (hov) Draw.roundOutline(ctx, x, y, w, h, 3, Draw.opaque(0xFFFFFF));
    }

    public record Grid(int x, int y, int cols, int cellW, int cellH, int gap) {
        public int cellX(int i) { return x + (i % cols) * (cellW + gap); }
        public int cellY(int i) { return y + (i / cols) * (cellH + gap); }
        public int rows(int count) { return (count + cols - 1) / cols; }
        public int height(int count) { return rows(count) * (cellH + gap) - gap; }
        public int indexAt(double mx, double my, int count) {
            for (int i = 0; i < count; i++)
                if (hit(mx, my, cellX(i), cellY(i), cellW, cellH)) return i;
            return -1;
        }
    }

    public static final class Cluster {
        private final int[] xs, ws;

        public Cluster(int x, int w, int gap, int... widths) {
            xs = new int[widths.length];
            ws = widths.clone();
            int total = gap * Math.max(0, widths.length - 1);
            for (int cw : widths) total += cw;
            if (total > w && widths.length > 0) {
                int share = (w - gap * (widths.length - 1)) / widths.length;
                for (int i = 0; i < ws.length; i++) ws[i] = share;
                total = w;
            }
            int at = x + Math.max(0, (w - total) / 2);
            for (int i = 0; i < ws.length; i++) {
                xs[i] = at;
                at += ws[i] + gap;
            }
        }

        public int x(int i) { return xs[i]; }
        public int w(int i) { return ws[i]; }

        public boolean hit(int i, double mx, double my, int y, int h) {
            return Ui.hit(mx, my, xs[i], y, ws[i], h);
        }
    }

    public static int buttonW(TextRenderer tr, String label) { return tr.getWidth(label) + 22; }

    public static int buttonW(TextRenderer tr, String[] glyph, String label) {
        return Draw.glyphW(glyph) + 3 + tr.getWidth(label) + 22;
    }

    public static final class Chips {
        public record Cell(int index, String label, int dx, int dy, int w) {}

        public final List<Cell> cells = new ArrayList<>();
        public final int rowH, rows, gap;

        public Chips(TextRenderer tr, List<String> labels, int full, int rowH, int gap) {
            this(tr, labels, full, rowH, gap, false);
        }

        public Chips(TextRenderer tr, List<String> labels, int full, int rowH, int gap,
                     boolean centred) {
            this.rowH = rowH;
            this.gap = gap;
            int cx = 0, cy = 0, lines = 1;
            for (int i = 0; i < labels.size(); i++) {
                String label = labels.get(i);
                int w = Math.min(full, tr.getWidth(label) + 14);
                if (cx > 0 && cx + w > full) { cx = 0; cy += rowH + gap; lines++; }
                cells.add(new Cell(i, label, cx, cy, w));
                cx += w + gap;
            }
            this.rows = lines;
            if (centred) centre(full);
        }

        private void centre(int full) {
            int from = 0;
            while (from < cells.size()) {
                int to = from;
                while (to + 1 < cells.size() && cells.get(to + 1).dy() == cells.get(from).dy()) to++;
                Cell last = cells.get(to);
                int used = last.dx() + last.w();
                int shift = Math.max(0, (full - used) / 2);
                if (shift > 0)
                    for (int i = from; i <= to; i++) {
                        Cell c = cells.get(i);
                        cells.set(i, new Cell(c.index(), c.label(), c.dx() + shift, c.dy(), c.w()));
                    }
                from = to + 1;
            }
        }

        public int height() { return rows * rowH + (rows - 1) * gap; }

        public int width() {
            int w = 0;
            for (Cell c : cells) w = Math.max(w, c.dx() + c.w());
            return w;
        }

        public int indexAt(double mx, double my, int ox, int oy) {
            for (Cell c : cells)
                if (hit(mx, my, ox + c.dx(), oy + c.dy(), c.w(), rowH)) return c.index();
            return -1;
        }

        public void render(DrawContext ctx, TextRenderer tr, int mx, int my, int ox, int oy,
                           int active, int accent) {
            for (Cell c : cells) {
                int cx = ox + c.dx(), cy = oy + c.dy();
                chip(ctx, tr, cx, cy, c.w(), rowH, c.label(), c.index() == active,
                        hit(mx, my, cx, cy, c.w(), rowH), accent);
            }
        }

        public void render(DrawContext ctx, TextRenderer tr, int mx, int my, int ox, int oy,
                           boolean[] active, int accent) {
            for (Cell c : cells) {
                int cx = ox + c.dx(), cy = oy + c.dy();
                boolean on = c.index() < active.length && active[c.index()];
                chip(ctx, tr, cx, cy, c.w(), rowH, c.label(), on,
                        hit(mx, my, cx, cy, c.w(), rowH), accent);
            }
        }
    }

    public static String plural(int n, String one, String few, String many) {
        int tens = n % 100, ones = n % 10;
        String word = tens >= 11 && tens <= 14 ? many
                : ones == 1 ? one : ones >= 2 && ones <= 4 ? few : many;
        return n + " " + word;
    }

    public static List<String> wrap(TextRenderer tr, String s, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank() || width <= 0) return out;
        StringBuilder line = new StringBuilder();
        for (String word : s.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && tr.getWidth(next) > width) {
                out.add(line.toString());
                if (out.size() == maxLines) return out;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty() && out.size() < maxLines) out.add(Draw.fit(tr, line.toString(), width));
        return out;
    }

    private Ui() {}
}
