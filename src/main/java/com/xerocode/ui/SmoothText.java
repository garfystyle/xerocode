package com.xerocode.ui;

import com.xerocode.Settings;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class SmoothText {
    private SmoothText() {}

    private static final int LIGHT = 15728880;
    private static final float PIXEL = 0.999f;
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static ScreenRectangle clip;

    private static boolean broken;

    private static Field current, glyphs;
    private static boolean reflected, reflectable;

    private static Run last;

    public static void clip(ScreenRectangle area) { clip = area; }

    public static boolean draw(GuiGraphicsExtractor ctx, Font tr, FormattedCharSequence text,
                               int x, int y, int argb, boolean shadow) {
        if (!smoothing(ctx)) return false;
        try {
            return submit(ctx, tr.prepareText(text, x, y, argb, shadow, false, 0));
        } catch (Throwable e) {
            broken = true;
            return false;
        }
    }

    public static boolean draw(GuiGraphicsExtractor ctx, Font tr, String text,
                               int x, int y, int argb, boolean shadow) {
        if (!smoothing(ctx)) return false;
        try {
            return submit(ctx, tr.prepareText(text, x, y, argb, shadow, 0));
        } catch (Throwable e) {
            broken = true;
            return false;
        }
    }

    private static boolean enabled() {
        return !broken && clip != null && Settings.smoothText();
    }

    private static float screenScale(GuiGraphicsExtractor ctx) {
        var m = ctx.pose();
        float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        Minecraft mc = Minecraft.getInstance();
        int gs = mc == null || mc.getWindow() == null ? 1 : Math.max(1, mc.getWindow().getGuiScale());
        return sx * gs;
    }

    private static boolean smoothing(GuiGraphicsExtractor ctx) {
        return enabled() && screenScale(ctx) < PIXEL;
    }

    static int flags(GuiGraphicsExtractor ctx) {
        if (!enabled()) return 0;
        float scale = screenScale(ctx);
        int bits = 1;
        for (int k = 1; k <= Layout.NAME_SCALE; k++) if (scale * k < PIXEL) bits |= 1 << k;
        return bits;
    }

    private static void reflect() {
        reflected = true;
        try {
            current = GuiRenderState.class.getDeclaredField("current");
            current.setAccessible(true);
            Class<?> node = Class.forName("net.minecraft.client.renderer.state.gui.GuiRenderState$Node");
            glyphs = node.getDeclaredField("glyphStates");
            glyphs.setAccessible(true);
            reflectable = true;
        } catch (Throwable e) {
            reflectable = false;
        }
    }

    private static boolean stillLast(GuiRenderState state, Run run) {
        if (!reflected) reflect();
        if (!reflectable) return false;
        try {
            Object node = current.get(state);
            if (node == null) return false;
            List<?> list = (List<?>) glyphs.get(node);
            return list != null && !list.isEmpty() && list.get(list.size() - 1) == run;
        } catch (Throwable e) {
            reflectable = false;
            return false;
        }
    }

    static final class Pen {
        final Matrix4f pose;
        final ScreenRectangle scissor;
        final GuiRenderState state;
        boolean fresh = true;

        Pen(GuiGraphicsExtractor ctx) {
            pose = new Matrix4f().mul(new Matrix3x2f(ctx.pose()));
            scissor = clip;
            state = ctx.guiRenderState;
        }

        void add(TextRenderable drawable) {
            RenderPipeline pipeline = drawable.guiPipeline();
            GpuTextureView view = drawable.textureView();
            Run run = last;
            boolean joins = run != null && run.state == state && run.pipeline == pipeline
                    && run.view == view && run.scissor == scissor
                    && (!fresh || stillLast(state, run));
            if (!joins) {
                run = new Run(state, pipeline, view, scissor);
                state.addGlyphToCurrentLayer(run);
                last = run;
            }
            fresh = false;
            run.add(drawable, pose);
        }
    }

    private static boolean submit(GuiGraphicsExtractor ctx, Font.PreparedText prepared) {
        Pen sink = new Pen(ctx);
        List<TextRenderable> into = Tape.captureGlyphs();
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled glyph) { add(glyph); }

            @Override
            public void acceptEffect(TextRenderable rect) { add(rect); }

            private void add(TextRenderable drawable) {
                sink.add(drawable);
                if (into != null) into.add(drawable);
            }
        });
        return true;
    }

    static void replay(GuiGraphicsExtractor ctx, TextRenderable[] glyphs, Pen pen) {
        if (glyphs.length == 0) return;
        Pen sink = pen == null ? new Pen(ctx) : pen;
        sink.fresh = true;
        for (TextRenderable g : glyphs) sink.add(g);
    }

    static TextRenderable bake(TextRenderable r) {
        Recorder rec = new Recorder();
        try {
            r.render(IDENTITY, rec, LIGHT, true);
        } catch (Throwable e) {
            return r;
        }
        if (!rec.ok || rec.state != 0) return r;
        if (rec.n == 0) return null;
        return new Baked(r, Arrays.copyOf(rec.f, rec.n * 5), Arrays.copyOf(rec.i, rec.n * 2));
    }

    static TextRenderable[] segments(TextRenderable[] glyphs) {
        List<TextRenderable> out = new ArrayList<>();
        List<TextRenderable> parts = new ArrayList<>();
        RenderPipeline pipeline = null;
        GpuTextureView view = null;
        for (TextRenderable g : glyphs) {
            if (!parts.isEmpty() && (g.guiPipeline() != pipeline || g.textureView() != view)) {
                out.add(fuse(parts));
                parts.clear();
            }
            pipeline = g.guiPipeline();
            view = g.textureView();
            parts.add(g);
        }
        if (!parts.isEmpty()) out.add(fuse(parts));
        return out.toArray(new TextRenderable[0]);
    }

    private static TextRenderable fuse(List<TextRenderable> parts) {
        List<TextRenderable> merged = new ArrayList<>();
        List<Baked> run = new ArrayList<>();
        for (TextRenderable p : parts) {
            if (p instanceof Baked b) { run.add(b); continue; }
            if (!run.isEmpty()) { merged.add(join(run)); run.clear(); }
            merged.add(p);
        }
        if (!run.isEmpty()) merged.add(join(run));
        if (merged.size() == 1) return merged.get(0);
        return new Chain(merged.toArray(new TextRenderable[0]));
    }

    private static Baked join(List<Baked> run) {
        if (run.size() == 1) return run.get(0);
        int nf = 0, ni = 0;
        for (Baked b : run) { nf += b.f.length; ni += b.i.length; }
        float[] f = new float[nf];
        int[] i = new int[ni];
        int af = 0, ai = 0;
        for (Baked b : run) {
            System.arraycopy(b.f, 0, f, af, b.f.length);
            System.arraycopy(b.i, 0, i, ai, b.i.length);
            af += b.f.length;
            ai += b.i.length;
        }
        return new Baked(run.get(0).source, f, i);
    }

    private abstract static class Delegate implements TextRenderable {
        abstract TextRenderable head();

        @Override public RenderType renderType(Font.DisplayMode mode) { return head().renderType(mode); }
        @Override public GpuTextureView textureView() { return head().textureView(); }
        @Override public RenderPipeline guiPipeline() { return head().guiPipeline(); }
        @Override public float left() { return head().left(); }
        @Override public float top() { return head().top(); }
        @Override public float right() { return head().right(); }
        @Override public float bottom() { return head().bottom(); }
    }

    private static final class Chain extends Delegate {
        private final TextRenderable[] parts;

        Chain(TextRenderable[] parts) { this.parts = parts; }

        @Override TextRenderable head() { return parts[0]; }

        @Override
        public void render(Matrix4fc pose, VertexConsumer vc, int light, boolean seeThrough) {
            for (TextRenderable p : parts) p.render(pose, vc, light, seeThrough);
        }
    }

    private static final class Recorder implements VertexConsumer {
        float[] f = new float[40];
        int[] i = new int[16];
        int n;
        int state;
        boolean ok = true;

        private void fail() { ok = false; }

        @Override
        public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
            if (state != 0) fail();
            if (f.length < (n + 1) * 5) {
                f = Arrays.copyOf(f, f.length * 2);
                i = Arrays.copyOf(i, i.length * 2);
            }
            f[n * 5] = x;
            f[n * 5 + 1] = y;
            f[n * 5 + 2] = z;
            state = 1;
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            if (state != 1) fail();
            i[n * 2] = argb;
            state = 2;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (state != 2) fail();
            f[n * 5 + 3] = u;
            f[n * 5 + 4] = v;
            state = 3;
            return this;
        }

        @Override
        public VertexConsumer setLight(int light) {
            if (state != 3) fail();
            i[n * 2 + 1] = light;
            n++;
            state = 0;
            return this;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { fail(); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { fail(); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { fail(); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { fail(); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { fail(); return this; }
        @Override public VertexConsumer setLineWidth(float w) { fail(); return this; }
    }

    private static final class Baked extends Delegate {
        final TextRenderable source;
        final float[] f;
        final int[] i;
        private final Matrix4f movedBy = new Matrix4f();
        private float[] moved;
        private final Blob.Slot blob = new Blob.Slot();

        Baked(TextRenderable source, float[] f, int[] i) {
            this.source = source;
            this.f = f;
            this.i = i;
        }

        @Override TextRenderable head() { return source; }

        private boolean holds(Matrix4fc m) { return moved != null && movedBy.equals(m, 0f); }

        private void transform(Matrix4fc m) {
            int n = i.length / 2;
            if (moved == null) moved = new float[n * 3];
            Vector3f v = new Vector3f();
            for (int k = 0, a = 0; k < n; k++, a += 5) {
                m.transformPosition(f[a], f[a + 1], f[a + 2], v);
                moved[k * 3] = v.x();
                moved[k * 3 + 1] = v.y();
                moved[k * 3 + 2] = v.z();
            }
            movedBy.set(m);
            blob.drop();
        }

        @Override
        public void render(Matrix4fc pose, VertexConsumer vc, int light, boolean seeThrough) {
            int n = i.length / 2;
            boolean still = holds(pose);
            if (still && blob.push(vc, n)) return;
            if (!still) transform(pose);
            long mark = still ? Blob.start(vc) : -1;
            float[] t = moved;
            for (int k = 0, a = 0, b = 0, c = 0; k < n; k++, a += 5, b += 2, c += 3)
                vc.addVertex(t[c], t[c + 1], t[c + 2]).setColor(i[b]).setUv(f[a + 3], f[a + 4])
                        .setLight(i[b + 1]);
            if (still) blob.capture(vc, mark, n);
        }
    }

    private static final class Run implements GuiElementRenderState {
        final GuiRenderState state;
        final RenderPipeline pipeline;
        final GpuTextureView view;
        final ScreenRectangle scissor;
        private TextureSetup setup;
        private TextRenderable[] items = new TextRenderable[8];
        private Matrix4fc[] poses = new Matrix4fc[8];
        private int size;

        Run(GuiRenderState state, RenderPipeline pipeline, GpuTextureView view, ScreenRectangle scissor) {
            this.state = state;
            this.pipeline = pipeline;
            this.view = view;
            this.scissor = scissor;
        }

        void add(TextRenderable r, Matrix4fc pose) {
            if (size == items.length) {
                items = Arrays.copyOf(items, size * 2);
                poses = Arrays.copyOf(poses, size * 2);
            }
            items[size] = r;
            poses[size] = pose;
            size++;
        }

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < size; i++) items[i].render(poses[i], vc, LIGHT, true);
        }

        @Override
        public RenderPipeline pipeline() { return pipeline; }

        @Override
        public TextureSetup textureSetup() {
            if (setup == null)
                setup = TextureSetup.singleTextureWithLightmap(view,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            return setup;
        }

        @Override
        public ScreenRectangle scissorArea() { return scissor; }

        @Override
        public ScreenRectangle bounds() { return null; }
    }
}
