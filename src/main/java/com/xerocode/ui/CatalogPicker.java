package com.xerocode.ui;

import com.xerocode.Env;
import com.xerocode.Catalog;
import com.xerocode.Search;
import com.xerocode.Values;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;

public final class CatalogPicker extends PickerPanel {
    public record Item(String id, String name, String category, String icon,
                       String description, String badge, String note, ItemStack stack,
                       String alias) {
        public Item(String id, String name, String category, String icon,
                    String description, String badge, String note) {
            this(id, name, category, icon, description, badge, note, null, "");
        }

        public Item(String id, String name, String category, String icon,
                    String description, String badge, String note, ItemStack stack) {
            this(id, name, category, icon, description, badge, note, stack, "");
        }

        public ItemStack picture() {
            return stack != null ? stack : Catalog.stackOf(icon);
        }
    }

    public interface Done { void apply(String id); }

    public interface Extra {
        int height();

        void render(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY,
                    boolean flush);

        default void hover(String id) {}

        default void select(String id) {}

        default void picked(String id) { select(id); }

        default boolean mouseClicked(int mx, int my, int x, int y, int w, int h) { return false; }

        default boolean mouseDragged(int mx, int x, int y, int w, int h) { return false; }

        default void mouseReleased() {}

        default boolean keyPressed(KeyEvent in) { return false; }

        default boolean mouseScrolled(double mx, double my, double amount, int x, int y, int w, int h) {
            return false;
        }

        default String hint() { return null; }
    }

    private final int catH = Env.touch() ? 24 : 16;
    private final int rowH = Env.touch() ? 26 : 18;
    private final int lift = (rowH - 18) / 2;
    private static final int LIST_MIN = 200;

    private final Done done;
    private final String title;
    private final List<Item> all;
    private final Map<String, Integer> categories;
    private final Extra extra;

    private List<Item> hits = List.of();
    private String category;
    private String selected;
    private int scroll;
    private boolean blocks;
    private int pressedRow = -1;
    private final Ui.Bar bar = new Ui.Bar();
    private int rows;
    private final int stripH;

    public CatalogPicker(Font tr, int screenW, int screenH, String title, int accent,
                         List<Item> items, Map<String, Integer> categories, String current,
                         Extra extra, Done done) {
        super(tr, screenW, screenH, accent);
        this.title = title;
        this.all = items;
        this.categories = categories == null ? countCategories(items) : categories;
        this.done = done;
        this.extra = extra;
        this.stripH = extra == null ? 0 : extra.height() + 2;
        this.selected = current == null ? "" : current;

        layout();
        refresh(false);
        scrollToSelected();
    }

