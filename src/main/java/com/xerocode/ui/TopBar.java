package com.xerocode.ui;

import com.xerocode.Backpack;
import com.xerocode.History;
import com.xerocode.Settings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

final class TopBar {
    static final int NONE = 0;
    static final int UNDO = 1, REDO = 2, PLAY = 3, BUILD = 4, CLEAR = 5,
            ZOOM_OUT = 6, ZOOM_IN = 7, FIT = 8,
            ORIGINAL = 9, CANVAS = 10, SETTINGS = 11, UPLOAD = 12, MORE = 13,
            ZOOM_LABEL = 14, LOAD = 15, BACKPACK = 16, FIND = 17, MARKET = 18;

    private static final int[] HIDE_ORDER = {CLEAR, LOAD, BUILD, PLAY, UPLOAD, REDO,
            UNDO, FIT, ZOOM_LABEL, ZOOM_OUT, FIND, MARKET, BACKPACK, ORIGINAL};

    interface Host {
        int left();
        int width();
        int blocks();
        double zoom();
        boolean empty();
        boolean finding();
    }

    private static final class Btn {
        int id, x, w;
        String[] icon;
        String label, tip;
        boolean enabled = true;
        boolean separatorAfter;
        boolean active;
        boolean joinLeft, joinRight;
    }

    private final TextRenderer tr;
    private final Host host;

    private final List<Btn> hidden = new ArrayList<>();
    private List<Btn> cache;
    private int stamp = Integer.MIN_VALUE;
    private boolean zoomLabelShown = true;

    TopBar(TextRenderer tr, Host host) {
        this.tr = tr;
        this.host = host;
    }

    void invalidate() { stamp = Integer.MIN_VALUE; }

    private Btn btn(int id, String[] icon, String tip) { return btn(id, icon, null, tip); }

    private Btn btn(int id, String[] icon, String label, String tip) {
        Btn b = new Btn();
        b.id = id;
        b.icon = icon;
        b.label = label;
        b.tip = tip;
        b.w = 8 + (icon == null ? 0 : Draw.glyphW(icon) + (label == null ? 0 : 5))
                + (label == null ? 0 : tr.getWidth(label)) + 8;
        return b;
    }

