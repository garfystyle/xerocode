package net.minecraft.client.renderer.state.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;

public record ColoredRectangleRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                                          int x0, int y0, int x1, int y1, int col1, int col2,
                                          ScreenRectangle scissorArea, ScreenRectangle bounds)
        implements GuiElementRenderState {
    public ColoredRectangleRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                                       int x0, int y0, int x1, int y1, int col1, int col2, ScreenRectangle scissorArea) {
        this(pipeline, textureSetup, pose, x0, y0, x1, y1, col1, col2, scissorArea,
                bounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vc) {
        vc.addVertexWith2DPose(pose, x0, y0).setColor(col1);
        vc.addVertexWith2DPose(pose, x0, y1).setColor(col2);
        vc.addVertexWith2DPose(pose, x1, y1).setColor(col2);
        vc.addVertexWith2DPose(pose, x1, y0).setColor(col1);
    }

    static ScreenRectangle bounds(int x0, int y0, int x1, int y1, Matrix3x2fc pose, ScreenRectangle scissor) {
        ScreenRectangle b = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        return scissor != null ? scissor.intersection(b) : b;
    }
}
