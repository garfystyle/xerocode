package com.xerocode.ui;

import com.xerocode.Backpack;
import com.xerocode.Env;
import com.xerocode.History;
import com.xerocode.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

final class TopBar {
    static final int NONE = 0;
    static final int UNDO = 1, REDO = 2, PLAY = 3, BUILD = 4, CLEAR = 5,
            ZOOM_OUT = 6, ZOOM_IN = 7, FIT = 8,
            ORIGINAL = 9, CANVAS = 10, SETTINGS = 11, UPLOAD = 12, MORE = 13,
            ZOOM_LABEL = 14, LOAD = 15, BACKPACK = 16, FIND = 17, MARKET = 18,
            SELECT = 19, COPY = 20, CUT = 21, PASTE = 22, CODE = 23;

    private static final int[] HIDE_ORDER = {CLEAR, LOAD, BUILD, PLAY, UPLOAD, MARKET, BACKPACK,
            REDO, UNDO, FIT, ZOOM_LABEL, ZOOM_OUT, FIND, CUT, COPY, PASTE, ORIGINAL, CODE};

    interface Host {
        int left();
        int width();
        double zoom();
        boolean empty();
        boolean nothingToSend();
        boolean finding();
        boolean selecting();
        int pickedCount();
        StatusBar.Said sync();
    }

    private static final class Btn {
        int id, x, w;
        String[] icon;
        String label, tip;
        boolean enabled = true;
        boolean active;
        int group;
    }

    private static final int GAP = 6;

    private static int btnH() { return Env.touch() ? 26 : 18; }

    private static int btnY() { return (Theme.topH() - btnH()) / 2; }

    private static int textY() { return btnY() + (btnH() - 7) / 2; }
    private static final String ZOOM_WIDEST = "999%";

    private final Font tr;
    private final Host host;

    private final List<Btn> hidden = new ArrayList<>();
    private List<Btn> cache;
    private int stamp = Integer.MIN_VALUE;
    private boolean zoomLabelShown = true;

    TopBar(Font tr, Host host) {
        this.tr = tr;
        this.host = host;
    }

    void invalidate() { stamp = Integer.MIN_VALUE; }

    private Btn btn(int group, int id, String[] icon, String tip) {
        return btn(group, id, icon, null, tip);
    }

    private Btn btn(int group, int id, String[] icon, String label, String tip) {
        Btn b = new Btn();
        b.group = group;
        b.id = id;
        b.icon = icon;
        b.label = label;
        b.tip = tip;
        int pad = label == null ? (Env.touch() ? 9 : 6) : Env.touch() ? 11 : 8;
        b.w = pad + (icon == null ? 0 : Draw.glyphW(icon) + (label == null ? 0 : 5))
                + (label == null ? 0 : tr.width(label)) + pad;
        return b;
    }

    private List<Btn> buttons() {
        int fresh = host.width() * 31 + host.left() * 7
                + Backpack.count() * 1048576 + (host.finding() ? 16 : 0)
                + (History.canUndo() ? 1 : 0) + (History.canRedo() ? 2 : 0)
                + (host.empty() ? 0 : 4)
                + (Settings.canvasMode() ? 8 : 0)
                + (Env.touch() ? 32 : 0) + (host.selecting() ? 64 : 0) + host.pickedCount() * 128;
        if (cache != null && fresh == stamp) return cache;
        List<Btn> list = build(0, true);
        if (!fits(list)) list = build(0, false);
        for (int drop = 1; !fits(list) && drop <= HIDE_ORDER.length; drop++) list = build(drop, false);
        stamp = fresh;
        cache = list;
        return list;
    }

    private boolean fits(List<Btn> list) {
        int leftEnd = host.left(), rightStart = host.width();
        for (Btn b : list) {
            boolean right = b.id == ZOOM_OUT || b.id == ZOOM_IN || b.id == FIT
                    || b.id == FIND || b.id == SETTINGS || b.id == SELECT || b.id == COPY
                    || b.id == CUT || b.id == PASTE;
            if (right) rightStart = Math.min(rightStart, b.x);
            else leftEnd = Math.max(leftEnd, b.x + b.w);
        }
        return leftEnd + 10 <= rightStart;
    }

