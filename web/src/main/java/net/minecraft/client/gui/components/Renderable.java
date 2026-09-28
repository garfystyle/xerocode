package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface Renderable {
    void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);
}
