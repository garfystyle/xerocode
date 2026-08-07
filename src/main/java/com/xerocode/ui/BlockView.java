package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.item.ItemStack;

public final class BlockView {
    public interface Accepts { boolean test(Layout.Box box, Layout.Chip chip); }

    public static final class Look {
        public Layout.Box hover;
        public Layout.Chip chip;
        public boolean dragging;
        public boolean carrying;
        public Accepts accepts;
        public double mx, my;

        public static final Look PLAIN = new Look();
    }

    public static int color(Script.Node n) {
        int base = n.action.category == null ? 0x7A7A7A : n.action.category.color;
        return n.action.unavailable ? Draw.mix(base, 0x8A8A8A, 0.4f) : base;
    }

    public static void shadow(DrawContext ctx, Layout.Box box) {
        Draw.shadow(ctx, box.x, box.y + box.hatH, box.w, box.headerH - box.hatH, 1);
        if (box.node.wraps()) Draw.shadow(ctx, box.x, box.armY(), box.w, Layout.ARM_H, 1);
    }

    public static void paint(DrawContext ctx, TextRenderer tr, Layout layout,
                             ScreenRect area, Look look) {
        Draw.batch(Batch.open(ctx, area, area, 256));
        for (Layout.Box b : layout.boxes) shadow(ctx, b);
        Draw.batch(null);
        if (layout.chunks.isEmpty()) {
            Draw.batch(Batch.open(ctx, area, area, 1024));
            for (Layout.Box b : layout.boxes) block(ctx, tr, b, look);
            Draw.batch(null);
            return;
        }
        for (Layout.Chunk chunk : layout.chunks) {
            Draw.batch(Batch.open(ctx, area, area, 512));
            for (int i = chunk.from; i < chunk.to; i++) block(ctx, tr, layout.boxes.get(i), look);
            Draw.batch(null);
        }
    }

    private static int rampAt(int y0, int span, int top, int bottom, int y) {
        if (span <= 1) return top;
        float t = (y - y0) / (float) span;
        return Draw.mixArgb(top, bottom, Math.max(0f, Math.min(1f, t)));
    }

    public static void block(DrawContext ctx, TextRenderer tr, Layout.Box box, Look look) {
        if (Audit.on()) Audit.role("block");
        Script.Node n = box.node;
        boolean hovered = look.hover == box && look.chip == null && !look.dragging;
        boolean grad = Settings.gradient();
        int base = hovered ? color(n) : 0;
        int top = hovered ? Draw.opaque(Draw.shade(base, grad ? 0.28f : 0.16f)) : box.top;
        int bottom = hovered ? (grad ? Draw.opaque(Draw.shade(base, 0.02f)) : top) : box.bottom;
        int border = hovered ? Draw.opaque(Draw.shade(base, -0.28f)) : box.border;
        boolean wraps = n.wraps();
        int headerBottom = box.y + box.headerH;

        int y0 = box.y + box.hatH;
        int span = Math.max(1, box.totalH - box.hatH - 1);

        if (wraps) {
            int armY = box.armY();
            int spineY = headerBottom - 1, spineH = armY - headerBottom + 2;
            Draw.blockShape(ctx, box.x, spineY, Layout.INDENT + 1, spineH, 0, 0,
                    rampAt(y0, span, top, bottom, spineY),
                    rampAt(y0, span, top, bottom, spineY + spineH - 1), border);
            Draw.blockShape(ctx, box.x, armY, box.w, Layout.ARM_H, box.coverFrom, box.coverTo,
                    rampAt(y0, span, top, bottom, armY + 1),
                    rampAt(y0, span, top, bottom, armY + Layout.ARM_H - 2), border);
            Draw.rect(ctx, box.x + 1, armY, Layout.INDENT - 1, 1,
                    rampAt(y0, span, top, bottom, armY));
        }

        Draw.blockShape(ctx, box.x, box.y, box.w, box.headerH,
                wraps ? box.mouthFrom : box.coverFrom, wraps ? box.mouthTo : box.coverTo,
                rampAt(y0, span, top, bottom, box.y + 1),
                rampAt(y0, span, top, bottom, headerBottom - 2), border);
        if (wraps) Draw.rect(ctx, box.x + 1, headerBottom - 1, Layout.INDENT - 1, 1,
                rampAt(y0, span, top, bottom, headerBottom - 1));

        if (n.isHat())
            Draw.rect(ctx, box.x + 4, box.y + 2, box.w - 8, 3, Draw.argb(0x4D, 0xFFFFFF));
        else
            Draw.rect(ctx, box.x + 1, box.y + 1, box.w - 2, 1, Draw.argb(0x2E, 0xFFFFFF));

        int iconY = box.y + box.hatH + 5;
        boolean lightHead = hovered
                ? Draw.isLight(Draw.shade(base, grad ? 0.28f : 0.16f)) : box.lightHead;
        int ink = hovered ? (lightHead ? 0x141821 : 0xFFFFFF) : box.ink;
        if (box.card != null) {
            card(ctx, tr, box, look, ink, lightHead, top);
            for (Layout.Chip chip : box.chips) chip(ctx, tr, box, chip, look);
            if (Audit.on()) Audit.clearRole();
            return;
        }
        ctx.drawItem(n.action.icon(), box.x + Layout.PAD - 1, iconY);
        Draw.text(ctx, tr, box.title, box.x + Layout.PAD + 20, iconY + 4, ink, !lightHead);
        if (box.target != null)
            Draw.text(ctx, tr, box.target, box.targetX, iconY + 4,
                    Draw.argb(0xC4, ink), !lightHead);
        if (n.action.unavailable)
            Draw.glyph(ctx, Draw.WARN, box.x + box.w - Layout.PAD - 5, iconY + 5,
                    lightHead ? 0x7A5300 : 0xFFE066);

        for (Layout.Chip chip : box.chips) chip(ctx, tr, box, chip, look);
        if (Audit.on()) Audit.clearRole();
    }

