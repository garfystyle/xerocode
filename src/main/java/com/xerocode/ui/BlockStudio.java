package com.xerocode.ui;

import com.xerocode.Blocks;
import com.xerocode.Catalog;
import com.xerocode.Search;
import com.xerocode.Stacks;
import net.minecraft.client.MinecraftClient;
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

public final class BlockStudio extends PickerPanel {
    public interface Done { void apply(String id); }

    private static final int TAB_H = 18, CELL = 18, GRID_MIN = 9 * CELL + 8;

    private record Rail(String name, ItemStack icon, List<Blocks.Entry> entries) {}

    private final Done done;
    private final List<Rail> rails = new ArrayList<>();

    private List<Blocks.Entry> shown = List.of();
    private Blocks.Entry selected;
    private int tab, scroll;
    private final Ui.Bar bar = new Ui.Bar();
    private int cols, rows;

    public BlockStudio(TextRenderer tr, int screenW, int screenH, int accent,
                       String current, Done done) {
        super(tr, screenW, screenH, accent);
        this.done = done;

        rails.add(new Rail("Всё", Catalog.stackOf("minecraft:grass_block"), Blocks.all()));
        List<Blocks.Entry> mine = fromInventory();
        if (!mine.isEmpty())
            rails.add(new Rail("Мой инвентарь", Catalog.stackOf("minecraft:chest"), mine));
        for (Map.Entry<String, List<Blocks.Entry>> e : byCategory().entrySet())
            rails.add(new Rail(e.getKey(), e.getValue().get(0).icon(), e.getValue()));

        layout();
        refresh(false);
        if (current != null && !current.isEmpty())
            for (Blocks.Entry e : shown)
                if (e.id().equals(current)) { selected = e; break; }
        scrollToSelected();
    }

    private static Map<String, List<Blocks.Entry>> byCategory() {
        Map<String, List<Blocks.Entry>> out = new LinkedHashMap<>();
        for (Blocks.Entry e : Blocks.all())
            out.computeIfAbsent(e.category(), key -> new ArrayList<>()).add(e);
        return out;
    }

    private static List<Blocks.Entry> fromInventory() {
        List<Blocks.Entry> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Stacks.Entry it : Stacks.inventory()) {
            String id = Blocks.of(it.stack());
            if (id == null || seen.contains(id)) continue;
            Blocks.Entry e = Blocks.entry(id);
            if (e == null) continue;
            seen.add(id);
            out.add(e);
        }
        return out;
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
        rows = Math.max(3, Math.min(20, (Ui.fitH(screenH, 1000) - HEAD_H - FOOT_H - 8) / CELL));
        place(panelW, HEAD_H + 1 + 4 + rows * CELL + 4 + FOOT_H, rail, det, "найти блок…");
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    @Override
    protected int searchMaxW() { return 220; }

    @Override
    protected int bodyH() { return rows * CELL + 8; }

    private int gridX() { return railX() + railW + 1 + 4; }

    private int gridY() { return bodyY() + 4; }

    private List<Blocks.Entry> pool() { return rails.get(tab).entries(); }

