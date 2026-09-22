package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Menus;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ChestMenu {
    private static final int HEAD_H = 26, PAD = 8, CELL = 20, SLOT = 18, HOTBAR_GAP = 5;

    private record Step(Menus.Node node, int page) {}

    private static final List<Step> PATH = new ArrayList<>();

    private final Font tr;
    private final Consumer<Catalog.Action> done;
    private int screenW, screenH;
    private int x, y, w, h;
    private boolean closed;
    private Menus.Cell hover;

    public ChestMenu(Font tr, int screenW, int screenH, Consumer<Catalog.Action> done) {
        this.tr = tr;
        this.done = done;
        resize(screenW, screenH);
    }

    public boolean isClosed() { return closed; }

    public void resize(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        place();
    }

    private void place() {
        w = PAD * 2 + 9 * CELL - (CELL - SLOT);
        h = HEAD_H + PAD * 2 + gridH() - (CELL - SLOT);
        x = Ui.midX(screenW, w);
        y = Ui.midY(screenH, h);
    }

    private Step step() { return PATH.isEmpty() ? null : PATH.get(PATH.size() - 1); }

    private Menus.Page page() {
        Step s = step();
        if (s == null) return null;
        return s.node.pages.get(Math.max(0, Math.min(s.node.pages.size() - 1, s.page)));
    }

    private int rows() {
        Menus.Page p = page();
        return p == null ? Menus.KIT_SIZE / 9 : Math.max(1, (p.size + 8) / 9);
    }

    private int gridH() { return rows() * CELL + (page() == null ? HOTBAR_GAP : 0); }

    private int slotX(int slot) { return x + PAD + (slot % 9) * CELL; }

    private int slotY(int slot) {
        int top = y + HEAD_H + PAD;
        if (page() != null) return top + (slot / 9) * CELL;
        if (slot < 9) return top + 3 * CELL + HOTBAR_GAP;
        return top + (slot / 9 - 1) * CELL;
    }

    private List<Menus.Cell> cells() {
        Menus.Page p = page();
        return p == null ? Menus.kit() : p.cells;
    }

    private int accent() {
        Step s = step();
        return s == null ? Theme.ACCENT : s.node.category.color;
    }

    private Menus.Cell cellAt(double mx, double my) {
        for (Menus.Cell c : cells())
            if (Ui.hit(mx, my, slotX(c.slot), slotY(c.slot), SLOT, SLOT)) return c;
        return null;
    }

    private String title() {
        Menus.Page p = page();
        return p == null ? "Блоки кода" : p.title;
    }

    private String pageNote() {
        Step s = step();
        if (s == null || s.node.pages.size() < 2) return "";
        return (s.page + 1) + "/" + s.node.pages.size();
    }

    private int backX() { return x + PAD - 2; }
    private int closeX() { return x + w - PAD - 14; }
    private int iconY() { return y + (HEAD_H - 14) / 2; }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        place();
        int accent = accent();
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent);

        int textX;
        if (step() != null) {
            Ui.iconButton(ctx, mouseX, mouseY, backX(), iconY(), 14, Draw.CHEVRON_LEFT, Ui.GHOST, true);
            textX = backX() + 14 + 5;
        } else {
            Draw.round(ctx, x + PAD, y + 8, 3, 10, 1, Draw.opaque(accent));
            textX = x + PAD + 9;
        }
        String note = pageNote();
        int right = closeX() - 6;
        if (!note.isEmpty()) {
            Draw.textRight(ctx, tr, note, right, y + (HEAD_H - Ui.TEXT_H) / 2, Theme.TEXT_FAINT, false);
            right -= tr.width(note) + 6;
        }
        Draw.textFit(ctx, tr, title(), textX, y + (HEAD_H - Ui.TEXT_H) / 2, right - textX,
                Theme.TEXT, false);
        Ui.closeButton(ctx, mouseX, mouseY, closeX(), iconY(), 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);

        hover = cellAt(mouseX, mouseY);
        int n = page() == null ? Menus.KIT_SIZE : rows() * 9;
        for (int s = 0; s < n; s++)
            Draw.card(ctx, slotX(s), slotY(s), SLOT, SLOT, Ui.R_SM,
                    Draw.opaque(Ui.WELL), Draw.opaque(Ui.LINE_IN));
        for (Menus.Cell c : cells()) {
            int cx = slotX(c.slot), cy = slotY(c.slot);
            int color = colorOf(c, accent);
            if (c == hover)
                Draw.card(ctx, cx, cy, SLOT, SLOT, Ui.R_SM,
                        Draw.opaque(Draw.mix(Ui.WELL, color, 0.40f)),
                        Draw.opaque(Draw.mix(Ui.LINE_IN, color, 0.85f)));
            ctx.item(Catalog.stackOf(c.item), cx + 1, cy + 1);
        }
    }

    private static int colorOf(Menus.Cell c, int accent) {
        if (c.category != null) return c.category.color;
        if (c.action != null && c.action.category != null) return c.action.category.color;
        return accent;
    }

    public Catalog.Action hoverAction() {
        return hover == null ? null : hover.action;
    }

    public List<Component> hoverLines() {
        Menus.Cell c = hover;
        if (c == null || c.action != null) return null;
        List<Component> lines = new ArrayList<>();
        if (c.nav != 0) {
            lines.add(Component.literal(c.nav == Menus.PREV ? "Предыдущая страница" : "Следующая страница"));
            return lines;
        }
        lines.add(Component.literal(c.name));
        if (c.category != null) {
            Menus.Node root = Menus.root(c.category);
            String said = root == null ? "поставить блок"
                    : Ui.plural(c.category.count(), "действие", "действия", "действий");
            lines.add(Component.literal("§8" + said));
        } else if (!c.description.isEmpty()) {
            for (String l : Ui.wrap(tr, c.description, 200, 4))
                lines.add(Component.literal("§7" + l));
        }
        return lines;
    }

    public boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, w, h); }

    public void mouseClicked(MouseButtonEvent click) {
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) { closed = true; return; }
        if (click.button() == 1) { back(); return; }
        if (click.button() != 0) return;
        if (Ui.hit(mx, my, closeX(), iconY(), 14, 14)) { closed = true; return; }
        if (step() != null && Ui.hit(mx, my, backX(), iconY(), 14, 14)) { back(); return; }
        Menus.Cell c = cellAt(mx, my);
        if (c == null) return;
        if (c.nav != 0) { turn(c.nav == Menus.PREV ? -1 : 1); return; }
        if (c.action != null) { pick(c.action); return; }
        if (c.child != null) { PATH.add(new Step(c.child, 0)); return; }
        if (c.category != null) {
            Menus.Node root = Menus.root(c.category);
            if (root != null) { PATH.add(new Step(root, 0)); return; }
            if (c.category.count() > 0) pick(c.category.subActions.get(0).get(0));
        }
    }

    private void pick(Catalog.Action a) {
        closed = true;
        done.accept(a);
    }

    private void back() {
        if (PATH.isEmpty()) { closed = true; return; }
        PATH.remove(PATH.size() - 1);
    }

    private void turn(int by) {
        Step s = step();
        if (s == null) return;
        int to = s.page + by;
        if (to < 0 || to >= s.node.pages.size()) return;
        PATH.set(PATH.size() - 1, new Step(s.node, to));
    }

    public boolean mouseScrolled(double amount) {
        turn(amount < 0 ? 1 : -1);
        return true;
    }

    public void keyPressed(KeyEvent input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { closed = true; return; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) back();
        else if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) turn(-1);
        else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) turn(1);
    }
}
