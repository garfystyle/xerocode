package net.minecraft.client.renderer.state.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;

public record BlitRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                              int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color,
                              ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
    public BlitRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                           int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color,
                           ScreenRectangle scissorArea) {
        this(pipeline, textureSetup, pose, x0, y0, x1, y1, u0, u1, v0, v1, color, scissorArea,
                ColoredRectangleRenderState.bounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vc) {
        vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
        vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
        vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
        vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
    }
}
