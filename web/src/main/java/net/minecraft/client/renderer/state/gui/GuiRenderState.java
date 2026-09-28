package net.minecraft.client.renderer.state.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public class GuiRenderState {
    private static final Comparator<ScreenRectangle> SCISSOR = Comparator.nullsFirst(
            Comparator.comparingInt(ScreenRectangle::top).thenComparingInt(ScreenRectangle::bottom)
                    .thenComparingInt(ScreenRectangle::left).thenComparingInt(ScreenRectangle::right));
    public static final Comparator<GuiElementRenderState> ORDER =
            Comparator.comparing(GuiElementRenderState::scissorArea, SCISSOR)
                    .thenComparingInt(e -> e.pipeline().getSortKey())
                    .thenComparingInt(e -> e.textureSetup() == null ? -1 : e.textureSetup().getSortKey());

    private final List<Node> strata = new ArrayList<>();
    private Node current;
    private ScreenRectangle lastBounds;

    public GuiRenderState() {
        nextStratum();
    }

    public void nextStratum() {
        current = new Node(null);
        strata.add(current);
    }

    public void up() {
        if (current.up == null) current.up = new Node(current);
        current = current.up;
    }

    public void addItem(GuiItemRenderState item) {
        if (find(item)) current.items().add(item);
    }

    public void addText(GuiTextRenderState text) {
        if (find(text)) current.texts().add(text);
    }

    public void addGuiElement(GuiElementRenderState element) {
        if (find(element)) current.elements().add(element);
    }

    public void addGlyphToCurrentLayer(GuiElementRenderState glyph) {
        current.glyphs().add(glyph);
    }

    public void addBlitToCurrentLayer(GuiElementRenderState blit) {
        current.elements().add(blit);
    }

    private boolean find(ScreenArea area) {
        ScreenRectangle bounds = area.bounds();
        if (bounds == null) return false;
        if (lastBounds != null && lastBounds.encompasses(bounds)) up();
        else aboveHighestIntersecting(bounds);
        lastBounds = bounds;
        return true;
    }

    private void aboveHighestIntersecting(ScreenRectangle bounds) {
        Node node = strata.get(strata.size() - 1);
        while (node.up != null) node = node.up;
        boolean found = false;
        while (!found) {
            found = hits(bounds, node.elements) || hits(bounds, node.items) || hits(bounds, node.texts);
            if (node.parent == null) break;
            if (!found) node = node.parent;
        }
        current = node;
        if (found) up();
    }

    private static boolean hits(ScreenRectangle bounds, List<? extends ScreenArea> list) {
        if (list == null) return false;
        for (ScreenArea a : list) {
            ScreenRectangle b = a.bounds();
            if (b != null && b.intersects(bounds)) return true;
        }
        return false;
    }

    public void forEachNode(Consumer<Node> visitor) {
        for (Node stratum : strata) {
            for (Node n = stratum; n != null; n = n.up) {
                current = n;
                visitor.accept(n);
            }
        }
    }

    public void reset() {
        strata.clear();
        lastBounds = null;
        nextStratum();
    }

    public static final class Node {
        final Node parent;
        Node up;
        List<GuiElementRenderState> elements;
        List<GuiElementRenderState> glyphs;
        List<GuiItemRenderState> items;
        List<GuiTextRenderState> texts;

        Node(Node parent) {
            this.parent = parent;
        }

        List<GuiElementRenderState> elements() {
            if (elements == null) elements = new ArrayList<>();
            return elements;
        }

        List<GuiElementRenderState> glyphs() {
            if (glyphs == null) glyphs = new ArrayList<>();
            return glyphs;
        }

        List<GuiItemRenderState> items() {
            if (items == null) items = new ArrayList<>();
            return items;
        }

        List<GuiTextRenderState> texts() {
            if (texts == null) texts = new ArrayList<>();
            return texts;
        }

        public List<GuiElementRenderState> elementList() { return elements; }
        public List<GuiElementRenderState> glyphList() { return glyphs; }
        public List<GuiItemRenderState> itemList() { return items; }
        public List<GuiTextRenderState> textList() { return texts; }
    }
}
