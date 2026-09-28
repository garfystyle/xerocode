package net.minecraft.client.renderer.state.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

public record GuiItemRenderState(Matrix3x2f pose, ItemStack stack, int x, int y, ScreenRectangle scissorArea,
                                 ScreenRectangle bounds) implements ScreenArea {
    public GuiItemRenderState(Matrix3x2f pose, ItemStack stack, int x, int y, ScreenRectangle scissorArea) {
        this(pose, stack, x, y, scissorArea, clip(new ScreenRectangle(x, y, 16, 16).transformMaxBounds(pose), scissorArea));
    }

    private static ScreenRectangle clip(ScreenRectangle b, ScreenRectangle scissor) {
        return scissor != null ? scissor.intersection(b) : b;
    }
}
