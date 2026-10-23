package com.xerocode.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

interface Overlay {
    void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta);

    boolean mouseClicked(MouseButtonEvent click, boolean doubled);

    boolean mouseDragged(MouseButtonEvent click, double dx, double dy);

    default void mouseReleased() {}

    boolean mouseScrolled(double mx, double my, double amount);

    boolean keyPressed(KeyEvent input);

    boolean charTyped(CharacterEvent input);

    boolean isClosed();
}
