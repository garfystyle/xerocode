package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Settings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;

import java.util.ArrayList;
import java.util.List;

public final class Palette {
    private static final String[][] GROUPS = {
            {"События",    "Событие игрока", "Событие мира", "Событие сущности"},
            {"Условия",    "Если игрок", "Если сущность", "Если переменная", "Если в мире", "Иначе"},
            {"Управление", "Повторение", "Контроль действий", "Контроллер"},
            {"Действия",   "Действие над игроком", "Действие над сущностью", "Действие над миром",
                           "Действие с переменной", "Выбрать цель"},
            {"Значения",   "Значения"},
    };

    public static final int SEARCH_Y = 7;
    public static final int HEADER_H = 32;
    public static final int CRUMB_H = 21;

    private static final int CAPTION_X = 9, SECTION_X = 20, CARET_BOX = 5;

    public static final class Entry {
        public final Catalog.Category category;
        public final Catalog.Action action;
        public final String caption;
        public final int h;
        int y;

        String key;
        boolean folded;
        int inside;
        int labelW;

        net.minecraft.text.OrderedText label;
        int labelFor = -1;
        net.minecraft.text.OrderedText extra;
        int extraW;
        Entry(Catalog.Category c, Catalog.Action a, String caption, int h) {
            this.category = c; this.action = a; this.caption = caption; this.h = h;
        }
        public boolean isRow() { return category != null || action != null; }
        public boolean isSection() { return key != null; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int catIndex = -1;
    private String query = "";
    private double scroll, scrollTarget;
    private final Ui.Bar bar = new Ui.Bar();
    private int contentH;
    private boolean dirty = true;

    public Catalog.Category category() {
        return catIndex < 0 || catIndex >= Catalog.CATEGORIES.size()
                ? null : Catalog.CATEGORIES.get(catIndex);
    }
    public String query() { return query; }
    public boolean hasCrumb() { return catIndex >= 0 || !query.isBlank(); }
    public int listTop() { return HEADER_H + (hasCrumb() ? CRUMB_H : 0); }

    public void setQuery(String q) {
        if (q.equals(query)) return;
        query = q;
        dirty = true;
        scrollTarget = scroll = 0;
    }

    public void openCategory(int index) {
        catIndex = index;
        dirty = true;
        scrollTarget = scroll = 0;
    }

    public void back() {
        if (!query.isBlank()) { query = ""; dirty = true; scrollTarget = scroll = 0; return; }
        openCategory(-1);
    }

    public void invalidate() { dirty = true; }

    private void rebuild() {
        dirty = false;
        entries.clear();
        if (!query.isBlank()) {
            Catalog.Category in = category();
            List<Catalog.Action> hits = in == null
                    ? Catalog.search(query, 250)
                    : Catalog.searchIn(in, query, 250);
            if (hits.isEmpty()) add(new Entry(null, null, "ничего не найдено", Theme.ROW_HEAD_H));
            else for (Catalog.Action a : hits) add(new Entry(null, a, null, Theme.ROW_ACTION_H));
        } else if (catIndex < 0) {
            List<Catalog.Category> left = new ArrayList<>(Catalog.CATEGORIES);
            for (String[] group : GROUPS) {
                List<Catalog.Category> found = new ArrayList<>();
                for (int i = 1; i < group.length; i++) {
                    for (Catalog.Category c : Catalog.CATEGORIES)
                        if (c.name.equals(group[i])) { found.add(c); left.remove(c); }
                }
                if (found.isEmpty()) continue;
                if (addSection(group[0], found.size())) continue;
                for (Catalog.Category c : found) add(row(c));
            }
            if (!left.isEmpty() && !addSection("Прочее", left.size()))
                for (Catalog.Category c : left) add(row(c));
        } else {
            Catalog.Category c = category();
            if (c != null) {
                for (int si = 0; si < c.subNames.size(); si++) {
                    String sn = c.subNames.get(si);
                    List<Catalog.Action> acts = c.subActions.get(si);
                    if (sn != null && addSection(sn, acts.size())) continue;
                    for (Catalog.Action a : acts)
                        add(new Entry(null, a, null, Theme.ROW_ACTION_H));
                }
            }
        }

        int y = 4;
        boolean first = true;
        for (Entry e : entries) {
            if (!e.isRow() && !first) y += 6;
            e.y = y;
            y += e.h + Theme.ROW_GAP;
            first = false;
        }
        contentH = y + 4;
    }

    private boolean addSection(String caption, int inside) {
        Entry e = new Entry(null, null, caption, Theme.ROW_HEAD_H);
        Catalog.Category c = category();
        e.key = (c == null ? "@" : c.name) + "|" + caption;
        e.inside = inside;
        e.folded = Settings.get().collapsed.contains(e.key);
        add(e);
        return e.folded;
    }

    public void toggle(Entry e) {
        if (e == null || e.key == null) return;
        List<String> folded = Settings.get().collapsed;
        if (!folded.remove(e.key)) folded.add(e.key);
        Settings.get().save();
        dirty = true;
    }

    private Entry row(Catalog.Category c) {
        Catalog.Action only = c.count() == 1 && !c.subActions.isEmpty()
                && !c.subActions.get(0).isEmpty() ? c.subActions.get(0).get(0) : null;
        return only == null
                ? new Entry(c, null, null, Theme.ROW_CAT_H)
                : new Entry(null, only, null, Theme.ROW_CAT_H);
    }

    private void add(Entry e) { entries.add(e); }

    private double maxScroll(int screenH) {
        return Math.max(0, contentH - (screenH - listTop()));
    }

    public boolean barPress(double mx, double my) { return bar.press(mx, my); }

    public boolean barDragging() { return bar.dragging(); }

    public void barRelease() { bar.release(); }

    public void barDrag(double my, int screenH) {
        scrollTarget = bar.follow(my, 1, (int) maxScroll(screenH));
        scroll = scrollTarget;
    }

    public void scrollBy(double amount, int screenH) {
        scrollTarget = Math.max(0, Math.min(maxScroll(screenH), scrollTarget - amount * 42));
    }

    private void animate(int screenH) {
        scrollTarget = Math.max(0, Math.min(maxScroll(screenH), scrollTarget));
        double d = scrollTarget - scroll;
        scroll = Math.abs(d) < 0.6 ? scrollTarget : scroll + d * 0.4;
    }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, int screenH) {
        if (dirty) rebuild();
        animate(screenH);

        int w = Theme.PALETTE_W;
        Draw.rect(ctx, 0, 0, w, screenH, Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, w - 1, 0, 1, screenH, Draw.opaque(Theme.LINE));

        drawSearchBox(ctx, tr, mouseX, mouseY);
        if (hasCrumb()) drawCrumb(ctx, tr, mouseX, mouseY);

        int top = listTop();
        Draw.rect(ctx, 0, top - 1, w - 1, 1, Draw.opaque(Theme.LINE));

        ctx.enableScissor(0, top, w - 1, screenH);
        ScreenRect listArea = new ScreenRect(0, top, w - 1, Math.max(0, screenH - top));
        Draw.batch(Batch.open(ctx, listArea, listArea, 2048));
        Entry hovered = entryAt(mouseX, mouseY, screenH);
        for (Entry e : entries) {
            int ey = top + e.y - (int) Math.round(scroll);
            if (ey + e.h < top - 4 || ey > screenH) continue;
            if (e.action != null) drawAction(ctx, tr, e, ey, e == hovered);
            else if (e.category != null) drawCategory(ctx, tr, e, ey, e == hovered);
            else drawCaption(ctx, tr, e, ey, e == hovered);
        }
        Draw.batch(null);
        ctx.disableScissor();

        double max = maxScroll(screenH);
        if (max > 0)
            bar.draw(ctx, w - 6, top + 4, screenH - top - 8, contentH,
                    (int) (contentH - max), (int) Math.round(scroll), mouseX, mouseY);
    }

