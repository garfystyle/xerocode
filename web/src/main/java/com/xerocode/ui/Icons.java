package com.xerocode.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.world.item.ItemStack;

final class Icons {
    private Icons() {}

    static void draw(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
        ctx.item(stack, x, y);
    }

    static GuiItemRenderState draw(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y, GuiItemRenderState prev) {
        ctx.item(stack, x, y);
        return null;
    }
}
