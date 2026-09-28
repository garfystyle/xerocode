package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Env;
import com.xerocode.Menus;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ChestMenu {
    private static final int PAD = 8, HOTBAR_GAP = 5, FOOT_H = 66, SIDE_W = 150, TAKE_W = 52, TAKE_H = 20;
    private static final int FIND_H = 16, FIND_GAP = 5;

    private record Step(Menus.Node node, int page) {}

    private static final List<Step> PATH = new ArrayList<>();

    public static void home() { PATH.clear(); }

    public static void openAt(Catalog.Category category) {
        PATH.clear();
        Menus.Node root = Menus.root(category);
        if (root != null) PATH.add(new Step(root, 0));
    }

    private final Font tr;
    private final Consumer<Catalog.Action> done;
    private final EditBox field;
    private int screenW, screenH;
    private int x, y, w, h;
    private boolean closed;
    private Menus.Cell hover;
    private Menus.Cell picked;
    private int cell = 20, slot = 18;
    private String heading;
    private Menus.Node found;
    private int foundPage;

    public ChestMenu(Font tr, int screenW, int screenH, Consumer<Catalog.Action> done) {
        this.tr = tr;
        this.done = done;
        field = Ui.field(tr, 0, 0, 40, 10, "найти блок…");
        field.setMaxLength(48);
        field.setResponder(t -> refind());
        field.setFocused(!Env.touch());
        resize(screenW, screenH);
    }

    public void titled(String title) { heading = title; }

    public boolean isClosed() { return closed; }

    public void resize(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        place();
    }

    private boolean touch() { return Env.touch(); }

    private boolean side() { return touch() && screenH < 300 && screenW > screenH; }

    private int head() { return touch() && !side() ? 32 : 26; }

    private int icon() { return touch() ? 20 : 14; }

    private int findH() { return FIND_H + FIND_GAP; }

    private void place() {
        cell = 20;
        int extraW = side() ? SIDE_W + PAD : 0, extraH = touch() && !side() ? FOOT_H : 0;
        if (touch()) {
            int byW = (screenW - 12 - PAD * 2 - extraW + 2) / 9;
            int byH = (screenH - 12 - head() - findH() - PAD * 2 - extraH - HOTBAR_GAP + 2) / 6;
            cell = Math.max(20, Math.min(40, Math.min(byW, byH)));
        }
        slot = cell - 2;
        w = PAD * 2 + 9 * cell - (cell - slot) + extraW;
        h = head() + findH() + PAD * 2 + gridH() - (cell - slot) + extraH;
        x = Ui.midX(screenW, w);
        y = touch() ? Math.max(Ui.margin(screenH), screenH - h - 12) : Ui.midY(screenH, h);
        field.setX(fieldX() + 16);
        field.setY(fieldY() + (FIND_H - 8) / 2);
        Ui.width(field, fieldW() - 16 - (field.getValue().isEmpty() ? 4 : 16));
    }

    private int fieldX() { return x + PAD; }

    private int fieldY() { return y + head() + FIND_GAP; }

    private int fieldW() { return (side() ? w - SIDE_W - PAD : w) - PAD * 2; }

    private boolean searching() { return found != null; }

    private Step step() {
        if (found != null) return new Step(found, foundPage);
        return PATH.isEmpty() ? null : PATH.get(PATH.size() - 1);
    }

    private Menus.Page page() {
        Step s = step();
        if (s == null) return null;
        return s.node.pages.get(Math.max(0, Math.min(s.node.pages.size() - 1, s.page)));
    }

    private int rows() {
        Menus.Page p = page();
        return p == null ? Menus.KIT_SIZE / 9 : Math.max(1, (p.size + 8) / 9);
    }

    private int gridH() { return rows() * cell + (page() == null ? HOTBAR_GAP : 0); }

    private int slotX(int slot) { return x + PAD + (slot % 9) * cell; }

    private int slotY(int slot) {
        int top = y + head() + findH() + PAD;
        if (page() != null) return top + (slot / 9) * cell;
        if (slot < 9) return top + 3 * cell + HOTBAR_GAP;
        return top + (slot / 9 - 1) * cell;
    }

    private List<Menus.Cell> cells() {
        Menus.Page p = page();
        return p == null ? Menus.kit() : p.cells;
    }

    private int accent() {
        Step s = step();
        return s == null || s.node.category == null ? Theme.ACCENT : s.node.category.color;
    }

    private Menus.Cell cellAt(double mx, double my) {
        for (Menus.Cell c : cells())
            if (Ui.hit(mx, my, slotX(c.slot), slotY(c.slot), slot, slot)) return c;
        return null;
    }

    private String title() {
        Menus.Page p = page();
        if (p != null) return p.title;
        return heading != null ? heading : "Блоки кода";
    }

    private String pageNote() {
        Step s = step();
        if (s == null || s.node.pages.size() < 2) return "";
        return (s.page + 1) + "/" + s.node.pages.size();
    }

    private boolean canBack() { return searching() || !PATH.isEmpty(); }

    private boolean canHome() { return !searching() && PATH.size() > 0; }

    private int backX() { return x + PAD - 2; }
    private int homeX() { return backX() + icon() + 3; }
    private int closeX() { return x + w - PAD - icon(); }
    private int iconY() { return y + (head() - icon()) / 2; }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        place();
        int accent = accent();
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, head(), accent);

        int textX;
        if (canBack()) {
            Ui.iconButton(ctx, mouseX, mouseY, backX(), iconY(), icon(), Draw.CHEVRON_LEFT, Ui.GHOST, true);
            textX = backX() + icon() + 5;
            if (canHome()) {
                Ui.iconButton(ctx, mouseX, mouseY, homeX(), iconY(), icon(), Draw.HOME, Ui.GHOST, true);
                textX = homeX() + icon() + 5;
            }
        } else {
            Draw.round(ctx, x + PAD, y + (head() - 10) / 2, 3, 10, 1, Draw.opaque(accent));
            textX = x + PAD + 9;
        }
        String note = pageNote();
        int right = closeX() - 6;
        if (!note.isEmpty()) {
            Draw.textRight(ctx, tr, note, right, y + (head() - Ui.TEXT_H) / 2, Theme.TEXT_FAINT, false);
            right -= tr.width(note) + 6;
        }
        Draw.textFit(ctx, tr, title(), textX, y + (head() - Ui.TEXT_H) / 2, right - textX,
                Theme.TEXT, false);
        Ui.closeButton(ctx, mouseX, mouseY, closeX(), iconY(), icon());
        Ui.hairline(ctx, x + 1, y + head(), w - 2);

        drawField(ctx, mouseX, mouseY);

        hover = cellAt(mouseX, mouseY);
        if (picked != null && !cells().contains(picked)) picked = null;
        int n = page() == null ? Menus.KIT_SIZE : rows() * 9;
        for (int s = 0; s < n; s++)
            Draw.card(ctx, slotX(s), slotY(s), slot, slot, Ui.R_SM,
                    Draw.opaque(Ui.WELL), Draw.opaque(Ui.LINE_IN));
        int icon = slot == 18 ? 16 : slot - 4;
        for (Menus.Cell c : cells()) {
            int cx = slotX(c.slot), cy = slotY(c.slot);
            int color = colorOf(c, accent);
            if (c == hover || c == picked)
                Draw.card(ctx, cx, cy, slot, slot, Ui.R_SM,
                        Draw.opaque(Draw.mix(Ui.WELL, color, 0.40f)),
                        Draw.opaque(Draw.mix(Ui.LINE_IN, color, 0.85f)));
            int at = (slot - icon) / 2;
            if (icon == 16) ctx.item(Catalog.stackOf(c.item), cx + at, cy + at);
            else Draw.item(ctx, Catalog.stackOf(c.item), cx + at, cy + at, icon);
        }
        if (touch()) drawFoot(ctx, mouseX, mouseY);
    }

    private void drawField(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int fx = fieldX(), fy = fieldY(), fw = fieldW();
        Ui.input(ctx, fx, fy, fw, FIND_H, field.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, fx + 5, fy + (FIND_H - Draw.glyphH(Draw.SEARCH)) / 2,
                field.getValue().isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT_DIM);
        field.extractRenderState(ctx, mouseX, mouseY, 0);
        Ui.placeholder(ctx, tr, field);
        if (!field.getValue().isEmpty()) {
            boolean hov = Ui.hit(mouseX, mouseY, clearX(), fy, 14, FIND_H);
            Draw.glyph(ctx, Draw.CROSS, clearX() + (14 - Draw.glyphW(Draw.CROSS)) / 2,
                    fy + (FIND_H - Draw.glyphH(Draw.CROSS)) / 2, hov ? Theme.TEXT : Theme.TEXT_FAINT);
        }
    }

    private int clearX() { return fieldX() + fieldW() - 15; }

    private static final String TOUCH_HINT = "тап — описание, ещё тап — выбрать";

    private int infoX() { return side() ? x + w - PAD - SIDE_W : x + PAD; }

    private int infoY() { return side() ? y + head() + PAD : y + h - FOOT_H; }

    private int infoW() { return side() ? SIDE_W : w - PAD * 2; }

    private int infoH() { return side() ? h - head() - PAD * 2 : FOOT_H; }

    private int takeW() { return side() ? SIDE_W : TAKE_W; }

    private int takeX() { return side() ? infoX() : x + w - PAD - TAKE_W; }

    private int takeY() { return side() ? infoY() + infoH() - TAKE_H : infoY() + 6; }

    private Menus.Cell shown() {
        if (hover != null && hover.nav == 0) return hover;
        return picked;
    }

    private void drawFoot(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int tx = infoX(), ty = infoY(), room = infoW();
        if (side()) Ui.vline(ctx, tx - PAD / 2 - 1, y + head() + 1, h - head() - 2);
        else Ui.hairline(ctx, x + 1, ty, w - 2);
        Menus.Cell c = shown();
        if (c == null) {
            if (side()) {
                int ly = ty;
                for (String line : Ui.wrap(tr, TOUCH_HINT, room, 3)) {
                    Draw.textFit(ctx, tr, line, tx, ly, room, Theme.TEXT_FAINT, false);
                    ly += 10;
                }
            } else {
                Draw.textFit(ctx, tr, TOUCH_HINT, tx, ty + (FOOT_H - Ui.TEXT_H) / 2, room, Theme.TEXT_FAINT, false);
            }
            return;
        }
        Catalog.Action a = direct(c);
        if (c == picked)
            Ui.button(ctx, tr, mouseX, mouseY, takeX(), takeY(), takeW(), TAKE_H, a != null ? "Взять" : "Открыть", Ui.ACCENT);
        int nameRoom = side() ? room : takeX() - 6 - tx;
        int top = side() ? ty : ty + 7;
        String name, where, about;
        if (a != null) {
            name = a.name;
            where = a.category == null ? "" : a.category.name + (a.subcategory == null ? "" : " / " + a.subcategory);
            about = plain(a.description);
        } else {
            name = c.name;
            where = c.category != null ? Ui.plural(c.category.count(), "действие", "действия", "действий") : "";
            about = c.description;
        }
        Draw.textFit(ctx, tr, name, tx, top, nameRoom, Theme.TEXT, false);
        Draw.textFit(ctx, tr, where, tx, top + 11, nameRoom, Theme.TEXT_FAINT, false);
        int ly = top + 24;
        int lines = side() ? Math.max(0, (takeY() - 4 - ly) / 10) : 3;
        for (String line : lines > 0 ? Ui.wrap(tr, about, room, lines) : List.<String>of()) {
            Draw.textFit(ctx, tr, line, tx, ly, room, Theme.TEXT_DIM, false);
            ly += 10;
        }
    }

    private static String plain(String description) {
        if (description == null) return "";
        StringBuilder out = new StringBuilder();
        int open = 0;
        for (char ch : description.toCharArray()) {
            if (ch == '«') open++;
            if (ch == '»' && open == 0) { out.append(' '); continue; }
            if (ch == '»') open--;
            out.append(ch);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static int colorOf(Menus.Cell c, int accent) {
        if (c.category != null) return c.category.color;
        if (c.action != null && c.action.category != null) return c.action.category.color;
        return accent;
    }

    public Catalog.Action hoverAction() {
        return hover == null || touch() ? null : hover.action;
    }

    public List<Component> hoverLines() {
        Menus.Cell c = hover;
        if (c == null || c.action != null || touch()) return null;
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
        }
        if (!c.description.isEmpty())
            for (String l : Ui.wrap(tr, c.description, 200, 5))
                lines.add(Component.literal("§7" + l));
        for (String ex : c.examples)
            for (String l : Ui.wrap(tr, "» " + ex, 200, 2))
                lines.add(Component.literal("§8" + l));
        return lines;
    }

    public List<Component> headLines(int mouseX, int mouseY) {
        if (touch()) return null;
        if (canBack() && Ui.hit(mouseX, mouseY, backX(), iconY(), icon(), icon()))
            return List.of(Component.literal(searching() ? "Сбросить поиск" : "Назад"),
                    Component.literal("§8" + Ui.rmb() + " или Backspace"));
        if (canHome() && Ui.hit(mouseX, mouseY, homeX(), iconY(), icon(), icon()))
            return List.of(Component.literal("К началу"), Component.literal("§8все блоки кода"));
        return null;
    }

    public boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, w, h); }

    public void mouseClicked(MouseButtonEvent click) {
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) { closed = true; return; }
        if (click.button() == 1) { back(); return; }
        if (click.button() != 0) return;
        if (Ui.hit(mx, my, closeX(), iconY(), icon(), icon())) { closed = true; return; }
        if (canBack() && Ui.hit(mx, my, backX(), iconY(), icon(), icon())) { back(); return; }
        if (canHome() && Ui.hit(mx, my, homeX(), iconY(), icon(), icon())) { PATH.clear(); return; }
        if (Ui.hit(mx, my, fieldX(), fieldY(), fieldW(), FIND_H)) {
            if (!field.getValue().isEmpty() && mx >= clearX()) { field.setValue(""); return; }
            field.setFocused(true);
            if (!field.mouseClicked(click, false)) field.onClick(click, false);
            return;
        }
        if (touch() && picked != null
                && Ui.hit(mx, my, takeX(), takeY(), takeW(), TAKE_H)) { open(picked); return; }
        Menus.Cell c = cellAt(mx, my);
        if (c == null) return;
        if (c.nav != 0) { turn(c.nav == Menus.PREV ? -1 : 1); return; }
        if (touch() && c != picked) { picked = c; return; }
        open(c);
    }

    private void open(Menus.Cell c) {
        picked = null;
        if (c.action != null) { pick(c.action); return; }
        if (c.child != null) { PATH.add(new Step(c.child, 0)); return; }
        if (c.category != null) {
            Menus.Node root = Menus.root(c.category);
            if (root != null) { PATH.add(new Step(root, 0)); return; }
            if (c.category.count() > 0) pick(c.category.subActions.get(0).get(0));
        }
    }

    private static Catalog.Action direct(Menus.Cell c) {
        if (c == null) return null;
        if (c.action != null) return c.action;
        if (c.nav != 0 || c.child != null || c.category == null || Menus.root(c.category) != null) return null;
        return c.category.count() > 0 ? c.category.subActions.get(0).get(0) : null;
    }

    private void pick(Catalog.Action a) {
        closed = true;
        done.accept(a);
    }

    private void back() {
        if (searching()) { field.setValue(""); return; }
        if (PATH.isEmpty()) { closed = true; return; }
        PATH.remove(PATH.size() - 1);
    }

    private void turn(int by) {
        Step s = step();
        if (s == null) return;
        int to = s.page + by;
        if (to < 0 || to >= s.node.pages.size()) return;
        if (searching()) foundPage = to;
        else PATH.set(PATH.size() - 1, new Step(s.node, to));
    }

    private void refind() {
        String q = field.getValue().trim();
        picked = null;
        foundPage = 0;
        if (q.isEmpty()) { found = null; return; }
        List<Catalog.Action> hits = Catalog.search(q, Menus.FOUND_ROWS * 9 * 6);
        found = Menus.found(hits.isEmpty() ? "Ничего не найдено" : "Найдено: " + hits.size(), hits);
    }

    private Catalog.Action first() {
        Menus.Page p = searching() ? page() : null;
        if (p == null) return null;
        for (Menus.Cell c : p.cells) if (c.action != null) return c.action;
        return null;
    }

    public boolean mouseScrolled(double amount) {
        int n = Ui.rows(amount, 1, 48);
        if (n != 0) turn(n < 0 ? 1 : -1);
        return true;
    }

    public void keyPressed(KeyEvent input) {
        int key = input.key();
        boolean typed = !field.getValue().isEmpty();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (typed) field.setValue(""); else closed = true;
            return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            Catalog.Action a = first();
            if (a != null) pick(a);
            return;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) { turn(-1); return; }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) { turn(1); return; }
        if (typed && field.isFocused()) { field.keyPressed(input); return; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) back();
        else if (key == GLFW.GLFW_KEY_LEFT) turn(-1);
        else if (key == GLFW.GLFW_KEY_RIGHT) turn(1);
        else if (field.isFocused()) field.keyPressed(input);
    }

    public void charTyped(CharacterEvent input) {
        String s = input.codepointAsString();
        if (s == null || s.isBlank() && field.getValue().isEmpty()) return;
        field.setFocused(true);
        field.charTyped(input);
    }
}