    @Override
    protected void layout() {
        int panelW = sizer().w(screenW, Ui.fitW(screenW, 620));
        int measured = tr.width("Всё") + 46;
        for (String c : this.categories.keySet()) measured = Math.max(measured, tr.width(c) + 46);
        int rail = railW(panelW, measured);
        int det = Math.min(DET_W, panelW - rail - Math.min(LIST_MIN, panelW * 45 / 100));
        det = det < 120 ? 0 : det;

        int wanted = sizer().h(screenH,
                Ui.fitH(screenH, Ui.tall(screenH, HEAD_H + 2 + 14 * rowH + stripH + footH())));
        this.rows = Math.max(3, (wanted - HEAD_H - footH() - stripH - 2) / rowH);
        place(panelW, HEAD_H + 2 + rows * rowH + stripH + footH(), rail, det, "найти…");
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private static Map<String, Integer> countCategories(List<Item> items) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Item i : items) out.merge(i.category(), 1, Integer::sum);
        return out;
    }

    @Override
    protected String sizeKey() { return "catalog"; }

    @Override
    protected int bodyH() { return rows * rowH; }

    private int listX() { return railX() + railW + 1; }
    private int listW() { return detX() - 1 - listX(); }
    private int stripY() { return bodyY() + bodyH() + 1; }
    private int stripBodyH() { return Math.max(1, footY() - 1 - stripY()); }
    private int stripX() { return x + 1; }
    private int stripW() { return w - 2; }

    @Override
    protected void refresh(boolean resetScroll) {
        String q = search == null ? "" : search.getValue().trim();
        List<Item> pool = new ArrayList<>();
        for (Item i : all) if (category == null || category.equals(i.category())) pool.add(i);
        hits = q.isEmpty() ? pool
                : Search.rank(pool, q, pool.size(), i -> new Search.Fields(
                        i.name(), i.id(), i.category(), i.description(), i.alias()));
        if (resetScroll) scroll = 0;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private int maxScroll() { return Math.max(0, hits.size() - rows); }

    private int selectedIndex() {
        for (int i = 0; i < hits.size(); i++) if (hits.get(i).id().equals(selected)) return i;
        return -1;
    }

    private void scrollToSelected() {
        int i = selectedIndex();
        if (i >= 0) scroll = Math.max(0, Math.min(maxScroll(), i - rows / 2));
    }

    private void move(int delta) {
        if (hits.isEmpty()) return;
        int i = selectedIndex();
        i = i < 0 ? (delta > 0 ? 0 : hits.size() - 1)
                  : Math.max(0, Math.min(hits.size() - 1, i + delta));
        selected = hits.get(i).id();
        if (extra != null) extra.select(selected);
        if (i < scroll) scroll = i;
        else if (i >= scroll + rows) scroll = i - rows + 1;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private Item byId(String id) {
        for (Item i : all) if (i.id().equals(id)) return i;
        return null;
    }

    private Item focused() {
        return hovered >= 0 && hovered < hits.size() ? hits.get(hovered) : byId(selected);
    }

    @Override
    protected String title() { return title; }

    @Override
    protected List<RailRow> railRows() {
        List<RailRow> out = new ArrayList<>();
        out.add(new RailRow(null, "Всё", all.size()));
        for (Map.Entry<String, Integer> e : categories.entrySet())
            out.add(new RailRow(null, e.getKey(), e.getValue()));
        return out;
    }

    @Override
    protected int railRowH() { return catH; }

    @Override
    protected int railActive() {
        if (category == null) return 0;
        int i = 1;
        for (String c : categories.keySet()) {
            if (Objects.equals(category, c)) return i;
            i++;
        }
        return -1;
    }

    @Override
    protected void railChosen(int index) {
        category = index == 0 ? null : new ArrayList<>(categories.keySet()).get(index - 1);
        refresh(true);
        scrollToSelected();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        drawList(ctx);
        drawStrip(ctx, mouseX, mouseY);
    }

    private void drawStrip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (extra == null) return;
        Ui.hairline(ctx, x + 1, bodyY() + bodyH(), w - 2);
        Item it = focused();
        extra.hover(it == null ? "" : it.id());
        extra.render(ctx, stripX(), stripY(), stripW(), stripBodyH(), mouseX, mouseY, true);
    }

    private void drawList(GuiGraphicsExtractor ctx) {
        int lx = listX(), ly = bodyY(), lw = listW(), lh = bodyH();
        Draw.rect(ctx, lx, ly, lw, lh, Draw.opaque(Ui.WELL));
        ctx.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i < 0 || i >= hits.size()) break;
            Item it = hits.get(i);
            int ry = ly + r * rowH;
            boolean on = it.id().equals(selected);
            if (on) {
                Draw.rect(ctx, lx, ry, lw, rowH, Draw.opaque(Ui.BTN_ON));
                Draw.rect(ctx, lx, ry, 2, rowH, Draw.opaque(Theme.ACCENT));
            } else if (i == hovered) {
                Draw.rect(ctx, lx, ry, lw, rowH, Draw.opaque(Ui.BTN_HOVER));
            } else if (i % 2 == 1) {
                Draw.rect(ctx, lx, ry, lw, rowH, Draw.opaque(Ui.WELL));
            }
            ctx.item(it.picture(), lx + 6, ry + 1 + lift);
            String badge = it.badge(), shown = Values.shortType(badge);
            int badgeW = badge.isEmpty() ? 0 : Draw.badgeWidth(tr, shown) + 6;
            Draw.textFit(ctx, tr, it.name(), lx + 27, ry + 5 + lift, lw - 33 - badgeW,
                    on || i == hovered ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (!badge.isEmpty()) {
                int bc = Catalog.TYPE_COLORS.getOrDefault(badge, 0x8A93A6);
                Draw.badge(ctx, tr, shown, lx + lw - 6 - Draw.badgeWidth(tr, shown), ry + 4 + lift,
                        Draw.opaque(Draw.shade(bc, -0.66f)), Draw.shade(bc, 0.15f));
            }
        }
        ctx.disableScissor();
        if (hits.isEmpty())
            Draw.textFit(ctx, tr, "ничего не найдено", lx + 10, ly + 6, lw - 16,
                    Theme.TEXT_FAINT, false);
        bar.draw(ctx, lx + lw - 4, ly + 1, lh - 2, hits.size() * rowH, lh, scroll * rowH,
                lastMx, lastMy);
    }

    @Override
    protected void drawDetails(GuiGraphicsExtractor ctx) {
        if (!detailsFrame(ctx)) return;
        Item it = focused();
        if (it == null) {
            detailsEmpty(ctx, "Выберите запись слева");
            return;
        }
        int inner = detailsInner(), tx = detailsX();
        int at = detailsHead(ctx, it.picture(), it.name(), it.category(),
                Draw.shade(accent, 0.1f));

        int bottom = detailsBottom();
        int room = Math.max(0, (bottom - at) / 10);
        int forNote = it.note().isEmpty() ? 0 : Math.min(3, room / 3);
        for (String line : Ui.wrap(tr, it.description(), inner, room - forNote)) {
            Draw.text(ctx, tr, line, tx, at, Theme.TEXT_DIM, false);
            at += 10;
        }
        if (forNote > 0 && at + 14 <= bottom) {
            at += 4;
            for (String line : Ui.wrap(tr, it.note(), inner, (bottom - at) / 10)) {
                Draw.text(ctx, tr, line, tx, at, Theme.TEXT_FAINT, false);
                at += 10;
            }
        }
    }

    @Override
    protected String footerHint() {
        String said = extra == null ? null : extra.hint();
        if (said != null) return said;
        Item sel = byId(selected);
        return sel == null ? (Env.touch() ? "" : "стрелки — выбрать, Enter — подтвердить")
                : hits.size() + " из " + all.size() + " · выбрано: " + sel.name();
    }

    @Override
    protected boolean canFinish() { return byId(selected) != null; }

    @Override
    protected void finish() {
        if (byId(selected) == null) return;
        done.apply(selected);
        closed = true;
    }

    @Override
    protected int indexAt(double mx, double my) {
        int lx = listX(), ly = bodyY();
        if (mx < lx || mx >= lx + listW() || my < ly || my >= ly + bodyH()) return -1;
        int i = scroll + (int) ((my - ly) / rowH);
        return i >= 0 && i < hits.size() ? i : -1;
    }

    public CatalogPicker forBlocks() {
        blocks = true;
        return this;
    }

    public void released(double mx, double my) {
        int i = pressedRow;
        pressedRow = -1;
        if (i < 0 || i >= hits.size() || indexAt(mx, my) != i) return;
        String id = hits.get(i).id();
        boolean focused = id.equals(selected);
        selected = id;
        if (extra != null) extra.picked(selected);
        if (focused || !Env.touch()) finish();
    }

    public void hold(double mx, double my) {
        int i = indexAt(mx, my);
        if (!blocks || i < 0) return;
        selected = hits.get(i).id();
        if (extra != null) extra.picked(selected);
    }

    @Override
    protected boolean bodyClicked(MouseButtonEvent click, boolean doubled, int mx, int my) {
        if (bar.grabbed(mx, my, rowH, maxScroll(), v -> scroll = v)) return true;
        int i = indexAt(mx, my);
        if (i >= 0 && blocks) {
            pressedRow = i;
            return true;
        }
        if (i >= 0) {
            selected = hits.get(i).id();
            if (extra != null) extra.picked(selected);
            if (doubled) finish();
            return true;
        }
        if (extra != null && Ui.hit(mx, my, stripX(), stripY(), stripW(), stripBodyH())) {
            extra.mouseClicked(mx, my, stripX(), stripY(), stripW(), stripBodyH());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (bar.dragged(click.y(), rowH, maxScroll(), v -> scroll = v)) return true;
        if (extra != null && extra.mouseDragged((int) click.x(), stripX(), stripY(), stripW(),
                stripBodyH())) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public void mouseReleased() {
        super.mouseReleased();
        bar.release();
        if (extra != null) extra.mouseReleased();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (extra != null && extra.mouseScrolled(mx, my, amount, stripX(), stripY(), stripW(),
                stripBodyH())) return true;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - Ui.rows(amount, 3, rowH)));
        return true;
    }

    @Override
    protected boolean bodyKey(KeyEvent in) {
        if (extra != null && extra.keyPressed(in)) return true;
        switch (in.key()) {
            case GLFW.GLFW_KEY_DOWN -> { move(1); return true; }
            case GLFW.GLFW_KEY_UP -> { move(-1); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { move(rows); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { move(-rows); return true; }
            default -> { return false; }
        }
    }
}
