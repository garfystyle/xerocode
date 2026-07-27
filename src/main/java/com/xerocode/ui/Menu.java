package com.xerocode.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class Menu {
    public static final class Item {
        public final String label;
        public final boolean danger;
        public final String[] icon;
        public final ItemStack stack;
        public final String note;
        public final List<String> desc;
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

    private final List<Item> items;
    private final String title;
    private final int checked;
    private final IntConsumer onPick;
    private final int[] offs;
    private final int total;
    private final boolean anyRich;

    private final int x, y, w, h, listH;
    private double scroll;
    private boolean closed;

    private Menu(int screenW, int screenH, int ax, int ay, TextRenderer tr,
                 String title, List<Item> items, int checked, IntConsumer onPick) {
        this.items = items;
        this.title = title;
        this.checked = checked;
        this.onPick = onPick;

        boolean rich = false;
        for (Item it : items) rich |= it.rich();
        this.anyRich = rich;

        int textW = title == null ? 0 : tr.getWidth(title) + 24;
        for (Item it : items) {
            int inset = rich ? RICH_INK_X + 12 : 34;
            textW = Math.max(textW, tr.getWidth(it.label) + inset);
            if (it.note != null) textW = Math.max(textW, tr.getWidth(it.note) + inset);
            for (String d : it.desc) textW = Math.max(textW, tr.getWidth(d) + inset);
        }
        this.w = Math.max(96, Math.min(rich ? RICH_W : 300, textW));

        int room = Math.max(40, w - RICH_INK_X - 10);
        for (Item it : items)
            for (String d : it.desc) it.wrapped.addAll(tr.wrapLines(Text.literal(d), room));

        this.offs = new int[items.size()];
        int at = 0;
        for (int i = 0; i < items.size(); i++) {
            offs[i] = at;
            at += rowH(items.get(i));
        }
        this.total = at;

        int wanted = total + PAD * 2 + (title == null ? 0 : TITLE_H);
        this.h = Math.min(Math.min(rich ? RICH_MAX_H : MAX_H, screenH - 8), wanted);
        this.listH = h - PAD * 2 - (title == null ? 0 : TITLE_H);

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
        return new Menu(screenW, screenH, x, y, tr, title, items, checked, onPick);
    }

    public static Menu actions(int screenW, int screenH, int x, int y, TextRenderer tr,
                               List<Item> items, IntConsumer onPick) {
        return new Menu(screenW, screenH, x, y, tr, null, items, -1, onPick);
    }

    public static Menu picker(int screenW, int screenH, int x, int y, TextRenderer tr,
                              String title, List<Item> items, int checked, IntConsumer onPick) {
        return new Menu(screenW, screenH, x, y, tr, title, items, checked, onPick);
    }

    private double maxScroll() { return Math.max(0, total - listH); }
    private int listTop() { return y + PAD + (title == null ? 0 : TITLE_H); }

    public boolean isClosed() { return closed; }
    public void close() { closed = true; }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        Draw.shadow(ctx, x, y, w, h, 5);
        Draw.card(ctx, x, y, w, h, 5, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));

        if (title != null) {
            Draw.textFit(ctx, tr, title, x + 9, y + 5, w - 18, Theme.TEXT_FAINT, false);
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
            if (i == hovered) Draw.round(ctx, x + 3, iy, w - 6, rh - 1, 3,
                    Draw.opaque(it.danger ? Ui.DANGER_BG : Ui.BTN_HOVER));
            int color = it.danger ? Theme.DANGER : (i == checked ? Theme.TEXT : Theme.TEXT_DIM);
            if (i == hovered && !it.danger) color = Theme.TEXT;
            if (it.rich()) {
                int textX = x + RICH_INK_X;
                int nameY = iy + RICH_PAD;
                if (i > 0) Draw.rect(ctx, x + 6, iy - 1, w - 12, 1, Draw.opaque(Ui.LINE));
                if (it.stack != null && !it.stack.isEmpty())
                    ctx.drawItem(it.stack, x + 6, iy + (rh - 16) / 2);
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
            if (checked >= 0 && i == checked)
                Draw.glyph(ctx, Draw.CHECK, x + 7, iy + 4, Theme.ACCENT);
            if (it.icon != null)
                Draw.glyph(ctx, it.icon, x + (anyRich ? 9 : 7),
                        iy + (ITEM_H - Draw.glyphH(it.icon)) / 2,
                        it.danger ? Theme.DANGER : Theme.TEXT_DIM);
            int textX = x + (anyRich ? RICH_INK_X
                    : (checked >= 0 || it.icon != null ? 20 : 10));
            Draw.textFit(ctx, tr, it.label, textX, iy + 4, x + w - 8 - textX, color, false);
        }
        ctx.disableScissor();

        if (maxScroll() > 0) {
            int trackH = listH;
            int thumbH = Math.max(16, (int) (trackH * (listH / (double) total)));
            int thumbY = top + (int) ((trackH - thumbH) * (scroll / maxScroll()));
            Draw.round(ctx, x + w - 5, thumbY, 3, thumbH, 1, Draw.argb(0xAA, 0x5A6478));
        }
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
        if (!contains(mx, my)) { closed = true; return false; }
        int i = indexAt(mx, my);
        if (i >= 0) {
            closed = true;
            onPick.accept(i);
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * ITEM_H * 2));
        return true;
    }
}
