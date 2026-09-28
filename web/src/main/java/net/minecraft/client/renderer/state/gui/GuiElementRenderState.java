package net.minecraft.client.renderer.state.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;

public interface GuiElementRenderState extends ScreenArea {
    void buildVertices(VertexConsumer vc);

    RenderPipeline pipeline();

    TextureSetup textureSetup();

    ScreenRectangle scissorArea();
}
