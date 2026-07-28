package com.xerocode.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

public final class Draw {
    public static int argb(int a, int rgb) { return (a << 24) | (rgb & 0xFFFFFF); }
    public static int opaque(int rgb)      { return 0xFF000000 | (rgb & 0xFFFFFF); }

    public static int shade(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        if (t >= 0) {
            r += (int) ((255 - r) * t); g += (int) ((255 - g) * t); b += (int) ((255 - b) * t);
        } else {
            float k = 1 + t;
            r = (int) (r * k); g = (int) (g * k); b = (int) (b * k);
        }
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    public static int mixArgb(int a, int b, float t) {
        int al = (int) (((a >>> 24) & 0xFF) + ((((b >>> 24) & 0xFF)) - ((a >>> 24) & 0xFF)) * t);
        return (clamp(al) << 24) | (mix(a, b, t) & 0xFFFFFF);
    }

    public static int readable(int rgb) {
        if (!Theme.LIGHT) return shade(rgb, 0.25f);
        return shade(rgb, isLight(rgb) ? -0.55f : -0.25f);
    }

    public static boolean isLight(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150;
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private static Batch batch;

    public static void batch(Batch b) { batch = b; }
    public static Batch batch() { return batch; }

    private static void quad(DrawContext ctx, int x0, int y0, int x1, int y1, int top, int bottom) {
        if (batch != null) { batch.quad(x0, y0, x1, y1, top, bottom); return; }
        if (top == bottom) ctx.fill(x0, y0, x1, y1, top);
        else ctx.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    public static void rect(DrawContext ctx, int x, int y, int w, int h, int argb) {
        if (w > 0 && h > 0) quad(ctx, x, y, x + w, y + h, argb, argb);
    }

    static int arcInset(int r, int d) {
        if (r <= 0 || d >= r || d < 0) return 0;
        double dy = r - d - 0.5;
        double dx = Math.sqrt(Math.max(0, (double) r * r - dy * dy));
        return (int) Math.round(r - dx);
    }

    public static void roundRect(DrawContext ctx, int x, int y, int w, int h,
                                 int tl, int tr, int br, int bl, int argb) {
        roundRectGrad(ctx, x, y, w, h, tl, tr, br, bl, argb, argb);
    }

    public static void round(DrawContext ctx, int x, int y, int w, int h, int r, int argb) {
        roundRectGrad(ctx, x, y, w, h, r, r, r, r, argb, argb);
    }

    public static void pill(DrawContext ctx, int x, int y, int w, int h, int argb) {
        pillGrad(ctx, x, y, w, h, argb, argb);
    }

    public static void pillGrad(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        int r = Math.min(h / 2, w / 2);
        roundRectGrad(ctx, x, y, w, h, r, r, r, r, top, bottom);
    }

    public static void dot(DrawContext ctx, int x, int y, int argb) {
        round(ctx, x, y, 4, 4, 2, argb);
    }

    public static void roundRectGrad(DrawContext ctx, int x, int y, int w, int h,
                                     int tl, int tr, int br, int bl, int top, int bottom) {
        rows(ctx, x, y, w, h, tl, tr, br, bl, 0, h, top, bottom);
    }

    static int insetLeft(int tl, int bl, int h, int dy) {
        int i = 0;
        if (dy < tl) i = Math.max(i, arcInset(tl, dy));
        if (dy >= h - bl) i = Math.max(i, arcInset(bl, h - 1 - dy));
        return i;
    }

    static int insetRight(int tr, int br, int h, int dy) {
        int i = 0;
        if (dy < tr) i = Math.max(i, arcInset(tr, dy));
        if (dy >= h - br) i = Math.max(i, arcInset(br, h - 1 - dy));
        return i;
    }

    static int colorAt(int top, int bottom, int h, int dy) {
        return h <= 1 || top == bottom ? top : mixArgb(top, bottom, dy / (float) (h - 1));
    }

    private static void rows(DrawContext ctx, int x, int y, int w, int h,
                             int tl, int tr, int br, int bl, int from, int to,
                             int top, int bottom) {
        if (w <= 0 || h <= 0 || to <= from) return;
        int start = from;
        int pl = insetLeft(tl, bl, h, from), pr = insetRight(tr, br, h, from);
        for (int dy = from + 1; dy <= to; dy++) {
            int li = dy < to ? insetLeft(tl, bl, h, dy) : Integer.MIN_VALUE;
            int ri = dy < to ? insetRight(tr, br, h, dy) : Integer.MIN_VALUE;
            if (li == pl && ri == pr) continue;
            int x0 = x + pl, x1 = x + w - pr;
            if (x1 > x0) {
                int cA = colorAt(top, bottom, h, start), cB = colorAt(top, bottom, h, dy - 1);
                quad(ctx, x0, y + start, x1, y + dy, cA, cB);
            }
            start = dy;
            pl = li;
            pr = ri;
        }
    }

    public static void hgrad(DrawContext ctx, int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < w; i++) {
            int c = w == 1 ? left : mixArgb(left, right, i / (float) (w - 1));
            quad(ctx, x + i, y, x + i + 1, y + h, c, c);
        }
    }

    public static void roundOutline(DrawContext ctx, int x, int y, int w, int h, int r, int argb) {
        if (w <= 0 || h <= 0) return;
        int ri = Math.max(0, r - 1);
        for (int dy = 0; dy < h; dy++) {
            int i = 0;
            if (dy < r) i = Math.max(i, arcInset(r, dy));
            if (dy >= h - r) i = Math.max(i, arcInset(r, h - 1 - dy));
            int left = x + i, right = x + w - i;
            int dy2 = dy - 1, h2 = h - 2;
            if (dy2 < 0 || dy2 >= h2 || w <= 2) {
                rect(ctx, left, y + dy, right - left, 1, argb);
                continue;
            }
            int ii = 0;
            if (dy2 < ri) ii = Math.max(ii, arcInset(ri, dy2));
            if (dy2 >= h2 - ri) ii = Math.max(ii, arcInset(ri, h2 - 1 - dy2));
            int il = x + 1 + ii, ir = x + w - 1 - ii;
            if (il >= ir) { rect(ctx, left, y + dy, right - left, 1, argb); continue; }
            rect(ctx, left, y + dy, Math.max(0, il - left), 1, argb);
            rect(ctx, ir, y + dy, Math.max(0, right - ir), 1, argb);
        }
    }

    public static void card(DrawContext ctx, int x, int y, int w, int h,
                            int tl, int tr, int br, int bl,
                            int fillTop, int fillBottom, int border) {
        roundRect(ctx, x, y, w, h, tl, tr, br, bl, border);
        roundRectGrad(ctx, x + 1, y + 1, w - 2, h - 2,
                Math.max(0, tl - 1), Math.max(0, tr - 1),
                Math.max(0, br - 1), Math.max(0, bl - 1), fillTop, fillBottom);
    }

    public static void card(DrawContext ctx, int x, int y, int w, int h, int r,
                            int fill, int border) {
        card(ctx, x, y, w, h, r, r, r, r, fill, fill, border);
    }

    public static void shadow(DrawContext ctx, int x, int y, int w, int h, int r) {
        if (w <= 0 || h <= 0 || !com.xerocode.Settings.shadows()) return;
        int core = Theme.SHADOW, soft = Theme.SHADOW_SOFT;
        int i = Math.max(0, r - 1);
        rect(ctx, x + w, y + 2 + i, 1, h - i, core);
        rect(ctx, x + 1 + i, y + h, w - i - 1, 2, core);
        rect(ctx, x + w, y + h + 2, 1, 1, soft);
        rect(ctx, x + 1 + i, y + h + 2, w - i - 1, 1, soft);
    }

    public static void blockShape(DrawContext ctx, int x, int y, int w, int h,
                                  int openFrom, int openTo,
                                  int fillTop, int fillBottom, int border) {
        if (w <= 0 || h <= 0) return;
        rect(ctx, x, y, w, h, border);
        int iw = w - 2, ih = h - 2;
        if (iw > 0 && ih > 0)
            quad(ctx, x + 1, y + 1, x + 1 + iw, y + 1 + ih, fillTop, fillBottom);
        if (openTo > openFrom)
            rect(ctx, openFrom, y + h - 1, openTo - openFrom, 1, fillBottom);
    }

    public static void blockSilhouette(DrawContext ctx, int x, int y, int w, int h, int argb) {
        rect(ctx, x, y, w, h, argb);
    }

    public static void span(DrawContext ctx, int l, int r, int y, int hole0, int hole1, int argb) {
        rect(ctx, l, y, Math.max(0, Math.min(hole0, r) - l), 1, argb);
        int right = Math.max(l, hole1);
        rect(ctx, right, y, Math.max(0, r - right), 1, argb);
    }

    public static String fit(TextRenderer tr, String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (tr.getWidth(s) <= maxWidth) return s;
        int room = maxWidth - tr.getWidth("…");
        if (room <= 0) return "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (tr.getWidth(sb.toString() + s.charAt(i)) > room) break;
            sb.append(s.charAt(i));
        }
        return sb + "…";
    }

    public static void textCenter(DrawContext ctx, TextRenderer tr, String s, int x, int y,
                                  int w, int room, int rgb, boolean shadow) {
        String t = fit(tr, s, room);
        text(ctx, tr, t, x + (w - tr.getWidth(t)) / 2, y, rgb, shadow);
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y,
                            int rgb, boolean shadow) {
        int argb = opaque(rgb);
        if (!SmoothText.draw(ctx, tr, s, x, y, argb, shadow)) ctx.drawText(tr, s, x, y, argb, shadow);
    }

    public static void text(DrawContext ctx, TextRenderer tr, OrderedText s, int x, int y,
                            int rgb, boolean shadow) {
        int argb = opaque(rgb);
        if (!SmoothText.draw(ctx, tr, s, x, y, argb, shadow)) ctx.drawText(tr, s, x, y, argb, shadow);
    }

    public static void textScaled(DrawContext ctx, TextRenderer tr, OrderedText s, int x, int y,
                                  int scale, int rgb, boolean shadow) {
        if (scale <= 1) { text(ctx, tr, s, x, y, rgb, shadow); return; }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        text(ctx, tr, s, 0, 0, rgb, shadow);
        m.popMatrix();
    }

    public static OrderedText ordered(String s) {
        return net.minecraft.util.Language.getInstance()
                .reorder(net.minecraft.text.StringVisitable.plain(s));
    }

    public static void textFit(DrawContext ctx, TextRenderer tr, String s, int x, int y,
                               int maxWidth, int rgb, boolean shadow) {
        text(ctx, tr, fit(tr, s, maxWidth), x, y, rgb, shadow);
    }

    public static void textRight(DrawContext ctx, TextRenderer tr, String s, int right, int y,
                                 int rgb, boolean shadow) {
        text(ctx, tr, s, right - tr.getWidth(s), y, rgb, shadow);
    }

    public static int badge(DrawContext ctx, TextRenderer tr, String s, int x, int y,
                            int fill, int rgb) {
        int w = tr.getWidth(s) + 8;
        round(ctx, x, y, w, 11, 3, fill);
        ctx.drawText(tr, s, x + 4, y + 2, opaque(rgb), false);
        return w;
    }

    public static int badgeWidth(TextRenderer tr, String s) { return tr.getWidth(s) + 8; }

    public static void glyph(DrawContext ctx, String[] rows, int x, int y, int rgb) {
        int c = opaque(rgb);
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int run = 0;
            for (int i = 0; i <= row.length(); i++) {
                boolean on = i < row.length() && row.charAt(i) == '#';
                if (on) { run++; continue; }
                if (run > 0) { quad(ctx, x + i - run, y + r, x + i, y + r + 1, c, c); run = 0; }
            }
        }
    }