    private static String hotkey(Settings.Hot hot) { return Env.touch() ? "" : Settings.get().labels(hot); }

    private List<Btn> build(int drop, boolean modeLabels) {
        List<Btn> list = new ArrayList<>();
        Btn undo = btn(1, UNDO, Draw.UNDO, "Отменить  " + hotkey(Settings.Hot.UNDO));
        undo.enabled = History.canUndo();
        Btn redo = btn(1, REDO, Draw.REDO, "Вернуть  " + hotkey(Settings.Hot.REDO));
        redo.enabled = History.canRedo();
        list.add(undo);
        list.add(redo);
        if (!Env.browser()) {
            list.add(btn(2, PLAY, Draw.PLAY, "Игра  " + hotkey(Settings.Hot.PLAY)));
            list.add(btn(2, BUILD, Draw.BRICKS, "Строительство  " + hotkey(Settings.Hot.BUILD)));
        }
        Btn clear = btn(3, CLEAR, Draw.TRASH, "Очистить полотно");
        clear.enabled = !host.empty();
        list.add(clear);
        Btn upload = btn(3, UPLOAD, Draw.UPLOAD, "Сохранить на сервер  "
                + hotkey(Settings.Hot.UPLOAD) + (Env.browser()
                ? "\nДаст команду для чата JustMC" : "\nЗаписать код блоками в мир"));
        upload.enabled = !host.nothingToSend();
        list.add(upload);
        list.add(btn(3, LOAD, Draw.LOAD, "Загрузить json\nПолотно заменится кодом из файла"));
        int stashed = Backpack.count();
        list.add(btn(4, BACKPACK, Draw.PACK, stashed == 0 ? null : String.valueOf(stashed),
                "Рюкзак кода  " + hotkey(Settings.Hot.BACKPACK) + (Env.touch() ? ""
                        : "\n" + hotkey(Settings.Hot.STASH) + " — убрать стопку под курсором")));
        list.add(btn(4, MARKET, Draw.SHOP, "Модули  " + hotkey(Settings.Hot.MARKET)));

        boolean canvasMode = Settings.canvasMode();
        Btn original = btn(5, ORIGINAL, Draw.BRICKS, modeLabels ? "3D" : null,
                "3D-кодинг  " + hotkey(Settings.Hot.MODE) + "\nКод блоками в мире");
        original.active = !canvasMode;
        Btn canvas = btn(5, CANVAS, Draw.CANVAS, modeLabels ? "2D" : null, "2D-кодинг");
        canvas.active = canvasMode;
        if (!Env.browser()) list.add(original);
        list.add(canvas);
        list.add(btn(5, CODE, Draw.CODE, modeLabels ? "Код" : null, "Код буквами\nXero, JMCC, JMScript, JustIce"));

        List<Btn> right = new ArrayList<>();
        if (Env.touch()) selectionTools(right);
        Btn find = btn(6, FIND, Draw.SEARCH, "Поиск по коду  " + hotkey(Settings.Hot.FIND));
        find.active = host.finding();
        right.add(find);
        right.add(btn(7, ZOOM_OUT, Draw.MINUS, "Отдалить"));
        right.add(btn(7, ZOOM_IN, Draw.PLUS, "Приблизить"));
        right.add(btn(7, FIT, Draw.FIT, "Показать всё  " + hotkey(Settings.Hot.FIT)));
        Btn gear = btn(8, SETTINGS, Draw.GEAR, "Настройки  " + hotkey(Settings.Hot.SETTINGS));

        hidden.clear();
        zoomLabelShown = true;
        for (int i = 0; i < drop && i < HIDE_ORDER.length; i++) {
            if (HIDE_ORDER[i] == ZOOM_LABEL) zoomLabelShown = false;
            Btn hide = find(list, HIDE_ORDER[i]);
            if (hide == null) hide = find(right, HIDE_ORDER[i]);
            if (hide != null) hidden.add(hide);
            if (HIDE_ORDER[i] == ZOOM_OUT) {
                Btn zoomIn = find(right, ZOOM_IN);
                if (zoomIn != null) hidden.add(zoomIn);
            }
        }
        list.removeAll(hidden);
        right.removeAll(hidden);
        if (find(list, ORIGINAL) == null && find(list, CODE) == null) list.remove(find(list, CANVAS));
        if (!hidden.isEmpty()) list.add(0, btn(0, MORE, Draw.CARET_DOWN, "Ещё"));
        place(list, host.left() + 8);
        right.add(gear);
        int total = place(right, 0);
        for (Btn b : right) b.x += host.width() - 8 - total;
        list.addAll(right);
        return list;
    }

