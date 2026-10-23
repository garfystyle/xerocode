package com.xerocode.ui;

import com.xerocode.Modules;
import com.xerocode.Stacks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class ModuleArt {
    public static final int CARD_H = 140, CARD_BANNER = 46, CARD_MIN = 170;
    public static final int CARD_BTN_H = 16, CARD_INSET = 8;
    private static final int PATTERNS = 6, CACHE_MAX = 6000;
    private static final int OTHER_COLOR = 0x7A8496;

    private static final Map<String, Integer> CAT_COLOR = Map.of(
            "TEXT", 0xE0A83C, "GAME_MECHANICS", 0xE0654F, "COMMANDS", 0x8E7BE0,
            "INSTRUMENTS", 0x4FA3E0, "GENERATIONS", 0x4FBF8B, "SINGS", 0xC85B7A,
            "VARIABLE_MANIPULATIONS", 0xC77BE0, "OTHER", 0x6E7C93);
    private static final int[] HUES = {0xE0654F, 0xE0A83C, 0xC85B7A, 0x8E7BE0, 0x4FA3E0,
            0x4FBF8B, 0xC77BE0, 0x6E7C93};

    private record Fit(int w, boolean light, FormattedCharSequence text) {}
    private record Wrap(int w, List<String> lines) {}

    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static final Map<String, Fit> TITLES = new HashMap<>();
    private static final Map<String, Wrap> ABOUT = new HashMap<>();

    public static int catColor(String cat) { return CAT_COLOR.getOrDefault(cat, OTHER_COLOR); }

    public static ItemStack iconOf(Modules.Module m) {
        ItemStack have = ICONS.get(m.id);
        if (have != null) return have;
        ItemStack made = m.item.isEmpty() ? null : Stacks.icon(m.item);
        if (made == null || made.isEmpty()) made = Stacks.stack(Modules.category(m.category).item());
        if (ICONS.size() > CACHE_MAX) ICONS.clear();
        ICONS.put(m.id, made);
        return made;
    }

    public static FormattedCharSequence title(Font tr, Modules.Module m, int w, boolean coloured) {
        boolean light = !coloured || Theme.LIGHT;
        String key = m.id + (coloured ? "" : "~");
        Fit have = TITLES.get(key);
        if (have != null && have.w() == w && have.light() == light) return have.text();
        FormattedCharSequence made;
        if (light) {
            made = Draw.ordered(Draw.fit(tr, m.label, w));
        } else {
            Component fitted = McText.fit(tr, McText.runs(m.title, McText.LEGACY), w);
            made = tr.width(fitted) <= w ? fitted.getVisualOrderText()
                    : Draw.ordered(Draw.fit(tr, m.label, w));
        }
        if (TITLES.size() > CACHE_MAX) TITLES.clear();
        TITLES.put(key, new Fit(w, light, made));
        return made;
    }

    public static List<FormattedCharSequence> about(Font tr, Modules.Module m, int w) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String line : m.about) {
            if (line.isEmpty()) {
                out.add(FormattedCharSequence.EMPTY);
                continue;
            }
            Component text = Theme.LIGHT ? Component.literal(McText.plain(line, McText.LEGACY))
                    : McText.preview(line, McText.LEGACY);
            out.addAll(tr.split(text, w));
        }
        return out;
    }

    private static List<String> summary(Font tr, Modules.Module m, int w) {
        Wrap have = ABOUT.get(m.id);
        if (have != null && have.w() == w) return have.lines();
        String said = m.summary.isEmpty() ? "обновлён " + m.when() : m.summary;
        List<String> lines = Ui.wrap(tr, said, w, 2);
        if (ABOUT.size() > CACHE_MAX) ABOUT.clear();
        ABOUT.put(m.id, new Wrap(w, lines));
        return lines;
    }

    private static int[] gradOf(Modules.Module m) {
        int base = catColor(m.category);
        int hash = m.id.hashCode();
        int tint = HUES[Math.floorMod(hash >> 3, HUES.length)];
        return new int[]{Draw.shade(Draw.mix(base, tint, 0.25f), 0.10f), Draw.shade(base, -0.45f),
                1 + Math.floorMod(hash, PATTERNS - 1)};
    }

    static ScreenRectangle clip;

    public static void banner(GuiGraphicsExtractor ctx, Modules.Module m, int x, int y, int w, int h,
                              int tl, int tr, int br, int bl, int around) {
        if (w <= 0 || h <= 0) return;
        int[] g = gradOf(m);
        boolean own = clip != null && Draw.batch() == null;
        if (own) Draw.batch(Batch.open(ctx, clip, new ScreenRectangle(x, y, w, h), 256));
        paint(ctx, x, y, w, h, g[0], g[1], g[2]);
        if (around != 0) Draw.notch(ctx, x, y, w, h, tl, tr, br, bl, around);
        if (own) Draw.batch(null);
    }

    private static void paint(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                              int from, int to, int pattern) {
        switch (pattern) {
            case 0 -> Draw.rect(ctx, x, y, w, h, Draw.opaque(from));
            case 2 -> Draw.hgrad(ctx, x, y, w, h, Draw.opaque(from), Draw.opaque(to));
            default -> Draw.vgrad(ctx, x, y, w, h, Draw.opaque(from), Draw.opaque(to));
        }
        int ink = Draw.argb(0x30, Draw.isLight(from) ? 0x000000 : 0xFFFFFF);
        switch (pattern) {
            case 3 -> {
                for (int i = -h; i < w; i += 10)
                    for (int dy = 0; dy < h; dy++) {
                        int px = x + i + dy;
                        if (px >= x && px < x + w) Draw.rect(ctx, px, y + dy, 3, 1, ink);
                    }
            }
            case 4 -> {
                for (int dy = 3; dy < h; dy += 9)
                    for (int dx = (dy / 9 % 2) * 4 + 3; dx < w; dx += 9)
                        Draw.rect(ctx, x + dx, y + dy, 2, 2, ink);
            }
            case 5 -> {
                for (int dx = 8; dx < w; dx += 12) Draw.rect(ctx, x + dx, y, 1, h, ink);
                for (int dy = 8; dy < h; dy += 12) Draw.rect(ctx, x, y + dy, w, 1, ink);
            }
            default -> { }
        }
    }

    public static void icon(GuiGraphicsExtractor ctx, Modules.Module m, int x, int y, int size) {
        int fallback = catColor(m.category);
        int r = Math.max(2, size / 4);
        int in = Math.max(1, r - 1);
        Draw.round(ctx, x, y, size, size, r, Draw.opaque(Draw.shade(fallback, -0.55f)));
        Draw.round(ctx, x + 1, y + 1, size - 2, size - 2, in,
                Draw.opaque(Draw.shade(fallback, -0.25f)));
        ItemStack stack = iconOf(m);
        int icon = size >= 40 ? 32 : Math.min(16, size - 2);
        Draw.item(ctx, stack, x + (size - icon) / 2, y + (size - icon) / 2, icon);
        Draw.roundOutline(ctx, x, y, size, size, r, Draw.argb(0x3C, 0x000000));
    }

    public static void card(GuiGraphicsExtractor ctx, Font tr, Modules.Module m,
                            int x, int y, int w, boolean hot, boolean acts) {
        int face = hot ? Ui.BTN_HOVER : Ui.PANEL;
        Draw.card(ctx, x, y, w, CARD_H, Ui.R, Draw.opaque(face),
                Draw.opaque(hot ? Draw.mix(Ui.BORDER, Theme.ACCENT, 0.5f) : Ui.LINE));
        banner(ctx, m, x + 1, y + 1, w - 2, CARD_BANNER, Ui.R - 1, Ui.R - 1, 0, 0, Draw.opaque(face));
        Draw.rect(ctx, x + 1, y + CARD_BANNER + 1, w - 2, 1, Draw.argb(0x50, 0x000000));

        String cat = Draw.fit(tr, m.categoryLabel(), Math.max(28, w / 2 - 10));
        int cw = Draw.badgeWidth(tr, cat);
        Draw.badge(ctx, tr, cat, x + w - cw - 6, y + 5, Draw.argb(0x9A, 0x000000), 0xE8ECF4);
        if (m.approved) {
            String ok = Draw.fit(tr, "проверенный", Math.max(20, w - cw - 30));
            Draw.badge(ctx, tr, ok, x + 6, y + 5, Draw.argb(0xCC, Theme.OK), 0xFFFFFF);
        }

        int ax = x + 7, ay = y + CARD_BANNER - 14;
        Draw.round(ctx, ax - 2, ay - 2, 32, 32, 8, Draw.opaque(face));
        icon(ctx, m, ax, ay, 28);

        int tx = x + 8, room = w - 16;
        Draw.text(ctx, tr, title(tr, m, room, true), tx, y + CARD_BANNER + 18, Theme.TEXT, false);
        Draw.textFit(ctx, tr, m.name, tx, y + CARD_BANNER + 30, room, Theme.TEXT_FAINT, false);

        int sy = y + CARD_BANNER + 44;
        for (String line : summary(tr, m, room)) {
            Draw.textFit(ctx, tr, line, tx, sy, room, Theme.TEXT_DIM, false);
            sy += 11;
        }

        Ui.hairline(ctx, tx, y + CARD_H - 28, room);
        if (acts) return;
        int by = y + CARD_H - 20;
        int right = stat(ctx, tr, x + w - 8, by, Draw.GRAB, m.installs, Theme.TEXT_FAINT);
        right = stat(ctx, tr, right - 6, by, Draw.STAR, m.stars, Theme.TEXT_FAINT);
        String left = m.open ? m.when() : "код закрыт";
        Draw.textFit(ctx, tr, left, tx, by, right - tx - 8, Theme.TEXT_FAINT, false);
    }

    public static int stat(GuiGraphicsExtractor ctx, Font tr, int right, int y,
                           String[] icon, int value, int ink) {
        String text = String.valueOf(value);
        int tw = tr.width(text);
        Draw.text(ctx, tr, text, right - tw, y, ink, false);
        int gx = right - tw - 3 - Draw.glyphW(icon);
        Draw.glyph(ctx, icon, gx, y, ink);
        return gx;
    }

    public static void skeleton(GuiGraphicsExtractor ctx, int x, int y, int w, int seed) {
        Draw.card(ctx, x, y, w, CARD_H, Ui.R, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.LINE));
        Draw.round(ctx, x + 1, y + 1, w - 2, CARD_BANNER, Ui.R - 1,
                Draw.opaque(Draw.mix(Ui.PANEL, Ui.LINE, 0.7f)));
        Draw.round(ctx, x + 5, y + CARD_BANNER - 16, 32, 32, 8, Draw.opaque(Ui.WELL));
        int bone = Draw.opaque(Draw.mix(Ui.PANEL, Ui.LINE, 0.85f));
        int faint = Draw.opaque(Draw.mix(Ui.PANEL, Ui.LINE, 0.5f));
        Draw.round(ctx, x + 8, y + CARD_BANNER + 18, Math.max(40, (w - 16) * (7 - seed % 3) / 10),
                7, 2, bone);
        Draw.round(ctx, x + 8, y + CARD_BANNER + 31, Math.max(30, (w - 16) / 2), 6, 2, faint);
        Draw.round(ctx, x + 8, y + CARD_BANNER + 45, w - 16, 6, 2, faint);
        Draw.round(ctx, x + 8, y + CARD_BANNER + 56, (w - 16) * (6 + seed % 3) / 10, 6, 2, faint);
        Draw.round(ctx, x + 8, y + CARD_H - 13, 34, 6, 2, faint);
    }

    public static int tick(GuiGraphicsExtractor ctx, int x, int y, boolean on) {
        if (!on) return 0;
        Draw.glyph(ctx, Draw.CHECK, x, y, Theme.OK);
        return Draw.glyphW(Draw.CHECK) + 3;
    }

    private ModuleArt() {}
}
