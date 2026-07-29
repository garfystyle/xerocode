package com.xerocode.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class Menu {
    public static final class Item {
        public final String label;
        public final boolean danger;
        public final String[] icon;
        public final ItemStack stack;
        public final String note;
        public final List<String> desc;
        public boolean enabled = true;
        final List<OrderedText> wrapped = new ArrayList<>();

        public Item(String label) { this(label, false, null); }
        public Item(String label, boolean danger, String[] icon) {
            this(label, danger, icon, null, null, List.of());
        }
        private Item(String label, boolean danger, String[] icon,
                     ItemStack stack, String note, List<String> desc) {
            this.label = label; this.danger = danger; this.icon = icon;
            this.stack = stack; this.note = note;
            this.desc = desc == null ? List.of() : desc;
        }

        public static Item rich(String label, ItemStack stack, String note, List<String> desc) {
            List<String> lines = new ArrayList<>();
            if (desc != null) for (String d : desc) if (d != null && !d.isBlank()) lines.add(d);
            return new Item(label, false, null, stack,
                    note == null || note.isBlank() ? null : note, lines);
        }

        boolean rich() { return stack != null || note != null || !desc.isEmpty(); }
    }

    private static final int ITEM_H = 15;
    private static final int TITLE_H = 16;
    private static final int MAX_H = 232;
    private static final int RICH_W = 360, RICH_MAX_H = 320;
    private static final int PAD = 4;
    private static final int RICH_PAD = 3, LINE = 9;
    private static final int RICH_INK_X = 26;
    private static final int FOOT_H = 24;
    private static final int TICK = 9, TICK_W = 14, ALL_W = 46;

    private final List<Item> items;
    private final String title;
    private final int checked;
    private final IntConsumer onPick;
    private final int[] offs;
    private final int total;
    private final boolean anyRich;

    private record Multi(boolean[] on, String confirm, Consumer<List<Integer>> onDone) {}

    private final Multi multi;
    private final boolean[] ticked;
    private final int inkX;
    private final int footH;

    private final int x, y, w, h, listH;
    private double scroll;
    private final Ui.Bar bar = new Ui.Bar();
    private int lastMx, lastMy;
    private boolean closed;

    private boolean multi() { return multi != null; }

    private Menu(int screenW, int screenH, int ax, int ay, TextRenderer tr,
                 String title, List<Item> items, int checked, IntConsumer onPick, Multi multi) {
        this.items = items;
        this.title = title;
        this.checked = checked;
        this.onPick = onPick;
        this.multi = multi;
        this.ticked = new boolean[items.size()];
        boolean[] on = multi == null ? null : multi.on();
        if (on != null) System.arraycopy(on, 0, ticked, 0, Math.min(on.length, ticked.length));
        this.footH = multi() ? FOOT_H : 0;

        boolean rich = false;
        for (Item it : items) rich |= it.rich();
        this.anyRich = rich;
        this.inkX = RICH_INK_X + (multi() ? TICK_W : 0);

        int textW = title == null ? 0 : tr.getWidth(title) + (multi() ? 70 : 24);
        for (Item it : items) {
            int inset = rich ? inkX + 12 : 34 + (multi() ? TICK_W : 0);
            textW = Math.max(textW, tr.getWidth(it.label) + inset);
            if (it.note != null) textW = Math.max(textW, tr.getWidth(it.note) + inset);
            for (String d : it.desc) textW = Math.max(textW, tr.getWidth(d) + inset);
        }
        if (multi()) textW = Math.max(textW, ALL_W + 17 + tr.getWidth(multi.confirm() + " (00)") + 22);
        this.w = Math.max(Math.min(96, screenW - 4),
                Math.min(Math.min(rich ? RICH_W : 300, screenW - 4), textW));

        int room = Math.max(40, w - inkX - 10);
        for (Item it : items)
            for (String d : it.desc) it.wrapped.addAll(tr.wrapLines(Text.literal(d), room));

        this.offs = new int[items.size()];
        int at = 0;
        for (int i = 0; i < items.size(); i++) {
            offs[i] = at;
            at += rowH(items.get(i));
        }
        this.total = at;

        int wanted = total + PAD * 2 + (title == null ? 0 : TITLE_H) + footH;
        this.h = Math.min(Math.min(rich ? RICH_MAX_H : MAX_H, screenH - 8), wanted);
        this.listH = h - PAD * 2 - (title == null ? 0 : TITLE_H) - footH;

        int px = Math.max(2, Math.min(ax, screenW - w - 2));
        int py = ay + h > screenH - 2 ? Math.max(2, screenH - h - 2) : ay;
        this.x = px;
        this.y = py;

        if (checked >= 0 && checked < items.size()) {
            double target = offs[checked] - listH / 2.0 + rowH(items.get(checked)) / 2.0;
            scroll = Math.max(0, Math.min(maxScroll(), target));
        }
    }

    private static int rowH(Item it) {
        if (!it.rich()) return ITEM_H;
        int lines = 1 + (it.note == null ? 0 : 1) + it.wrapped.size();
        return Math.max(22, RICH_PAD * 2 + lines * LINE - 1);
    }

    public static Menu options(int screenW, int screenH, int x, int y, TextRenderer tr,
                               String title, List<String> options, int checked, IntConsumer onPick) {
        List<Item> items = new ArrayList<>();
        for (String o : options) items.add(new Item(o));
        return picker(screenW, screenH, x, y, tr, title, items, checked, onPick);
    }

    public static Menu actions(int screenW, int screenH, int x, int y, TextRenderer tr,
                               List<Item> items, IntConsumer onPick) {
        return picker(screenW, screenH, x, y, tr, null, items, -1, onPick);
    }

    public static Menu picker(int screenW, int screenH, int x, int y, TextRenderer tr,
                              String title, List<Item> items, int checked, IntConsumer onPick) {
        return new Menu(screenW, screenH, x, y, tr, title, items, checked, onPick, null);
    }

    public static Menu multi(int screenW, int screenH, int x, int y, TextRenderer tr,
                             String title, List<Item> items, boolean[] on, String confirm,
                             Consumer<List<Integer>> onDone) {
        return new Menu(screenW, screenH, x, y, tr, title, items, -1, i -> {},
                new Multi(on, confirm, onDone));
    }

    private int count() {
        int n = 0;
        for (boolean b : ticked) if (b) n++;
        return n;
    }

    private void done() {
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < ticked.length; i++) if (ticked[i]) picked.add(i);
        multi.onDone().accept(picked);
    }

    private int footY() { return y + h - FOOT_H + 4; }
    private int footBtnH() { return FOOT_H - 8; }
    private int confirmX() { return x + 6 + ALL_W + 5; }
    private int confirmW() { return x + w - 6 - confirmX(); }

    private static void tickBox(DrawContext ctx, int x, int y, boolean on, boolean hov) {
        Draw.round(ctx, x, y, TICK, TICK, 2, Draw.opaque(on ? Theme.ACCENT : Ui.WELL));
        Draw.roundOutline(ctx, x, y, TICK, TICK, 2,
                Draw.opaque(on ? Draw.shade(Theme.ACCENT, -0.30f) : hov ? Ui.BORDER : Ui.LINE_IN));
        if (on) Draw.glyph(ctx, Draw.CHECK, x + 2, y + 2, Theme.ON_ACCENT);
    }

    private double maxScroll() { return Math.max(0, total - listH); }
    private int listTop() { return y + PAD + (title == null ? 0 : TITLE_H); }

    public boolean isClosed() { return closed; }
    public void close() { closed = true; }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        lastMx = mouseX;
        lastMy = mouseY;
        Draw.shadow(ctx, x, y, w, h, 5);
        Draw.card(ctx, x, y, w, h, 5, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));

        int ticks = multi() ? count() : 0;
        if (title != null) {
            String tally = multi() ? ticks + " из " + items.size() : null;
            int tallyW = tally == null ? 0 : tr.getWidth(tally) + 8;
            Draw.textFit(ctx, tr, title, x + 9, y + 5, w - 18 - tallyW, Theme.TEXT_FAINT, false);
            if (tally != null)
                Draw.textRight(ctx, tr, tally, x + w - 9, y + 5,
                        ticks > 0 ? Theme.ACCENT : Theme.TEXT_FAINT, false);
            Draw.rect(ctx, x + 6, y + TITLE_H - 2, w - 12, 1, Draw.opaque(Ui.LINE));
        }

        int top = listTop();
        ctx.enableScissor(x + 1, top, x + w - 1, top + listH);
        int hovered = indexAt(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int rh = rowH(it);
            int iy = top + offs[i] - (int) Math.round(scroll);
            if (iy + rh < top || iy > top + listH) continue;
            if (i == hovered && it.enabled) Draw.round(ctx, x + 3, iy, w - 6, rh - 1, 3,
                    Draw.opaque(it.danger ? Ui.DANGER_BG : Ui.BTN_HOVER));
            int color = it.danger ? Theme.DANGER : (i == checked ? Theme.TEXT : Theme.TEXT_DIM);
            if (i == hovered && !it.danger && it.enabled) color = Theme.TEXT;
            if (!it.enabled) color = Theme.TEXT_FAINT;
            if (it.rich()) {
                int textX = x + inkX;
                int nameY = iy + RICH_PAD;
                if (i > 0) Draw.rect(ctx, x + 6, iy - 1, w - 12, 1, Draw.opaque(Ui.LINE));
                if (multi()) tickBox(ctx, x + 7, iy + (rh - TICK) / 2, ticked[i], i == hovered);
                if (it.stack != null && !it.stack.isEmpty())
                    ctx.drawItem(it.stack, x + (multi() ? 6 + TICK_W : 6), iy + (rh - 16) / 2);
                int right = x + w - 8;
                if (i == checked) {
                    Draw.glyph(ctx, Draw.CHECK, right - Draw.glyphW(Draw.CHECK), nameY + 1,
                            Theme.ACCENT);
                    right -= Draw.glyphW(Draw.CHECK) + 5;
                }
                Draw.textFit(ctx, tr, it.label, textX, nameY, right - textX, Theme.TEXT, false);
                int ly = nameY + LINE;
                int room = x + w - 8 - textX;
                if (it.note != null) {
                    Draw.textFit(ctx, tr, it.note, textX, ly, room, Theme.TEXT_DIM, false);
                    ly += LINE;
                }
                for (OrderedText d : it.wrapped) {
                    Draw.text(ctx, tr, d, textX, ly, Theme.TEXT_FAINT, false);
                    ly += LINE;
                }
                continue;
            }
            if (anyRich && i > 0) Draw.rect(ctx, x + 6, iy - 1, w - 12, 1, Draw.opaque(Ui.LINE));
            if (multi()) tickBox(ctx, x + 7, iy + (ITEM_H - TICK) / 2, ticked[i], i == hovered);
            else if (checked >= 0 && i == checked)
                Draw.glyph(ctx, Draw.CHECK, x + 7, iy + 4, Theme.ACCENT);
            if (it.icon != null)
                Draw.glyph(ctx, it.icon, x + (anyRich ? 9 : 7) + (multi() ? TICK_W : 0),
                        iy + (ITEM_H - Draw.glyphH(it.icon)) / 2,
                        !it.enabled ? Theme.TEXT_FAINT
                                : it.danger ? Theme.DANGER : Theme.TEXT_DIM);
            int plain = (checked >= 0 || it.icon != null ? 20 : 10)
                    + (multi() ? (it.icon != null ? TICK_W : 10) : 0);
            int textX = x + (anyRich ? inkX : plain);
            Draw.textFit(ctx, tr, it.label, textX, iy + 4, x + w - 8 - textX, color, false);
        }
        ctx.disableScissor();

        if (maxScroll() > 0)
            bar.draw(ctx, x + w - 5, top, listH, total, listH, (int) Math.round(scroll),
                    lastMx, lastMy);

        if (multi()) drawFoot(ctx, tr, mouseX, mouseY, ticks);
    }

    private void drawFoot(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, int ticks) {
        Draw.rect(ctx, x + 6, y + h - FOOT_H, w - 12, 1, Draw.opaque(Ui.LINE));
        String confirm = multi.confirm();
        Ui.button(ctx, tr, mouseX, mouseY, x + 6, footY(), ALL_W, footBtnH(),
                ticks == items.size() ? "Снять" : "Все", Ui.GHOST);
        Ui.button(ctx, tr, mouseX, mouseY, confirmX(), footY(), confirmW(), footBtnH(),
                ticks > 0 ? confirm + " (" + ticks + ")" : confirm, Ui.ACCENT, ticks > 0);
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int indexAt(double mx, double my) {
        if (mx < x || mx >= x + w) return -1;
        int top = listTop();
        if (my < top || my >= top + listH) return -1;
        double rel = my - top + scroll;
        for (int i = 0; i < items.size(); i++)
            if (rel >= offs[i] && rel < offs[i] + rowH(items.get(i))) return i;
        return -1;
    }

    public boolean mouseClicked(double mx, double my) {
        if (!contains(mx, my)) {
            closed = true;
            if (multi() && count() > 0) done();
            return false;
        }
        if (multi() && my >= y + h - FOOT_H) {
            if (Ui.hit(mx, my, x + 6, footY(), ALL_W, footBtnH())) {
                boolean all = count() == items.size();
                for (int i = 0; i < ticked.length; i++) ticked[i] = !all;
                return true;
            }
            if (Ui.hit(mx, my, confirmX(), footY(), confirmW(), footBtnH()) && count() > 0) {
                closed = true;
                done();
            }
            return true;
        }
        if (bar.grabbed(mx, my, 1, (int) maxScroll(), v -> scroll = v)) return true;
        int i = indexAt(mx, my);
        if (i >= 0 && !items.get(i).enabled) return true;
        if (i >= 0) {
            if (multi()) { ticked[i] = !ticked[i]; return true; }
            closed = true;
            onPick.accept(i);
        }
        return true;
    }

    public boolean mouseDragged(double my) {
        return bar.dragged(my, 1, (int) maxScroll(), v -> scroll = v);
    }

    public void mouseReleased() { bar.release(); }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * ITEM_H * 2));
        return true;
    }
}
