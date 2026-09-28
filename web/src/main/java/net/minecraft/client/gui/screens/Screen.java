package net.minecraft.client.gui.screens;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class Screen implements Renderable, GuiEventListener {
    protected final Component title;
    protected Minecraft minecraft;
    protected Font font;
    public int width;
    public int height;
    private final List<GuiEventListener> children = new ArrayList<>();
    private final List<Renderable> renderables = new ArrayList<>();
    private GuiEventListener focused;
    private boolean dragging;
    private boolean initialized;

    protected Screen(Component title) {
        this.title = title;
        this.minecraft = Minecraft.getInstance();
        this.font = minecraft.font;
    }

    public Component getTitle() { return title; }

    public final void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.nextStratum();
        extractBackground(g, mouseX, mouseY, a);
        g.nextStratum();
        extractRenderState(g, mouseX, mouseY, a);
        g.renderDeferredElements();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        for (Renderable r : renderables) r.extractRenderState(g, mouseX, mouseY, a);
    }

    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    public List<? extends GuiEventListener> children() { return children; }

    public GuiEventListener getFocused() { return focused; }

    public void setFocused(GuiEventListener listener) {
        if (focused == listener) return;
        if (focused != null) focused.setFocused(false);
        if (listener != null) listener.setFocused(true);
        focused = listener;
    }

    @Override
    public void setFocused(boolean f) {
        if (!f) setFocused((GuiEventListener) null);
    }

    @Override
    public boolean isFocused() { return focused != null; }

    public void clearFocus() { setFocused((GuiEventListener) null); }

    public boolean isDragging() { return dragging; }

    public void setDragging(boolean dragging) { this.dragging = dragging; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (GuiEventListener child : children) {
            if (!child.isMouseOver(event.x(), event.y())) continue;
            if (child.mouseClicked(event, doubleClick) && child.shouldTakeFocusAfterInteraction()) {
                setFocused(child);
                if (event.button() == 0) setDragging(true);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging) {
            dragging = false;
            if (focused != null) return focused.mouseReleased(event);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return focused != null && dragging && event.button() == 0 && focused.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        for (GuiEventListener child : children)
            if (child.isMouseOver(x, y)) return child.mouseScrolled(x, y, sx, sy);
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return focused != null && focused.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return focused != null && focused.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return focused != null && focused.charTyped(event);
    }

    public boolean shouldCloseOnEsc() { return true; }

    public void onClose() { minecraft.gui.setScreen(null); }

    protected <T extends GuiEventListener & Renderable> T addRenderableWidget(T widget) {
        renderables.add(widget);
        return addWidget(widget);
    }

    protected <T extends Renderable> T addRenderableOnly(T renderable) {
        renderables.add(renderable);
        return renderable;
    }

    protected <T extends GuiEventListener> T addWidget(T widget) {
        children.add(widget);
        return widget;
    }

    protected void removeWidget(GuiEventListener widget) {
        if (widget instanceof Renderable r) renderables.remove(r);
        if (focused == widget) clearFocus();
        children.remove(widget);
    }

    protected void clearWidgets() {
        renderables.clear();
        children.clear();
    }

    public final void init(int width, int height) {
        this.width = width;
        this.height = height;
        if (!initialized) init();
        else repositionElements();
        initialized = true;
    }

    protected void rebuildWidgets() {
        clearWidgets();
        clearFocus();
        init();
    }

    protected void init() {}

    public void tick() {}

    public void removed() {}

    public void added() {}

    public boolean isPauseScreen() { return true; }

    protected void repositionElements() { rebuildWidgets(); }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        repositionElements();
    }

    public static boolean hasControlDown() { return com.xerocode.web.Input.ctrl(); }

    public static boolean hasShiftDown() { return com.xerocode.web.Input.shift(); }

    public static boolean hasAltDown() { return com.xerocode.web.Input.alt(); }
}