    public static int glyphW(String[] rows) { return rows[0].length(); }
    public static int glyphH(String[] rows) { return rows.length; }

    public static final String[] CHEVRON_LEFT = {
            "  #  ",
            " ##  ",
            "##   ",
            " ##  ",
            "  #  "};
    public static final String[] CARET_DOWN = {
            "#####",
            " ### ",
            "  #  "};
    public static final String[] PLUS = {
            "  #  ",
            "  #  ",
            "#####",
            "  #  ",
            "  #  "};
    public static final String[] MINUS = {
            "     ",
            "     ",
            "#####",
            "     ",
            "     "};
    public static final String[] CROSS = {
            "#    #",
            " #  # ",
            "  ##  ",
            "  ##  ",
            " #  # ",
            "#    #"};
    public static final String[] CHECK = {
            "     #",
            "    ##",
            "#  ## ",
            "## ## ",
            " #### ",
            "  ##  "};
    public static final String[] SEARCH = {
            " ###  ",
            "#   # ",
            "#   # ",
            "#   # ",
            " ###  ",
            "   ## ",
            "    ##"};
    public static final String[] TRASH = {
            "  ###  ",
            "#######",
            " ##### ",
            " # # # ",
            " # # # ",
            " # # # ",
            " ##### "};
    public static final String[] SAVE = {
            "#######",
            "## # ##",
            "## # ##",
            "#     #",
            "# ### #",
            "# ### #",
            "#######"};
    public static final String[] LOAD = {
            "   #   ",
            "   #   ",
            " ##### ",
            "  ###  ",
            "   #   ",
            "       ",
            "#######"};
    public static final String[] UPLOAD = {
            "   #   ",
            "  ###  ",
            " ##### ",
            "   #   ",
            "   #   ",
            "       ",
            "#######"};
    public static final String[] FIT = {
            "##   ##",
            "#     #",
            "       ",
            "       ",
            "       ",
            "#     #",
            "##   ##"};
    public static final String[] UNDO = {
            "  #    ",
            " ##    ",
            "###### ",
            " ##   #",
            "  #   #",
            "     ##",
            "       "};
    public static final String[] REDO = {
            "    #  ",
            "    ## ",
            " ######",
            "#   ## ",
            "#   #  ",
            "##     ",
            "       "};
    public static final String[] WARN = {
            "  #  ",
            " ### ",
            " ### ",
            "  #  ",
            "     ",
            "  #  "};
    public static final String[] WINDOW = {
            "#########",
            "#########",
            "#       #",
            "# ##### #",
            "#       #",
            "# ###   #",
            "#       #",
            "#########"};
    public static final String[] STRIKE_TEXT = {
            "### #### ",
            "         ",
            "         ",
            "         ",
            "#### ### "};
    public static final String[] STRIKE_LINE = {
            "         ",
            "         ",
            "#########",
            "         ",
            "         "};
    public static final String[] RESET = {
            "#   ###  ",
            "##    ## ",
            "###    ##",
            "#       #",
            "#       #",
            "##     ##",
            " ##   ## ",
            "  #####  "};
    public static final String[] COPY = {
            " ##### ",
            " #   # ",
            "###  # ",
            "# #### ",
            "# #    ",
            "# #    ",
            "###    "};
    public static final String[] LOCK = {
            " ###  ",
            "#   # ",
            "#   # ",
            "##### ",
            "## ## ",
            "##### "};
    public static final String[] PIN = {
            " ###  ",
            "#   # ",
            "# # # ",
            "#   # ",
            " ###  ",
            "  #   ",
            "  #   "};
    public static final String[] LOOK = {
            "  ##   ",
            " #  #  ",
            "#  # # ",
            "#  # # ",
            " #  #  ",
            "  ##   "};
    public static final String[] ARROW_RIGHT = {
            "     ",
            "  #  ",
            "#####",
            "  #  ",
            "     "};
    public static final String[] BRICKS = {
            "#######",
            "#  #  #",
            "#######",
            "  #  # ",
            "#######",
            "#  #  #",
            "#######"};
    public static final String[] PLAY = {
            "#    ",
            "##   ",
            "###  ",
            "#### ",
            "###  ",
            "##   ",
            "#    "};
    public static final String[] PAUSE = {
            "##  ##",
            "##  ##",
            "##  ##",
            "##  ##",
            "##  ##",
            "##  ##",
            "##  ##"};
    public static final String[] REWIND = {
            "##    #",
            "##   ##",
            "##  ###",
            "## ####",
            "##  ###",
            "##   ##",
            "##    #"};
    public static final String[] GEAR = {
            "   ##    ",
            "#########",
            "   ##    ",
            "         ",
            "     ##  ",
            "#########",
            "     ##  "};
    public static final String[] CANVAS = {
            "#######",
            "#     #",
            "# ### #",
            "#     #",
            "# ##  #",
            "#     #",
            "#######"};
    public static final String[] LOOP = {
            " ##   ## ",
            "#  # #  #",
            "#   #   #",
            "#  # #  #",
            " ##   ## "};

    private Draw() {}
}