    private List<Btn> buttons() {
        int fresh = host.width() * 31 + host.left() * 7 + host.blocks() * 64
                + Backpack.count() * 1048576 + (host.finding() ? 16 : 0)
                + (History.canUndo() ? 1 : 0) + (History.canRedo() ? 2 : 0)
                + (host.empty() ? 0 : 4)
                + (Settings.canvasMode() ? 8 : 0);
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
                    || b.id == FIND || b.id == SETTINGS;
            if (right) rightStart = Math.min(rightStart, b.x);
            else leftEnd = Math.max(leftEnd, b.x + b.w);
        }
        return leftEnd + 10 <= rightStart;
    }

    private static String hotkey(Settings.Hot hot) { return Settings.get().label(hot); }

    private List<Btn> build(int drop, boolean modeLabels) {
        List<Btn> list = new ArrayList<>();
        Btn undo = btn(UNDO, Draw.UNDO, "Отменить  " + hotkey(Settings.Hot.UNDO));
        undo.enabled = History.canUndo();
        Btn redo = btn(REDO, Draw.REDO, "Вернуть  " + hotkey(Settings.Hot.REDO));
        redo.enabled = History.canRedo();
        redo.separatorAfter = true;
        list.add(undo);
        list.add(redo);
        list.add(btn(PLAY, Draw.PLAY, "Игра  " + hotkey(Settings.Hot.PLAY)
                + "\nЗапустить мир и проверить код"));
        list.add(btn(BUILD, Draw.BRICKS, "Строительство  " + hotkey(Settings.Hot.BUILD)
                + "\nВернуться строить мир"));
        Btn clear = btn(CLEAR, Draw.TRASH, "Очистить полотно");
        clear.enabled = !host.empty();
        list.add(clear);
        Btn upload = btn(UPLOAD, Draw.UPLOAD, "Сохранить на сервер  "
                + hotkey(Settings.Hot.UPLOAD) + "\nЗаписать код полотна блоками в мир");
        upload.enabled = !host.empty();
        list.add(upload);
        list.add(btn(LOAD, Draw.LOAD, "Загрузить json\nЗаменить полотно кодом из файла"));
        int stashed = Backpack.count();
        list.add(btn(BACKPACK, Draw.PACK, stashed == 0 ? null : String.valueOf(stashed),
                "Рюкзак кода  " + hotkey(Settings.Hot.BACKPACK)
                        + "\nСохранённые куски кода, один на все миры"
                        + "\n" + hotkey(Settings.Hot.STASH) + " — убрать стопку под курсором"
                        + "\nShift+клик по блокам — выделить несколько и убрать пачкой"));
        Btn shop = btn(MARKET, Draw.SHOP, "Магазин модулей  " + hotkey(Settings.Hot.MARKET)
                + "\nЧужие модули: посмотреть, забрать, выложить свой");
        shop.separatorAfter = true;
        list.add(shop);

        boolean canvasMode = Settings.canvasMode();
        Btn original = btn(ORIGINAL, Draw.BRICKS, modeLabels ? "3D" : null,
                "3D-кодинг  " + hotkey(Settings.Hot.MODE)
                        + "\nЗакрыть полотно и собирать код блоками в мире");
        original.active = !canvasMode;
        original.joinRight = true;
        Btn canvas = btn(CANVAS, Draw.CANVAS, modeLabels ? "2D" : null,
                "2D-кодинг\nСобирать код на полотне, как сейчас");
        canvas.active = canvasMode;
        canvas.joinLeft = true;
        list.add(original);
        list.add(canvas);

        List<Btn> right = new ArrayList<>();
        Btn find = btn(FIND, Draw.SEARCH, "Поиск по коду  " + hotkey(Settings.Hot.FIND)
                + "\nНайти блок на полотне и оглавление строк");
        find.active = host.finding();
        find.separatorAfter = true;
        right.add(find);
        right.add(btn(ZOOM_OUT, Draw.MINUS, "Отдалить"));
        right.add(btn(ZOOM_IN, Draw.PLUS, "Приблизить"));
        right.add(btn(FIT, Draw.FIT, "Показать всё  " + hotkey(Settings.Hot.FIT)));
        Btn gear = btn(SETTINGS, Draw.GEAR, "Настройки  " + hotkey(Settings.Hot.SETTINGS)
                + "\nГорячие клавиши и внешний вид");

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
        if (find(list, ORIGINAL) == null) list.remove(find(list, CANVAS));
        if (find(list, CANVAS) == null) for (Btn b : list) b.joinRight = false;
        if (!hidden.isEmpty()) {
            Btn more = btn(MORE, Draw.CARET_DOWN, "Ещё");
            more.separatorAfter = true;
            list.add(0, more);
        }
        for (int i = 0; i < list.size(); i++)
            list.get(i).separatorAfter = list.get(i).separatorAfter && i < list.size() - 1;

        int x = host.left() + 8;
        for (Btn b : list) {
            b.x = x;
            x += b.w + (b.separatorAfter ? 11 : b.joinRight ? 0 : 4);
        }

        boolean zoomShown = find(right, ZOOM_OUT) != null && zoomLabelShown;
        int zoomLabelW = zoomShown ? tr.getWidth("999%") + 6 : 0;
        int total = zoomLabelW + gear.w + (right.isEmpty() ? 0 : 11);
        for (Btn b : right) total += b.w + (b.separatorAfter ? 11 : 4);
        int rx = host.width() - 12 - (infoShown() ? infoWidth() + 12 : 0) - total;
        for (Btn b : right) {
            b.x = rx;
            rx += b.w + (b.separatorAfter ? 11 : 4) + (b.id == ZOOM_OUT ? zoomLabelW + 4 : 0);
        }
        if (!right.isEmpty()) {
            right.get(right.size() - 1).separatorAfter = true;
            rx += 7;
        }
        gear.x = rx;
        right.add(gear);
        list.addAll(right);
        return list;
    }

    private static Btn find(List<Btn> list, int id) {
        for (Btn b : list) if (b.id == id) return b;
        return null;
    }

    private boolean infoShown() { return host.width() - host.left() >= 400; }

    private String infoText() { return "блоков: " + host.blocks(); }

    private int infoWidth() { return tr.getWidth(infoText()); }

    private static boolean over(Btn b, double mx, double my) {
        return mx >= b.x && mx < b.x + b.w && my >= 6 && my < 24;
    }

    int hit(double mx, double my) {
        for (Btn b : buttons()) if (b.enabled && over(b, mx, my)) return b.id;
        return NONE;
    }

    private int menuX() {
        Btn more = find(buttons(), MORE);
        return more == null ? host.left() : more.x;
    }

    Menu menu(int screenW, int screenH, java.util.function.IntConsumer pick) {
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
        return Menu.actions(screenW, screenH, menuX(), Theme.TOPBAR_H, tr, items,
                i -> { if (i >= 0 && i < acts.size()) pick.accept(acts.get(i).id); });
    }

    boolean tooltip(DrawContext ctx, int mouseX, int mouseY) {
        for (Btn b : buttons()) {
            if (!over(b, mouseX, mouseY)) continue;
            List<Text> tip = new ArrayList<>();
            String[] parts = b.tip.split("\n");
            tip.add(Text.literal(parts[0]));
            for (int i = 1; i < parts.length; i++) tip.add(Text.literal("§7" + parts[i]));
            ctx.drawTooltip(tr, tip, mouseX, mouseY);
            return true;
        }
        return false;
    }

    void draw(DrawContext ctx, int mouseX, int mouseY) {
        int left = host.left(), room = host.width() - left;
        ScreenRect area = new ScreenRect(left, 0, room, Theme.TOPBAR_H);
        Draw.batch(Batch.open(ctx, null, area, 512));
        Draw.rect(ctx, left, 0, room, Theme.TOPBAR_H, Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, left, Theme.TOPBAR_H - 1, room, 1, Draw.opaque(Theme.LINE));

        List<Btn> list = buttons();
        Btn joinFirst = null, joinLast = null;
        for (Btn b : list) {
            if (b.joinRight) joinFirst = b;
            if (b.joinLeft) joinLast = b;
        }
        boolean outlined = Settings.outlined();
        int r = Settings.radius(18);

        for (Btn b : list) drawButton(ctx, b, mouseX, mouseY, outlined, r);
        if (joinFirst != null && joinLast != null) {
            int gx = joinFirst.x, gw = joinLast.x + joinLast.w - gx;
            if (outlined) Draw.roundOutline(ctx, gx, 6, gw, 18, r, Draw.opaque(Ui.BORDER));
            Draw.rect(ctx, joinLast.x, 7, 1, 16,
                    Draw.argb(outlined ? 0xFF : 0x66, outlined ? Ui.BORDER : 0x000000));
        }
        Draw.batch(null);
        if (infoShown())
            Draw.textRight(ctx, tr, infoText(), host.width() - 12, 11, Theme.TEXT_FAINT, false);
    }

    private void drawButton(DrawContext ctx, Btn b, int mouseX, int mouseY,
                            boolean outlined, int r) {
        boolean hover = b.enabled && over(b, mouseX, mouseY);
        boolean joined = b.joinLeft || b.joinRight;
        int fill = hover ? Theme.SURFACE_HOVER : Theme.SURFACE;
        if (b.active) fill = Draw.mix(Ui.BTN_ON, Theme.ACCENT, hover ? 0.35f : 0.22f);
        if (!b.enabled) fill = Theme.PANEL_RAISED;
        int rl = b.joinLeft ? 0 : r, rr = b.joinRight ? 0 : r;
        if (outlined && !(joined && b.active)) {
            Draw.roundRect(ctx, b.x, 6, b.w, 18, rl, rr, rr, rl,
                    Draw.argb(hover ? 0x66 : 0x28, fill));
            if (!joined) Draw.roundOutline(ctx, b.x, 6, b.w, 18, r,
                    Draw.opaque(Draw.shade(fill, hover || b.active ? 0.62f : 0.42f)));
        } else {
            Draw.roundRect(ctx, b.x, 6, b.w, 18, rl, rr, rr, rl, Draw.opaque(fill));
        }
        if (b.active && !outlined)
            Draw.rect(ctx, b.x + 4, 22, b.w - 8, 1, Draw.opaque(Theme.ACCENT));
        int color = !b.enabled ? Theme.TEXT_FAINT
                : b.active ? Theme.ON_ACCENT : hover ? Theme.TEXT : Theme.TEXT_DIM;
        int gx = b.x + 8;
        if (b.icon != null) {
            Draw.glyph(ctx, b.icon, gx, 6 + (18 - Draw.glyphH(b.icon)) / 2, color);
            gx += Draw.glyphW(b.icon) + 5;
        }
        if (b.label != null) Draw.text(ctx, tr, b.label, gx, 11, color, false);
        if (b.separatorAfter) Draw.rect(ctx, b.x + b.w + 5, 8, 1, 14, Draw.opaque(Theme.LINE));
        if (b.id != ZOOM_OUT || !zoomLabelShown) return;
        String z = Math.round(host.zoom() * 100) + "%";
        Draw.text(ctx, tr, z, b.x + b.w + 4 + (tr.getWidth("999%") + 6 - tr.getWidth(z)) / 2,
                11, Theme.TEXT_DIM, false);
    }
}
