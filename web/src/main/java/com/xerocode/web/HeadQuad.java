package com.xerocode.web;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

record HeadQuad(TextureSetup textureSetup, Matrix3x2fc pose, float[] xy, float[] uv, int color,
                ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
    @Override
    public RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }

    @Override
    public void buildVertices(VertexConsumer vc) {
        for (int i = 0; i < 4; i++)
            vc.addVertexWith2DPose(pose, xy[2 * i], xy[2 * i + 1]).setUv(uv[2 * i], uv[2 * i + 1])
                    .setColor(color);
    }
}
