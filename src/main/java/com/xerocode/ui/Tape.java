package com.xerocode.ui;

import com.xerocode.XeroCode;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class Tape {
    private static final TextRenderable[] NO_GLYPHS = new TextRenderable[0];

    private static int epoch;
    private static Tape recording;
    private static int originX, originY, scale = 1;

    static int epoch() { return epoch; }

    public static void register() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath("xerocode",
                        "tape_" + Integer.toHexString(System.identityHashCode(Tape.class))),
                (ResourceManagerReloadListener) manager -> {
                    if (XeroCode.retired()) return;
                    epoch++;
                });
    }

    static void scaled(int x, int y, int s) {
        originX = x;
        originY = y;
        scale = s;
    }

    static void unscaled() { scale = 1; }

    private static final class Glyphs {
        final int x, y, scale;
        TextRenderable[] glyphs;
        Glyphs(int x, int y, int scale, TextRenderable[] glyphs) {
            this.x = x; this.y = y; this.scale = scale; this.glyphs = glyphs;
        }
    }

    private record Vanilla(Object text, int x, int y, int argb, boolean shadow, int ox, int oy, int scale) {}

    private static final class Icon {
        final ItemStack stack;
        final int x, y, size;
        GuiItemRenderState last;
        Icon(ItemStack stack, int x, int y, int size) {
            this.stack = stack; this.x = x; this.y = y; this.size = size;
        }
    }

    private static final class Piece {
        final int x0, y0, x1, y1, quadFrom, opFrom;
        Batch.Part part;
        List<Object> ops;

        Piece(int x0, int y0, int x1, int y1, int quadFrom, int opFrom) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.quadFrom = quadFrom; this.opFrom = opFrom;
        }

        boolean seen(double vx0, double vy0, double vx1, double vy1) {
            return x1 >= vx0 && x0 <= vx1 && y1 >= vy0 && y0 <= vy1;
        }
    }

    private final long key;
    private final int epochAt;
    private Piece[] pieces = new Piece[0];
    private Batch.Part[] shown = new Batch.Part[0];
    private List<Piece> marks = new ArrayList<>();
    private List<Object> ops = new ArrayList<>();
    private List<TextRenderable> capture;

    private Tape(long key) {
        this.key = key;
        this.epochAt = epoch;
    }

    boolean valid(long want) {
        return want == key && epochAt == epoch && Minecraft.getInstance().gui.overlay() == null;
    }

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
        if (t == null) return null;
        int quads = batch == null ? 0 : batch.size();
        List<Piece> list = t.marks;
        if (list.isEmpty())
            list = List.of(new Piece(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0));
        int n = list.size();
        t.pieces = new Piece[n];
        t.shown = new Batch.Part[n];
        for (int i = 0; i < n; i++) {
            Piece p = list.get(i);
            int quadFrom = i == 0 ? 0 : p.quadFrom, opFrom = i == 0 ? 0 : p.opFrom;
            int quadTo = i + 1 < n ? list.get(i + 1).quadFrom : quads;
            int opTo = i + 1 < n ? list.get(i + 1).opFrom : t.ops.size();
            p.part = new Batch.Part(batch == null ? new int[0] : batch.copy(quadFrom, quadTo));
            p.ops = coalesce(t.ops.subList(opFrom, opTo));
            t.pieces[i] = p;
        }
        t.marks = null;
        t.ops = null;
        return t;
    }

    static void abort() {
        recording = null;
        scale = 1;
    }

    private static List<Object> coalesce(List<Object> ops) {
        List<Object> out = new ArrayList<>(ops.size());
        Glyphs open = null;
        List<TextRenderable> joined = null;
        for (Object op : ops) {
            if (op instanceof Glyphs g && g.scale == 1) {
                if (open == null) {
                    open = g;
                    joined = new ArrayList<>();
                    out.add(g);
                }
                bakeInto(g.glyphs, joined);
                continue;
            }
            if (open != null) {
                seal(open, joined);
                open = null;
            }
            if (op instanceof Glyphs g) {
                List<TextRenderable> baked = new ArrayList<>();
                bakeInto(g.glyphs, baked);
                seal(g, baked);
            }
            out.add(op);
        }
        if (open != null) seal(open, joined);
        return out;
    }

    private static void bakeInto(TextRenderable[] glyphs, List<TextRenderable> out) {
        for (TextRenderable r : glyphs) {
            TextRenderable b = SmoothText.bake(r);
            if (b != null) out.add(b);
        }
    }

    private static void seal(Glyphs g, List<TextRenderable> baked) {
        g.glyphs = SmoothText.segments(baked.toArray(NO_GLYPHS));
    }

    static List<TextRenderable> captureGlyphs() {
        if (recording == null) return null;
        recording.capture = new ArrayList<>();
        return recording.capture;
    }

    static void text(boolean smooth, Object text, int x, int y, int argb, boolean shadow) {
        Tape t = recording;
        if (t == null) return;
        if (smooth && t.capture != null) {
            TextRenderable[] g = t.capture.toArray(NO_GLYPHS);
            t.ops.add(scale > 1 ? new Glyphs(originX, originY, scale, g) : new Glyphs(0, 0, 1, g));
        } else {
            t.ops.add(new Vanilla(text, x, y, argb, shadow, originX, originY, scale));
        }
        t.capture = null;
    }

    static void item(ItemStack stack, int x, int y, int size) {
        if (recording != null) recording.ops.add(new Icon(stack, x, y, size));
    }

    void replay(GuiGraphicsExtractor ctx, Font font, ScreenRectangle area,
                double vx0, double vy0, double vx1, double vy1) {
        int n = 0;
        boolean any = false;
        for (Piece p : pieces) {
            if (!p.seen(vx0, vy0, vx1, vy1)) continue;
            any = true;
            if (p.part.count() > 0) shown[n++] = p.part;
        }
        if (!any) return;
        if (n > 0) Batch.replay(ctx, area, area, shown, n);
        SmoothText.Pen pen = null;
        for (Piece p : pieces)
            if (p.seen(vx0, vy0, vx1, vy1)) pen = play(ctx, font, p.ops, pen);
    }

    private static SmoothText.Pen play(GuiGraphicsExtractor ctx, Font font, List<Object> ops, SmoothText.Pen pen) {
        for (Object op : ops) {
            if (op instanceof Glyphs g) {
                if (g.scale > 1) {
                    lift(ctx, g.x, g.y, g.scale);
                    SmoothText.replay(ctx, g.glyphs, null);
                    ctx.pose().popMatrix();
                } else {
                    if (pen == null) pen = new SmoothText.Pen(ctx);
                    SmoothText.replay(ctx, g.glyphs, pen);
                }
            } else if (op instanceof Vanilla v) {
                if (v.scale > 1) {
                    lift(ctx, v.ox, v.oy, v.scale);
                    vanilla(ctx, font, v);
                    ctx.pose().popMatrix();
                } else {
                    vanilla(ctx, font, v);
                }
            } else if (op instanceof Icon i) {
                if (i.size == 16) i.last = Icons.draw(ctx, i.stack, i.x, i.y, i.last);
                else Draw.item(ctx, i.stack, i.x, i.y, i.size);
            }
        }
        return pen;
    }

    private static void lift(GuiGraphicsExtractor ctx, int x, int y, int scale) {
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
    }

    private static void vanilla(GuiGraphicsExtractor ctx, Font font, Vanilla v) {
        if (v.text instanceof String s) ctx.text(font, s, v.x, v.y, v.argb, v.shadow);
        else ctx.text(font, (FormattedCharSequence) v.text, v.x, v.y, v.argb, v.shadow);
    }
}
