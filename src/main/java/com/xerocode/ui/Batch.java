package com.xerocode.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class Batch implements SimpleGuiElementRenderState {
    private static final int STRIDE = 6;

    private final Matrix3x2f pose;
    private final ScreenRect scissor;
    private final ScreenRect bounds;
    private int[] data;
    private int count;

    private Batch(Matrix3x2fc pose, ScreenRect scissor, ScreenRect bounds, int capacity) {
        this.pose = new Matrix3x2f(pose);
        this.scissor = scissor;
        this.bounds = bounds;
        this.data = new int[capacity * STRIDE];
    }

    public static Batch open(DrawContext ctx, ScreenRect scissor, ScreenRect bounds, int capacity) {
        Batch batch = new Batch(ctx.getMatrices(), scissor, bounds, capacity);
        ctx.state.addSimpleElement(batch);
        return batch;
    }

    public void quad(int x0, int y0, int x1, int y1, int top, int bottom) {
        if (x1 <= x0 || y1 <= y0) return;
        int at = count * STRIDE;
        if (at + STRIDE > data.length) {
            int[] bigger = new int[Math.max(data.length * 2, STRIDE * 64)];
            System.arraycopy(data, 0, bigger, 0, data.length);
            data = bigger;
        }
        data[at] = x0;
        data[at + 1] = y0;
        data[at + 2] = x1;
        data[at + 3] = y1;
        data[at + 4] = top;
        data[at + 5] = bottom;
        count++;
    }

    public int size() { return count; }

    @Override
    public void setupVertices(VertexConsumer vc) {
        if (count == 0) {
            for (int k = 0; k < 4; k++) vc.vertex(pose, 0, 0).color(0);
            return;
        }
        for (int i = 0, at = 0; i < count; i++, at += STRIDE) {
            int x0 = data[at], y0 = data[at + 1], x1 = data[at + 2], y1 = data[at + 3];
            int top = data[at + 4], bottom = data[at + 5];
            vc.vertex(pose, x0, y0).color(top);
            vc.vertex(pose, x0, y1).color(bottom);
            vc.vertex(pose, x1, y1).color(bottom);
            vc.vertex(pose, x1, y0).color(top);
        }
    }

    @Override
    public RenderPipeline pipeline() { return RenderPipelines.GUI; }

    @Override
    public TextureSetup textureSetup() { return TextureSetup.empty(); }

    @Override
    public ScreenRect scissorArea() { return scissor; }

    @Override
    public ScreenRect bounds() { return bounds; }
}
