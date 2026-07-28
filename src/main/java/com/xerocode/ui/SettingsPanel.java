package com.xerocode.ui;

import com.xerocode.Catalog;
import com.xerocode.Settings;
import com.xerocode.Settings.Hot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class SettingsPanel {
    private static final int WANT_W = 384;
    private static final int PAD = 14;
    private static final int HEAD_H = 26;
    private static final int TAB_H = 18;
    private static final int ROW_H = 17;
    private static final int ROW_GAP = 3;
    private static final int BTN_H = 20;
    private static final int PREVIEW_H = 58;
    private static final int WANT_LABEL_W = 104;
    private static final int WANT_KEY_W = 96;
    private static final int HINT_H = 10;
    private static final int COLOR_H = 16;
    private static final int WANT_SV_W = 168;
    private static final int SV_H = 56, HUE_H = 8;
    private static final String WAITING = "жду клавишу…";

    private static final List<String> TABS = List.of("Клавиши", "Внешний вид", "Цвета");
    private static final int TAB_KEYS = 0, TAB_LOOK = 1, TAB_COLORS = 2;

    private static final class Row {
        final String label, hint;
        final IntSupplier get;
        final IntConsumer set;
        final Ui.Chips cells;
        int dy;

        Row(String label, String hint, String[] options, IntSupplier get, IntConsumer set,
            TextRenderer tr, int width) {
            this.label = label;
            this.hint = hint;
            this.get = get;
            this.set = set;
            this.cells = new Ui.Chips(tr, List.of(options), width, 15, 4);
        }

        int height() { return Math.max(cells.height(), 15) + 2 + HINT_H; }
    }

    private final Settings s = Settings.get();
    private final TextRenderer tr;
    private final List<Row> rows = new ArrayList<>();
    private final List<Catalog.Category> categories = new ArrayList<>();

    private int screenW, screenH;
    private int W, LABEL_W, KEY_W, SV_W;
    private int x, y, h;
    private int tab;
    private final int[] scroll = new int[TABS.size()];
    private Hot binding;
    private int openColor = -1;
    private float pickH, pickS, pickV;
    private TextFieldWidget hexField;
    private boolean syncing;
    private int dragging;
    private boolean hexDrag;
    private boolean closed, changed;

    public SettingsPanel(TextRenderer tr, int screenW, int screenH) {
        this.tr = tr;
        categories.addAll(Catalog.CATEGORIES);
        build(screenW, screenH);
    }

    public void resize(int sw, int sh) {
        if (sw == screenW && sh == screenH) return;
        rows.clear();
        binding = null;
        hexField = null;
        openColor = -1;
        build(sw, sh);
    }

    private void build(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        this.W = Ui.fitW(screenW, WANT_W);
        int inner = W - PAD * 2;
        this.LABEL_W = Math.min(WANT_LABEL_W, inner * 40 / 100);
        this.KEY_W = Math.min(WANT_KEY_W, inner * 45 / 100);
        this.SV_W = Math.min(WANT_SV_W, inner - 12 - 74);

        int chipsW = inner - LABEL_W;
        rows.add(new Row("Тема", "цвет самого редактора: панели, полотно, подписи",
                Settings.THEME_NAMES, () -> s.theme, v -> s.theme = v, tr, chipsW));
        rows.add(new Row("Кнопки", "форма и заливка кнопок, чипов и полей во всём редакторе",
                Settings.BTN_NAMES, () -> s.buttons, v -> s.buttons = v, tr, chipsW));
        rows.add(new Row("Тени", "тени под блоками и всплывающими панелями",
                Settings.YES_NO, () -> s.shadows ? 0 : 1, v -> s.shadows = v == 0, tr, chipsW));
        rows.add(new Row("Блоки", "заливка блоков и чипов: с переходом или в один тон",
                Settings.BLOCK_NAMES, () -> s.gradient ? 0 : 1, v -> s.gradient = v == 0,
                tr, chipsW));
        rows.add(new Row("Сетка полотна", "чем размечен фон за блоками",
                Settings.GRID_NAMES, () -> s.grid, v -> s.grid = v, tr, chipsW));
        rows.add(new Row("Мелкий текст", "сглаживать подписи, когда полотно отдалено и буква мельче пикселя",
                Settings.YES_NO, () -> s.smoothText ? 0 : 1, v -> s.smoothText = v == 0, tr, chipsW));

        int dy = 0;
        for (Row r : rows) {
            r.dy = dy;
            dy += r.height() + 7;
        }

        this.x = Ui.midX(screenW, W);
        this.h = Ui.fitH(screenH, 392);
        this.y = Ui.midY(screenH, h);
    }

    public boolean isClosed() { return closed; }

    public boolean consumeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    public boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, W, h); }

    private int tabsY()    { return y + HEAD_H + 10; }
    private int contentY() { return tabsY() + TAB_H + 12; }
    private int footerY()  { return y + h - 12 - BTN_H; }
    private int contentH() { return footerY() - 12 - contentY(); }

    private int contentNeed() {
        return switch (tab) {
            case TAB_KEYS -> Hot.values().length * (ROW_H + ROW_GAP) - ROW_GAP + 16;
            case TAB_LOOK -> {
                Row last = rows.get(rows.size() - 1);
                yield PREVIEW_H + 10 + last.dy + last.height();
            }
            default -> BTN_H + 10 + categories.size() * (COLOR_H + ROW_GAP) - ROW_GAP
                    + (openColor >= 0 ? SV_H + HUE_H + 12 + ROW_H + 8 : 0);
        };
    }

    private int maxScroll() { return Math.max(0, contentNeed() - contentH()); }

    private int scroll() { return Math.min(scroll[tab], maxScroll()); }

    private final Ui.Bar bar = new Ui.Bar();
    private int lastMx, lastMy;

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        lastMx = mouseX;
        lastMy = mouseY;
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, W, h);
        Ui.headerStrip(ctx, x, y, W, HEAD_H, Theme.ACCENT);
        Draw.glyph(ctx, Draw.GEAR, x + PAD, y + (HEAD_H - Draw.glyphH(Draw.GEAR)) / 2 + 1,
                Theme.TEXT_DIM);
        Draw.textFit(ctx, tr, "НАСТРОЙКИ", x + PAD + Draw.glyphW(Draw.GEAR) + 6,
                y + (HEAD_H - Ui.TEXT_H) / 2 + 1, W - PAD * 2 - 40, Theme.TEXT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + W - PAD - 14, y + (HEAD_H - 14) / 2, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, W - 2);

        Ui.segmented(ctx, tr, mouseX, mouseY, x + PAD, tabsY(), W - PAD * 2, TAB_H,
                TABS, tab, Theme.ACCENT);

        int cy = contentY(), ch = contentH();
        ctx.enableScissor(x + 1, cy, x + W - 1, cy + ch);
        int top = cy - scroll();
        switch (tab) {
            case TAB_KEYS -> drawKeys(ctx, mouseX, mouseY, top);
            case TAB_LOOK -> drawLook(ctx, mouseX, mouseY, top);
            default -> drawColors(ctx, mouseX, mouseY, delta, top);
        }
        ctx.disableScissor();
        bar.draw(ctx, x + W - 5, cy, ch, contentNeed(), ch, scroll(), lastMx, lastMy);

        Ui.hairline(ctx, x + 1, footerY() - 12, W - 2);
        int resetW = Ui.buttonW(tr, "По умолчанию");
        int doneW = Math.max(84, Ui.buttonW(tr, "Готово"));
        Ui.button(ctx, tr, mouseX, mouseY, x + PAD, footerY(), resetW, BTN_H,
                "По умолчанию", Ui.GHOST);
        Ui.button(ctx, tr, mouseX, mouseY, x + W - PAD - doneW, footerY(), doneW, BTN_H,
                "Готово", Ui.ACCENT);
    }

    private void drawKeys(DrawContext ctx, int mouseX, int mouseY, int top) {
        Hot[] all = Hot.values();
        for (int i = 0; i < all.length; i++) {
            Hot hot = all[i];
            int ry = top + i * (ROW_H + ROW_GAP);
            boolean waiting = binding == hot;
            boolean clash = !waiting && s.clashes(hot);
            Draw.textFit(ctx, tr, hot.label, x + PAD, ry + (ROW_H - Ui.TEXT_H) / 2,
                    W - PAD * 2 - KEY_W - 10, waiting ? Theme.TEXT : Theme.TEXT_DIM, false);
            String label = waiting ? WAITING : s.label(hot);
            int kind = waiting ? Ui.ACTIVE : clash ? Ui.DANGER : Ui.GHOST;
            Ui.button(ctx, tr, mouseX, mouseY, x + W - PAD - KEY_W, ry, KEY_W, ROW_H,
                    label, kind);
        }
        int hintY = top + all.length * (ROW_H + ROW_GAP) + 4;
        Draw.textFit(ctx, tr, "ЛКМ — назначить · ПКМ — снять · Esc — отмена",
                x + PAD, hintY, W - PAD * 2, Theme.TEXT_FAINT, false);
    }

    private void drawLook(DrawContext ctx, int mouseX, int mouseY, int top) {
        drawPreview(ctx, x + PAD, top, W - PAD * 2, PREVIEW_H, mouseX, mouseY);
        int rowsTop = top + PREVIEW_H + 10;
        for (Row r : rows) {
            int ry = rowsTop + r.dy;
            Draw.textFit(ctx, tr, r.label, x + PAD, ry + (15 - Ui.TEXT_H) / 2, LABEL_W - 8,
                    Theme.TEXT, false);
            r.cells.render(ctx, tr, mouseX, mouseY, x + PAD + LABEL_W, ry,
                    r.get.getAsInt(), Theme.ACCENT);
            String hint = r.hint;
            Draw.textFit(ctx, tr, hint, x + PAD,
                    ry + Math.max(r.cells.height(), 15) + 2, W - PAD * 2, Theme.TEXT_FAINT, false);
        }
    }

    private void drawPreview(DrawContext ctx, int px, int py, int pw, int ph, int mouseX, int mouseY) {
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

        drawPreviewBlock(ctx, px + pw - 118, py + 12);
        ctx.disableScissor();
    }

    private void drawPreviewGrid(DrawContext ctx, int gx, int gy, int gw, int gh) {
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

    private void drawPreviewBlock(DrawContext ctx, int bx, int by) {
        Catalog.Category cat = Catalog.category("Событие игрока");
        int base = cat == null ? 0x44EBF1 : cat.color;
        boolean grad = s.gradient;
        int head = Draw.shade(base, grad ? 0.15f : 0.04f);
        int top = Draw.opaque(head);
        int bottom = grad ? Draw.opaque(Draw.shade(base, -0.12f)) : top;
        int border = Draw.opaque(Draw.shade(base, -0.46f));
        int w = 106, hh = 34;
        Draw.shadow(ctx, bx, by + 6, w, hh - 6, 1);
        Draw.blockShape(ctx, bx, by, w, hh, 0, 0, top, bottom, border);
        Draw.rect(ctx, bx + 4, by + 2, w - 8, 3, Draw.argb(0x4D, 0xFFFFFF));
        boolean light = Draw.isLight(head);
        Draw.textFit(ctx, tr, "Событие игрока", bx + 7, by + 8, w - 14,
                light ? 0x141821 : 0xFFFFFF, !light);
        int chipBase = Catalog.TYPE_COLORS.getOrDefault("Текст", 0x3AB3DA);
        int chipTop = Draw.opaque(Draw.shade(chipBase, grad ? 0.12f : 0.02f));
        int chipBottom = grad ? Draw.opaque(Draw.shade(chipBase, -0.10f)) : chipTop;
        Draw.pill(ctx, bx + 7, by + 19, 60, 11, Draw.opaque(Draw.shade(chipBase, -0.5f)));
        Draw.pillGrad(ctx, bx + 8, by + 20, 58, 9, chipTop, chipBottom);
    }

    private int colorsTop() { return BTN_H + 10; }

    private int rowY(int i) {
        int at = colorsTop() + i * (COLOR_H + ROW_GAP);
        if (openColor >= 0 && i > openColor) at += pickerH();
        return at;
    }

    private int pickerH() { return SV_H + HUE_H + 12 + ROW_H + 8; }

    private void drawColors(DrawContext ctx, int mouseX, int mouseY, float delta, int top) {
        int resetW = Ui.buttonW(tr, "Сбросить всё");
        int presetW = Ui.buttonW(tr, "Классические");
        Ui.button(ctx, tr, mouseX, mouseY, x + PAD, top, resetW, BTN_H, "Сбросить всё", Ui.GHOST,
                !s.colors.isEmpty());
        Ui.button(ctx, tr, mouseX, mouseY, x + PAD + resetW + 6, top, presetW, BTN_H,
                "Классические", Ui.GHOST);

        for (int i = 0; i < categories.size(); i++) {
            Catalog.Category cat = categories.get(i);
            int ry = top + rowY(i);
            boolean hov = Ui.hit(mouseX, mouseY, x + PAD, ry, W - PAD * 2, COLOR_H);
            boolean open = openColor == i;
            if (open || hov)
                Draw.round(ctx, x + PAD - 4, ry - 1, W - PAD * 2 + 8, COLOR_H + 2,
                        Settings.radius(COLOR_H), Draw.opaque(open ? Ui.BTN_ON : Ui.BTN));
            Ui.swatch(ctx, x + PAD, ry + 2, 12, 12, cat.color, true, false);
            boolean custom = s.colors.containsKey(cat.name);
            Draw.textFit(ctx, tr, cat.name, x + PAD + 18, ry + (COLOR_H - Ui.TEXT_H) / 2,
                    W - PAD * 2 - 18 - 62,
                    open ? (Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT) : Theme.TEXT_DIM,
                    false);
            String value = String.format("#%06X", cat.color);
            Draw.textRight(ctx, tr, value, x + W - PAD - (custom ? 12 : 0),
                    ry + (COLOR_H - Ui.TEXT_H) / 2,
                    open ? (Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT) : Theme.TEXT_FAINT,
                    false);
            if (custom) Draw.dot(ctx, x + W - PAD - 8, ry + COLOR_H / 2 - 2,
                    Draw.opaque(Theme.ACCENT));
            if (open) drawPicker(ctx, mouseX, mouseY, delta, ry + COLOR_H + 4, cat);
        }
    }

    private void drawPicker(DrawContext ctx, int mouseX, int mouseY, float delta, int py,
                            Catalog.Category cat) {
        int lx = x + PAD;
        Ui.svSquare(ctx, lx, py, SV_W, SV_H, pickH, pickS, pickV, 3);
        Ui.hueBar(ctx, lx, py + SV_H + 4, SV_W, HUE_H, pickH);

        int rx = lx + SV_W + 12, rw = W - PAD * 2 - SV_W - 12;
        Ui.swatch(ctx, rx, py, rw, 22, cat.color, true, false);
        boolean focused = hexField != null && hexField.isFocused();
        Ui.input(ctx, rx, py + 26, rw, ROW_H, focused);
        Draw.text(ctx, tr, "#", rx + 6, py + 26 + (ROW_H - Ui.TEXT_H) / 2, Theme.TEXT_FAINT, false);
        if (hexField != null) {
            hexField.setX(rx + 13);
            hexField.setY(py + 26 + (ROW_H - Ui.TEXT_H) / 2);
            hexField.setWidth(rw - 19);
            hexField.render(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, tr, hexField);
        }
        Ui.button(ctx, tr, mouseX, mouseY, rx, py + 26 + ROW_H + 4, rw, ROW_H, "Вернуть",
                Ui.GHOST, s.colors.containsKey(cat.name));
    }

    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        if (!contains(mx, my)) { close(); return true; }
        if (bar.press(mx, my)) { scroll[tab] = bar.follow(my, 1, maxScroll()); return true; }
        if (hexField != null) {
            boolean inField = Ui.hit(mx, my, hexField.getX() - 8, hexField.getY() - 5,
                    hexField.getWidth() + 12, ROW_H);
            hexField.setFocused(inField);
            if (inField) {
                if (doubled) {
                    hexField.setCursor(0, false);
                    hexField.setCursor(hexField.getText().length(), true);
                } else {
                    hexField.setCursor(hexIndexAt(mx), shiftDown());
                }
                hexDrag = true;
                return true;
            }
        }

        if (Ui.hit(mx, my, x + W - PAD - 14, y + (HEAD_H - 14) / 2, 14, 14)) { close(); return true; }
        if (hexField != null) hexField.setFocused(false);

        int seg = Ui.segmentAt(mx, my, x + PAD, tabsY(), W - PAD * 2, TAB_H, TABS.size());
        if (seg >= 0) {
            if (seg != tab) { tab = seg; binding = null; hexField = null; openColor = -1; }
            return true;
        }

        int resetW = Ui.buttonW(tr, "По умолчанию");
        int doneW = Math.max(84, Ui.buttonW(tr, "Готово"));
        if (Ui.hit(mx, my, x + PAD, footerY(), resetW, BTN_H)) {
            s.reset();
            binding = null;
            openColor = -1;
            hexField = null;
            changed = true;
            return true;
        }
        if (Ui.hit(mx, my, x + W - PAD - doneW, footerY(), doneW, BTN_H)) { close(); return true; }

        int cy = contentY(), ch = contentH();
        if (!Ui.hit(mx, my, x, cy, W, ch)) return true;
        double dy = my + scroll() - cy;
        switch (tab) {
            case TAB_KEYS -> keysClicked(mx, dy, button);
            case TAB_LOOK -> lookClicked(mx, dy);
            default -> colorsClicked(mx, my, dy, button);
        }
        return true;
    }

    private void keysClicked(double mx, double dy, int button) {
        Hot[] all = Hot.values();
        for (int i = 0; i < all.length; i++) {
            int ry = i * (ROW_H + ROW_GAP);
            if (dy < ry || dy >= ry + ROW_H) continue;
            if (mx < x + W - PAD - KEY_W) return;
            if (button == 1) {
                s.bind(all[i], Settings.NONE, 0);
                changed = true;
            } else {
                binding = all[i];
            }
            return;
        }
    }

    private void lookClicked(double mx, double dy) {
        int rowsTop = PREVIEW_H + 10;
        for (Row r : rows) {
            int i = r.cells.indexAt(mx, dy, x + PAD + LABEL_W, rowsTop + r.dy);
            if (i < 0) continue;
            r.set.accept(i);
            s.apply();
            changed = true;
            return;
        }
    }

    private void colorsClicked(double mx, double my, double dy, int button) {
        int resetW = Ui.buttonW(tr, "Сбросить всё");
        int presetW = Ui.buttonW(tr, "Классические");
        if (dy >= 0 && dy < BTN_H) {
            if (mx >= x + PAD && mx < x + PAD + resetW && !s.colors.isEmpty()) {
                s.applyPreset(java.util.Map.of());
                changed = true;
            } else if (mx >= x + PAD + resetW + 6 && mx < x + PAD + resetW + 6 + presetW) {
                s.applyPreset(Catalog.classicPalette());
                changed = true;
            }
            return;
        }
        for (int i = 0; i < categories.size(); i++) {
            int ry = rowY(i);
            if (dy >= ry && dy < ry + COLOR_H) {
                Catalog.Category cat = categories.get(i);
                if (button == 1) {
                    s.clearColor(cat.name);
                    changed = true;
                    return;
                }
                if (openColor == i) { openColor = -1; hexField = null; return; }
                openColor = i;
                setPick(cat.color);
                openHexField(cat.color);
                return;
            }
            if (openColor != i) continue;
            int py = ry + COLOR_H + 4;
            Catalog.Category cat = categories.get(i);
            int lx = x + PAD, rx = lx + SV_W + 12, rw = W - PAD * 2 - SV_W - 12;
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
            if (Ui.hit(mx, dy, rx, py + 26 + ROW_H + 4, rw, ROW_H)
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
        hexField = Ui.field(tr, 0, 0, 40, 10, "RRGGBB");
        hexField.setMaxLength(6);
        hexField.setTextPredicate(t -> t.chars().allMatch(c -> Character.digit(c, 16) >= 0));
        hexField.setText(String.format("%06X", rgb));
        hexField.setChangedListener(t -> {
            if (syncing || t.length() != 6 || openColor < 0) return;
            int color = McText.hexRgb(t);
            s.setColor(categories.get(openColor).name, color);
            setPick(color);
            changed = true;
        });
    }

    private static boolean shiftDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return false;
        return InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private int hexIndexAt(double mx) {
        int rel = (int) Math.round(mx - hexField.getX());
        if (rel <= 0) return 0;
        return tr.trimToWidth(hexField.getText(), rel).length();
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
        hexField.setText(String.format("%06X", rgb));
        syncing = false;
    }

    private void dragSv(double mx, double dy) {
        int lx = x + PAD;
        pickS = clamp01((float) (mx - lx) / (SV_W - 1));
        pickV = 1 - clamp01((float) dy / (SV_H - 1));
        applyPick();
    }

    private void dragHue(double mx) {
        pickH = clamp01((float) (mx - (x + PAD)) / (SV_W - 1));
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

    public boolean mouseDragged(double mx, double my) {
        if (bar.dragging()) { scroll[tab] = bar.follow(my, 1, maxScroll()); return true; }
        if (hexDrag && hexField != null) {
            hexField.setCursor(hexIndexAt(mx), true);
            return true;
        }
        if (dragging == 0) return false;
        double dy = my + scroll() - contentY();
        if (dragging == 1) {
            int ry = rowY(openColor) + COLOR_H + 4;
            dragSv(mx, dy - ry);
        } else {
            dragHue(mx);
        }
        return true;
    }

    public void mouseReleased() { dragging = 0; hexDrag = false; bar.release(); }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        scroll[tab] = Math.max(0, Math.min(maxScroll(),
                scroll() - (int) Math.round(amount * 18)));
        return true;
    }

    public boolean keyPressed(KeyInput input) {
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
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return true;
    }

    public boolean charTyped(CharInput input) {
        if (hexField == null || !hexField.isFocused()) return false;
        return hexField.charTyped(input);
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
