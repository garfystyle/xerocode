package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Search;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CatalogPicker extends PickerPanel {
    public record Item(String id, String name, String category, String icon,
                       String description, String badge, String note, ItemStack stack) {
        public Item(String id, String name, String category, String icon,
                    String description, String badge, String note) {
            this(id, name, category, icon, description, badge, note, null);
        }

        public ItemStack picture() {
            return stack != null ? stack : Catalog.stackOf(icon);
        }
    }

    public interface Done { void apply(String id); }

    public interface Extra {
        int height();

        void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY,
                    boolean flush);

        default void hover(String id) {}

        default void select(String id) {}

        default boolean mouseClicked(int mx, int my, int x, int y, int w, int h) { return false; }

        default boolean mouseDragged(int mx, int x, int y, int w, int h) { return false; }

        default void mouseReleased() {}

        default boolean keyPressed(KeyInput in) { return false; }

        default String hint() { return null; }
    }

    private static final int CAT_H = 16;
    private static final int ROW_H = 18;
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
    private final Ui.Bar bar = new Ui.Bar();
    private int rows;
    private final int stripH;

    public CatalogPicker(TextRenderer tr, int screenW, int screenH, String title, int accent,
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
        int panelW = Ui.fitW(screenW, 620);
        int measured = tr.getWidth("Всё") + 46;
        for (String c : this.categories.keySet()) measured = Math.max(measured, tr.getWidth(c) + 46);
        int rail = railW(panelW, measured);
        int det = Math.min(DET_W, panelW - rail - Math.min(LIST_MIN, panelW * 45 / 100));
        det = det < 120 ? 0 : det;

        int wanted = Ui.fitH(screenH, HEAD_H + 2 + 14 * ROW_H + stripH + FOOT_H);
        this.rows = Math.max(3, (wanted - HEAD_H - FOOT_H - stripH - 2) / ROW_H);
        place(panelW, HEAD_H + 2 + rows * ROW_H + stripH + FOOT_H, rail, det, "найти…");
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private static Map<String, Integer> countCategories(List<Item> items) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Item i : items) out.merge(i.category(), 1, Integer::sum);
        return out;
    }

    @Override
    protected int bodyH() { return rows * ROW_H; }

    private int listX() { return railX() + railW + 1; }
    private int listW() { return detX() - 1 - listX(); }
    private int stripY() { return bodyY() + bodyH() + 1; }
    private int stripBodyH() { return Math.max(1, footY() - 1 - stripY()); }
    private int stripX() { return x + 1; }
    private int stripW() { return w - 2; }

    @Override
    protected void refresh(boolean resetScroll) {
        String q = search == null ? "" : search.getText().trim();
        List<Item> pool = new ArrayList<>();
        for (Item i : all) if (category == null || category.equals(i.category())) pool.add(i);
        hits = q.isEmpty() ? pool
                : Search.rank(pool, q, pool.size(), i -> new Search.Fields(
                        i.name(), i.id(), i.category(), i.description()));
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
    protected int railRowH() { return CAT_H; }

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
    protected void drawBody(DrawContext ctx, int mouseX, int mouseY, float delta) {
        drawList(ctx);
        drawStrip(ctx, mouseX, mouseY);
    }

    private void drawStrip(DrawContext ctx, int mouseX, int mouseY) {
        if (extra == null) return;
        Ui.hairline(ctx, x + 1, bodyY() + bodyH(), w - 2);
        Item it = focused();
        extra.hover(it == null ? "" : it.id());
        extra.render(ctx, stripX(), stripY(), stripW(), stripBodyH(), mouseX, mouseY, true);
    }

    private void drawList(DrawContext ctx) {
        int lx = listX(), ly = bodyY(), lw = listW(), lh = bodyH();
        Draw.rect(ctx, lx, ly, lw, lh, Draw.opaque(Ui.WELL));
        ctx.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i < 0 || i >= hits.size()) break;
            Item it = hits.get(i);
            int ry = ly + r * ROW_H;
            boolean on = it.id().equals(selected);
            if (on) {
                Draw.rect(ctx, lx, ry, lw, ROW_H, Draw.opaque(Ui.BTN_ON));
                Draw.rect(ctx, lx, ry, 2, ROW_H, Draw.opaque(Theme.ACCENT));
            } else if (i == hovered) {
                Draw.rect(ctx, lx, ry, lw, ROW_H, Draw.opaque(Ui.BTN_HOVER));
            } else if (i % 2 == 1) {
                Draw.rect(ctx, lx, ry, lw, ROW_H, Draw.opaque(Ui.WELL));
            }
            ctx.drawItem(it.picture(), lx + 6, ry + 1);
            String badge = it.badge();
            int badgeW = badge.isEmpty() ? 0 : Draw.badgeWidth(tr, badge) + 6;
            Draw.textFit(ctx, tr, it.name(), lx + 27, ry + 5, lw - 33 - badgeW,
                    on || i == hovered ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (!badge.isEmpty()) {
                int bc = Catalog.TYPE_COLORS.getOrDefault(badge, 0x8A93A6);
                Draw.badge(ctx, tr, badge, lx + lw - 6 - Draw.badgeWidth(tr, badge), ry + 4,
                        Draw.opaque(Draw.shade(bc, -0.66f)), Draw.shade(bc, 0.15f));
            }
        }
        ctx.disableScissor();
        if (hits.isEmpty())
            Draw.textFit(ctx, tr, "ничего не найдено", lx + 10, ly + 6, lw - 16,
                    Theme.TEXT_FAINT, false);
        bar.draw(ctx, lx + lw - 4, ly + 1, lh - 2, hits.size() * ROW_H, lh, scroll * ROW_H,
                lastMx, lastMy);
    }

    @Override
    protected void drawDetails(DrawContext ctx) {
        if (!detailsFrame(ctx)) return;
        Item it = focused();
        if (it == null) {
            detailsEmpty(ctx, "Выберите запись слева — здесь будет её описание.");
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
        return sel == null ? "стрелки — выбрать, Enter — подтвердить"
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
        int i = scroll + (int) ((my - ly) / ROW_H);
        return i >= 0 && i < hits.size() ? i : -1;
    }

    @Override
    protected boolean bodyClicked(Click click, boolean doubled, int mx, int my) {
        if (bar.press(mx, my)) { scroll = bar.follow(my, ROW_H, maxScroll()); return true; }
        int i = indexAt(mx, my);
        if (i >= 0) {
            selected = hits.get(i).id();
            if (extra != null) extra.select(selected);
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
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (bar.dragging()) { scroll = bar.follow(click.y(), ROW_H, maxScroll()); return true; }
        if (extra != null && extra.mouseDragged((int) click.x(), stripX(), stripY(), stripW(),
                stripBodyH())) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public void mouseReleased() {
        bar.release();
        if (extra != null) extra.mouseReleased();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount) * 3));
        return true;
    }

    @Override
    protected boolean bodyKey(KeyInput in) {
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
