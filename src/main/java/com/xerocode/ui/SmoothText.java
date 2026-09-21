package com.xerocode.ui;

import com.xerocode.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

public final class SmoothText {
    private SmoothText() {}

    private static final int LIGHT = 15728880;

    private static ScreenRectangle clip;

    private static boolean broken;

    public static void clip(ScreenRectangle area) { clip = area; }

    public static boolean draw(GuiGraphics ctx, Font tr, FormattedCharSequence text,
                               int x, int y, int argb, boolean shadow) {
        if (!smoothing(ctx)) return false;
        try {
            return submit(ctx, tr.prepareText(text, x, y, argb, shadow, false, 0));
        } catch (Throwable e) {
            broken = true;
            return false;
        }
    }

    public static boolean draw(GuiGraphics ctx, Font tr, String text,
                               int x, int y, int argb, boolean shadow) {
        if (!smoothing(ctx)) return false;
        try {
            return submit(ctx, tr.prepareText(text, x, y, argb, shadow, 0));
        } catch (Throwable e) {
            broken = true;
            return false;
        }
    }

    private static boolean smoothing(GuiGraphics ctx) {
        if (broken || clip == null || !Settings.smoothText()) return false;
        var m = ctx.pose();
        float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        Minecraft mc = Minecraft.getInstance();
        int gs = mc == null || mc.getWindow() == null ? 1 : Math.max(1, mc.getWindow().getGuiScale());
        return sx * gs < 0.999f;
    }

    private static boolean submit(GuiGraphics ctx, Font.PreparedText prepared) {
        Matrix3x2f pose = new Matrix3x2f(ctx.pose());
        ScreenRectangle scissor = clip;
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(TextRenderable.Styled glyph) { add(glyph); }

            @Override
            public void acceptEffect(TextRenderable rect) { add(rect); }

            private void add(TextRenderable drawable) {
                ctx.guiRenderState.submitGlyphToCurrentLayer(new Glyph(pose, drawable, scissor));
            }
        });
        return true;
    }

    private record Glyph(Matrix3x2f pose, TextRenderable renderable, ScreenRectangle scissor)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            renderable.render(new Matrix4f().mul(pose), vc, LIGHT, true);
        }

        @Override
        public RenderPipeline pipeline() { return renderable.guiPipeline(); }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.singleTextureWithLightmap(renderable.textureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        }

        @Override
        public ScreenRectangle scissorArea() { return scissor; }

        @Override
        public ScreenRectangle bounds() { return null; }
    }
}
