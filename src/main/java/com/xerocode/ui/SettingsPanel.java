package com.xerocode.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.xerocode.Catalog;
import com.xerocode.Collab;
import com.xerocode.Settings;
import com.xerocode.Settings.Hot;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public final class SettingsPanel {
    private static final int WANT_W = 560;
    private static final int WANT_H = 404;
    private static final int SIDE_W = 128;
    private static final int SIDE_FROM = 470;
    private static final int PAD = 14;
    private static final int HEAD_H = 30;
    private static final int TAB_H = 18;
    private static final int NAV_H = 22;
    private static final int ROW_H = 17;
    private static final int KEY_H = 20;
    private static final int CAP_ROW = 16;
    private static final int GROUP_GAP = 8;
    private static final int ROW_GAP = 3;
    private static final int BTN_H = 20;
    private static final int PREVIEW_H = 58;
    private static final int COLOR_H = 18;
    private static final int WANT_SV_W = 168;
    private static final int SV_H = 56, HUE_H = 8;
    private static final int SEARCH_W = 170, SEARCH_H = 16;
    private static final int CAP_KEY_H = 14;
    private static final int SWITCH_W = 24, SWITCH_H = 12;
    private static final int CAP_H = 10;
    private static final int GAP = 10;
    private static final int MEMBER_H = 14;
    private static final long CONFIRM_MS = 3000;
    private static final String WAITING = "жду клавишу…";
    private static final String UNSET = "не задано";
    private static final String NOTHING = "ничего не нашлось";
    private static final String CONFIRM = "Точно сбросить?";
    private static final String PRESET = "Классическая палитра";

    private static final List<String> TABS = List.of("Клавиши", "Внешний вид", "Цвета", "Вместе");
    private static final int TAB_KEYS = 0, TAB_LOOK = 1, TAB_COLORS = 2, TAB_COLLAB = 3;
    private static final String[] RESET_LABELS = {"Сбросить клавиши", "Сбросить вид", "Сбросить цвета"};

    private record KeyGroup(String caption, List<Hot> keys) {}

    private static final List<KeyGroup> KEY_GROUPS = keyGroups();

    private static List<KeyGroup> keyGroups() {
        List<KeyGroup> groups = new ArrayList<>(List.of(
                new KeyGroup("ОСНОВНОЕ", List.of(Hot.OPEN, Hot.SETTINGS, Hot.MODE, Hot.SAVE,
                        Hot.UPLOAD, Hot.UNDO, Hot.REDO)),
                new KeyGroup("ПРАВКА", List.of(Hot.QUICK_ADD, Hot.COPY, Hot.COPY_ONE, Hot.CUT,
                        Hot.PASTE, Hot.DUPLICATE, Hot.DUP_ONE, Hot.DELETE, Hot.DEL_STACK, Hot.SELECT)),
                new KeyGroup("НАВИГАЦИЯ", List.of(Hot.SEARCH, Hot.FIND, Hot.FIT, Hot.PREV_LINE,
                        Hot.NEXT_LINE, Hot.FOLD, Hot.FOLD_ALL, Hot.TIDY)),
                new KeyGroup("РЮКЗАК И МАГАЗИН", List.of(Hot.BACKPACK, Hot.STASH, Hot.MARKET)),
                new KeyGroup("ИГРА", List.of(Hot.PLAY, Hot.BUILD, Hot.RESTART))));
        Set<Hot> rest = EnumSet.allOf(Hot.class);
        for (KeyGroup g : groups) rest.removeAll(g.keys());
        groups.add(new KeyGroup("ПРОЧЕЕ", List.copyOf(rest)));
        return groups;
    }

    private static final class Row {
        final String section, label, hint;
        final String[] options;
        final IntSupplier get;
        final IntConsumer set;
        final boolean flag;
        Ui.Chips cells;
        boolean inline;
        List<String> lines = List.of();
        int h;

        Row(String section, String label, String hint, String[] options, IntSupplier get,
            IntConsumer set) {
            this.section = section;
            this.label = label;
            this.hint = hint;
            this.options = options;
            this.get = get;
            this.set = set;
            this.flag = options == Settings.YES_NO;
        }

        static Row toggle(String section, String label, String hint, BooleanSupplier get,
                          Consumer<Boolean> set) {
            return new Row(section, label, hint, Settings.YES_NO,
                    () -> get.getAsBoolean() ? 0 : 1, v -> set.accept(v == 0));
        }

        boolean on() { return get.getAsInt() == 0; }

        int ctrlW() { return flag ? SWITCH_W : cells.width(); }

        int ctrlH() { return flag ? SWITCH_H : cells.height(); }

        void measure(Font tr, int width) {
            cells = new Ui.Chips(tr, List.of(options), width, 15, 4);
            inline = flag || cells.width() <= width - 140;
            int textW = inline ? width - ctrlW() - 14 : width;
            lines = Ui.wrap(tr, hint, textW, 2);
            int text = 9 + lines.size() * 10;
            h = inline ? Math.max(text, ctrlH()) + 10 : 5 + text + 5 + ctrlH() + 6;
        }

        int ctrlY() { return inline ? (h - ctrlH()) / 2 : 5 + 9 + lines.size() * 10 + 5; }

        boolean matches(String q) {
            if (has(label, q) || has(hint, q) || has(section, q)) return true;
            if (flag) return false;
            for (String o : options) if (has(o, q)) return true;
            return false;
        }
    }

    private record Item(int y, int h, String caption, Hot hot, Row row) {
        boolean preview() { return caption == null && hot == null && row == null; }
    }

    private final Settings s = Settings.get();
    private final Font tr;
    private final List<Row> rows = new ArrayList<>();
    private final List<Catalog.Category> categories = new ArrayList<>();

    private int screenW, screenH;
    private int W, SV_W, side;
    private int x, y, h;
    private int tab;
    private final int[] scroll = new int[TABS.size()];
    private Hot binding;
    private int openColor = -1;
    private float pickH, pickS, pickV;
    private EditBox hexField;
    private EditBox nameField, codeField, searchField;
    private boolean syncing;
    private int dragging;
    private boolean hexDrag;
    private boolean closed, changed;
    private int confirmTab = -1;
    private long confirmAt;
    private long copied;
    private EditBox dragField;
    private final Ui.Bar bar = new Ui.Bar();

    public SettingsPanel(Font tr, int screenW, int screenH) {
        this.tr = tr;
        categories.addAll(Catalog.CATEGORIES);
        build(screenW, screenH);
    }

    public void resize(int sw, int sh) {
        if (sw == screenW && sh == screenH) return;
        rows.clear();
        binding = null;
        closeColor();
        build(sw, sh);
    }

    private void build(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        this.W = Ui.fitW(screenW, WANT_W);
        this.side = W >= SIDE_FROM ? SIDE_W : 0;
        this.SV_W = Math.min(WANT_SV_W, cw() - 12 - 74);

        String look = "ОФОРМЛЕНИЕ", canvas = "ПОЛОТНО";
        rows.add(new Row(look, "Тема", "",
                Settings.THEME_NAMES, () -> s.theme, v -> s.theme = v));
        rows.add(new Row(look, "Кнопки", "",
                Settings.BTN_NAMES, () -> s.buttons, v -> s.buttons = v));
        rows.add(new Row(look, "Блоки", "",
                Settings.BLOCK_NAMES, () -> s.gradient ? 0 : 1, v -> s.gradient = v == 0));
        rows.add(Row.toggle(look, "Тени", "", () -> s.shadows, v -> s.shadows = v));
        rows.add(new Row(canvas, "Сетка", "",
                Settings.GRID_NAMES, () -> s.grid, v -> s.grid = v));
        rows.add(Row.toggle(canvas, "Номера строк", "", () -> s.lineNumbers, v -> s.lineNumbers = v));
        rows.add(Row.toggle(canvas, "Мини-карта", "", () -> s.minimap, v -> s.minimap = v));
        rows.add(Row.toggle(canvas, "Мелкий текст", "сглаживать при отдалении",
                () -> s.smoothText, v -> s.smoothText = v));
        rows.add(Row.toggle(canvas, "Строка состояния", "мир, строки и блоки внизу",
                () -> s.statusBar, v -> s.statusBar = v));
        rows.add(new Row(canvas, "Каталог блоков", "",
                Settings.CATALOG_NAMES, () -> s.chests ? 1 : 0, v -> s.chests = v == 1));
        for (Row r : rows) r.measure(tr, cw());

        this.x = Ui.midX(screenW, W);
        this.h = Ui.fitH(screenH, WANT_H);
        this.y = Ui.midY(screenH, h);

        String hadName = nameField == null ? s.collabName : nameField.getValue();
        String hadCode = codeField == null ? s.collabCode : codeField.getValue();
        String hadQuery = searchField == null ? "" : searchField.getValue();
        nameField = Ui.field(tr, hadName, Collab.myName(), 16);
        nameField.setResponder(t -> s.collabName = t);
        codeField = Ui.field(tr, hadCode, "код приглашения", 40);
        codeField.setResponder(t -> s.collabCode = t.trim());
        searchField = Ui.field(tr, hadQuery, "поиск настройки", 40);
        searchField.setResponder(t -> searched());
    }

    public boolean isClosed() { return closed; }

    public boolean consumeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    public boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, W, h); }

    private int cx() { return x + side + PAD; }
    private int cw() { return W - side - PAD * 2; }

    private int tabsY()    { return y + HEAD_H + 10; }
    private int contentY() { return side > 0 ? y + HEAD_H + 12 : tabsY() + TAB_H + 12; }
    private int footerY()  { return y + h - 12 - BTN_H; }
    private int contentH() { return footerY() - 12 - contentY(); }

    private boolean inView(double my) { return my >= contentY() && my < contentY() + contentH(); }

    private boolean inRowX(double mx) { return mx >= cx() - 6 && mx < cx() + cw() + 6; }

    private int searchW() {
        int room = W - PAD * 3 - 14 - Draw.glyphW(Draw.GEAR) - 6 - tr.width("НАСТРОЙКИ") - 8;
        int w = Math.min(SEARCH_W, room);
        return w < 80 ? 0 : w;
    }

    private int searchX() { return x + W - PAD - 14 - 8 - searchW(); }
    private int searchY() { return y + (HEAD_H - SEARCH_H) / 2; }

    private int navY(int i) { return y + HEAD_H + 10 + i * (NAV_H + 2); }

    private int navAt(double mx, double my) {
        if (side == 0) return Ui.segmentAt(mx, my, x + PAD, tabsY(), W - PAD * 2, TAB_H, TABS.size());
        for (int i = 0; i < TABS.size(); i++)
            if (Ui.hit(mx, my, x + 6, navY(i), side - 12, NAV_H)) return i;
        return -1;
    }

    private String query() {
        return searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean has(String text, String q) {
        return text.toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean keyMatches(Hot hot, String q) {
        return q.isEmpty() || has(hot.label, q) || has(s.label(hot), q);
    }

    private List<Integer> visibleColors() {
        String q = query();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++)
            if (q.isEmpty() || has(categories.get(i).name, q)) out.add(i);
        return out;
    }

    private int matchCount(int t) {
        String q = query();
        return switch (t) {
            case TAB_KEYS -> (int) Arrays.stream(Hot.values()).filter(h -> keyMatches(h, q)).count();
            case TAB_LOOK -> (int) rows.stream().filter(r -> r.matches(q)).count();
            case TAB_COLORS -> visibleColors().size();
            default -> 0;
        };
    }

    private void searched() {
        Arrays.fill(scroll, 0);
        binding = null;
        if (openColor >= 0 && !visibleColors().contains(openColor)) closeColor();
        if (query().isEmpty() || tab == TAB_COLLAB || matchCount(tab) > 0) return;
        for (int t = 0; t < TAB_COLLAB; t++)
            if (matchCount(t) > 0) { switchTab(t); return; }
    }

    private void closeColor() {
        openColor = -1;
        hexField = null;
    }

    private void switchTab(int t) {
        if (t == tab) return;
        tab = t;
        binding = null;
        closeColor();
        confirmTab = -1;
    }

    private List<Item> keyItems() {
        String q = query();
        List<Item> out = new ArrayList<>();
        int at = 0;
        for (KeyGroup g : KEY_GROUPS) {
            List<Hot> shown = new ArrayList<>();
            for (Hot hot : g.keys()) if (keyMatches(hot, q)) shown.add(hot);
            if (shown.isEmpty()) continue;
            if (!out.isEmpty()) at += GROUP_GAP;
            out.add(new Item(at, CAP_ROW, g.caption(), null, null));
            at += CAP_ROW;
            for (Hot hot : shown) {
                out.add(new Item(at, KEY_H, null, hot, null));
                at += KEY_H + 1;
            }
        }
        return out;
    }

    private List<Item> lookItems() {
        String q = query();
        List<Item> out = new ArrayList<>();
        int at = 0;
        if (q.isEmpty()) {
            out.add(new Item(0, PREVIEW_H, null, null, null));
            at = PREVIEW_H + 12;
        }
        String section = null;
        for (Row r : rows) {
            if (!q.isEmpty() && !r.matches(q)) continue;
            if (!r.section.equals(section)) {
                if (section != null) at += GROUP_GAP;
                section = r.section;
                out.add(new Item(at, CAP_ROW, section, null, null));
                at += CAP_ROW;
            }
            out.add(new Item(at, r.h, null, null, r));
            at += r.h + 1;
        }
        return out;
    }

    private static int end(List<Item> items) {
        if (items.isEmpty()) return 0;
        Item last = items.get(items.size() - 1);
        return last.y() + last.h();
    }

    private int contentNeed() {
        return switch (tab) {
            case TAB_KEYS -> end(keyItems()) + 22;
            case TAB_LOOK -> Math.max(end(lookItems()), 20) + 4;
            case TAB_COLORS -> colorsTop() + visibleColors().size() * (COLOR_H + ROW_GAP) - ROW_GAP
                    + (openColor >= 0 ? pickerH() : 0) + 4;
            default -> collab(null, 0, 0, 0, null, false, 0, 0);
        };
    }

    private int maxScroll() { return Math.max(0, contentNeed() - contentH()); }

    private int scroll() { return Math.min(scroll[tab], maxScroll()); }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (confirmTab >= 0 && System.currentTimeMillis() - confirmAt > CONFIRM_MS) confirmTab = -1;
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, W, h);
        if (side > 0) drawSide(ctx, mouseX, mouseY);
        Ui.headerStrip(ctx, x, y, W, HEAD_H, Theme.ACCENT);
        Draw.glyph(ctx, Draw.GEAR, x + PAD, y + (HEAD_H - Draw.glyphH(Draw.GEAR)) / 2 + 1,
                Theme.TEXT_DIM);
        Draw.textFit(ctx, tr, "НАСТРОЙКИ", x + PAD + Draw.glyphW(Draw.GEAR) + 6,
                y + (HEAD_H - Ui.TEXT_H) / 2 + 1, W - PAD * 2 - 40, Theme.TEXT, false);
        drawSearch(ctx, mouseX, mouseY);
        Ui.closeButton(ctx, mouseX, mouseY, x + W - PAD - 14, y + (HEAD_H - 14) / 2, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, W - 2);

        if (side == 0)
            Ui.segmented(ctx, tr, mouseX, mouseY, x + PAD, tabsY(), W - PAD * 2, TAB_H,
                    TABS, tab, Theme.ACCENT);

        int cy = contentY(), ch = contentH();
        ctx.enableScissor(x + side + 1, cy, x + W - 1, cy + ch);
        int top = cy - scroll();
        switch (tab) {
            case TAB_KEYS -> drawKeys(ctx, mouseX, mouseY, top);
            case TAB_LOOK -> drawLook(ctx, mouseX, mouseY, top);
            case TAB_COLORS -> drawColors(ctx, mouseX, mouseY, delta, top);
            default -> collab(ctx, mouseX, mouseY, top, null, false, 0, 0);
        }
        ctx.disableScissor();
        bar.draw(ctx, x + W - 5, cy, ch, contentNeed(), ch, scroll(), mouseX, mouseY);

        Ui.hairline(ctx, x + side + 1, footerY() - 12, W - side - 2);
        if (tab != TAB_COLLAB) {
            boolean armed = confirmTab == tab;
            String label = armed ? CONFIRM : RESET_LABELS[tab];
            Ui.button(ctx, tr, mouseX, mouseY, cx(), footerY(), resetW(), BTN_H, label,
                    armed ? Ui.DANGER : Ui.GHOST, armed || !tabDefault());
        }
        int doneW = doneW();
        Ui.button(ctx, tr, mouseX, mouseY, x + W - PAD - doneW, footerY(), doneW, BTN_H,
                "Готово", Ui.ACCENT);
    }

    private int resetW() {
        return Math.min(Math.max(Ui.buttonW(tr, CONFIRM), Ui.buttonW(tr, RESET_LABELS[tab])),
                cw() - doneW() - 8);
    }

    private int doneW() { return Math.max(84, Ui.buttonW(tr, "Готово")); }

    private boolean tabDefault() {
        return switch (tab) {
            case TAB_KEYS -> s.keysDefault();
            case TAB_LOOK -> s.lookDefault();
            case TAB_COLORS -> s.colors.isEmpty();
            default -> true;
        };
    }

    private void resetTab() {
        switch (tab) {
            case TAB_KEYS -> s.resetKeys();
            case TAB_LOOK -> s.resetLook();
            case TAB_COLORS -> s.applyPreset(Map.of());
            default -> { return; }
        }
        binding = null;
        closeColor();
        changed = true;
    }

    private void drawSide(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int top = y + HEAD_H + 1;
        Draw.roundRectGrad(ctx, x + 1, top, side - 1, y + h - 1 - top, 0, 0, 0, Ui.R - 1,
                Draw.opaque(Ui.RAIL), Draw.opaque(Ui.RAIL));
        Ui.vline(ctx, x + side, top, y + h - 1 - top);
        boolean searching = !query().isEmpty();
        for (int i = 0; i < TABS.size(); i++) {
            int ny = navY(i), nx = x + 6, nw = side - 12;
            boolean active = i == tab;
            boolean hov = !active && Ui.hit(mouseX, mouseY, nx, ny, nw, NAV_H);
            boolean empty = searching && i != TAB_COLLAB && matchCount(i) == 0;
            if (active) {
                Draw.round(ctx, nx, ny, nw, NAV_H, Settings.radius(NAV_H),
                        Draw.opaque(Draw.mix(Ui.BTN_ON, Theme.ACCENT, 0.25f)));
                Draw.rect(ctx, nx, ny + 5, 2, NAV_H - 10, Draw.opaque(Theme.ACCENT));
            } else if (hov) {
                Draw.round(ctx, nx, ny, nw, NAV_H, Settings.radius(NAV_H), Draw.opaque(Ui.BTN_HOVER));
            }
            int ink = active ? (Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT)
                    : empty ? Theme.TEXT_FAINT : hov ? Theme.TEXT : Theme.TEXT_DIM;
            navIcon(ctx, i, nx + 8, ny + NAV_H / 2, ink);
            int badgeW = navBadge(ctx, i, nx + nw - 5, ny + (NAV_H - 11) / 2, searching);
            Draw.textFit(ctx, tr, TABS.get(i), nx + 24, ny + (NAV_H - Ui.TEXT_H) / 2,
                    nw - 24 - 6 - badgeW, ink, false);
        }
    }

    private void navIcon(GuiGraphicsExtractor ctx, int i, int left, int midY, int ink) {
        if (i == TAB_COLORS) {
            int n = categories.size();
            for (int k = 0; k < 4; k++) {
                int rgb = n == 0 ? ink : categories.get(k * Math.max(1, n / 4) % n).color;
                Draw.rect(ctx, left + 1 + (k % 2) * 4, midY - 4 + (k / 2) * 4, 3, 3, Draw.opaque(rgb));
            }
            return;
        }
        String[] g = switch (i) {
            case TAB_KEYS -> Draw.KEYBOARD;
            case TAB_LOOK -> Draw.IMAGE;
            default -> Draw.USERS;
        };
        Draw.glyph(ctx, g, left + (9 - Draw.glyphW(g)) / 2, midY - Draw.glyphH(g) / 2, ink);
    }

    private int navBadge(GuiGraphicsExtractor ctx, int i, int right, int by, boolean searching) {
        String text = "";
        int fill = Ui.BTN, ink = Theme.TEXT_DIM;
        if (searching) {
            if (i != TAB_COLLAB) text = String.valueOf(matchCount(i));
        } else if (i == TAB_KEYS && s.clashCount() > 0) {
            text = String.valueOf(s.clashCount());
            fill = Ui.DANGER_BG;
            ink = Theme.DANGER;
        } else if (i == TAB_COLORS && !s.colors.isEmpty()) {
            text = String.valueOf(s.colors.size());
        } else if (i == TAB_COLLAB && Collab.on()) {
            int lamp = switch (Collab.stage()) {
                case LIVE -> Collab.paused() ? Theme.TEXT_FAINT : Theme.OK;
                case BROKEN -> Theme.DANGER;
                default -> Theme.ACCENT;
            };
            Draw.dot(ctx, right - 5, by + 4, Draw.opaque(lamp));
            return 8;
        }
        if (text.isEmpty()) return 0;
        int w = Draw.badgeWidth(tr, text);
        Draw.badge(ctx, tr, text, right - w, by, Draw.opaque(fill), ink);
        return w;
    }

    private void drawSearch(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int sw = searchW();
        if (sw == 0 || searchField == null) return;
        int sx = searchX(), sy = searchY();
        boolean focused = searchField.isFocused();
        Ui.input(ctx, sx, sy, sw, SEARCH_H, focused);
        Draw.glyph(ctx, Draw.SEARCH, sx + 6, sy + (SEARCH_H - Draw.glyphH(Draw.SEARCH)) / 2,
                focused ? Theme.TEXT_DIM : Theme.TEXT_FAINT);
        boolean clear = !searchField.getValue().isEmpty();
        searchField.setX(sx + 17);
        searchField.setY(sy + (SEARCH_H - Ui.TEXT_H) / 2);
        Ui.width(searchField, sw - 23 - (clear ? 12 : 0));
        searchField.extractRenderState(ctx, mouseX, mouseY, 0);
        Ui.placeholder(ctx, tr, searchField);
        if (clear) {
            boolean hov = Ui.hit(mouseX, mouseY, sx + sw - 16, sy, 16, SEARCH_H);
            Draw.glyph(ctx, Draw.CROSS, sx + sw - 12, sy + (SEARCH_H - 6) / 2,
                    hov ? Theme.TEXT : Theme.TEXT_FAINT);
        }
    }

    private void drawCaption(GuiGraphicsExtractor ctx, String text, int cy) {
        int lx = cx();
        String fit = Draw.fit(tr, text, cw());
        Draw.text(ctx, tr, fit, lx, cy + 3, Theme.TEXT_FAINT, false);
        int after = lx + tr.width(fit) + 6;
        if (after < lx + cw()) Ui.hairline(ctx, after, cy + 7, lx + cw() - after);
    }

    private void rowBack(GuiGraphicsExtractor ctx, int ry, int rh, int fill) {
        Draw.round(ctx, cx() - 6, ry, cw() + 12, rh, Math.min(Ui.R_SM, rh / 2), Draw.opaque(fill));
    }

    private boolean over(int mouseX, int mouseY, int ry, int rh) {
        return inView(mouseY) && inRowX(mouseX) && mouseY >= ry && mouseY < ry + rh;
    }

    private void drawNothing(GuiGraphicsExtractor ctx, int top) {
        Draw.textCenter(ctx, tr, NOTHING, cx(), top + 8, cw(), cw(), Theme.TEXT_FAINT, false);
    }

    private List<String> keyParts(Hot hot) {
        List<String> parts = new ArrayList<>();
        int mods = s.mods(hot);
        if ((mods & Settings.CTRL) != 0) parts.add("Ctrl");
        if ((mods & Settings.SHIFT) != 0) parts.add("Shift");
        if ((mods & Settings.ALT) != 0) parts.add("Alt");
        parts.add(Settings.keyName(s.code(hot)));
        return parts;
    }

    private int capW(String part) { return Math.max(CAP_KEY_H, tr.width(part) + 10); }

    private int capsW(Hot hot) {
        if (binding == hot) return Ui.buttonW(tr, WAITING);
        if (s.code(hot) == Settings.NONE) return tr.width(UNSET);
        int w = 0;
        for (String part : keyParts(hot)) w += capW(part) + 3;
        return w - 3 + (s.clashes(hot) ? Draw.glyphW(Draw.WARN) + 5 : 0);
    }

    private int resetIconX(Hot hot) { return cx() + cw() - capsW(hot) - 6 - 14; }

    private void drawKeys(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int top) {
        List<Item> items = keyItems();
        if (items.isEmpty()) { drawNothing(ctx, top); return; }
        for (Item it : items) {
            int ry = top + it.y();
            if (it.caption() != null) { drawCaption(ctx, it.caption(), ry); continue; }
            drawKeyRow(ctx, mouseX, mouseY, it.hot(), ry);
        }
        Draw.textFit(ctx, tr, "ПКМ — снять привязку",
                cx(), top + end(items) + 8, cw(), Theme.TEXT_FAINT, false);
    }

    private void drawKeyRow(GuiGraphicsExtractor ctx, int mouseX, int mouseY, Hot hot, int ry) {
        boolean waiting = binding == hot;
        boolean clash = !waiting && s.clashes(hot);
        boolean hov = over(mouseX, mouseY, ry, KEY_H);
        if (waiting) rowBack(ctx, ry, KEY_H, Ui.BTN_HOVER);
        else if (hov) rowBack(ctx, ry, KEY_H, Ui.BTN);

        int right = cx() + cw();
        int capsW = capsW(hot);
        boolean custom = !s.isDefault(hot);
        Draw.textFit(ctx, tr, hot.label, cx(), ry + (KEY_H - Ui.TEXT_H) / 2,
                cw() - capsW - (custom ? 26 : 8), clash ? Theme.DANGER : Theme.TEXT, false);

        int cy = ry + (KEY_H - CAP_KEY_H) / 2;
        if (waiting) {
            Ui.button(ctx, tr, mouseX, mouseY, right - capsW, cy, capsW, CAP_KEY_H, WAITING, Ui.ACTIVE);
        } else if (s.code(hot) == Settings.NONE) {
            Draw.textRight(ctx, tr, UNSET, right, ry + (KEY_H - Ui.TEXT_H) / 2,
                    Theme.TEXT_FAINT, false);
        } else {
            int at = right - capsW;
            if (clash) {
                Draw.glyph(ctx, Draw.WARN, at, ry + (KEY_H - Draw.glyphH(Draw.WARN)) / 2,
                        Theme.DANGER);
                at += Draw.glyphW(Draw.WARN) + 5;
            }
            for (String part : keyParts(hot)) {
                int w = capW(part);
                keyCap(ctx, at, cy, w, part, clash);
                at += w + 3;
            }
        }

        if (custom && !waiting) {
            int ix = resetIconX(hot), iy = ry + (KEY_H - 14) / 2;
            boolean ih = inView(mouseY) && Ui.hit(mouseX, mouseY, ix, iy, 14, 14);
            if (ih) Draw.round(ctx, ix, iy, 14, 14, Settings.radius(14), Draw.opaque(Ui.BTN_HOVER));
            Draw.glyph(ctx, Draw.RESET, ix + (14 - Draw.glyphW(Draw.RESET)) / 2,
                    iy + (14 - Draw.glyphH(Draw.RESET)) / 2, ih ? Theme.TEXT : Theme.TEXT_FAINT);
        }
    }

    private void keyCap(GuiGraphicsExtractor ctx, int kx, int ky, int w, String text, boolean clash) {
        int face = clash ? Ui.DANGER_BG : Ui.BTN_HOVER;
        int r = Settings.radius(CAP_KEY_H);
        Draw.round(ctx, kx, ky, w, CAP_KEY_H, r, Draw.opaque(Draw.shade(face, -0.35f)));
        Draw.round(ctx, kx, ky, w, CAP_KEY_H - 2, r, Draw.opaque(face));
        Draw.textCenter(ctx, tr, text, kx, ky + (CAP_KEY_H - 2 - Ui.TEXT_H) / 2 + 1, w, w - 4,
                clash ? Theme.DANGER : Theme.TEXT, false);
    }

    private void drawLook(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int top) {
        List<Item> items = lookItems();
        if (items.isEmpty()) { drawNothing(ctx, top); return; }
        for (Item it : items) {
            int ry = top + it.y();
            if (it.preview()) drawPreview(ctx, cx(), ry, cw(), PREVIEW_H, mouseX, mouseY);
            else if (it.caption() != null) drawCaption(ctx, it.caption(), ry);
            else drawLookRow(ctx, mouseX, mouseY, it.row(), ry);
        }
    }

    private void drawLookRow(GuiGraphicsExtractor ctx, int mouseX, int mouseY, Row r, int ry) {
        boolean hov = over(mouseX, mouseY, ry, r.h);
        if (hov && r.flag) rowBack(ctx, ry, r.h, Ui.BTN);
        int textW = r.inline ? cw() - r.ctrlW() - 14 : cw();
        int textTop = r.inline ? ry + (r.h - 9 - r.lines.size() * 10) / 2 : ry + 5;
        Draw.textFit(ctx, tr, r.label, cx(), textTop, textW, Theme.TEXT, false);
        for (int i = 0; i < r.lines.size(); i++)
            Draw.text(ctx, tr, r.lines.get(i), cx(), textTop + 11 + i * 10, Theme.TEXT_FAINT, false);
        int ox = r.inline ? cx() + cw() - r.ctrlW() : cx();
        int oy = ry + r.ctrlY();
        if (r.flag) toggleSwitch(ctx, ox, oy, r.on(), hov);
        else r.cells.render(ctx, tr, mouseX, inView(mouseY) ? mouseY : -1000, ox, oy,
                r.get.getAsInt(), Theme.ACCENT);
    }

    private static void toggleSwitch(GuiGraphicsExtractor ctx, int sx, int sy, boolean on, boolean hov) {
        int track = on ? (hov ? Draw.shade(Theme.ACCENT, 0.15f) : Theme.ACCENT)
                : (hov ? Draw.shade(Ui.BORDER, 0.12f) : Ui.BORDER);
        Draw.pill(ctx, sx, sy, SWITCH_W, SWITCH_H, Draw.opaque(track));
        int k = SWITCH_H - 4;
        int kx = on ? sx + SWITCH_W - 2 - k : sx + 2;
        Draw.round(ctx, kx, sy + 2, k, k, k / 2, Draw.opaque(on ? 0xFFFFFF : Theme.TEXT_DIM));
    }

    private void drawPreview(GuiGraphicsExtractor ctx, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        Ui.well(ctx, px, py, pw, ph);
        ctx.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
        drawPreviewGrid(ctx, px + 1, py + 1, pw - 2, ph - 2);

        int bx = px + 10, by = py + 10;
        int w1 = Ui.buttonW(tr, "Кнопка"), w2 = Ui.buttonW(tr, "Под курсором");
        Ui.button(ctx, tr, mouseX, mouseY, bx, by, w1, 18, "Кнопка", Ui.GHOST);
        Ui.button(ctx, tr, bx + w1 + 6 + w2 / 2, by + 9, bx + w1 + 6, by, w2, 18,
                "Под курсором", Ui.GHOST);
        int w3 = Ui.buttonW(tr, "Готово");
        Ui.button(ctx, tr, mouseX, mouseY, bx, by + 24, w3, 18, "Готово", Ui.ACCENT);
        Ui.chip(ctx, tr, bx + w3 + 6, by + 24, 58, 18, "выбрано", true, false, Theme.ACCENT);
        Ui.chip(ctx, tr, bx + w3 + 6 + 62, by + 24, 46, 18, "нет", false, false, Theme.ACCENT);

        if (pw >= 330) drawPreviewBlock(ctx, px + pw - 118, py + 12);
        ctx.disableScissor();
    }

    private void drawPreviewGrid(GuiGraphicsExtractor ctx, int gx, int gy, int gw, int gh) {
        if (s.grid == Settings.GRID_NONE) return;
        int step = 13;
        for (int i = 0; gx + i * step < gx + gw; i++) {
            for (int j = 0; gy + j * step < gy + gh; j++) {
                int cx = gx + i * step, cy = gy + j * step;
                boolean big = i % 4 == 0 && j % 4 == 0;
                if (s.grid == Settings.GRID_DOTS) {
                    Draw.rect(ctx, cx, cy, big ? 2 : 1, big ? 2 : 1,
                            big ? Theme.GRID_STRONG : Theme.GRID);
                } else {
                    if (j == 0) Draw.rect(ctx, cx, gy, 1, gh,
                            i % 4 == 0 ? Theme.GRID_STRONG : Theme.GRID);
                    if (i == 0) Draw.rect(ctx, gx, cy, gw, 1,
                            j % 4 == 0 ? Theme.GRID_STRONG : Theme.GRID);
                }
            }
        }
    }

    private void drawPreviewBlock(GuiGraphicsExtractor ctx, int bx, int by) {
        Catalog.Category cat = Catalog.category("Событие игрока");
        int base = cat == null ? 0x44EBF1 : cat.color;
        BlockView.sample(ctx, tr, bx, by, 106, 34, base, "Событие игрока",
                Catalog.TYPE_COLORS.getOrDefault("Текст", 0x3AB3DA));
    }

    private int collab(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int top,
                       MouseButtonEvent click, boolean doubled, double cx, double cy) {
        boolean act = click != null;
        int inner = cw();
        int lx = cx();
        int at = 0;

        Collab.Stage stage = Collab.stage();
        boolean on = Collab.on();
        boolean live = Collab.live();

        String said = Collab.note().isEmpty()
                ? "полотно станет общим для всех, у кого есть код"
                : Collab.note();
        List<String> lines = Ui.wrap(tr, said, inner - 27, 3);
        int statusH = 25 + Math.max(1, lines.size()) * 10 + 9;

        if (ctx != null) {
            Ui.well(ctx, lx, top + at, inner, statusH);
            int lamp = switch (stage) {
                case LIVE -> Collab.paused() ? Theme.TEXT_FAINT : Theme.OK;
                case CONNECTING, WAITING -> Theme.ACCENT;
                case BROKEN -> Theme.DANGER;
                default -> Theme.TEXT_FAINT;
            };
            Draw.dot(ctx, lx + 9, top + at + 12, Draw.opaque(lamp));
            String head = switch (stage) {
                case OFF -> "Совместная работа выключена";
                case CONNECTING -> "Подключаюсь…";
                case WAITING -> "Забираю полотно комнаты…";
                case BROKEN -> "Не получилось";
                default -> Collab.paused() ? "На паузе: вы в другом мире" : "В комнате";
            };
            String count = live ? Ui.plural(Collab.members(), "участник", "участника", "участников") : "";
            int countW = count.isEmpty() ? 0 : tr.width(count) + 8;
            Draw.textFit(ctx, tr, head, lx + 18, top + at + 10, inner - 26 - countW, Theme.TEXT, false);
            if (!count.isEmpty())
                Draw.textRight(ctx, tr, count, lx + inner - 9, top + at + 10, Theme.TEXT_DIM, false);
            for (int i = 0; i < lines.size(); i++)
                Draw.text(ctx, tr, lines.get(i), lx + 18, top + at + 25 + i * 10,
                        Theme.TEXT_FAINT, false);
        }
        at += statusH + GAP;

        String code = Collab.code();
        if (on && !code.isEmpty()) {
            int copyW = Ui.buttonW(tr, "Копировать");
            int wellW = inner - copyW - 6;
            int rowY = top + at + CAP_H + 2;
            if (ctx != null) {
                Ui.caption(ctx, tr, "КОД ПРИГЛАШЕНИЯ", lx, top + at, inner);
                Ui.well(ctx, lx, rowY, wellW, ROW_H + 3);
                Draw.textFit(ctx, tr, code, lx + 7, rowY + (ROW_H + 3 - Ui.TEXT_H) / 2,
                        wellW - 14, Theme.TEXT, false);
                boolean fresh = System.currentTimeMillis() - copied < 1500;
                Ui.button(ctx, tr, mouseX, mouseY, lx + wellW + 6, rowY, copyW, ROW_H + 3,
                        fresh ? "Готово" : "Копировать", fresh ? Ui.ACTIVE : Ui.GHOST);
            } else if (act && Ui.hit(cx, cy, lx + wellW + 6, rowY, copyW, ROW_H + 3)) {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.keyboardHandler != null) client.keyboardHandler.setClipboard(code);
                copied = System.currentTimeMillis();
            }
            at += CAP_H + 2 + ROW_H + 3 + GAP;
        }

        if (on) {
            if (ctx != null)
                Ui.button(ctx, tr, mouseX, mouseY, lx, top + at, inner, BTN_H, "Отключиться",
                        Ui.DANGER);
            else if (act && Ui.hit(cx, cy, lx, top + at, inner, BTN_H)) Collab.stop();
            at += BTN_H + GAP;
        } else {
            if (ctx != null)
                Ui.button(ctx, tr, mouseX, mouseY, lx, top + at, inner, BTN_H,
                        "Открыть общий доступ", Ui.ACCENT);
            else if (act && Ui.hit(cx, cy, lx, top + at, inner, BTN_H)) Collab.host();
            at += BTN_H + GAP;

            int goW = Ui.buttonW(tr, "Войти");
            int fieldW = inner - goW - 6;
            int rowY = top + at + CAP_H + 2;
            boolean full = codeField != null && !codeField.getValue().isBlank();
            if (ctx != null) {
                Ui.caption(ctx, tr, "ВОЙТИ ПО ЧУЖОМУ КОДУ", lx, top + at, inner);
                Ui.input(ctx, lx, rowY, fieldW, ROW_H, codeField != null && codeField.isFocused());
                if (codeField != null) {
                    codeField.setX(lx + 7);
                    codeField.setY(rowY + (ROW_H - Ui.TEXT_H) / 2);
                    Ui.width(codeField, fieldW - 12);
                }
                Ui.button(ctx, tr, mouseX, mouseY, lx + fieldW + 6, rowY, goW, ROW_H, "Войти",
                        Ui.GHOST, full);
                if (codeField != null) {
                    codeField.extractRenderState(ctx, mouseX, mouseY, 0);
                    Ui.placeholder(ctx, tr, codeField);
                }
                Draw.textFit(ctx, tr, "полотно заменится, копия — в файле",
                        lx, rowY + ROW_H + 4, inner, Theme.TEXT_FAINT, false);
            } else if (act) {
                if (full && Ui.hit(cx, cy, lx + fieldW + 6, rowY, goW, ROW_H))
                    Collab.guest(codeField.getValue());
                else if (Ui.hit(cx, cy, lx, rowY, fieldW, ROW_H)) grab(codeField, click, doubled);
            }
            at += CAP_H + 2 + ROW_H + 4 + CAP_H + GAP;
        }

        int nameY = top + at + CAP_H + 2;
        if (ctx != null) {
            Ui.caption(ctx, tr, "ИМЯ В КОМНАТЕ", lx, top + at, inner);
            Ui.input(ctx, lx, nameY, inner, ROW_H, nameField != null && nameField.isFocused());
            if (nameField != null) {
                nameField.setX(lx + 7);
                nameField.setY(nameY + (ROW_H - Ui.TEXT_H) / 2);
                Ui.width(nameField, inner - 12);
                nameField.extractRenderState(ctx, mouseX, mouseY, 0);
                Ui.placeholder(ctx, tr, nameField);
            }
        } else if (act && Ui.hit(cx, cy, lx, nameY, inner, ROW_H)) {
            grab(nameField, click, doubled);
        }
        at += CAP_H + 2 + ROW_H + GAP;

        if (ctx != null)
            Ui.toggle(ctx, tr, mouseX, mouseY, lx, top + at, inner, BTN_H,
                    "Показывать чужие курсоры", s.collabCursors);
        else if (act && Ui.hit(cx, cy, lx, top + at, inner, BTN_H))
            s.collabCursors = !s.collabCursors;
        at += BTN_H + GAP;

        if (live) {
            if (ctx != null) {
                Ui.caption(ctx, tr, "УЧАСТНИКИ", lx, top + at, inner);
                int my = top + at + CAP_H + 2;
                Draw.dot(ctx, lx + 2, my + 3, Draw.opaque(Theme.ACCENT));
                Draw.textFit(ctx, tr, Collab.myName() + " · вы", lx + 11, my, inner - 11,
                        Theme.TEXT, false);
                my += MEMBER_H;
                for (Collab.Peer p : Collab.peers()) {
                    Draw.dot(ctx, lx + 2, my + 3, Draw.opaque(p.ink));
                    Draw.textFit(ctx, tr, p.name.isEmpty() ? "…" : p.name, lx + 11, my,
                            inner - 11, Theme.TEXT_DIM, false);
                    my += MEMBER_H;
                }
            }
            at += CAP_H + 2 + MEMBER_H * (1 + Collab.peers().size());
        }
        return at + 6;
    }

    private void grab(EditBox field, MouseButtonEvent click, boolean doubled) {
        if (field == null) return;
        field.setFocused(true);
        field.onClick(click, doubled);
        dragField = field;
    }

    private int colorsTop() { return BTN_H + 10; }

    private int rowY(int pos) {
        int at = colorsTop() + pos * (COLOR_H + ROW_GAP);
        if (openColor >= 0) {
            int open = visibleColors().indexOf(openColor);
            if (open >= 0 && pos > open) at += pickerH();
        }
        return at;
    }

    private int pickerH() { return SV_H + HUE_H + 12 + ROW_H + 8; }

    private int presetW() { return Ui.buttonW(tr, PRESET); }

    private int pickRx() { return cx() + SV_W + 12; }

    private int pickRw() { return cw() - SV_W - 12; }

    private void drawColors(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, int top) {
        int presetW = presetW();
        Ui.button(ctx, tr, mouseX, mouseY, cx(), top, presetW, BTN_H, PRESET, Ui.GHOST);
        Draw.textFit(ctx, tr, "ПКМ — вернуть цвет", cx() + presetW + 10, top + (BTN_H - Ui.TEXT_H) / 2,
                cw() - presetW - 10, Theme.TEXT_FAINT, false);

        List<Integer> shown = visibleColors();
        if (shown.isEmpty()) { drawNothing(ctx, top + colorsTop()); return; }
        for (int pos = 0; pos < shown.size(); pos++) {
            int i = shown.get(pos);
            Catalog.Category cat = categories.get(i);
            int ry = top + rowY(pos);
            boolean hov = over(mouseX, mouseY, ry, COLOR_H);
            boolean open = openColor == i;
            if (open) rowBack(ctx, ry, COLOR_H, Ui.BTN_ON);
            else if (hov) rowBack(ctx, ry, COLOR_H, Ui.BTN);
            Ui.swatch(ctx, cx(), ry + 3, 12, 12, cat.color, true, false);
            boolean custom = s.colors.containsKey(cat.name);
            int ink = open ? (Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT) : Theme.TEXT;
            Draw.textFit(ctx, tr, cat.name, cx() + 18, ry + (COLOR_H - Ui.TEXT_H) / 2,
                    cw() - 18 - 62, ink, false);
            String value = String.format("#%06X", cat.color);
            Draw.textRight(ctx, tr, value, cx() + cw() - (custom ? 12 : 0),
                    ry + (COLOR_H - Ui.TEXT_H) / 2,
                    open ? ink : Theme.TEXT_FAINT, false);
            if (custom) Draw.dot(ctx, cx() + cw() - 6, ry + COLOR_H / 2 - 2,
                    Draw.opaque(Theme.ACCENT));
            if (open) drawPicker(ctx, mouseX, mouseY, delta, ry + COLOR_H + 4, cat);
        }
    }

    private void drawPicker(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, int py,
                            Catalog.Category cat) {
        int lx = cx();
        Ui.svSquare(ctx, lx, py, SV_W, SV_H, pickH, pickS, pickV, 3);
        Ui.hueBar(ctx, lx, py + SV_H + 4, SV_W, HUE_H, pickH);

        int rx = pickRx(), rw = pickRw();
        Ui.swatch(ctx, rx, py, rw, 22, cat.color, true, false);
        boolean focused = hexField != null && hexField.isFocused();
        Ui.input(ctx, rx, py + 26, rw, ROW_H, focused);
        Draw.text(ctx, tr, "#", rx + 6, py + 26 + (ROW_H - Ui.TEXT_H) / 2, Theme.TEXT_FAINT, false);
        if (hexField != null) {
            hexField.setX(rx + 13);
            hexField.setY(py + 26 + (ROW_H - Ui.TEXT_H) / 2);
            Ui.width(hexField, rw - 19);
            hexField.extractRenderState(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, tr, hexField);
        }
        Ui.button(ctx, tr, mouseX, mouseY, rx, py + 26 + ROW_H + 4, rw, ROW_H, "Вернуть",
                Ui.GHOST, s.colors.containsKey(cat.name));
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (!contains(mx, my)) { close(); return true; }
        if (bar.grabbed(mx, my, 1, maxScroll(), v -> scroll[tab] = v)) return true;
        if (hexField != null) {
            boolean inField = Ui.hit(mx, my, hexField.getX() - 8, hexField.getY() - 5,
                    hexField.getWidth() + 12, ROW_H);
            hexField.setFocused(inField);
            if (inField) {
                if (doubled) {
                    hexField.moveCursorTo(0, false);
                    hexField.moveCursorTo(hexField.getValue().length(), true);
                } else {
                    hexField.moveCursorTo(hexIndexAt(mx), shiftDown());
                }
                hexDrag = true;
                return true;
            }
        }

        if (Ui.hit(mx, my, x + W - PAD - 14, y + (HEAD_H - 14) / 2, 14, 14)) { close(); return true; }
        if (hexField != null) hexField.setFocused(false);

        searchField.setFocused(false);
        int sw = searchW();
        if (sw > 0 && Ui.hit(mx, my, searchX(), searchY(), sw, SEARCH_H)) {
            if (!searchField.getValue().isEmpty() && mx >= searchX() + sw - 16) {
                searchField.setValue("");
                return true;
            }
            binding = null;
            grab(searchField, click, doubled);
            return true;
        }

        int seg = navAt(mx, my);
        if (seg >= 0) { switchTab(seg); return true; }

        if (tab != TAB_COLLAB && Ui.hit(mx, my, cx(), footerY(), resetW(), BTN_H)) {
            if (confirmTab == tab) {
                confirmTab = -1;
                resetTab();
            } else if (!tabDefault()) {
                confirmTab = tab;
                confirmAt = System.currentTimeMillis();
            }
            return true;
        }
        confirmTab = -1;
        int doneW = doneW();
        if (Ui.hit(mx, my, x + W - PAD - doneW, footerY(), doneW, BTN_H)) { close(); return true; }

        int cy = contentY(), ch = contentH();
        if (!Ui.hit(mx, my, x + side, cy, W - side, ch)) return true;
        double dy = my + scroll() - cy;
        switch (tab) {
            case TAB_KEYS -> keysClicked(mx, dy, button);
            case TAB_LOOK -> lookClicked(mx, dy);
            case TAB_COLORS -> colorsClicked(mx, dy, button);
            default -> {
                if (nameField != null) nameField.setFocused(false);
                if (codeField != null) codeField.setFocused(false);
                dragField = null;
                collab(null, 0, 0, 0, click, doubled, mx, dy);
            }
        }
        return true;
    }

    private void keysClicked(double mx, double dy, int button) {
        if (!inRowX(mx)) return;
        for (Item it : keyItems()) {
            Hot hot = it.hot();
            if (hot == null || dy < it.y() || dy >= it.y() + it.h()) continue;
            if (overResetIcon(hot, mx, dy - it.y())) s.resetKey(hot);
            else if (button == 1) s.bind(hot, Settings.NONE, 0);
            else { binding = binding == hot ? null : hot; return; }
            binding = null;
            changed = true;
            return;
        }
        binding = null;
    }

    private boolean overResetIcon(Hot hot, double mx, double rowDy) {
        if (s.isDefault(hot) || binding == hot) return false;
        int ix = resetIconX(hot);
        double iy = (KEY_H - 14) / 2.0;
        return mx >= ix && mx < ix + 14 && rowDy >= iy && rowDy < iy + 14;
    }

    private void lookClicked(double mx, double dy) {
        for (Item it : lookItems()) {
            Row r = it.row();
            if (r == null || dy < it.y() || dy >= it.y() + it.h()) continue;
            if (r.flag) {
                if (!inRowX(mx)) return;
                r.set.accept(r.on() ? 1 : 0);
            } else {
                int ox = r.inline ? cx() + cw() - r.ctrlW() : cx();
                int i = r.cells.indexAt(mx, dy, ox, it.y() + r.ctrlY());
                if (i < 0) return;
                r.set.accept(i);
            }
            s.apply();
            changed = true;
            return;
        }
    }

    private void colorsClicked(double mx, double dy, int button) {
        if (dy >= 0 && dy < BTN_H) {
            if (mx >= cx() && mx < cx() + presetW()) {
                s.applyPreset(Catalog.classicPalette());
                closeColor();
                changed = true;
            }
            return;
        }
        List<Integer> shown = visibleColors();
        for (int pos = 0; pos < shown.size(); pos++) {
            int i = shown.get(pos);
            int ry = rowY(pos);
            if (dy >= ry && dy < ry + COLOR_H) {
                Catalog.Category cat = categories.get(i);
                if (button == 1) {
                    s.clearColor(cat.name);
                    if (openColor == i) { setPick(cat.color); syncHexField(cat.color); }
                    changed = true;
                    return;
                }
                if (openColor == i) { closeColor(); return; }
                openColor = i;
                setPick(cat.color);
                openHexField(cat.color);
                return;
            }
            if (openColor != i) continue;
            int py = ry + COLOR_H + 4;
            Catalog.Category cat = categories.get(i);
            int lx = cx();
            if (mx >= lx && mx < lx + SV_W && dy >= py && dy < py + SV_H) {
                dragging = 1;
                dragSv(mx, dy - py);
                return;
            }
            int hy = py + SV_H + 4;
            if (mx >= lx && mx < lx + SV_W && dy >= hy - 2 && dy < hy + HUE_H + 2) {
                dragging = 2;
                dragHue(mx);
                return;
            }
            if (Ui.hit(mx, dy, pickRx(), py + 26 + ROW_H + 4, pickRw(), ROW_H)
                    && s.colors.containsKey(cat.name)) {
                s.clearColor(cat.name);
                setPick(cat.color);
                syncHexField(cat.color);
                changed = true;
                return;
            }
        }
    }

    private void openHexField(int rgb) {
        hexField = Ui.hexField(tr, 0, 0, 40, 10, "RRGGBB");
        hexField.setMaxLength(6);
        hexField.setValue(String.format("%06X", rgb));
        hexField.setResponder(t -> {
            if (syncing || t.length() != 6 || openColor < 0) return;
            int color = McText.hexRgb(t);
            s.setColor(categories.get(openColor).name, color);
            setPick(color);
            changed = true;
        });
    }

    private static boolean shiftDown() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        return InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private int hexIndexAt(double mx) {
        int rel = (int) Math.round(mx - hexField.getX());
        if (rel <= 0) return 0;
        return tr.plainSubstrByWidth(hexField.getValue(), rel).length();
    }

    private void setPick(int rgb) {
        float[] hsv = McText.rgbHsv(rgb);
        pickH = hsv[0];
        pickS = hsv[1];
        pickV = hsv[2];
    }

    private void syncHexField(int rgb) {
        if (hexField == null) return;
        syncing = true;
        hexField.setValue(String.format("%06X", rgb));
        syncing = false;
    }

    private void dragSv(double mx, double dy) {
        pickS = clamp01((float) (mx - cx()) / (SV_W - 1));
        pickV = 1 - clamp01((float) dy / (SV_H - 1));
        applyPick();
    }

    private void dragHue(double mx) {
        pickH = clamp01((float) (mx - cx()) / (SV_W - 1));
        applyPick();
    }

    private void applyPick() {
        if (openColor < 0) return;
        int rgb = McText.hsvRgb(pickH, pickS, pickV);
        s.setColor(categories.get(openColor).name, rgb);
        syncHexField(rgb);
        changed = true;
    }

    private static float clamp01(float v) { return v < 0 ? 0 : Math.min(v, 1); }

    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        double mx = click.x(), my = click.y();
        if (bar.dragged(my, 1, maxScroll(), v -> scroll[tab] = v)) return true;
        if (dragField != null) {
            dragField.mouseDragged(click, dx, dy);
            return true;
        }
        if (hexDrag && hexField != null) {
            hexField.moveCursorTo(hexIndexAt(mx), true);
            return true;
        }
        if (dragging == 0) return false;
        double inside = my + scroll() - contentY();
        if (dragging == 1) {
            int pos = visibleColors().indexOf(openColor);
            if (pos < 0) return true;
            dragSv(mx, inside - (rowY(pos) + COLOR_H + 4));
        } else {
            dragHue(mx);
        }
        return true;
    }

    public void mouseReleased() { dragging = 0; hexDrag = false; dragField = null; bar.release(); }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        scroll[tab] = Math.max(0, Math.min(maxScroll(),
                scroll() - (int) Math.round(amount * 18)));
        return true;
    }

    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (binding != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { binding = null; return true; }
            if (isModifier(key)) return true;
            s.bind(binding, key, input.modifiers());
            binding = null;
            changed = true;
            return true;
        }
        if (hexField != null && hexField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_KP_ENTER) {
                hexField.setFocused(false);
                return true;
            }
            hexField.keyPressed(input);
            return true;
        }
        EditBox typing = focusedField();
        if (typing != null) {
            boolean done = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
            if (typing == searchField && key == GLFW.GLFW_KEY_ESCAPE && !typing.getValue().isEmpty()) {
                typing.setValue("");
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || done) {
                typing.setFocused(false);
                if (done && typing == codeField && !codeField.getValue().isBlank())
                    Collab.guest(codeField.getValue());
                return true;
            }
            typing.keyPressed(input);
            return true;
        }
        int mods = input.modifiers() & Settings.MOD_MASK;
        if (key == GLFW.GLFW_KEY_TAB && (mods & Settings.CTRL) != 0) {
            int step = (mods & Settings.SHIFT) != 0 ? TABS.size() - 1 : 1;
            switchTab((tab + step) % TABS.size());
            return true;
        }
        if (s.match(key, mods) == Hot.SEARCH && searchW() > 0) {
            searchField.setFocused(true);
            searchField.moveCursorToEnd(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) close();
        return true;
    }

    private EditBox focusedField() {
        if (nameField != null && nameField.isFocused()) return nameField;
        if (codeField != null && codeField.isFocused()) return codeField;
        if (searchField != null && searchField.isFocused()) return searchField;
        return null;
    }

    public boolean charTyped(CharacterEvent input) {
        if (hexField != null && hexField.isFocused()) return hexField.charTyped(input);
        EditBox typing = focusedField();
        return typing != null && typing.charTyped(input);
    }

    private static boolean isModifier(int key) {
        return key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private void close() {
        hexField = null;
        binding = null;
        closed = true;
        s.apply();
        s.save();
    }

    public void dispose() {
        if (!closed) close();
    }
}
