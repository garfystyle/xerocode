package com.xerocode.ui;

import com.xerocode.Market;
import com.xerocode.MarketImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class MarketArt {
    public static final int PATTERNS = 6;
    public static final int CARD_H = 140, CARD_BANNER = 46, CARD_MIN = 170;
    public static final int CARD_BTN_H = 16, CARD_INSET = 8;

    private static final int[] CAT_COLOR = {
            0xE0654F, 0xE0A83C, 0xC85B7A, 0x8E7BE0, 0x4FA3E0,
            0x4FBF8B, 0xC77BE0, 0x6E7C93, 0x7A8496};

    private static final Map<String, ItemStack> ITEMS = new HashMap<>();

    public static int catColor(String cat) {
        int i = Market.categories().indexOf(cat);
        return CAT_COLOR[i < 0 ? CAT_COLOR.length - 1 : i % CAT_COLOR.length];
    }

    public static ItemStack itemOf(String ref) {
        if (ref == null || !ref.startsWith("item:")) return ItemStack.EMPTY;
        String id = ref.substring(5);
        ItemStack have = ITEMS.get(id);
        if (have != null) return have;
        ItemStack made = ItemStack.EMPTY;
        try {
            Identifier key = Identifier.tryParse(id);
            if (key != null) {
                var item = Registries.ITEM.getOptionalValue(key).orElse(null);
                if (item != null && item != Items.AIR) made = new ItemStack(item);
            }
        } catch (Throwable ignored) {
        }
        ITEMS.put(id, made);
        return made;
    }

    public static String refOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return "item:" + Registries.ITEM.getId(stack.getItem());
    }

    private static Identifier skinOf(String nick) {
        if (nick == null || nick.isBlank()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (client.player != null
                    && nick.equalsIgnoreCase(client.player.getGameProfile().name()))
                return pathOf(client.player.getSkin());
            ClientPlayNetworkHandler net = client.getNetworkHandler();
            if (net == null) return null;
            PlayerListEntry seen = net.getPlayerListEntry(nick);
            if (seen == null)
                for (PlayerListEntry one : net.getPlayerList())
                    if (nick.equalsIgnoreCase(one.getProfile().name())) {
                        seen = one;
                        break;
                    }
            return seen == null ? null : pathOf(seen.getSkinTextures());
        } catch (Throwable e) {
            return null;
        }
    }

    private static Identifier pathOf(SkinTextures skin) {
        return skin == null || skin.body() == null ? null : skin.body().texturePath();
    }

    private static boolean picture(DrawContext ctx, String ref, int x, int y, int w, int h,
                                  boolean crop) {
        MarketImage.Shot shot = MarketImage.get(ref);
        if (shot == null || shot.w <= 0 || shot.h <= 0) return false;
        int dw = w, dh = h, dx = x, dy = y;
        if (crop) {
            double want = w / (double) h, has = shot.w / (double) shot.h;
            if (has > want) {
                dw = (int) Math.round(h * has);
                dx = x - (dw - w) / 2;
            } else {
                dh = (int) Math.round(w / has);
                dy = y - (dh - h) / 2;
            }
        }
        ctx.enableScissor(x, y, x + w, y + h);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, shot.id, dx, dy, 0f, 0f, dw, dh,
                shot.w, shot.h, shot.w, shot.h);
        ctx.disableScissor();
        return true;
    }

    public static int[] gradOf(String ref, int fallback) {
        if (ref != null && ref.startsWith("grad:")) {
            String[] parts = ref.split(":");
            if (parts.length == 4) {
                try {
                    return new int[]{Integer.parseInt(parts[1], 16),
                            Integer.parseInt(parts[2], 16),
                            Math.max(0, Math.min(PATTERNS - 1, Integer.parseInt(parts[3])))};
                } catch (Exception ignored) {
                }
            }
        }
        return new int[]{Draw.shade(fallback, 0.10f), Draw.shade(fallback, -0.45f), 1};
    }

    public static String gradRef(int from, int to, int pattern) {
        return String.format("grad:%06x:%06x:%d", from & 0xFFFFFF, to & 0xFFFFFF,
                Math.max(0, Math.min(PATTERNS - 1, pattern)));
    }

    public static void banner(DrawContext ctx, String ref, int x, int y, int w, int h,
                              int fallback, int tl, int tr, int br, int bl, int around) {
        if (w <= 0 || h <= 0) return;
        if (!picture(ctx, ref, x, y, w, h, true)) {
            int[] g = gradOf(ref, fallback);
            paint(ctx, x, y, w, h, g[0], g[1], g[2]);
        }
        if (around != 0) Draw.notch(ctx, x, y, w, h, tl, tr, br, bl, around);
    }

    private static void paint(DrawContext ctx, int x, int y, int w, int h,
                              int from, int to, int pattern) {
        switch (pattern) {
            case 0 -> Draw.rect(ctx, x, y, w, h, Draw.opaque(from));
            case 2 -> Draw.hgrad(ctx, x, y, w, h, Draw.opaque(from), Draw.opaque(to));
            default -> ctx.fillGradient(x, y, x + w, y + h, Draw.opaque(from), Draw.opaque(to));
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

    public static void avatar(DrawContext ctx, String ref, int x, int y, int size,
                              int fallback, String initials, TextRenderer tr) {
        avatar(ctx, ref, "", x, y, size, fallback, initials, tr);
    }

    public static void avatar(DrawContext ctx, String ref, String nick, int x, int y, int size,
                              int fallback, String initials, TextRenderer tr) {
        int r = Math.max(2, size / 4);
        int rim = Draw.shade(fallback, -0.55f);
        int in = Math.max(1, r - 1);
        Draw.round(ctx, x, y, size, size, r, Draw.opaque(rim));
        boolean shown = picture(ctx, ref, x + 1, y + 1, size - 2, size - 2, true);
        if (!shown) {
            Identifier live = nick == null || nick.isEmpty() ? null : skinOf(nick);
            if (live != null) {
                Draw.round(ctx, x + 1, y + 1, size - 2, size - 2, in,
                        Draw.opaque(Draw.shade(fallback, -0.35f)));
                PlayerSkinDrawer.draw(ctx, live, x + 1, y + 1, size - 2, true, false, -1);
                shown = true;
            }
        }
        if (shown) {
            Draw.notch(ctx, x + 1, y + 1, size - 2, size - 2, in, in, in, in, Draw.opaque(rim));
            Draw.roundOutline(ctx, x, y, size, size, r, Draw.argb(0x3C, 0x000000));
            return;
        }
        ItemStack stack = itemOf(ref);
        if (!stack.isEmpty()) {
            Draw.round(ctx, x + 1, y + 1, size - 2, size - 2, in,
                    Draw.opaque(Draw.shade(fallback, -0.25f)));
            int icon = Math.min(16, size - 2);
            Draw.item(ctx, stack, x + (size - icon) / 2, y + (size - icon) / 2, icon);
            Draw.roundOutline(ctx, x, y, size, size, r, Draw.argb(0x3C, 0x000000));
            return;
        }
        Draw.roundRectGrad(ctx, x + 1, y + 1, size - 2, size - 2, in, in, in, in,
                Draw.opaque(Draw.shade(fallback, 0.18f)), Draw.opaque(Draw.shade(fallback, -0.2f)));
        Draw.roundOutline(ctx, x, y, size, size, r, Draw.argb(0x3C, 0x000000));
        if (tr == null || initials == null || initials.isEmpty()) return;
        String one = initials.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        Draw.textCenter(ctx, tr, one, x, y + (size - Ui.TEXT_H) / 2, size, size,
                Draw.readable(fallback), false);
    }

    public static void moduleIcon(DrawContext ctx, Market.Module module, int x, int y, int size,
                                  TextRenderer tr) {
        avatar(ctx, module.icon, x, y, size, catColor(module.cat), module.name, tr);
    }

    public static String about(Market.Module m) {
        if (!m.summary.isBlank()) return m.summary;
        if (!m.descr.isBlank()) return m.descr.replace('\n', ' ').trim();
        String when = m.when();
        String facts = Ui.plural(m.roots, "стопка", "стопки", "стопок") + " · " + m.sizeText();
        return when.isEmpty() ? facts : when + " · " + facts;
    }

    public static void card(DrawContext ctx, TextRenderer tr, Market.Module m,
                            int x, int y, int w, boolean hot, boolean stats) {
        int face = hot ? Ui.BTN_HOVER : Ui.PANEL;
        Draw.card(ctx, x, y, w, CARD_H, Ui.R, Draw.opaque(face),
                Draw.opaque(hot ? Draw.mix(Ui.BORDER, Theme.ACCENT, 0.5f) : Ui.LINE));
        banner(ctx, m.banner, x + 1, y + 1, w - 2, CARD_BANNER, catColor(m.cat),
                Ui.R - 1, Ui.R - 1, 0, 0, Draw.opaque(face));
        Draw.rect(ctx, x + 1, y + CARD_BANNER + 1, w - 2, 1, Draw.argb(0x50, 0x000000));

        if (!m.cat.isEmpty()) {
            String cat = Draw.fit(tr, m.cat, Math.max(28, w / 2 - 10));
            int cw = Draw.badgeWidth(tr, cat);
            Draw.badge(ctx, tr, cat, x + w - cw - 6, y + 5, Draw.argb(0x9A, 0x000000), 0xE8ECF4);
        }
        if (m.hidden)
            Draw.badge(ctx, tr, "спрятан", x + 6, y + 5, Draw.argb(0xCC, Theme.DANGER), 0xFFFFFF);
        else if (m.mine)
            Draw.badge(ctx, tr, "моё", x + 6, y + 5, Draw.argb(0x9A, 0x000000), 0xE8ECF4);

        int ax = x + 7, ay = y + CARD_BANNER - 14;
        Draw.round(ctx, ax - 2, ay - 2, 32, 32, 8, Draw.opaque(face));
        moduleIcon(ctx, m, ax, ay, 28, tr);

        int tx = x + 8, room = w - 16;
        Draw.textFit(ctx, tr, m.name, tx, y + CARD_BANNER + 18, room, Theme.TEXT, false);

        int head = author(ctx, tr, m.author, m.authorIcon, tx, y + CARD_BANNER + 29, 9);
        int tick = m.authorOk ? Draw.glyphW(Draw.CHECK) + 3 : 0;
        int nameW = Math.min(room - head - tick, tr.getWidth(m.author));
        Draw.textFit(ctx, tr, m.author, tx + head, y + CARD_BANNER + 30, room - head - tick,
                Theme.TEXT_FAINT, false);
        if (m.authorOk)
            Draw.glyph(ctx, Draw.CHECK, tx + head + nameW + 3, y + CARD_BANNER + 30, Theme.OK);

        int sy = y + CARD_BANNER + 44;
        for (String line : Ui.wrap(tr, about(m), room, 2)) {
            Draw.textFit(ctx, tr, line, tx, sy, room, Theme.TEXT_DIM, false);
            sy += 11;
        }

        Ui.hairline(ctx, tx, y + CARD_H - 28, room);
        int by = y + CARD_H - 20;
        Draw.textFit(ctx, tr, m.blocksText(), tx, by, room / 2, Theme.TEXT_FAINT, false);
        if (!stats) return;
        int right = stat(ctx, tr, x + w - 8, by, Draw.HEART, m.likes,
                m.liked ? Theme.DANGER : Theme.TEXT_FAINT);
        stat(ctx, tr, right - 6, by, Draw.GRAB, m.downloads, Theme.TEXT_FAINT);
    }

    public static int stat(DrawContext ctx, TextRenderer tr, int right, int y,
                           String[] icon, int value, int ink) {
        String text = String.valueOf(value);
        int tw = tr.getWidth(text);
        Draw.text(ctx, tr, text, right - tw, y, ink, false);
        int gx = right - tw - 3 - Draw.glyphW(icon);
        Draw.glyph(ctx, icon, gx, y, ink);
        return gx;
    }

    public static void skeleton(DrawContext ctx, int x, int y, int w, int seed) {
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

    public static int author(DrawContext ctx, TextRenderer tr, String nick, String icon,
                             int x, int y, int size) {
        avatar(ctx, icon, nick, x, y, size, 0x6E7C93, nick, tr);
        return size + 4;
    }

    public static int tick(DrawContext ctx, int x, int y, boolean on) {
        if (!on) return 0;
        Draw.glyph(ctx, Draw.CHECK, x, y, Theme.OK);
        return Draw.glyphW(Draw.CHECK) + 3;
    }

    private MarketArt() {}
}