    private static void chip(DrawContext ctx, TextRenderer tr, Layout.Box box, Layout.Chip chip,
                             Look look) {
        if (chip.isPlus()) plusChip(ctx, tr, chip);
        else if (chip.isCondition()) conditionChip(ctx, tr, box, chip);
        else if (chip.isArg() || chip.isCell()) argChip(ctx, tr, chip);
        else markerChip(ctx, tr, box, chip);
        if (look.carrying) {
            if (look.accepts == null || !look.accepts.test(box, chip)) return;
            boolean under = look.chip == chip;
            if (under) Draw.roundOutline(ctx, chip.x - 3, chip.y - 3, chip.w + 6, Layout.CHIP_H + 6,
                    (Layout.CHIP_H + 6) / 2, Draw.argb(0x3A, Theme.ACCENT));
            Draw.roundOutline(ctx, chip.x - 1, chip.y - 1, chip.w + 2, Layout.CHIP_H + 2,
                    (Layout.CHIP_H + 2) / 2,
                    Draw.argb(under ? 0xFF : 0x55, Theme.ACCENT));
            return;
        }
        if (look.chip == chip) Draw.roundOutline(ctx, chip.x, chip.y, chip.w, Layout.CHIP_H,
                Layout.CHIP_H / 2, Draw.argb(0xAA, 0xFFFFFF));
    }

    private static void card(DrawContext ctx, TextRenderer tr, Layout.Box box, Look look,
                             int ink, boolean lightHead, int head) {
        Layout.Card c = box.card;
        int mute = Draw.mix(ink, head, 0.42f);
        int soft = Draw.mix(ink, head, 0.30f);

        Draw.item(ctx, c.icon, c.iconX, c.iconY, c.iconSize);
        if (c.verb != null)
            Draw.text(ctx, tr, c.verb, c.verbX, c.nameY, soft, !lightHead);
        Draw.textScaled(ctx, tr, c.name, c.nameX, c.nameY, c.scale,
                c.named ? ink : mute, !lightHead);
        if (c.id != null)
            Draw.text(ctx, tr, c.id, c.idX, c.idY, mute, !lightHead);
        if (c.kind != null)
            Draw.text(ctx, tr, c.kind, c.kindX, c.kindY, mute, !lightHead);
        if (c.missing)
            Draw.glyph(ctx, Draw.WARN, box.x + box.w - Layout.PAD - 5, c.nameY + (c.scale > 1 ? 4 : 0),
                    lightHead ? 0x7A5300 : 0xFFE066);
        for (int i = 0; i < c.desc.size(); i++)
            Draw.text(ctx, tr, c.desc.get(i), c.descX,
                    c.descY + i * Layout.DESC_H, soft, !lightHead);
        if (look.hover == box && look.chip == null && !look.dragging) {
            if (c.hitName(look.mx, look.my)) {
                int from = c.verb == null ? c.nameX : c.verbX;
                Draw.rect(ctx, from, c.nameY + 8 * c.scale, c.nameX + c.nameW - from, 1,
                        Draw.opaque(mute));
            }
            if (c.hitId(look.mx, look.my))
                Draw.rect(ctx, c.idX, c.idY + 8, c.idW, 1, Draw.opaque(mute));
            if (c.hitDesc(look.mx, look.my))
                Draw.rect(ctx, c.descX, c.descY + c.desc.size() * Layout.DESC_H - 2, c.descW, 1,
                        Draw.opaque(mute));
            if (c.hitIcon(look.mx, look.my))
                Draw.roundOutline(ctx, c.iconX - 2, c.iconY - 2, c.iconSize + 4, c.iconSize + 4, 3,
                        Draw.argb(0x99, ink));
        }
        if (c.sepY > 0) {
            Draw.rect(ctx, box.x + Layout.PAD, c.sepY, box.w - Layout.PAD * 2, 1,
                    Draw.argb(0x3A, 0x000000));
            Draw.rect(ctx, box.x + Layout.PAD, c.sepY + 1, box.w - Layout.PAD * 2, 1,
                    Draw.argb(0x22, 0xFFFFFF));
        }
    }

