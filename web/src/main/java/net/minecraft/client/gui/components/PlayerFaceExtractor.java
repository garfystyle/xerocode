package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class PlayerFaceExtractor {
    private PlayerFaceExtractor() {}

    public static void extractRenderState(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size,
                                          boolean hat, boolean upsideDown, int color) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, x, y, 8, 8, size, size, 8, 8, 64, 64, color);
        if (hat) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, x, y, 40, 8, size, size, 8, 8, 64, 64, color);
    }
}
