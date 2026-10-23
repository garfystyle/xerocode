package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Script;
import com.xerocode.Value;
import com.xerocode.Values;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

final class Carry {
    private static final int MAX = 4;
    private static final int MAX_W = 190, GAP = 2, MORE_H = 11;

    private Carry() {}

    static List<Value> values(Layout.Box box, Layout.Chip chip) {
        if (box == null || chip == null || (!chip.isArg() && !chip.isCell())) return List.of();
        Script.Node n = Layout.chipNode(box.node);
        if (chip.argIndex < 0 || chip.argIndex >= n.args().size()) return List.of();
        List<Value> all = n.values.get(chip.argIndex);
        if (all == null || all.isEmpty()) return List.of();
        if (chip.isCell())
            return chip.cell < all.size() && !all.get(chip.cell).isBlank()
                    ? List.of(all.get(chip.cell)) : List.of();
        List<Value> out = new ArrayList<>(all.size());
        for (Value v : all) if (!v.isBlank()) out.add(v);
        return out;
    }

    static boolean paramCell(Layout.Box box, Layout.Chip chip) {
        return chip.isCell() && chip.argIndex == Catalog.FN_PARAMS && Layout.chipNode(box.node).declares();
    }

    static List<Value> inner(Value v) {
        if (!Value.ARRAY.equals(v.type)) return List.of();
        List<Value> out = new ArrayList<>();
        for (Value it : v.items) if (!it.isBlank()) out.add(it);
        return out.size() >= 2 ? out : List.of();
    }

    static ItemStack icon(Value v) {
        ItemStack own = BlockView.itemIcon(v);
        if (!own.isEmpty()) return own;
        Values.Kind k = Values.kind(v.type);
        return k == null ? ItemStack.EMPTY : Catalog.stackOf(k.item());
    }

    static boolean fits(Script.Node n, int argIndex, Value v) {
        if ("Параметр".equals(n.args().get(argIndex).type))
            return Value.PARAMETER.equals(v.type);
        if (n.declares()) {
            if (argIndex == Catalog.FN_NAME || argIndex == Catalog.FN_DESC
                    || argIndex == Catalog.FN_DISPLAY) return Value.TEXT.equals(v.type);
            if (argIndex == Catalog.FN_ICON) return Value.ITEM.equals(v.type);
        }
        return Values.EDITABLE.contains(v.type);
    }

    static boolean accepts(List<Value> carry, Layout.Box box, Layout.Chip chip) {
        if (carry == null || box == null || chip == null) return false;
        if (!chip.isArg() && !chip.isCell()) return false;
        Script.Node n = Layout.chipNode(box.node);
        if (chip.argIndex < 0 || chip.argIndex >= n.args().size()) return false;
        for (Value v : carry) if (!fits(n, chip.argIndex, v)) return false;
        return true;
    }

    static int put(Layout.Box box, Layout.Chip chip, List<Value> values) {
        Script.Node n = Layout.chipNode(box.node);
        List<Value> dst = n.valuesOf(chip.argIndex);
        Catalog.Arg a = n.args().get(chip.argIndex);
        if (chip.isCell()) {
            while (dst.size() <= chip.cell) dst.add(Value.blank());
            dst.set(chip.cell, values.get(0).copy());
            return 1;
        }
        if (a.list) {
            int put = 0;
            for (Value v : values) {
                if (dst.size() >= a.capacity) break;
                dst.add(v.copy());
                put++;
            }
            return put;
        }
        dst.clear();
        dst.add(values.get(0).copy());
        return 1;
    }

    static void draw(GuiGraphicsExtractor ctx, Font font, List<Value> carry, boolean held,
                     int mouseX, int mouseY, int screenW, int screenH) {
        if (carry == null) return;
        ctx.nextStratum();
        int shown = Math.min(carry.size(), MAX);
        String more = carry.size() > shown ? "и ещё " + (carry.size() - shown) : null;

        int w = 0;
        for (int i = 0; i < shown; i++) {
            Value v = carry.get(i);
            String note = v.note();
            w = Math.max(w, Layout.CHIP_INK_X + font.width(v.label()) + 7
                    + (note.isEmpty() ? 0 : font.width(note) + 6));
        }
        if (more != null) w = Math.max(w, font.width(more) + 22);
        w = Math.max(Layout.CHIP_MIN_W, Math.min(MAX_W, w));

        int step = Layout.CHIP_H + GAP;
        int h = shown * step - GAP + (more == null ? 0 : MORE_H);
        int edge = held ? 2 : 10;
        int x = held ? mouseX - w / 2 : mouseX + 11;
        int y = held ? mouseY - h / 2 : mouseY + 13;
        x = Math.max(edge, Math.min(x, screenW - w - 2));
        y = Math.max(Theme.TOPBAR_H + edge, Math.min(y, screenH - h - 3));

        for (int i = 0; i < shown; i++) pill(ctx, font, carry.get(i), x, y + i * step, w, held);
        if (more != null)
            Draw.textFit(ctx, font, more, x + Layout.CHIP_INK_X,
                    y + shown * step + 1, w - Layout.CHIP_INK_X - 4, Theme.TEXT_DIM, true);
        if (!held) badge(ctx, x - 8, y - 8);
    }

    private static void pill(GuiGraphicsExtractor ctx, Font font, Value v, int x, int y, int w,
                             boolean held) {
        int tc = v.color();
        int ink = Layout.ink(Draw.isLight(tc));

        Draw.shadow(ctx, x, y, w, Layout.CHIP_H, 7);
        BlockView.pill(ctx, x, y, w, tc, true, held ? 0xE0 : 0xFF);

        ItemStack stack = BlockView.itemIcon(v);
        BlockView.badge(ctx, x, y, stack, Draw.opaque(Draw.shade(tc, -0.55f)));
        int textX = x + (stack.isEmpty() ? Layout.CHIP_INK_X : Layout.CHIP_ITEM_INK_X);

        int right = x + w - 6;
        String note = v.note();
        if (!note.isEmpty()) {
            int nw = font.width(note);
            if (right - nw - textX > 24) {
                Draw.text(ctx, font, note, right - nw, y + 4,
                        Draw.shade(tc, -0.42f), false);
                right -= nw + 4;
            }
        }
        Draw.textFit(ctx, font, v.label(), textX, y + 4, right - textX, ink, false);
    }

    private static void badge(GuiGraphicsExtractor ctx, int x, int y) {
        Draw.round(ctx, x, y, 11, 11, 3, Draw.opaque(Theme.ACCENT));
        Draw.roundOutline(ctx, x, y, 11, 11, 3, Draw.opaque(Draw.shade(Theme.ACCENT, -0.45f)));
        Draw.glyph(ctx, Draw.PLUS, x + 3, y + 3, Theme.ON_ACCENT);
    }
}