    private void selectionTools(List<Btn> list) {
        int count = host.pickedCount();
        boolean on = host.selecting();
        list.add(btn(9, PASTE, Draw.PASTE, "Вставить"));
        if (on) {
            Btn copy = btn(9, COPY, Draw.COPY, "Копировать выделенное");
            copy.enabled = count > 0;
            list.add(copy);
            Btn cut = btn(9, CUT, Draw.CUT, "Вырезать выделенное");
            cut.enabled = count > 0;
            list.add(cut);
        }
        Btn select = btn(9, SELECT, Draw.MARQUEE, on && count > 0 ? String.valueOf(count) : null,
                "Режим выделения");
        select.active = on;
        list.add(select);
    }

    private int place(List<Btn> list, int x) {
        for (int i = 0; i < list.size(); i++) {
            Btn b = list.get(i);
            if (i == 0 || list.get(i - 1).group != b.group) x += 1;
            b.x = x;
            x += span(b);
            if (i == list.size() - 1 || list.get(i + 1).group != b.group) x += 1 + GAP;
        }
        return x - GAP;
    }

    private int span(Btn b) { return b.w + (b.id == ZOOM_OUT ? zoomLabelW() : 0); }

    private int zoomLabelW() { return zoomLabelShown ? tr.width(ZOOM_WIDEST) + 8 : 0; }

    private static Btn find(List<Btn> list, int id) {
        for (Btn b : list) if (b.id == id) return b;
        return null;
    }

    private static boolean over(Btn b, double mx, double my) {
        if (Env.touch())
            return mx >= b.x - GAP / 2 && mx < b.x + b.w + GAP / 2 && my >= 0 && my < Theme.topH();
        return mx >= b.x && mx < b.x + b.w && my >= btnY() - 1 && my < btnY() + btnH() + 1;
    }

    int hit(double mx, double my) {
        for (Btn b : buttons()) if (b.enabled && over(b, mx, my)) return b.id;
        return NONE;
    }

    private int menuX() {
        Btn more = find(buttons(), MORE);
        return more == null ? host.left() : more.x;
    }

    Menu menu(int screenW, int screenH, IntConsumer pick) {
        List<Menu.Item> items = new ArrayList<>();
        List<Btn> acts = new ArrayList<>(hidden);
        for (Btn b : acts) {
            String label = b.tip == null ? "" : b.tip;
            int nl = label.indexOf('\n');
            if (nl >= 0) label = label.substring(0, nl);
            Menu.Item item = new Menu.Item(label.trim(), b.id == CLEAR, b.icon);
            item.enabled = b.enabled;
            items.add(item);
        }
        return Menu.actions(screenW, screenH, menuX(), Theme.topH(), tr, items,
                i -> { if (i >= 0 && i < acts.size()) pick.accept(acts.get(i).id); });
    }

