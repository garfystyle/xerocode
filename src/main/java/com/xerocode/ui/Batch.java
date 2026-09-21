package com.xerocode.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class Batch implements GuiElementRenderState {
    private static final int STRIDE = 6;

    private final Matrix3x2f pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;
    private int[] data;
    private int count;

    private Batch(Matrix3x2fc pose, ScreenRectangle scissor, ScreenRectangle bounds, int capacity) {
        this.pose = new Matrix3x2f(pose);
        this.scissor = scissor;
        this.bounds = bounds;
        this.data = new int[capacity * STRIDE];
    }

    public static Batch open(GuiGraphics ctx, ScreenRectangle scissor, ScreenRectangle bounds, int capacity) {
        Batch batch = new Batch(ctx.pose(), scissor, bounds, capacity);
        ctx.guiRenderState.submitGuiElement(batch);
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
    public void buildVertices(VertexConsumer vc) {
        if (count == 0) {
            for (int k = 0; k < 4; k++) vc.addVertexWith2DPose(pose, 0, 0).setColor(0);
            return;
        }
        for (int i = 0, at = 0; i < count; i++, at += STRIDE) {
            int x0 = data[at], y0 = data[at + 1], x1 = data[at + 2], y1 = data[at + 3];
            int top = data[at + 4], bottom = data[at + 5];
            vc.addVertexWith2DPose(pose, x0, y0).setColor(top);
            vc.addVertexWith2DPose(pose, x0, y1).setColor(bottom);
            vc.addVertexWith2DPose(pose, x1, y1).setColor(bottom);
            vc.addVertexWith2DPose(pose, x1, y0).setColor(top);
        }
    }

    @Override
    public RenderPipeline pipeline() { return RenderPipelines.GUI; }

    @Override
    public TextureSetup textureSetup() { return TextureSetup.noTexture(); }

    @Override
    public ScreenRectangle scissorArea() { return scissor; }

    @Override
    public ScreenRectangle bounds() { return bounds; }
}