    private void drawSearchBox(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int w = Theme.PALETTE_W;
        boolean hasText = !query.isBlank();
        Draw.card(ctx, 8, SEARCH_Y, w - 16, Theme.SEARCH_H, 5,
                Draw.opaque(Ui.INPUT), Draw.opaque(hasText ? Ui.LINE_IN : Ui.LINE));
        Draw.glyph(ctx, Draw.SEARCH, 15, SEARCH_Y + 6, hasText ? Theme.ACCENT : Theme.TEXT_FAINT);
        if (hasText) {
            int cx = w - 16 - 6;
            boolean hov = mouseX >= cx - 4 && mouseX < cx + 9 && mouseY >= SEARCH_Y
                    && mouseY < SEARCH_Y + Theme.SEARCH_H;
            Draw.glyph(ctx, Draw.CROSS, cx,
                    SEARCH_Y + (Theme.SEARCH_H - Draw.glyphH(Draw.CROSS)) / 2,
                    hov ? Theme.TEXT : Theme.TEXT_FAINT);
        }
    }

    public static int searchTextX() { return 27; }
    public static int searchTextW() { return Theme.PALETTE_W - 27 - 22; }
    public static boolean hitSearchClear(double mx, double my) {
        int cx = Theme.PALETTE_W - 22;
        return mx >= cx - 4 && mx < cx + 9 && my >= SEARCH_Y && my < SEARCH_Y + Theme.SEARCH_H;
    }