    private static void plusChip(DrawContext ctx, TextRenderer tr, Layout.Chip chip) {
        Draw.pill(ctx, chip.x, chip.y, chip.w, Layout.CHIP_H, chip.border);
        Draw.pillGrad(ctx, chip.x + 1, chip.y + 1, chip.w - 2, Layout.CHIP_H - 2,
                chip.top, chip.bottom);
        Draw.glyph(ctx, Draw.PLUS, chip.x + 5, chip.y + 5, chip.ink);
        Draw.text(ctx, tr, chip.fitted, chip.x + 16, chip.y + 4, chip.ink, false);
    }

    private static void argChip(DrawContext ctx, TextRenderer tr, Layout.Chip chip) {
        Draw.pill(ctx, chip.x, chip.y, chip.w, Layout.CHIP_H, chip.border);
        Draw.pillGrad(ctx, chip.x + 1, chip.y + 1, chip.w - 2, Layout.CHIP_H - 2,
                chip.top, chip.bottom);
        chipBadge(ctx, chip, chip.icon, chip.dot);

        int textRight = chip.x + chip.w - 6;
        if (chip.count != null) {
            Draw.text(ctx, tr, chip.count, textRight - chip.countW, chip.y + 4,
                    chip.dim, false);
            textRight -= chip.countW + 5;
        }
        if (chip.note != null) {
            Draw.text(ctx, tr, chip.note, textRight - chip.noteW, chip.y + 4,
                    chip.dim, false);
            textRight -= chip.noteW + 4;
        }
        Draw.text(ctx, tr, chip.fitted,
                chip.x + (chip.icon.isEmpty() ? Layout.CHIP_INK_X : Layout.CHIP_ITEM_INK_X),
                chip.y + 4, chip.ink, false);
    }

    public static void badge(DrawContext ctx, int x, int y, ItemStack icon, int dot) {
        if (icon == null || icon.isEmpty()) Draw.dot(ctx, x + 5, y + 5, dot);
        else Draw.item(ctx, icon, x + 2, y + 2, 11);
    }

    private static void chipBadge(DrawContext ctx, Layout.Chip chip, ItemStack icon, int dot) {
        badge(ctx, chip.x, chip.y, icon, dot);
    }

    public static ItemStack itemIcon(Value v) {
        return v.hasIcon() ? Stacks.preview(v) : ItemStack.EMPTY;
    }

    public static int pill(DrawContext ctx, int x, int y, int w, int face,
                           boolean tinted, int alpha) {
        boolean grad = Settings.gradient();
        int top = tinted ? Draw.shade(face, grad ? 0.12f : 0.02f) : Theme.MARKER_TOP;
        int bottom = tinted ? Draw.shade(face, grad ? -0.10f : 0.02f)
                : grad ? Theme.MARKER_BOTTOM : Theme.MARKER_TOP;
        Draw.pill(ctx, x, y, w, Layout.CHIP_H,
                Draw.argb(alpha, tinted ? Draw.shade(face, -0.5f) : Theme.MARKER_BORDER));
        Draw.pillGrad(ctx, x + 1, y + 1, w - 2, Layout.CHIP_H - 2,
                Draw.argb(alpha, top), Draw.argb(alpha, bottom));
        return top;
    }

    private static int chipPill(DrawContext ctx, Layout.Chip chip, int face, boolean tinted) {
        return pill(ctx, chip.x, chip.y, chip.w, face, tinted, 0xFF);
    }

    private static void conditionChip(DrawContext ctx, TextRenderer tr, Layout.Box box,
                                      Layout.Chip chip) {
        Script.Node cond = box.node.cond;
        boolean set = cond != null;
        int face = set && cond.action.category != null
                ? cond.action.category.color : Theme.MARKER_TOP;
        int top = chipPill(ctx, chip, face, set);
        chipBadge(ctx, chip, set && !cond.action.item.isEmpty()
                ? Catalog.stackOf(cond.action.item) : null, Draw.opaque(Draw.shade(face, -0.55f)));
        int ink = Draw.isLight(top) ? 0x141821 : set ? 0xFFFFFF : Theme.TEXT_DIM;
        Draw.text(ctx, tr, chip.fitted, chip.x + 15, chip.y + 4, ink, false);
    }

    private static void markerChip(DrawContext ctx, TextRenderer tr, Layout.Box box,
                                   Layout.Chip chip) {
        boolean bound = Layout.markerBound(Layout.chipNode(box.node), chip.settingIndex);
        int face = bound ? Values.color(Value.VARIABLE) : Theme.MARKER_TOP;
        int top = chipPill(ctx, chip, face, bound);
        int ink = Draw.isLight(top) ? 0x141821 : bound ? 0xFFFFFF : Theme.TEXT;
        if (bound) Draw.dot(ctx, chip.x + 5, chip.y + 5, Draw.opaque(Draw.shade(face, -0.55f)));
        Draw.text(ctx, tr, chip.fitted, chip.x + (bound ? 13 : 8), chip.y + 4,
                ink, false);
        Draw.glyph(ctx, Draw.CARET_DOWN, chip.x + chip.w - 11, chip.y + 6,
                bound ? Draw.argb(0xCC, ink) : Draw.opaque(Theme.TEXT_DIM));
    }

    private BlockView() {}
}
