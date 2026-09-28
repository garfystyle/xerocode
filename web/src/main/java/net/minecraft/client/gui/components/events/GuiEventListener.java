package net.minecraft.client.gui.components.events;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public interface GuiEventListener {
    default void mouseMoved(double x, double y) {}
    default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return false; }
    default boolean mouseReleased(MouseButtonEvent event) { return false; }
    default boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return false; }
    default boolean mouseScrolled(double x, double y, double scrollX, double scrollY) { return false; }
    default boolean keyPressed(KeyEvent event) { return false; }
    default boolean keyReleased(KeyEvent event) { return false; }
    default boolean charTyped(CharacterEvent event) { return false; }
    default boolean isMouseOver(double x, double y) { return false; }
    default boolean shouldTakeFocusAfterInteraction() { return true; }

    void setFocused(boolean focused);

    boolean isFocused();
}