    @Override
    protected void refresh(boolean resetScroll) {
        String q = search == null ? "" : search.getText().trim();
        shown = q.isEmpty() ? pool()
                : Search.rank(pool(), q, 4000,
                        e -> new Search.Fields(e.name(), e.id(), e.category(), ""));
        if (resetScroll) scroll = 0;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + cols - 1) / cols - rows);
    }

    private int indexOfSelected() {
        if (selected == null) return -1;
        for (int i = 0; i < shown.size(); i++)
            if (shown.get(i).id().equals(selected.id())) return i;
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

    private Blocks.Entry focused() {
        return hovered >= 0 && hovered < shown.size() ? shown.get(hovered) : selected;
    }

    @Override
    protected String title() { return "Блок"; }

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
        int chosen = indexOfSelected();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = (scroll + row) * cols + col;
                if (i >= shown.size()) break;
                int cx = gx + col * CELL, cy = gy + row * CELL;
                if (i == chosen) {
                    Draw.round(ctx, cx, cy, CELL, CELL, 3, Draw.opaque(Draw.shade(accent, -0.55f)));
                    Draw.roundOutline(ctx, cx, cy, CELL, CELL, 3, Draw.opaque(accent));
                } else if (i == hovered) {
                    Draw.round(ctx, cx, cy, CELL, CELL, 3, Draw.opaque(Ui.BTN_HOVER));
                }
                ctx.drawItem(shown.get(i).icon(), cx + 1, cy + 1);
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
        Blocks.Entry it = focused();
        if (it == null) {
            detailsEmpty(ctx, "Выберите блок слева.");
            return;
        }
        int inner = detailsInner(), tx = detailsX();
        int at = detailsHead(ctx, it.icon(), it.name(), it.id(), Theme.TEXT_FAINT);
        if (at + 12 > detailsBottom()) return;
        Draw.textFit(ctx, tr, it.category(), tx, at, inner, Theme.TEXT_DIM, false);
        at += 14;
        int size = Math.min(inner, detailsBottom() - at - 4);
        if (size >= 32) {
            int px = tx + (inner - size) / 2;
            Draw.round(ctx, px, at, size, size, Ui.R_SM, Draw.opaque(Ui.WELL));
            Draw.roundOutline(ctx, px, at, size, size, Ui.R_SM, Draw.opaque(Ui.LINE_IN));
            int icon = Math.min(48, size - 12);
            Draw.item(ctx, it.icon(), px + (size - icon) / 2, at + (size - icon) / 2, icon);
        }
    }

    private int handW() { return Ui.buttonW(tr, "Взять из руки"); }

    private boolean canHand() { return Blocks.of(held()) != null; }

    private static ItemStack held() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
    }

    @Override
    protected void drawFooterLeft(DrawContext ctx, int mouseX, int mouseY, int room) {
        int fw = handW();
        if (fw + 8 > room) {
            super.drawFooterLeft(ctx, mouseX, mouseY, room);
            return;
        }
        Ui.glyphButton(ctx, tr, mouseX, mouseY, x + PAD, footY2(), fw, 16,
                Draw.LOAD, "Взять из руки", Ui.GHOST, canHand());
        Blocks.Entry it = focused();
        int left = room - fw - 8;
        if (it != null && left > 60)
            Draw.textFit(ctx, tr, it.name(), x + PAD + fw + 8, footY2() + 4, left,
                    Theme.TEXT_FAINT, false);
    }

    @Override
    protected String footerHint() {
        Blocks.Entry it = focused();
        return it == null ? "" : shown.size() + " из " + pool().size();
    }

    @Override
    protected boolean canFinish() { return selected != null; }

    @Override
    protected void finish() {
        if (selected == null) return;
        done.apply(selected.id());
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
        if (my >= footY() && Ui.hit(mx, my, x + PAD, footY2(), handW(), 16)) {
            takeFromHand();
            return true;
        }
        int i = indexAt(mx, my);
        if (i < 0) return false;
        selected = shown.get(i);
        if (doubled) finish();
        return true;
    }

    private void takeFromHand() {
        String id = Blocks.of(held());
        if (id == null) return;
        Blocks.Entry e = Blocks.entry(id);
        if (e == null) return;
        tab = 0;
        if (search != null) search.setText("");
        refresh(true);
        selected = e;
        scrollToSelected();
    }

    @Override
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
    public void mouseReleased() {
        bar.release();
        super.mouseReleased();
    }

    @Override
    protected boolean bodyKey(KeyInput in) {
        switch (in.key()) {
            case GLFW.GLFW_KEY_RIGHT -> { move(1); return true; }
            case GLFW.GLFW_KEY_LEFT -> { move(-1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { move(cols); return true; }
            case GLFW.GLFW_KEY_UP -> { move(-cols); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { move(cols * rows); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { move(-cols * rows); return true; }
            default -> { return false; }
        }
    }
}
