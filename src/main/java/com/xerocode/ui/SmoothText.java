package com.xerocode.ui;

import com.xerocode.Settings;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.OrderedText;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

public final class SmoothText {
    private SmoothText() {}

    private static final int LIGHT = 15728880;

    private static ScreenRect clip;

    private static boolean broken;

    public static void clip(ScreenRect area) { clip = area; }

    public static boolean draw(DrawContext ctx, TextRenderer tr, OrderedText text,
                               int x, int y, int argb, boolean shadow) {
        if (!smoothing(ctx)) return false;
        try {
            return submit(ctx, tr.prepare(text, x, y, argb, shadow, false, 0));
        } catch (Throwable e) {
            broken = true;
            return false;
        }
    }

    public static boolean draw(DrawContext ctx, TextRenderer tr, String text,
                               int x, int y, int argb, boolean shadow) {
        if (!smoothing(ctx)) return false;
        try {
            return submit(ctx, tr.prepare(text, x, y, argb, shadow, 0));
        } catch (Throwable e) {
            broken = true;
            return false;
        }
    }

    private static boolean smoothing(DrawContext ctx) {
        if (broken || clip == null || !Settings.smoothText()) return false;
        var m = ctx.getMatrices();
        float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        MinecraftClient mc = MinecraftClient.getInstance();
        int gs = mc == null || mc.getWindow() == null ? 1 : Math.max(1, mc.getWindow().getScaleFactor());
        return sx * gs < 0.999f;
    }

    private static boolean submit(DrawContext ctx, TextRenderer.GlyphDrawable prepared) {
        Matrix3x2f pose = new Matrix3x2f(ctx.getMatrices());
        ScreenRect scissor = clip;
        prepared.draw(new TextRenderer.GlyphDrawer() {
            @Override
            public void drawGlyph(TextDrawable.DrawnGlyphRect glyph) { add(glyph); }

            @Override
            public void drawRectangle(TextDrawable rect) { add(rect); }

            private void add(TextDrawable drawable) {
                ctx.state.addPreparedTextElement(new Glyph(pose, drawable, scissor));
            }
        });
        return true;
    }

    private record Glyph(Matrix3x2f pose, TextDrawable renderable, ScreenRect scissor)
            implements SimpleGuiElementRenderState {
        @Override
        public void setupVertices(VertexConsumer vc) {
            renderable.render(new Matrix4f().mul(pose), vc, LIGHT, true);
        }

        @Override
        public RenderPipeline pipeline() { return renderable.getPipeline(); }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.withLightmap(renderable.textureView(),
                    RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
        }

        @Override
        public ScreenRect scissorArea() { return scissor; }

        @Override
        public ScreenRect bounds() { return null; }
    }
}
