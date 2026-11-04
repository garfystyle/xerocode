package com.xerocode.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xerocode.web.Renderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;

public final class Tape {
    private static final int NOTHING = 0, ICONS = 1, WORDS = 2, EVERYTHING = 3;
    private static final int LINE_H = 10, TOUCH_SLOP = 2, EDGE = 16, SPAN = 6;
    private static final int[] NO_QUADS = new int[0];
    private static final Renderer BAKER = new Renderer();
    private static final Matrix3x2fStack ROUND = new Matrix3x2fStack(2);

    private static int epoch, loads;
    private static Tape recording;
    private static int originX, originY, scale = 1;

    public static void loads(int count) { loads = count; }

    static int epoch() { return epoch; }

    public static void register() {}

    static void scaled(int x, int y, int s) {
        originX = x;
        originY = y;
        scale = s;
    }

    static void unscaled() { scale = 1; }

    private record Words(Object text, int x, int y, int argb, boolean shadow, int ox, int oy, int scale) {}

    private record Icon(ItemStack stack, int x, int y, int size) {}

    private static final class Piece {
        final int x0, y0, x1, y1, quadFrom;
        int opFrom, opTo, iconFrom, iconTo, wordFrom, wordTo, iconAfter, wordAfter;
        boolean pops;
        int[] quads = NO_QUADS;

        Piece(int x0, int y0, int x1, int y1, int quadFrom, int opFrom) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.quadFrom = quadFrom; this.opFrom = opFrom;
        }

