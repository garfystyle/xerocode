package com.xerocode.ui;

import com.xerocode.Collab;
import com.xerocode.Codespace;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Sync;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

final class StatusBar {
    interface Host {
        int lines();
        int blocks();
        boolean dirty();
        String picked();
        double zoom();
        int code();
    }

    record Said(String text, int dot) {}

    static final int NONE = -1, WORLD = 0, COUNT = 1, SYNC = 2, TOGETHER = 3, PICKED = 4, ZOOM = 5;

    private static final long SYNC_EVERY = 1500;
    private static final int GAP = 14, PAD = 8, DOT = 5;

    private static final class Item {
        final int id, dot;
        final String text;
        final String[] glyph;
        int x, w;

        Item(int id, String[] glyph, int dot, String text) {
            this.id = id; this.glyph = glyph; this.dot = dot; this.text = text;
        }
    }

    private record Key(int lines, int blocks, int members, boolean dirty, Sync.State sync,
                       String picked, Object level, String labels) {}

    private final Font tr;
    private final Host host;
    private Sync.State sync = Sync.State.UNKNOWN;
    private long syncAt;
    private int syncFor = Integer.MIN_VALUE;
    private Key builtKey;
    private List<Item> built;
    private List<Item> items = new ArrayList<>();
    private int x, y, w;

    StatusBar(Font tr, Host host) {
        this.tr = tr;
        this.host = host;
    }

    private Sync.State sync(Script script) {
        long now = System.currentTimeMillis();
        int code = host.code();
        if (now - syncAt > SYNC_EVERY || code != syncFor) {
            sync = Sync.state(script, Minecraft.getInstance().level, code);
            syncAt = now;
            syncFor = code;
        }
        return sync;
    }

    Said syncSaid(Script script) { return said(host.dirty(), sync(script)); }

    private static Said said(boolean dirty, Sync.State state) {
        Settings s = Settings.get();
        if (dirty) return new Said("не сохранено · " + s.label(Settings.Hot.SAVE), Theme.WARN);
        return switch (state) {
            case IN_SYNC -> new Said("совпадает с миром", Theme.OK);
            case CANVAS_AHEAD -> new Said("не отправлено · " + s.label(Settings.Hot.UPLOAD), Theme.WARN);
            case WORLD_AHEAD -> new Said("мир новее полотна", Theme.DANGER);
            case DIVERGED -> new Said("мир и полотно разошлись", Theme.DANGER);
            default -> new Said("мир не сверялся", 0);
        };
    }