    private void drawCrumb(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int w = Theme.PALETTE_W;
        boolean hov = mouseY >= HEADER_H && mouseY < HEADER_H + CRUMB_H && mouseX < w - 1;
        Draw.rect(ctx, 0, HEADER_H, w - 1, CRUMB_H, Draw.opaque(hov ? Theme.PANEL_RAISED : Theme.PANEL));
        Draw.glyph(ctx, Draw.CHEVRON_LEFT, 9, HEADER_H + 8, hov ? Theme.TEXT : Theme.TEXT_DIM);
        Catalog.Category c = category();
        String label;
        int color = Theme.TEXT_DIM;
        if (!query.isBlank()) {
            label = c == null ? "поиск по всем блокам" : "поиск в «" + c.name + "»";
        } else {
            label = c == null ? "все категории" : c.name;
            if (c != null) color = Draw.readable(c.color);
        }
        Draw.textFit(ctx, tr, label, 20, HEADER_H + 7, w - 34, hov ? Theme.TEXT : color, false);
    }

    private void drawCaption(DrawContext ctx, TextRenderer tr, Entry e, int y, boolean hover) {
        boolean head = e.isSection();
        if (head) drawFoldMark(ctx, e, y, hover);
        int textX = head ? SECTION_X : CAPTION_X;
        if (e.labelFor != Theme.PALETTE_W) measureCaption(tr, e, textX);
        Draw.text(ctx, tr, e.label, textX, y + 5, hover ? Theme.TEXT : Theme.TEXT_FAINT, false);
        int lineX = textX + e.labelW + 6;
        int lineR = Theme.PALETTE_W - 14 - (e.extra == null ? 0 : e.extraW + 6);
        Draw.rect(ctx, lineX, y + 8, Math.max(0, lineR - lineX), 1, Draw.argb(0x60, Ui.BORDER));
        if (e.extra != null)
            Draw.text(ctx, tr, e.extra, Theme.PALETTE_W - 14 - e.extraW, y + 5,
                    hover ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
    }

    private void drawFoldMark(DrawContext ctx, Entry e, int y, boolean hover) {
        if (hover)
            Draw.round(ctx, 7, y + 2, Theme.PALETTE_W - 14, e.h - 4, 4,
                    Draw.opaque(Theme.SURFACE_HOVER));
        String[] caret = e.folded ? Draw.CARET_RIGHT : Draw.CARET_DOWN;
        Draw.glyph(ctx, caret, 10 + (CARET_BOX - Draw.glyphW(caret)) / 2,
                y + 6 + (CARET_BOX - Draw.glyphH(caret)) / 2,
                hover ? Theme.TEXT : Theme.TEXT_DIM);
    }

    private static void measureCaption(TextRenderer tr, Entry e, int textX) {
        String count = e.isSection() && e.folded ? String.valueOf(e.inside) : null;
        e.extra = count == null ? null : Draw.ordered(count);
        e.extraW = count == null ? 0 : tr.getWidth(count);
        String fitted = Draw.fit(tr, e.caption,
                Theme.PALETTE_W - 14 - textX - (count == null ? 0 : e.extraW + 8));
        e.labelW = tr.getWidth(fitted);
        e.label = Draw.ordered(fitted);
        e.labelFor = Theme.PALETTE_W;
    }

    private void drawCategory(DrawContext ctx, TextRenderer tr, Entry e, int y, boolean hover) {
        Catalog.Category c = e.category;
        int w = Theme.PALETTE_W - 14;
        int fill = Draw.mix(Theme.SURFACE, c.color, hover ? 0.34f : 0.15f);
        Draw.card(ctx, 7, y, w, e.h, 5, Draw.opaque(fill),
                Draw.opaque(Draw.mix(Theme.LINE, c.color, hover ? 0.55f : 0.25f)));
        Draw.roundRect(ctx, 8, y + 1, 3, e.h - 2, 2, 0, 0, 2, Draw.opaque(c.color));
        ctx.drawItem(c.icon(), 15, y + (e.h - 16) / 2);

        if (e.labelFor != Theme.PALETTE_W) {
            int n = c.count();
            e.extra = n > 1 ? Draw.ordered(String.valueOf(n)) : null;
            e.extraW = e.extra == null ? 0 : tr.getWidth(String.valueOf(n));
            int room = w - 35 - 8 - (e.extra == null ? 0 : e.extraW + 4);
            e.label = Draw.ordered(Draw.fit(tr, c.name, room));
            e.labelFor = Theme.PALETTE_W;
        }
        Draw.text(ctx, tr, e.label, 35, y + (e.h - 8) / 2,
                hover && !Theme.LIGHT ? 0xFFFFFF : Theme.TEXT, false);
        if (e.extra != null)
            Draw.text(ctx, tr, e.extra, Theme.PALETTE_W - 12 - e.extraW, y + (e.h - 8) / 2,
                    hover ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
    }

    private void drawAction(DrawContext ctx, TextRenderer tr, Entry e, int y, boolean hover) {
        Catalog.Action a = e.action;
        int color = a.category == null ? 0x777777 : a.category.color;
        int w = Theme.PALETTE_W - 14;
        int fill = hover ? Draw.mix(Theme.SURFACE_HOVER, color, 0.28f) : Theme.SURFACE;
        Draw.card(ctx, 7, y, w, e.h, 4, Draw.opaque(fill),
                Draw.opaque(hover ? Draw.mix(Theme.LINE, color, 0.6f) : Theme.LINE));
        Draw.roundRect(ctx, 8, y + 1, 3, e.h - 2, 2, 0, 0, 2, Draw.opaque(color));
        ctx.drawItem(a.icon(), 14, y + (e.h - 16) / 2);

        int right = Theme.PALETTE_W - 12;
        int ty = y + (e.h - 8) / 2;
        if (!a.settings.isEmpty()) {
            String s = String.valueOf(a.settings.size());
            int bw = Draw.badgeWidth(tr, s);
            right -= bw;
            Draw.badge(ctx, tr, s, right, y + (e.h - 11) / 2, Draw.opaque(Ui.BTN_HOVER), Theme.TEXT_FAINT);
            right -= 3;
        }
        if (!a.args.isEmpty()) {
            String s = String.valueOf(a.args.size());
            int bw = Draw.badgeWidth(tr, s);
            right -= bw;
            Draw.badge(ctx, tr, s, right, y + (e.h - 11) / 2,
                    Draw.opaque(Draw.mix(Ui.BTN_HOVER, Theme.ACCENT, 0.25f)), Theme.TEXT_DIM);
            right -= 3;
        }
        if (a.unavailable) {
            right -= 7;
            Draw.glyph(ctx, Draw.WARN, right, y + (e.h - 6) / 2, 0xFFD24A);
            right -= 3;
        }
        if (e.labelFor != Theme.PALETTE_W) {
            e.label = Draw.ordered(Draw.fit(tr, a.name, right - 34));
            e.labelFor = Theme.PALETTE_W;
        }
        Draw.text(ctx, tr, e.label, 34, ty, hover && !Theme.LIGHT ? 0xFFFFFF : Theme.TEXT, false);
    }

    public Entry entryAt(double mx, double my, int screenH) {
        if (dirty) rebuild();
        int top = listTop();
        if (mx < 0 || mx >= Theme.PALETTE_W - 1 || my < top || my > screenH) return null;
        double ly = my - top + scroll;
        for (Entry e : entries) {
            if (!e.isRow() && !e.isSection()) continue;
            if (ly >= e.y && ly < e.y + e.h) return e;
        }
        return null;
    }

    public boolean hitCrumb(double mx, double my) {
        return hasCrumb() && mx < Theme.PALETTE_W - 1 && my >= HEADER_H && my < HEADER_H + CRUMB_H;
    }
}
