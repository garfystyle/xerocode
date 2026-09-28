package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public abstract class AbstractWidget implements Renderable, GuiEventListener {
    protected int width;
    protected int height;
    private int x;
    private int y;
    private Component message;
    protected boolean isHovered;
    public boolean active = true;
    public boolean visible = true;
    protected float alpha = 1.0f;
    private boolean focused;

    protected AbstractWidget(int x, int y, int width, int height, Component message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
    }

    public int getHeight() { return height; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setPosition(int x, int y) { setX(x); setY(y); }
    public int getRight() { return getX() + width; }
    public int getBottom() { return getY() + height; }
    public void setMessage(Component message) { this.message = message; }
    public Component getMessage() { return message; }
    public boolean isHovered() { return isHovered; }
    public boolean isActive() { return visible && active; }
    public void setAlpha(float alpha) { this.alpha = alpha; }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (!visible) return;
        isHovered = graphics.containsPointInScissor(mouseX, mouseY) && areCoordinatesInRectangle(mouseX, mouseY);
        extractWidgetRenderState(graphics, mouseX, mouseY, a);
    }

    protected abstract void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    public void onClick(MouseButtonEvent event, boolean doubleClick) {}

    public void onRelease(MouseButtonEvent event) {}

    protected void onDrag(MouseButtonEvent event, double dx, double dy) {}

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive()) return false;
        if (isValidClickButton(event.buttonInfo()) && isMouseOver(event.x(), event.y())) {
            onClick(event, doubleClick);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isValidClickButton(event.buttonInfo())) {
            onRelease(event);
            return true;
        }
        return false;
    }

    protected boolean isValidClickButton(MouseButtonInfo info) { return info.button() == 0; }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (isValidClickButton(event.buttonInfo())) {
            onDrag(event, dx, dy);
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mx, double my) { return isActive() && areCoordinatesInRectangle(mx, my); }

    protected boolean areCoordinatesInRectangle(double mx, double my) {
        return mx >= getX() && my >= getY() && mx < getRight() && my < getBottom();
    }

    @Override
    public void setFocused(boolean focused) { this.focused = focused; }

    @Override
    public boolean isFocused() { return focused; }
}