        boolean seen(double vx0, double vy0, double vx1, double vy1) {
            return x1 >= vx0 && x0 <= vx1 && y1 >= vy0 && y0 <= vy1;
        }
    }

    private static final class Stream {
        final double[] xy;
        final int[] rest, runs;
        final float[] lifts;
        final int[] spans, firstSpan;

        Stream(Renderer r, List<float[]> lifted, List<int[]> ops) {
            int n = r.vertexCount(), stride = Renderer.STRIDE;
            int[] v = r.verts();
            xy = new double[n * 2];
            double[] exact = r.exact();
            rest = new int[n * 3];
            for (int i = 0, at = 0; i < n; i++, at += stride) {
                xy[i * 2] = exact[i * 2];
                xy[i * 2 + 1] = exact[i * 2 + 1];
                rest[i * 3] = v[at + 2];
                rest[i * 3 + 1] = v[at + 3];
                rest[i * 3 + 2] = v[at + 4];
            }
            runs = Arrays.copyOf(r.cmds(), r.cmdCount() * Renderer.CMD);
            lifts = new float[r.cmdCount() * 3];
            spans = new int[ops.size() * SPAN];
            for (int i = 0; i < ops.size(); i++) System.arraycopy(ops.get(i), 0, spans, i * SPAN, SPAN);
            firstSpan = new int[r.cmdCount()];
            for (int c = 0, k = 0, cn = r.cmdCount(); c < cn; c++) {
                int from = runs[c * Renderer.CMD];
                while (k < ops.size() && spans[k * SPAN] + spans[k * SPAN + 1] <= from) k++;
                firstSpan[c] = k;
            }
            for (float[] l : lifted) {
                for (int c = (int) l[0], to = (int) l[1]; c < to; c++) {
                    lifts[c * 3] = l[2];
                    lifts[c * 3 + 1] = l[3];
                    lifts[c * 3 + 2] = l[4];
                }
            }
        }

        void play(Renderer r, Matrix3x2fc pose, ScreenRectangle area, boolean inner, int from, int to) {
            for (int c = from; c < to; c++) {
                int at = c * Renderer.CMD;
                float lift = lifts[c * 3 + 2];
                Matrix3x2fc m = lift == 0 ? pose
                        : new Matrix3x2f(pose).translate(lifts[c * 3], lifts[c * 3 + 1]).scale(lift, lift);
                r.enter(runs[at + 2], runs[at + 3] != 0, runs[at + 4], area);
                if (inner) r.points(m, xy, rest, runs[at], runs[at + 1]);
                else clipped(r, m, area, c);
            }
        }

        private void clipped(Renderer r, Matrix3x2fc m, ScreenRectangle area, int c) {
            int at = c * Renderer.CMD, v = runs[at], end = v + runs[at + 1], k = firstSpan[c], n = spans.length / SPAN;
            while (v < end) {
                while (k < n && spans[k * SPAN] + spans[k * SPAN + 1] <= v) k++;
                int s = k * SPAN;
                if (k >= n || spans[s] > v) {
                    int stop = k < n ? Math.min(end, spans[s]) : end;
                    r.points(m, xy, rest, v, stop - v);
                    v = stop;
                    continue;
                }
                int stop = Math.min(end, spans[s] + spans[s + 1]);
                ScreenRectangle b = new ScreenRectangle(spans[s + 2], spans[s + 3], spans[s + 4], spans[s + 5])
                        .transformMaxBounds(m);
                if (area.intersection(b) != null) r.points(m, xy, rest, v, stop - v);
                v = stop;
            }
        }
    }

    public static final class Run implements GuiElementRenderState {
        private final Tape tape;
        private final Matrix3x2f pose, rounded;
        private final ScreenRectangle area, bounds;
        private final int count;

        private Run(Tape tape, Matrix3x2fc pose, ScreenRectangle area, ScreenRectangle bounds, int count) {
            this.tape = tape;
            this.pose = new Matrix3x2f(pose);
            ROUND.set(pose);
            ROUND.pushMatrix();
            ROUND.popMatrix();
            this.rounded = new Matrix3x2f(ROUND);
            this.area = area;
            this.bounds = bounds;
            this.count = count;
        }

        private boolean inner(Piece p) {
            double a = pose.m00(), d = pose.m11(), c = pose.m20(), f = pose.m21();
            return a * p.x0 + c >= area.left() + EDGE && d * p.y0 + f >= area.top() + EDGE
                    && a * p.x1 + c <= area.right() - EDGE && d * p.y1 + f <= area.bottom() - EDGE;
        }

        public void emit(Renderer r) {
            Piece[] shown = tape.shown;
            r.enter(0, false, Renderer.BLEND_ALPHA, area);
            for (int i = 0; i < count; i++) r.quads(pose, shown[i].quads);
            boolean popped = false;
            for (int i = 0; i < count; i++) {
                Piece p = shown[i];
                boolean inner = inner(p);
                tape.icons.play(r, popped ? rounded : pose, area, inner, p.iconFrom, p.iconAfter);
                tape.icons.play(r, rounded, area, inner, p.iconAfter, p.iconTo);
                popped |= p.pops;
            }
            popped = false;
            for (int i = 0; i < count; i++) {
                Piece p = shown[i];
                boolean inner = inner(p);
                tape.words.play(r, popped ? rounded : pose, area, inner, p.wordFrom, p.wordAfter);
                tape.words.play(r, rounded, area, inner, p.wordAfter, p.wordTo);
                popped |= p.pops;
            }
        }

        @Override public void buildVertices(VertexConsumer vc) {}
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public ScreenRectangle scissorArea() { return area; }
        @Override public ScreenRectangle bounds() { return bounds; }
    }

    private final long key;
    private final int loadsAt;
    private Piece[] pieces = new Piece[0];
    private Piece[] shown = pieces;
    private List<Piece> marks = new ArrayList<>();
    private List<Object> ops = new ArrayList<>();
    private Stream icons, words;
    private int left, top, right, bottom;

    private Tape(long key) {
        this.key = key;
        this.loadsAt = loads;
    }

    boolean valid(long want) { return want == key && loadsAt == loads; }

    static void begin(long key) { recording = new Tape(key); }

    static void mark(int x0, int y0, int x1, int y1) {
        Tape t = recording;
        if (t == null) return;
        Batch b = Draw.batch();
        t.marks.add(new Piece(x0, y0, x1, y1, b == null ? 0 : b.size(), t.ops.size()));
    }

    static Tape end(Batch batch) {
        Tape t = recording;
        abort();
        if (t == null || !BAKER.ready()) return null;
        int quads = batch == null ? 0 : batch.size();
        List<Piece> list = t.marks;
        if (list.isEmpty())
            list = List.of(new Piece(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0));
        int n = list.size();
        t.pieces = new Piece[n];
        t.shown = new Piece[n];
        for (int i = 0; i < n; i++) {
            Piece p = list.get(i);
            int from = i == 0 ? 0 : p.quadFrom;
            int to = i + 1 < n ? list.get(i + 1).quadFrom : quads;
            if (to > from && batch != null) p.quads = batch.copy(from, to);
            p.opTo = i + 1 < n ? list.get(i + 1).opFrom : t.ops.size();
            t.pieces[i] = p;
        }
        t.pieces[0].opFrom = 0;
        Font font = Minecraft.getInstance().font;
        boolean mixed = t.wordsBeforeIcons(font);
        t.icons = t.bake(font, mixed ? NOTHING : ICONS);
        t.words = t.bake(font, mixed ? EVERYTHING : WORDS);
        t.measure();
        t.marks = null;
        t.ops = null;
        return t;
    }

    static void abort() {
        recording = null;
        scale = 1;
    }

    static void text(boolean smooth, Object text, int x, int y, int argb, boolean shadow) {
        Tape t = recording;
        if (t != null) t.ops.add(new Words(text, x, y, argb, shadow, originX, originY, scale));
    }

    static void item(ItemStack stack, int x, int y, int size) {
        if (recording != null) recording.ops.add(new Icon(stack, x, y, size));
    }

    private static FormattedCharSequence sequence(Object text) {
        if (text instanceof String s) return Language.getInstance().getVisualOrder(FormattedText.of(s));
        return (FormattedCharSequence) text;
    }

    private boolean wordsBeforeIcons(Font font) {
        for (Piece p : pieces) {
            for (int i = p.opFrom + 1; i < p.opTo; i++) {
                if (!(ops.get(i) instanceof Icon c)) continue;
                for (int k = p.opFrom; k < i; k++)
                    if (ops.get(k) instanceof Words w && touches(font, w, c)) return true;
            }
        }
        return false;
    }

    private static boolean touches(Font font, Words w, Icon c) {
        int lift = Math.max(1, w.scale);
        int wx = w.scale > 1 ? w.ox + w.x * lift : w.x, wy = w.scale > 1 ? w.oy + w.y * lift : w.y;
        int ww = font.width(sequence(w.text)) * lift, wh = LINE_H * lift;
        return wx - TOUCH_SLOP < c.x + c.size && wx + ww + TOUCH_SLOP > c.x
                && wy - TOUCH_SLOP < c.y + c.size && wy + wh + TOUCH_SLOP > c.y;
    }

    private Stream bake(Font font, int kind) {
        Renderer r = BAKER;
        r.clear();
        r.keepExact(true);
        List<float[]> lifted = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        for (Piece p : pieces) {
            int from = r.seal();
            int after = -1;
            for (int i = p.opFrom; i < p.opTo; i++) {
                Object op = ops.get(i);
                if (kind != NOTHING) bakeOp(r, font, op, kind, lifted, spans);
                boolean lift = op instanceof Words w ? w.scale > 1 : op instanceof Icon c && c.size != 16;
                if (lift && after < 0) after = r.seal();
            }
            int to = r.seal();
            if (after >= 0) p.pops = true;
            if (kind <= ICONS) {
                p.iconFrom = from;
                p.iconTo = to;
                p.iconAfter = after < 0 ? to : after;
            } else {
                p.wordFrom = from;
                p.wordTo = to;
                p.wordAfter = after < 0 ? to : after;
            }
        }
        return new Stream(r, lifted, spans);
    }

    private static void span(Renderer r, List<int[]> spans, int from, ScreenRectangle b) {
        int n = r.vertexCount() - from;
        if (n > 0 && b != null) spans.add(new int[]{from, n, b.left(), b.top(), b.width(), b.height()});
    }

    private static void bakeOp(Renderer r, Font font, Object op, int kind, List<float[]> lifted, List<int[]> spans) {
        if (op instanceof Words w && kind != ICONS) {
            if (w.scale <= 1) {
                int v0 = r.vertexCount();
                span(r, spans, v0, r.word(font, sequence(w.text), w.x, w.y, w.argb, w.shadow));
                return;
            }
            int from = r.seal();
            int v0 = r.vertexCount();
            span(r, spans, v0, r.word(font, sequence(w.text), w.x, w.y, w.argb, w.shadow));
            lifted.add(new float[]{from, r.seal(), w.ox, w.oy, w.scale});
        } else if (op instanceof Icon c && kind != WORDS) {
            if (c.size == 16) {
                int v0 = r.vertexCount();
                r.icon(c.stack, c.x, c.y);
                span(r, spans, v0, new ScreenRectangle(c.x, c.y, 16, 16));
                return;
            }
            int from = r.seal();
            int v0 = r.vertexCount();
            r.icon(c.stack, 0, 0);
            span(r, spans, v0, new ScreenRectangle(0, 0, 16, 16));
            lifted.add(new float[]{from, r.seal(), c.x, c.y, c.size / 16f});
        }
    }

    private void measure() {
        left = Integer.MAX_VALUE;
        top = Integer.MAX_VALUE;
        right = Integer.MIN_VALUE;
        bottom = Integer.MIN_VALUE;
        for (Piece p : pieces) {
            for (int i = 0; i < p.quads.length; i += 6) {
                grow(p.quads[i], p.quads[i + 1]);
                grow(p.quads[i + 2], p.quads[i + 3]);
            }
        }
        measure(icons);
        measure(words);
    }

    private void measure(Stream s) {
        for (int c = 0, n = s.runs.length / Renderer.CMD; c < n; c++) {
            int at = c * Renderer.CMD, from = s.runs[at], to = from + s.runs[at + 1];
            float lift = s.lifts[c * 3 + 2], ox = s.lifts[c * 3], oy = s.lifts[c * 3 + 1];
            for (int i = from; i < to; i++) {
                float x = (float) s.xy[i * 2], y = (float) s.xy[i * 2 + 1];
                grow(lift == 0 ? x : ox + x * lift, lift == 0 ? y : oy + y * lift);
            }
        }
    }

    private void grow(float x, float y) {
        left = Math.min(left, (int) Math.floor(x));
        top = Math.min(top, (int) Math.floor(y));
        right = Math.max(right, (int) Math.ceil(x));
        bottom = Math.max(bottom, (int) Math.ceil(y));
    }

    private ScreenRectangle bounds(Matrix3x2fc pose, ScreenRectangle area) {
        if (right <= left || bottom <= top) return null;
        ScreenRectangle b = new ScreenRectangle(left, top, right - left, bottom - top).transformMaxBounds(pose);
        return area.intersection(new ScreenRectangle(b.left() - 1, b.top() - 1, b.width() + 2, b.height() + 2));
    }

    void replay(GuiGraphicsExtractor ctx, Font font, ScreenRectangle area,
                double vx0, double vy0, double vx1, double vy1) {
        int n = 0;
        for (Piece p : pieces) if (p.seen(vx0, vy0, vx1, vy1)) shown[n++] = p;
        if (n == 0) return;
        ScreenRectangle bounds = bounds(ctx.pose(), area);
        if (bounds != null) ctx.guiRenderState.addGuiElement(new Run(this, ctx.pose(), area, bounds, n));
        for (int i = 0; i < n; i++) {
            if (shown[i].pops) {
                ctx.pose().pushMatrix();
                ctx.pose().popMatrix();
                return;
            }
        }
    }
}