    boolean tooltip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        for (Btn b : buttons()) {
            if (!over(b, mouseX, mouseY)) continue;
            List<Component> tip = new ArrayList<>();
            String[] parts = b.tip.split("\n");
            tip.add(Component.literal(parts[0]));
            for (int i = 1; i < parts.length; i++) tip.add(Component.literal("§7" + parts[i]));
            StatusBar.Said sync = b.id == UPLOAD ? host.sync() : null;
            if (sync != null) tip.add(Component.literal("§7Сейчас: " + sync.text()));
            ctx.setComponentTooltipForNextFrame(tr, tip, mouseX, mouseY);
            return true;
        }
        return false;
    }

    void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int left = host.left(), room = host.width() - left;
        ScreenRectangle area = new ScreenRectangle(left, 0, room, Theme.topH());
        Draw.batch(Batch.open(ctx, null, area, 512));
        int top = Settings.gradient() ? Theme.PANEL_RAISED : Theme.PANEL;
        Draw.roundRectGrad(ctx, left, 0, room, Theme.topH(), 0, 0, 0, 0,
                Draw.opaque(top), Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, left, Theme.topH() - 1, room, 1, Draw.opaque(Theme.LINE));

        List<Btn> list = buttons();
        boolean outlined = Settings.outlined();
        int r = Settings.radius(18);
        drawGroups(ctx, list, outlined, r == 0 ? 0 : r + 1);
        StatusBar.Said sync = host.sync();
        for (Btn b : list) drawButton(ctx, b, mouseX, mouseY, outlined, r, sync);
        Draw.batch(null);
        Btn paste = find(list, PASTE);
        if (paste != null) Env.pasteButton(paste.x - GAP / 2, 0, paste.w + GAP, Theme.topH());
    }

    private void drawGroups(GuiGraphicsExtractor ctx, List<Btn> list, boolean outlined, int r) {
        for (int i = 0; i < list.size(); i++) {
            Btn first = list.get(i);
            while (i + 1 < list.size() && list.get(i + 1).group == first.group) i++;
            Btn last = list.get(i);
            int gx = first.x - 1, gw = last.x + span(last) + 1 - gx;
            if (outlined) {
                Draw.round(ctx, gx, btnY() - 1, gw, btnH() + 2, r, Draw.argb(0x28, Theme.SURFACE));
                Draw.roundOutline(ctx, gx, btnY() - 1, gw, btnH() + 2, r, Draw.opaque(Ui.BORDER));
            } else {
                Draw.round(ctx, gx, btnY() - 1, gw, btnH() + 2, r, Draw.opaque(Theme.SURFACE));
            }
        }
    }

    private void drawButton(GuiGraphicsExtractor ctx, Btn b, int mouseX, int mouseY,
                            boolean outlined, int r, StatusBar.Said sync) {
        boolean hover = b.enabled && over(b, mouseX, mouseY);
        if (b.active) {
            int fill = Draw.mix(Ui.BTN_ON, Theme.ACCENT, hover ? 0.35f : 0.22f);
            Draw.round(ctx, b.x, btnY(), b.w, btnH(), r, outlined ? Draw.argb(0x66, fill) : Draw.opaque(fill));
            if (outlined) Draw.roundOutline(ctx, b.x, btnY(), b.w, btnH(), r,
                    Draw.opaque(Draw.shade(fill, 0.62f)));
            else Draw.rect(ctx, b.x + 4, btnY() + btnH() - 2, b.w - 8, 1, Draw.opaque(Theme.ACCENT));
        } else if (hover) {
            Draw.round(ctx, b.x, btnY(), b.w, btnH(), r, Draw.opaque(Theme.SURFACE_HOVER));
        }
        int color = !b.enabled ? Theme.TEXT_FAINT
                : b.active ? (outlined ? Theme.TEXT : Theme.ON_ACCENT)
                : hover ? Theme.TEXT : Theme.TEXT_DIM;
        int gx = b.label == null && b.icon != null ? b.x + (b.w - Draw.glyphW(b.icon)) / 2
                : b.x + (Env.touch() ? 11 : 8);
        if (b.icon != null) {
            Draw.glyph(ctx, b.icon, gx, btnY() + (btnH() - Draw.glyphH(b.icon)) / 2, color);
            gx += Draw.glyphW(b.icon) + 5;
        }
        if (b.label != null) Draw.text(ctx, tr, b.label, gx, textY(), color, false);
        if (b.id == UPLOAD && b.enabled && sync != null && sync.dot() != 0)
            Draw.dot(ctx, b.x + b.w - 5, btnY() + 2, Draw.opaque(sync.dot()));
        if (b.id != ZOOM_OUT || !zoomLabelShown) return;
        String z = Math.round(host.zoom() * 100) + "%";
        Draw.text(ctx, tr, z, b.x + b.w + (zoomLabelW() - tr.width(z)) / 2, textY(), Theme.TEXT_DIM, false);
    }
}
