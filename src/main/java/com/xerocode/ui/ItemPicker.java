package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Stacks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ItemPicker extends PickerPanel {
    public interface Done { void apply(ItemStack stack); }

    private static final int TAB_H = 18;
    private static final int CELL = 18;
    private static final int GRID_MIN = 9 * CELL + 8;

    private record Rail(String name, ItemStack icon, List<Stacks.Entry> entries) {}

    private final Done done;
    private final List<Rail> rails = new ArrayList<>();

    private List<Stacks.Entry> shown = List.of();
    private Stacks.Entry selected;
    private int tab, scroll;
    private final Ui.Bar bar = new Ui.Bar();
    private int cols, rows;

    public ItemPicker(TextRenderer tr, int screenW, int screenH, int accent,
                      ItemStack current, Done done) {
        super(tr, screenW, screenH, accent);
        this.done = done;

        Stacks.refresh();
        rails.add(new Rail("Всё", Catalog.stackOf("minecraft:compass"), Stacks.all()));
        List<Stacks.Entry> mine = Stacks.inventory();
        if (!mine.isEmpty())
            rails.add(new Rail("Мой инвентарь", Catalog.stackOf("minecraft:chest"), mine));
        for (Stacks.Tab t : Stacks.tabs()) rails.add(new Rail(t.name(), t.icon(), t.entries()));

        layout();
        refresh(false);
        if (current != null && !current.isEmpty()) {
            for (Stacks.Entry e : shown)
                if (same(e.stack(), current)) { selected = e; break; }
            if (selected == null)
                for (Stacks.Entry e : shown)
                    if (e.id().equals(Stacks.idOf(current))) { selected = e; break; }
            scrollToSelected();
        }
    }

    @Override
    protected void layout() {
        int panelW = Ui.fitW(screenW, 700);
        int measured = tr.getWidth("Мой инвентарь") + 46;
        for (Rail r : rails) measured = Math.max(measured, tr.getWidth(r.name()) + 46);
        int rail = railW(panelW, measured);
        int det = Math.min(DET_W, panelW - rail - Math.min(GRID_MIN, panelW * 45 / 100));
        det = det < 130 ? 0 : det;
        cols = Math.max(3, (panelW - rail - det - 10) / CELL);
        rows = Math.max(3, Math.min(20,
                (Ui.fitH(screenH, 1000) - HEAD_H - FOOT_H - 8) / CELL));
        place(panelW, HEAD_H + 1 + 4 + rows * CELL + 4 + FOOT_H, rail, det, "найти предмет…");
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getComponentChanges().equals(b.getComponentChanges());
    }

    @Override
    protected int searchMaxW() { return 220; }

    @Override
    protected int bodyH() { return rows * CELL + 8; }

    private int gridX() { return railX() + railW + 1 + 4; }
    private int gridY() { return bodyY() + 4; }

    private List<Stacks.Entry> pool() { return rails.get(tab).entries(); }

    @Override
    protected void refresh(boolean resetScroll) {
        String q = search == null ? "" : search.getText().trim();
        shown = q.isEmpty() ? pool() : Stacks.search(pool(), q, 4000);
        if (resetScroll) scroll = 0;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + cols - 1) / cols - rows);
    }

    private int indexOfSelected() {
        if (selected == null) return -1;
        for (int i = 0; i < shown.size(); i++) if (shown.get(i) == selected) return i;
        for (int i = 0; i < shown.size(); i++)
            if (same(shown.get(i).stack(), selected.stack())) return i;
        return -1;
    }

    private void scrollToSelected() {
        int i = indexOfSelected();
        if (i < 0) return;
        int row = i / cols;
        if (row < scroll) scroll = row;
        else if (row >= scroll + rows) scroll = row - rows + 1;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private void move(int delta) {
        if (shown.isEmpty()) return;
        int i = indexOfSelected();
        i = i < 0 ? (delta > 0 ? 0 : shown.size() - 1)
                  : Math.max(0, Math.min(shown.size() - 1, i + delta));
        selected = shown.get(i);
        scrollToSelected();
    }

    private Stacks.Entry focused() {
        return hovered >= 0 && hovered < shown.size() ? shown.get(hovered) : selected;
    }

    @Override
    protected String title() { return "Предмет"; }

    @Override
    protected List<RailRow> railRows() {
        List<RailRow> out = new ArrayList<>(rails.size());
        for (Rail r : rails) out.add(new RailRow(r.icon(), r.name(), r.entries().size()));
        return out;
    }

    @Override
    protected int railRowH() { return TAB_H; }

    @Override
    protected int railActive() { return tab; }

    @Override
    protected void railChosen(int index) {
        tab = index;
        refresh(true);
        scrollToSelected();
    }

    @Override
    protected void drawBody(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int gx = gridX(), gy = gridY(), gw = cols * CELL, gh = rows * CELL;
        Draw.round(ctx, gx - 3, gy - 3, gw + 6, gh + 6, Ui.R_SM, Draw.opaque(Ui.WELL));
        int selectedIndex = indexOfSelected();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = (scroll + row) * cols + col;
                if (i >= shown.size()) break;
                int cx = gx + col * CELL, cy = gy + row * CELL;
                if (i == selectedIndex) {
                    Draw.round(ctx, cx, cy, CELL, CELL, 3, Draw.opaque(Draw.shade(accent, -0.55f)));
                    Draw.roundOutline(ctx, cx, cy, CELL, CELL, 3, Draw.opaque(accent));
                } else if (i == hovered) {
                    Draw.round(ctx, cx, cy, CELL, CELL, 3, Draw.opaque(Ui.BTN_HOVER));
                }
                ItemStack st = shown.get(i).stack();
                ctx.drawItem(st, cx + 1, cy + 1);
                if (st.getCount() != 1) ctx.drawStackOverlay(tr, st, cx + 1, cy + 1);
            }
        }
        if (shown.isEmpty())
            Draw.textFit(ctx, tr, "ничего не найдено", gx + 4, gy + 5, gw - 8,
                    Theme.TEXT_FAINT, false);
        if (maxScroll() > 0) {
            int gridRows = (shown.size() + cols - 1) / cols;
            bar.draw(ctx, gx + gw + 1, gy, gh, gridRows * CELL, gh, scroll * CELL, lastMx, lastMy);
        }
    }

    @Override
    protected void drawDetails(DrawContext ctx) {
        if (!detailsFrame(ctx)) return;
        Stacks.Entry it = focused();
        if (it == null) {
            detailsEmpty(ctx, "Выберите предмет слева — здесь будет его подсказка.");
            return;
        }
        int inner = detailsInner(), tx = detailsX();
        int at = detailsHead(ctx, it.stack(), it.name(), it.id(), Theme.TEXT_FAINT);

        int bottom = detailsBottom();
        List<Text> lines = Stacks.tooltip(it.stack());
        for (int i = 1; i < lines.size() && at + 10 <= bottom; i++) {
            ctx.drawText(tr, McText.fit(tr, McText.runsOf(lines.get(i)), inner), tx, at,
                    Draw.opaque(Theme.TEXT_DIM), false);
            at += 10;
        }
        if (!it.stack().getComponentChanges().isEmpty() && at + 14 <= bottom) {
            at += 4;
            Draw.textFit(ctx, tr, "со своими компонентами", tx, at, inner, Theme.TEXT_FAINT, false);
        }
    }

    @Override
    protected String footerHint() {
        Stacks.Entry it = focused();
        return it == null ? "стрелки — выбрать, Enter — подтвердить"
                : shown.size() + " из " + pool().size() + " · " + it.name();
    }

    @Override
    protected boolean canFinish() { return selected != null; }

    @Override
    protected void finish() {
        if (selected == null) return;
        done.apply(selected.stack().copy());
        closed = true;
    }

    @Override
    protected int indexAt(double mx, double my) {
        int gx = gridX(), gy = gridY();
        if (mx < gx || mx >= gx + cols * CELL || my < gy || my >= gy + rows * CELL) return -1;
        int col = (int) ((mx - gx) / CELL), row = (int) ((my - gy) / CELL);
        int i = (scroll + row) * cols + col;
        return i >= 0 && i < shown.size() ? i : -1;
    }

    @Override
    protected boolean bodyClicked(Click click, boolean doubled, int mx, int my) {
        if (bar.grabbed(mx, my, CELL, maxScroll(), v -> scroll = v)) return true;
        int i = indexAt(mx, my);
        if (i < 0) return false;
        selected = shown.get(i);
        if (doubled) finish();
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(amount) * 2));
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (bar.dragged(click.y(), CELL, maxScroll(), v -> scroll = v)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public void mouseReleased() { bar.release(); }

    @Override
    protected boolean bodyKey(KeyInput in) {
        switch (in.key()) {
            case GLFW.GLFW_KEY_RIGHT -> { move(1); return true; }
            case GLFW.GLFW_KEY_LEFT -> { move(-1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { move(cols); return true; }
            case GLFW.GLFW_KEY_UP -> { move(-cols); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { move(cols * rows); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { move(-cols * rows); return true; }
            case GLFW.GLFW_KEY_TAB -> {
                tab = (tab + ((in.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0
                        ? rails.size() - 1 : 1)) % rails.size();
                refresh(true);
                return true;
            }
            default -> { return false; }
        }
    }
}