    private static String world() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "нет мира";
        if (!Codespace.inDev(mc.level)) return "вне кодинга";
        String id = Codespace.worldId(mc.level);
        int cut = id.indexOf("_" + Codespace.DEV_DIMENSION);
        if (cut > 0) id = id.substring(0, cut);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        if (id.startsWith("world_")) id = id.substring(6);
        if (id.length() > 8) id = id.substring(0, 8);
        return "мир " + id + " · кодинг";
    }

    private List<Item> build(Script script) {
        Settings s = Settings.get();
        Key key = new Key(host.lines(), host.blocks(), Collab.on() ? Collab.members() : -1,
                host.dirty(), sync(script), host.picked(), Minecraft.getInstance().level,
                s.label(Settings.Hot.SAVE) + s.label(Settings.Hot.UPLOAD));
        if (built == null || !key.equals(builtKey)) {
            built = fresh(key);
            builtKey = key;
        }
        return built;
    }

    private List<Item> fresh(Key key) {
        List<Item> out = new ArrayList<>();
        out.add(new Item(WORLD, Draw.BRICKS, 0, world()));
        out.add(new Item(COUNT, null, 0, Ui.plural(key.lines(), "строка", "строки", "строк")
                + " · " + Ui.plural(key.blocks(), "блок", "блока", "блоков")));
        if (!key.picked().isEmpty())
            out.add(new Item(PICKED, null, Theme.ACCENT, "выделено: " + key.picked()));
        Said said = said(key.dirty(), key.sync());
        out.add(new Item(SYNC, null, said.dot() == 0 ? Theme.TEXT_FAINT : said.dot(), said.text()));
        if (key.members() >= 0)
            out.add(new Item(TOGETHER, Draw.USERS, 0, "вместе: " + Math.max(1, key.members())));
        for (Item it : out)
            it.w = tr.width(it.text) + (it.glyph != null ? Draw.glyphW(it.glyph) + 5 : 0)
                    + (it.dot != 0 ? DOT + 5 : 0);
        return out;
    }

    private boolean over(Item it, double mx, double my) {
        return Ui.hit(mx, my, it.x - 4, y, it.w + 8, Theme.STATUS_H);
    }

    private void divider(GuiGraphicsExtractor ctx, int at) {
        Draw.rect(ctx, at, y + 4, 1, Theme.STATUS_H - 7, Draw.opaque(Theme.LINE));
    }

    void draw(GuiGraphicsExtractor ctx, Script script, int x, int y, int w, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.w = w;
        Draw.batch(Batch.open(ctx, null, new ScreenRectangle(x, y, Math.max(1, w), Theme.STATUS_H), 64));
        Draw.rect(ctx, x, y, w, Theme.STATUS_H, Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, x, y, w, 1, Draw.opaque(Theme.LINE));

        items = new ArrayList<>(build(script));
        Item zoom = new Item(ZOOM, null, 0, Math.round(host.zoom() * 100) + " %");
        zoom.w = tr.width(zoom.text);
        zoom.x = x + w - PAD - zoom.w;
        int ty = y + (Theme.STATUS_H - Ui.TEXT_H) / 2 + 1;
        Draw.text(ctx, tr, zoom.text, zoom.x, ty,
                over(zoom, mouseX, mouseY) ? Theme.TEXT : Theme.TEXT_FAINT, false);
        divider(ctx, zoom.x - GAP / 2);
        int end = zoom.x - GAP;
        int at = x + PAD;
        for (Item it : items) {
            if (at + it.w > end) { it.x = Integer.MIN_VALUE; continue; }
            if (at > x + PAD) divider(ctx, at - GAP / 2);
            it.x = at;
            boolean hot = it.id == SYNC && over(it, mouseX, mouseY);
            int cx = at;
            if (it.glyph != null) {
                Draw.glyph(ctx, it.glyph, cx, y + (Theme.STATUS_H - Draw.glyphH(it.glyph)) / 2 + 1,
                        Theme.TEXT_FAINT);
                cx += Draw.glyphW(it.glyph) + 5;
            }
            if (it.dot != 0) {
                Draw.rect(ctx, cx, y + (Theme.STATUS_H - DOT) / 2 + 1, DOT, DOT, Draw.opaque(it.dot));
                cx += DOT + 5;
            }
            Draw.text(ctx, tr, it.text, cx, ty, hot ? Theme.TEXT : Theme.TEXT_DIM, false);
            at += it.w + GAP;
        }
        items.add(zoom);
        Draw.batch(null);
    }

    int hit(double mx, double my) {
        if (!contains(mx, my)) return NONE;
        for (Item it : items)
            if (it.x != Integer.MIN_VALUE && mx >= it.x - 4 && mx < it.x + it.w + 4) return it.id;
        return NONE;
    }

    boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, w, Theme.STATUS_H); }

    String tip(double mx, double my) {
        Settings s = Settings.get();
        return switch (hit(mx, my)) {
            case SYNC -> "Отправить на сервер  " + s.label(Settings.Hot.UPLOAD);
            case COUNT -> "Строка — это строка в /dev; номер стоит над стопкой · "
                    + s.label(Settings.Hot.PREV_LINE) + " / " + s.label(Settings.Hot.NEXT_LINE)
                    + " — по строкам";
            case PICKED -> s.label(Settings.Hot.COPY) + " копировать · "
                    + s.label(Settings.Hot.DUPLICATE) + " дублировать · "
                    + s.label(Settings.Hot.DELETE) + " удалить · "
                    + s.label(Settings.Hot.STASH) + " в рюкзак · Esc снять";
            case ZOOM -> "Масштаб: клик — 100 %, ещё клик — показать всё";
            default -> null;
        };
    }
}
