package com.xerocode.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.Download;
import com.xerocode.Env;
import com.xerocode.Exporter;
import com.xerocode.Importer;
import com.xerocode.Match;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Tabs;
import com.xerocode.Value;
import com.xerocode.XeroCode;
import com.xerocode.code.CodeFile;
import com.xerocode.code.CodeProject;
import com.xerocode.code.Doc;
import com.xerocode.code.Editing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xero.dialect.Dialect;
import xero.dialect.Dialects;
import xero.dialect.Ink;
import xero.ide.Service;
import xero.json.Json;
import xero.lang.Diagnostic;

public final class CodeScreen extends Screen {
    private static final int ORIGINAL = 1, CANVAS = 2, CODE = 3, SIDE = 4, UNDO = 5, REDO = 6, BUILD = 7, UPLOAD = 8,
            LOAD = 9, TO_CANVAS = 10, FROM_CANVAS = 11, FIND = 12, COMMANDS = 13, SETTINGS = 14, VALUE = 15;
    private static final int GAP = 6, SIDE_MIN = 90, BOTTOM_MIN = 40, ANALYZE_AFTER_MS = 280, WORDS_MAX = 80;

    private static List<CodeProject.Problem> problems = List.of();

    static List<CodeProject.Problem> problems() { return problems; }

    private record Btn(int id, String[] icon, String label, String tip, int group, boolean active, boolean enabled) {}

    private record Spot(CodeFile file, int line, int col) {}

    private final CodeProject project;
    private final CodeArea area;
    private final CodeSide side;
    private final CodeBottom bottom;
    private final CodeFind finder;
    private final List<int[]> placed = new ArrayList<>();
    private List<Btn> buttons = List.of();
    private CodeQuick quick;
    private Menu menu;
    private SettingsPanel settings;
    private ValueEditor valueEditor;
    private CodeFile valueFile;
    private int valueStart, valueEnd;
    private static final String FILES_KEY = Env.browser() ? "Ctrl+P" : "Ctrl+E";
    private static final String SIDE_KEY = Env.browser() ? "Ctrl+B" : "Ctrl+Shift+E";
    private static final String COMMANDS_KEY = Env.browser() ? "Ctrl+Shift+P" : "F1";

    private boolean findOpen, sideOpen, bottomOpen;
    private int sideW, bottomH;
    private boolean draggingSide, draggingBottom;
    private String toast = "";
    private long toastAt;
    private List<Service.Symbol> outline = List.of();
    private long editedAt;
    private int analyzedVersion = -1;
    private CodeFile analyzedFile;
    private boolean analyzing;
    private final List<Spot> trail = new ArrayList<>();
    private int trailAt = -1;
    private Download download;
    private int[] tabXs = new int[0], tabWs = new int[0];
    private boolean languagesReady;

    public CodeScreen() {
        super(Component.literal("XeroCode"));
        Settings s = Settings.get();
        sideOpen = !Env.touch() && s.codeSideOpen;
        bottomOpen = !Env.touch() && s.codeBottomOpen;
        sideW = s.codeSideW;
        bottomH = s.codeBottomH;
        Dialects.all();
        languagesReady = true;
        project = CodeProject.of(Tabs.activeScript().plot);
        area = new CodeArea(font(), areaHost());
        side = new CodeSide(font(), sideHost());
        bottom = new CodeBottom(font(), bottomHost());
        finder = new CodeFind(font(), area);
        if (project.active != null) area.open(project.active);
    }

    private static net.minecraft.client.gui.Font font() { return Minecraft.getInstance().font; }

    private CodeArea.Host areaHost() {
        return new CodeArea.Host() {
            @Override public void edited() { fileEdited(); }
            @Override public void definition(int line, int col) { goDefinition(line, col); }
            @Override public void references() { findReferences(); }
            @Override public void rename() { askRename(); }
            @Override public void fix() { quickFix(); }
            @Override public void format() { formatFile(); }
            @Override public void back() { walk(-1); }
            @Override public void forward() { walk(1); }
            @Override public void nextProblem(int direction) { stepProblem(direction); }
            @Override public void contextMenu(int x, int y) { editorMenu(x, y); }
            @Override public void zoomChanged() { Settings.get().save(); }
            @Override public void editValue() { valueHere(); }
            @Override public void editValueOn(int line, int start, int end) { valueHere(); }
        };
    }

    private CodeSide.Host sideHost() {
        return new CodeSide.Host() {
            @Override public CodeProject project() { return project; }
            @Override public List<Service.Symbol> outline() { return outline; }
            @Override public void openFile(CodeFile file) { showFile(file); }
            @Override public void newFile(int x, int y) { newFileMenu(x, y); }
            @Override public void fileMenu(CodeFile file, int x, int y) { fileContextMenu(file, x, y); }
            @Override public void symbol(Service.Symbol s) { goOffset(s.nameStart()); }
        };
    }

    private CodeBottom.Host bottomHost() {
        return new CodeBottom.Host() {
            @Override public List<CodeProject.Problem> problems() { return problems; }
            @Override public void goTo(CodeFile file, int line, int col) { jump(file, line, col); }
            @Override public void fixProblem(CodeProject.Problem p, int x, int y) {
                jump(p.file(), p.line(), p.col());
                fixMenu(p.diagnostic().fixes(), x, y);
            }
        };
    }

    @Override
    protected void init() {
        area.metrics();
        layout();
        if (valueEditor != null) valueEditor.resize(width, height);
        if (area.focused()) area.focus(true);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return !Env.browser(); }

    private int topH() { return Theme.topH(); }

    private int tabsH() { return Env.touch() ? 24 : 18; }

    private int statusH() { return Theme.STATUS_H; }

    private int sideShown() { return sideOpen ? Math.max(SIDE_MIN, Math.min(width / 2, sideW)) : 0; }

    private int bottomShown() {
        return bottomOpen ? Math.max(BOTTOM_MIN, Math.min(height / 2, bottomH)) : 0;
    }

    private void layout() {
        int left = sideShown(), top = topH() + tabsH();
        int h = height - top - statusH() - bottomShown();
        area.place(left, top, width - left, Math.max(20, h));
        side.place(0, topH(), left, height - topH() - statusH());
        bottom.place(left, height - statusH() - bottomShown(), width - left, bottomShown());
    }

    private List<Btn> buttonList() {
        List<Btn> out = new ArrayList<>();
        Doc d = area.doc();
        boolean open = d != null;
        out.add(new Btn(SIDE, Draw.SIDEBAR, null, "Файлы  " + SIDE_KEY, 1, sideOpen, true));
        if (!Env.browser()) {
            out.add(new Btn(ORIGINAL, Draw.BRICKS, "3D", "3D-кодинг\nКод блоками в мире", 2, false, true));
        }
        out.add(new Btn(CANVAS, Draw.CANVAS, "2D", "2D-кодинг\nПолотно блоков", 2, false, true));
        out.add(new Btn(CODE, Draw.CODE, "Код", "Код буквами", 2, true, true));
        out.add(new Btn(VALUE, Draw.PIN, null, "Значение: местоположение, предмет, звук…  Alt+Enter", 3, false, open));
        out.add(new Btn(UNDO, Draw.UNDO, null, "Отменить  Ctrl+Z", 3, false, open && d.canUndo()));
        out.add(new Btn(REDO, Draw.REDO, null, "Вернуть  Ctrl+Y", 3, false, open && d.canRedo()));
        out.add(new Btn(BUILD, Draw.CHECK, width > 520 ? "Собрать" : null, "Проверить этот файл и его import  Ctrl+S", 4, false, true));
        if (!Env.touch()) out.add(new Btn(UPLOAD, Draw.UPLOAD, width > 520 ? "В мир" : null, "Этот файл и его import в мир  "
                + Settings.get().label(Settings.Hot.UPLOAD) + "\n"
                + (Env.browser() ? "Даст команду для чата JustMC" : "Заменит код мира"), 4, false, true));
        out.add(new Btn(LOAD, Draw.LOAD, null, Env.browser() ? "Открыть код из JSON или ссылки"
                : "Код из мира\nФайлом на языке этого файла", 5, false, true));
        out.add(new Btn(FROM_CANVAS, Draw.COPY, null, "С полотна\nПолотно 2D станет текстом", 5, false, true));
        out.add(new Btn(TO_CANVAS, Draw.CANVAS, null, "На полотно\nФайл откроется блоками в новой вкладке", 5, false, true));
        return out;
    }

    private List<Btn> rightList() {
        List<Btn> out = new ArrayList<>();
        if (Env.touch()) out.add(new Btn(UPLOAD, Draw.UPLOAD, null, "В мир", 6, false, true));
        else out.add(new Btn(FIND, Draw.SEARCH, null, "Поиск  Ctrl+F", 6, findOpen, area.doc() != null));
        out.add(new Btn(COMMANDS, Draw.KEYBOARD, null, "Все команды  " + COMMANDS_KEY, 6, false, true));
        out.add(new Btn(SETTINGS, Draw.GEAR, null, "Настройки", 7, false, true));
        return out;
    }

    private int btnH() { return Env.touch() ? 26 : 18; }

    private int btnY() { return (topH() - btnH()) / 2; }

    private int btnW(Btn b) {
        if (b.label == null) return (Env.touch() ? 18 : 12) + Draw.glyphW(b.icon);
        return (Env.touch() ? 22 : 16) + Draw.glyphW(b.icon) + 5 + font.width(b.label);
    }

    private void placeButtons() {
        List<Btn> left = buttonList(), right = rightList();
        List<Btn> all = new ArrayList<>(left);
        all.addAll(right);
        placed.clear();
        int rightW = 0;
        for (int i = 0; i < right.size(); i++) rightW += btnW(right.get(i)) + (i > 0 && right.get(i - 1).group != right.get(i).group ? GAP : 1);
        int x = 8, limit = width - 8 - rightW - GAP;
        for (int i = 0; i < left.size(); i++) {
            Btn b = left.get(i);
            if (i > 0 && left.get(i - 1).group != b.group) x += GAP;
            int w = btnW(b);
            if (x + w > limit) {
                left = left.subList(0, i);
                break;
            }
            placed.add(new int[]{b.id, x, w});
            x += w + 1;
        }
        int rx = width - 8 - rightW;
        for (int i = 0; i < right.size(); i++) {
            Btn b = right.get(i);
            if (i > 0 && right.get(i - 1).group != b.group) rx += GAP - 1;
            int w = btnW(b);
            placed.add(new int[]{b.id, rx, w});
            rx += w + 1;
        }
        buttons = all;
    }

    private Btn button(int id) {
        for (Btn b : buttons) if (b.id == id) return b;
        return null;
    }

    private void drawBar(GuiGraphicsExtractor ctx, int mx, int my) {
        Draw.vgrad(ctx, 0, 0, width, topH(), Draw.opaque(Theme.PANEL_RAISED), Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, 0, topH() - 1, width, 1, Draw.opaque(Theme.LINE));
        placeButtons();
        int r = Settings.radius(18);
        int by = btnY(), bh = btnH();
        int at = 0;
        while (at < placed.size()) {
            int[] first = placed.get(at);
            int group = button(first[0]).group;
            int last = at;
            while (last + 1 < placed.size() && button(placed.get(last + 1)[0]).group == group) last++;
            int[] end = placed.get(last);
            Draw.round(ctx, first[1] - 1, by - 1, end[1] + end[2] + 2 - first[1], bh + 2, r == 0 ? 0 : r + 1, Draw.opaque(Theme.SURFACE));
            at = last + 1;
        }
        for (int[] p : placed) {
            Btn b = button(p[0]);
            boolean over = b.enabled && Ui.hit(mx, my, p[1], by, p[2], bh);
            if (b.active) {
                Draw.round(ctx, p[1], by, p[2], bh, r, Draw.opaque(Draw.mix(Ui.BTN_ON, Theme.ACCENT, over ? 0.35f : 0.22f)));
                Draw.rect(ctx, p[1] + 4, by + bh - 2, p[2] - 8, 1, Draw.opaque(Theme.ACCENT));
            } else if (over) Draw.round(ctx, p[1], by, p[2], bh, r, Draw.opaque(Theme.SURFACE_HOVER));
            int color = !b.enabled ? Theme.TEXT_FAINT : b.active ? Theme.ON_ACCENT : over ? Theme.TEXT : Theme.TEXT_DIM;
            int gx = b.label == null ? p[1] + (p[2] - Draw.glyphW(b.icon)) / 2 : p[1] + (Env.touch() ? 11 : 8);
            Draw.glyph(ctx, b.icon, gx, by + (bh - Draw.glyphH(b.icon)) / 2, color);
            gx += Draw.glyphW(b.icon) + 5;
            if (b.label != null) Draw.text(ctx, font, b.label, gx, by + (bh - 7) / 2, color, false);
        }
    }

    private void barTip(GuiGraphicsExtractor ctx, int mx, int my) {
        if (my >= topH()) return;
        for (int[] p : placed) {
            if (!Ui.hit(mx, my, p[1], btnY(), p[2], btnH())) continue;
            Btn b = button(p[0]);
            List<Component> tip = new ArrayList<>();
            String[] parts = b.tip.split("\n");
            tip.add(Component.literal(parts[0]));
            for (int i = 1; i < parts.length; i++) tip.add(Component.literal("§7" + parts[i]));
            ctx.setComponentTooltipForNextFrame(font, tip, mx, my);
            return;
        }
    }

    private void drawTabs(GuiGraphicsExtractor ctx, int mx, int my) {
        int left = sideShown(), y = topH(), h = tabsH();
        Draw.rect(ctx, left, y, width - left, h, Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, left, y + h - 1, width - left, 1, Draw.opaque(Theme.LINE));
        int n = project.open.size();
        if (tabXs.length != n) {
            tabXs = new int[n];
            tabWs = new int[n];
        }
        int x = left + 2;
        for (int i = 0; i < n; i++) {
            CodeFile f = project.open.get(i);
            boolean active = f == project.active;
            int tw = Math.min(150, font.width(f.name())) + 26;
            tabXs[i] = x;
            tabWs[i] = tw;
            if (x > width) continue;
            boolean over = Ui.hit(mx, my, x, y, tw, h);
            if (active) {
                Draw.rect(ctx, x, y, tw, h - 1, Draw.opaque(Theme.CANVAS));
                Draw.rect(ctx, x, y, tw, 1, Draw.opaque(Theme.ACCENT));
            } else if (over) Draw.rect(ctx, x, y, tw, h - 1, Draw.opaque(Theme.SURFACE_HOVER));
            Draw.textFit(ctx, font, f.name(), x + 7, y + (h - 8) / 2, tw - 22, active ? Theme.TEXT : Theme.TEXT_DIM, false);
            int cx = x + tw - 13, cy = y + (h - 7) / 2;
            boolean dirty = f.doc != null && f.doc.dirty();
            if (over || !dirty) {
                if (over) Draw.glyph(ctx, Draw.CROSS, cx, cy, Ui.hit(mx, my, cx - 2, y, 11, h) ? Theme.TEXT : Theme.TEXT_FAINT);
            } else Draw.dot(ctx, cx + 1, cy + 2, Draw.opaque(Theme.WARN));
            x += tw + 1;
        }
    }

    private void drawStatus(GuiGraphicsExtractor ctx, int mx, int my) {
        int y = height - statusH();
        Draw.rect(ctx, 0, y, width, statusH(), Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, 0, y, width, 1, Draw.opaque(Theme.LINE));
        int ty = y + (statusH() - 7) / 2;
        int errors = 0, warnings = 0;
        for (CodeProject.Problem p : problems) {
            if (p.diagnostic().error()) errors++;
            else warnings++;
        }
        int x = 8;
        Draw.dot(ctx, x, y + statusH() / 2 - 1, Draw.opaque(errors > 0 ? Theme.DANGER : warnings > 0 ? Theme.WARN : Theme.OK));
        String said = errors + " ошиб. · " + warnings + " предупр.";
        Draw.text(ctx, font, said, x + 8, ty, Theme.TEXT_DIM, false);
        x += 8 + font.width(said) + 14;
        boolean fresh = System.currentTimeMillis() - toastAt < 6000;
        if (fresh) Draw.textFit(ctx, font, toast, x, ty, Math.max(20, width / 2 - x), Theme.TEXT, false);
        Doc d = area.doc();
        if (d == null) return;
        CodeFile f = area.file();
        Doc.Caret c = d.primary();
        String lang = f.dialect == null ? "текст" : f.dialect.title();
        String typeface = fontLabel();
        String where = "Стр " + (c.line + 1) + ", кол " + (c.col + 1)
                + (d.carets().size() > 1 ? " · курсоров " + d.carets().size() : "")
                + (c.selecting() ? " · выделено " + d.selected(c).length() : "");
        int lw = font.width(lang) + 10, lx = width - 8 - lw;
        boolean over = Ui.hit(mx, my, lx, y, lw, statusH());
        if (over) Draw.rect(ctx, lx, y + 1, lw, statusH() - 1, Draw.opaque(Theme.SURFACE_HOVER));
        Draw.text(ctx, font, lang, lx + 5, ty, Theme.TEXT, false);
        int fw = font.width(typeface) + 10, fx = lx - 4 - fw;
        if (Ui.hit(mx, my, fx, y, fw, statusH())) Draw.rect(ctx, fx, y + 1, fw, statusH() - 1, Draw.opaque(Theme.SURFACE_HOVER));
        Draw.text(ctx, font, typeface, fx + 5, ty, Theme.TEXT_DIM, false);
        Draw.textRight(ctx, font, where, fx - 8, ty, Theme.TEXT_DIM, false);
    }

    private void drawWelcome(GuiGraphicsExtractor ctx, int mx, int my) {
        int left = sideShown(), top = topH() + tabsH();
        int w = width - left, h = height - top - statusH() - bottomShown();
        Draw.rect(ctx, left, top, w, h, Draw.opaque(Theme.CANVAS));
        List<Dialect> all = chooseable();
        int rowH = 34, total = rowH * all.size() + 24;
        int cw = Math.min(360, w - 20), cx = left + (w - cw) / 2, cy = top + Math.max(10, (h - total) / 2);
        Draw.text(ctx, font, "Новый файл", cx, cy, Theme.TEXT, false);
        for (int i = 0; i < all.size(); i++) {
            Dialect d = all.get(i);
            int ry = cy + 18 + i * rowH;
            boolean over = Ui.hit(mx, my, cx, ry, cw, rowH - 4);
            Draw.round(ctx, cx, ry, cw, rowH - 4, Settings.radius(18), Draw.opaque(over ? Theme.SURFACE_HOVER : Theme.SURFACE));
            Draw.text(ctx, font, d.title(), cx + 10, ry + 6, Theme.TEXT, false);
            int ext = font.width("." + d.extension()), after = cx + 10 + font.width(d.title()) + 8;
            Draw.textFit(ctx, font, d.author(), after, ry + 6, cx + cw - 16 - ext - after, Theme.TEXT_FAINT, false);
            Draw.textRight(ctx, font, "." + d.extension(), cx + cw - 10, ry + 6, Theme.TEXT_FAINT, false);
            Draw.textFit(ctx, font, about(d), cx + 10, ry + 17, cw - 20, Theme.TEXT_DIM, false);
        }
    }

    private static List<Dialect> chooseable() {
        List<Dialect> out = new ArrayList<>();
        for (Dialect d : Dialects.all()) if (!"json".equals(d.id())) out.add(d);
        return out;
    }

    private static String about(Dialect d) {
        return switch (d.id()) {
            case "xero" -> "Свой язык XeroCode: подсказки по-русски, функции, формулы";
            case "jmcc" -> "JustCode — язык JMCC, файлы .jc";
            case "jmscript" -> "JMScript — похож на Go";
            case "justice" -> "JustIce — один к одному с блоками";
            default -> d.title();
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (minecraft != null) Backstage.hide(minecraft);
        Draw.batch(null);
        SmoothText.clip(null);
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        layout();
        area.frame();
        if (findOpen) finder.frame();
        analyzeWhenIdle();
        if (area.doc() != null) area.draw(ctx, mouseX, mouseY);
        else drawWelcome(ctx, mouseX, mouseY);
        ctx.nextStratum();
        if (sideOpen) side.draw(ctx, mouseX, mouseY);
        drawTabs(ctx, mouseX, mouseY);
        if (bottomOpen) bottom.draw(ctx, mouseX, mouseY);
        drawBar(ctx, mouseX, mouseY);
        drawStatus(ctx, mouseX, mouseY);
        ctx.nextStratum();
        if (findOpen) finder.draw(ctx, mouseX, mouseY, delta, width, topH() + tabsH());
        if (area.doc() != null) area.hints.draw(ctx, mouseX, mouseY, width, height);
        ctx.nextStratum();
        if (Env.touch() && area.focused() && area.doc() != null) drawKeys(ctx);
        if (quick != null) {
            quick.draw(ctx, mouseX, mouseY, delta, width, height);
            if (quick.done()) closeQuick();
        }
        if (menu != null) menu.render(ctx, font, mouseX, mouseY);
        if (valueEditor != null) {
            ctx.nextStratum();
            valueEditor.render(ctx, mouseX, mouseY, delta);
        }
        if (settings != null) {
            ctx.nextStratum();
            settings.resize(width, height);
            settings.render(ctx, mouseX, mouseY, delta);
        }
        barTip(ctx, mouseX, mouseY);
    }

    private static final String TAB_KEY = "Tab";
    private static final String[] KEYS = {"{", "}", "(", ")", "[", "]", "\"", ";", "=", ".", ",", TAB_KEY, "←", "→"};

    private int keysY() { return height - statusH() - bottomShown() - 26; }

    private void drawKeys(GuiGraphicsExtractor ctx) {
        int left = sideShown(), y = keysY(), w = (width - left) / KEYS.length;
        Draw.rect(ctx, left, y, width - left, 26, Draw.opaque(Theme.PANEL_RAISED));
        for (int i = 0; i < KEYS.length; i++) {
            int bx = left + i * w;
            Draw.round(ctx, bx + 1, y + 2, w - 2, 22, 3, Draw.opaque(Theme.SURFACE));
            Draw.text(ctx, font, KEYS[i], bx + (w - font.width(KEYS[i])) / 2, y + 9, Theme.TEXT, false);
        }
    }

    private boolean keyStrip(double mx, double my) {
        if (!Env.touch() || !area.focused() || area.doc() == null) return false;
        int left = sideShown(), y = keysY(), w = (width - left) / KEYS.length;
        if (my < y || my >= y + 26 || mx < left) return false;
        int i = Math.min(KEYS.length - 1, (int) ((mx - left) / w));
        String key = KEYS[i];
        switch (key) {
            case TAB_KEY -> area.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0));
            case "←" -> area.keyPressed(new KeyEvent(GLFW.GLFW_KEY_LEFT, 0, 0));
            case "→" -> area.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0));
            default -> area.typeText(key);
        }
        return true;
    }

    @Override
    public void tick() {
        project.tick();
        if (download != null) {
            download.tick();
            if (download.state == Download.State.DONE) {
                JsonArray handlers = download.handlers;
                download = null;
                takeHandlers(handlers, "мир");
            } else if (download.state == Download.State.FAILED) {
                say("мир не прочитан: " + download.error);
                download = null;
            }
        }
    }

    private void say(String s) {
        toast = s;
        toastAt = System.currentTimeMillis();
        bottom.say(s);
    }

    private void fileEdited() {
        editedAt = System.currentTimeMillis();
        project.touched();
    }

    private void analyzeWhenIdle() {
        CodeFile f = area.file();
        if (f == null || f.dialect == null || analyzing) return;
        int version = f.doc.version();
        if (f == analyzedFile && version == analyzedVersion) return;
        long wait = ANALYZE_AFTER_MS + (Env.browser() ? f.doc.lines() / 4 : 0);
        if (System.currentTimeMillis() - editedAt < wait) return;
        analyzing = true;
        analyzedFile = f;
        analyzedVersion = version;
        String text = f.doc.text();
        Dialect dialect = f.dialect;
        String path = f.path;
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (CodeFile other : project.files) if (other.doc != null) snapshot.put(other.path, other.doc.text());
        Path root = project.root;
        Minecraft mc = Minecraft.getInstance();
        Env.later(() -> {
            Dialect.Built built = null;
            List<Service.Mark> marks = List.of();
            List<Service.Symbol> symbols = List.of();
            try {
                synchronized (dialect) {
                    built = dialect.build(path, text, p -> read(snapshot, root, p));
                    marks = dialect.variables(text);
                    symbols = dialect.symbols(text);
                }
            } catch (Throwable e) {
                XeroCode.LOG.warn("[xerocode] разбор кода упал", e);
            }
            Dialect.Built result = built;
            List<Service.Mark> found = marks;
            List<Service.Symbol> listed = symbols;
            mc.execute(() -> analyzed(f, version, result, found, listed));
        });
    }

    private static String read(Map<String, String> snapshot, Path root, String path) {
        if (path.startsWith(xero.compile.Compiler.MODULE)) return null;
        String clean = CodeProject.clean(path);
        String text = snapshot.get(clean);
        if (text != null) return text;
        try {
            Path p = root.resolve(clean);
            return Files.isRegularFile(p) ? new String(Files.readAllBytes(p), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void analyzed(CodeFile f, int version, Dialect.Built built, List<Service.Mark> marks,
                          List<Service.Symbol> symbols) {
        analyzing = false;
        if (built == null || f.doc == null || f != area.file()) return;
        if (f.doc.version() != version) return;
        outline = symbols;
        List<CodeProject.Problem> out = new ArrayList<>();
        for (CodeProject.Problem p : problems) if (p.file() != f) out.add(p);
        for (Diagnostic d : built.diagnostics()) {
            if (d.file() != null && !CodeProject.clean(d.file()).equals(f.path)) continue;
            int at = Math.min(d.start(), f.doc.text().length());
            out.add(new CodeProject.Problem(f, d, f.doc.lineAt(at), f.doc.colAt(at)));
        }
        problems = out;
        area.problems(out);
        overlay(f.doc, marks);
    }

    private static void overlay(Doc d, List<Service.Mark> marks) {
        int[] palette = CodeTheme.palette();
        Map<Integer, List<Integer>> rows = new LinkedHashMap<>();
        for (Service.Mark m : marks) {
            int line = d.lineAt(m.start());
            List<Integer> list = rows.computeIfAbsent(line, k -> new ArrayList<>());
            list.add(d.colAt(m.start()));
            list.add(d.colAt(m.end()));
            list.add(scopeColor(palette, m.scope()));
        }
        for (int l = 0; l < d.lines(); l++) {
            Doc.Row r = d.row(l);
            List<Integer> list = rows.get(l);
            if (list == null) {
                if (r.overlay != null) {
                    r.overlay = null;
                    r.baked = null;
                }
                continue;
            }
            int[] over = new int[list.size()];
            for (int i = 0; i < over.length; i++) over[i] = list.get(i);
            if (java.util.Arrays.equals(over, r.overlay)) continue;
            r.overlay = over;
            r.baked = null;
        }
    }

    private static int scopeColor(int[] palette, String scope) {
        return switch (scope == null ? "" : scope) {
            case "game" -> palette[Ink.LITERAL];
            case "save" -> palette[Ink.EVENT];
            case "line" -> palette[Ink.CONDITION];
            case "option" -> palette[Ink.VALUE];
            default -> palette[Ink.VARIABLE];
        };
    }

    private void showFile(CodeFile f) {
        pushSpot();
        project.show(f);
        area.remember();
        area.open(f);
        area.focus(true);
        pushSpot();
        if (Env.touch()) sideOpen = false;
    }

    private void pushSpot() {
        CodeFile f = area.file();
        if (f == null || f.doc == null) return;
        Doc.Caret c = f.doc.primary();
        while (trail.size() > trailAt + 1) trail.remove(trail.size() - 1);
        if (!trail.isEmpty()) {
            Spot last = trail.get(trail.size() - 1);
            if (last.file == f && last.line == c.line) return;
        }
        trail.add(new Spot(f, c.line, c.col));
        if (trail.size() > 100) trail.remove(0);
        trailAt = trail.size() - 1;
    }

    private void walk(int dir) {
        int next = trailAt + dir;
        if (next < 0 || next >= trail.size()) return;
        trailAt = next;
        Spot s = trail.get(next);
        if (!project.files.contains(s.file)) return;
        project.show(s.file);
        area.open(s.file);
        area.goTo(s.line, s.col);
    }

    private void jump(CodeFile f, int line, int col) {
        pushSpot();
        project.load(f);
        project.show(f);
        area.open(f);
        area.focus(true);
        area.goTo(line, col);
        pushSpot();
    }

    private void goOffset(int offset) {
        Doc d = area.doc();
        if (d == null) return;
        jump(area.file(), d.lineAt(offset), d.colAt(offset));
    }

    private void goDefinition(int line, int col) {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        Doc d = f.doc;
        int at = d.offset(line, col);
        Service.Span span;
        String other = null;
        synchronized (f.dialect) {
            span = f.dialect.definition(d.text(), at);
            if (f.dialect instanceof xero.dialect.XeroDialect x) other = x.service().definitionFile(d.text(), at);
        }
        if (span == null) {
            say("объявление не найдено");
            return;
        }
        CodeFile target = f;
        if (other != null) {
            CodeFile found = project.find(CodeProject.clean(other));
            if (found != null) target = found;
        }
        project.load(target);
        jump(target, target.doc.lineAt(span.start()), target.doc.colAt(span.start()));
    }

    private void findReferences() {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        Doc d = f.doc;
        Doc.Caret c = d.primary();
        List<Service.Span> spans;
        synchronized (f.dialect) {
            spans = f.dialect.references(d.text(), d.offset(c.line, c.col));
        }
        List<CodeBottom.Result> out = new ArrayList<>();
        for (Service.Span s : spans) {
            int line = d.lineAt(s.start());
            out.add(new CodeBottom.Result(f, line, d.colAt(s.start()), s.end() - s.start(), d.line(line)));
        }
        bottomOpen = true;
        bottom.results("Использования: " + out.size(), out);
    }

    private void askRename() {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        Doc d = f.doc;
        Doc.Caret c = d.primary();
        int at = d.offset(c.line, c.col);
        String t = d.line(c.line);
        int a = c.col, b = c.col;
        while (a > 0 && Ink.wordPart(t.charAt(a - 1))) a--;
        while (b < t.length() && Ink.wordPart(t.charAt(b))) b++;
        String was = t.substring(a, b);
        if (was.isEmpty()) return;
        openQuick(CodeQuick.ask(font, "Новое имя", "имя", was, wanted -> {
            if (wanted.isBlank() || wanted.equals(was)) return;
            Service.Renamed r;
            synchronized (f.dialect) {
                r = f.dialect.rename(d.text(), at, wanted.strip());
            }
            if (r.error() != null) {
                say(r.error());
                return;
            }
            apply(f, r.edits());
            r.others().forEach((path, edits) -> {
                CodeFile other = project.find(CodeProject.clean(path));
                if (other == null) return;
                project.load(other);
                apply(other, edits);
            });
            say("переименовано: " + r.edits().size());
        }));
    }

    private void apply(CodeFile f, List<Service.Edit> edits) {
        Doc d = f.doc;
        List<Service.Edit> order = new ArrayList<>(edits);
        order.sort((x, y) -> Integer.compare(y.start(), x.start()));
        d.begin(Doc.OTHER);
        for (Service.Edit e : order) d.replace(d.lineAt(e.start()), d.colAt(e.start()), d.lineAt(e.end()), d.colAt(e.end()), e.text());
        d.end();
        if (f == area.file()) area.edited();
        else project.touched();
    }

    private void quickFix() {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        Doc d = f.doc;
        Doc.Caret c = d.primary();
        int a = d.offset(c.startLine(), c.startCol()), b = d.offset(c.endLine(), c.endCol());
        List<Diagnostic.Fix> fixes = new ArrayList<>();
        for (CodeProject.Problem p : problems) {
            Diagnostic x = p.diagnostic();
            if (p.file() == f && a >= x.start() && a <= Math.max(x.end(), x.start() + 1)) fixes.addAll(x.fixes());
        }
        synchronized (f.dialect) {
            fixes.addAll(f.dialect.actions(d.text(), a, b));
        }
        if (fixes.isEmpty()) {
            say("исправлений нет");
            return;
        }
        fixMenu(fixes, area.caretGuiX(), area.caretGuiY() + area.rowGuiH());
    }

    private void fixMenu(List<Diagnostic.Fix> fixes, int x, int y) {
        if (fixes.isEmpty()) return;
        List<Menu.Item> items = new ArrayList<>();
        for (Diagnostic.Fix f : fixes) items.add(new Menu.Item(f.title()));
        menu = Menu.actions(width, height, x, y, font, items, i -> {
            if (i < 0 || i >= fixes.size()) return;
            Diagnostic.Fix fix = fixes.get(i);
            Doc d = area.doc();
            d.replace(d.lineAt(fix.start()), d.colAt(fix.start()), d.lineAt(fix.end()), d.colAt(fix.end()), fix.text());
            area.edited();
        });
    }

    private static final String[][] VALUE_KINDS = {
            {Value.LOCATION, "Местоположение"}, {Value.VECTOR, "Вектор"}, {Value.ITEM, "Предмет"}, {Value.BLOCK, "Блок"},
            {Value.SOUND, "Звук"}, {Value.PARTICLE, "Эффект частиц"}, {Value.POTION, "Зелье"}, {Value.TEXT, "Текст"}};

    private boolean canEditValues() {
        CodeFile f = area.file();
        return f != null && f.dialect != null && f.dialect.editsValues();
    }

    private void valueHere() {
        if (!canEditValues()) return;
        Doc d = area.doc();
        Doc.Caret c = d.primary();
        int[] range = area.valueRange(c.line, c.col);
        if (range == null) {
            valueMenu(area.caretGuiX(), area.caretGuiY() + area.rowGuiH());
            return;
        }
        String expression = d.slice(range[0], range[1], range[2], range[3]);
        Dialect dialect = area.file().dialect;
        Map<String, Object> json;
        synchronized (dialect) {
            json = dialect.valueOf(expression);
        }
        Value v = json == null ? null : Importer.fromServer(Json.write(json));
        if (v == null) {
            say("это значение правится только буквами");
            return;
        }
        openValue(v, d.offset(range[0], range[1]), d.offset(range[2], range[3]));
    }

    private void valueMenu(int x, int y) {
        if (!canEditValues()) return;
        List<Menu.Item> items = new ArrayList<>();
        for (String[] k : VALUE_KINDS) items.add(new Menu.Item(k[1]));
        menu = Menu.actions(width, height, x, y, font, items, i -> {
            if (i < 0 || i >= VALUE_KINDS.length) return;
            Doc d = area.doc();
            Doc.Caret c = d.primary();
            openValue(Value.of(VALUE_KINDS[i][0]), d.offset(c.startLine(), c.startCol()), d.offset(c.endLine(), c.endCol()));
        });
    }

    private void openValue(Value v, int start, int end) {
        valueFile = area.file();
        valueStart = start;
        valueEnd = end;
        area.focus(false);
        valueEditor = ValueEditor.forCell(v, "Значение", null, font, area.caretGuiX(), area.caretGuiY() + area.rowGuiH(),
                width, height, List.of(), List.of(), this::writeValue);
        valueEditor.allowWorld();
    }

    private void writeValue(Value v) {
        CodeFile f = valueFile;
        if (f == null || v == null) return;
        String json = Exporter.toServer(v);
        if (json.isEmpty()) return;
        Dialect dialect = f.dialect;
        String text;
        synchronized (dialect) {
            text = dialect.valueText(Json.object(Json.parse(json)));
        }
        if (text == null) {
            say("значение не записалось на " + dialect.title());
            return;
        }
        Doc d = f.doc;
        d.replace(d.lineAt(valueStart), d.colAt(valueStart), d.lineAt(valueEnd), d.colAt(valueEnd), text);
        valueEnd = valueStart + text.length();
        d.single(d.lineAt(valueEnd), d.colAt(valueEnd));
        if (f == area.file()) area.edited();
        else project.touched();
    }

    private void finishValue() {
        ValueEditor open = valueEditor;
        if (open == null) return;
        valueEditor = null;
        if (!open.cancelled()) open.commit();
        Value picked = open.edited();
        boolean toWorld = open.pickInWorld();
        open.dispose();
        area.focus(true);
        if (toWorld) LocationPick.start(picked, this::writeValue);
    }

    private void formatFile() {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        String out;
        synchronized (f.dialect) {
            out = f.dialect.format(f.doc.text());
        }
        if (out == null) {
            say("не отформатировалось: в коде ошибка");
            return;
        }
        if (out.equals(f.doc.text())) return;
        f.doc.setAll(out);
        area.edited();
    }

    private void stepProblem(int dir) {
        CodeFile f = area.file();
        if (f == null) return;
        List<CodeProject.Problem> own = new ArrayList<>();
        for (CodeProject.Problem p : problems) if (p.file() == f) own.add(p);
        if (own.isEmpty()) return;
        own.sort((a, b) -> Doc.compare(a.line(), a.col(), b.line(), b.col()));
        Doc.Caret c = f.doc.primary();
        CodeProject.Problem pick = null;
        if (dir > 0) {
            for (CodeProject.Problem p : own) {
                if (Doc.compare(p.line(), p.col(), c.line, c.col) > 0) {
                    pick = p;
                    break;
                }
            }
            if (pick == null) pick = own.get(0);
        } else {
            for (int i = own.size() - 1; i >= 0 && pick == null; i--)
                if (Doc.compare(own.get(i).line(), own.get(i).col(), c.line, c.col) < 0) pick = own.get(i);
            if (pick == null) pick = own.get(own.size() - 1);
        }
        area.goTo(pick.line(), pick.col());
        say(pick.diagnostic().message());
    }

    private void newFileMenu(int x, int y) {
        List<Dialect> all = new ArrayList<>(Dialects.all());
        List<Menu.Item> items = new ArrayList<>();
        for (Dialect d : all) items.add(new Menu.Item(d.title() + "   ." + d.extension()));
        menu = Menu.actions(width, height, x, y, font, items, i -> {
            if (i >= 0 && i < all.size()) askNewFile(all.get(i));
        });
    }

    private void askNewFile(Dialect d) {
        String suggested = project.freeName("main", d.extension());
        openQuick(CodeQuick.ask(font, "Имя файла", "имя файла", suggested, wanted -> {
            String name = CodeProject.clean(wanted);
            if (name.isEmpty()) return;
            if (!name.toLowerCase(java.util.Locale.ROOT).endsWith("." + d.extension())) name += "." + d.extension();
            if (project.files.size() >= CodeProject.FILES_MAX) {
                say("слишком много файлов");
                return;
            }
            showFile(project.create(name, d.sample()));
        }));
    }

    private void fileContextMenu(CodeFile f, int x, int y) {
        List<Menu.Item> items = new ArrayList<>();
        items.add(new Menu.Item("Открыть"));
        items.add(new Menu.Item("Переименовать"));
        items.add(new Menu.Item("Скопировать путь"));
        items.add(new Menu.Item("Сохранить на диск"));
        items.add(new Menu.Item("Удалить", true, Draw.TRASH));
        menu = Menu.actions(width, height, x, y, font, items, i -> {
            String label = i >= 0 && i < items.size() ? items.get(i).label : "";
            switch (label) {
                case "Открыть" -> showFile(f);
                case "Переименовать" -> askRenameFile(f);
                case "Скопировать путь" -> Minecraft.getInstance().keyboardHandler.setClipboard(f.path);
                case "Сохранить на диск" -> {
                    project.load(f);
                    download(f);
                }
                case "Удалить" -> askDelete(f);
                default -> { }
            }
        });
    }

    private void download(CodeFile f) {
        byte[] bytes = f.doc.text().getBytes(StandardCharsets.UTF_8);
        FileDialog.save("Сохранить файл", Path.of(f.name()), new String[]{"*." + f.dialect.extension()},
                f.dialect.title(), bytes, p -> say("сохранено: " + p));
    }

    private void askRenameFile(CodeFile f) {
        openQuick(CodeQuick.ask(font, "Новое имя файла", "имя", f.path, wanted -> {
            if (!project.rename(f, wanted)) say("файл с таким именем нельзя");
        }));
    }

    private void askDelete(CodeFile f) {
        openQuick(CodeQuick.ask(font, "Удалить " + f.name() + "? Напиши да", "да", "", answer -> {
            if (!"да".equalsIgnoreCase(answer.strip())) return;
            project.delete(f);
            area.open(project.active);
        }));
    }

    private void editorMenu(int x, int y) {
        Doc d = area.doc();
        if (d == null) return;
        List<Menu.Item> items = new ArrayList<>();
        List<Runnable> runs = new ArrayList<>();
        add(items, runs, "К объявлению  F12", () -> {
            Doc.Caret c = d.primary();
            goDefinition(c.line, c.col);
        });
        add(items, runs, "Все использования  Shift+F12", this::findReferences);
        add(items, runs, "Переименовать  F2", this::askRename);
        add(items, runs, "Исправить  Ctrl+.", this::quickFix);
        add(items, runs, "Значение  Alt+Enter", this::valueHere);
        add(items, runs, "Формат  Shift+Alt+F", this::formatFile);
        add(items, runs, "Вырезать", area::cut);
        add(items, runs, "Копировать", area::copy);
        add(items, runs, "Вставить", () -> Env.clipboard(area::paste));
        add(items, runs, "Закомментировать  Ctrl+/", () -> {
            Editing.toggleComment(d);
            area.edited();
        });
        menu = Menu.actions(width, height, x, y, font, items, i -> {
            if (i >= 0 && i < runs.size()) runs.get(i).run();
        });
    }

    private static void add(List<Menu.Item> items, List<Runnable> runs, String label, Runnable run) {
        items.add(new Menu.Item(label));
        runs.add(run);
    }

    private void openQuick(CodeQuick next) {
        area.focus(false);
        finder.blur();
        quick = next;
    }

    private void closeQuick() {
        if (quick != null) quick.close();
        quick = null;
        if (settings == null && menu == null) area.focus(true);
    }

    private void quickFiles() {
        List<CodeQuick.Entry> out = new ArrayList<>();
        for (CodeFile f : project.files)
            out.add(new CodeQuick.Entry(f.name(), f.folder(), null, 0, () -> showFile(f)));
        openQuick(CodeQuick.list(font, "файл…", out));
    }

    private void quickWords() {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        List<Service.Item> words = f.dialect.vocabulary();
        openQuick(CodeQuick.dynamic(font, "действие, условие, событие… можно по-русски", query -> {
            String q = com.xerocode.Search.fold(query.strip());
            List<CodeQuick.Entry> out = new ArrayList<>();
            if (q.isEmpty()) return out;
            for (Service.Item w : words) {
                if (out.size() >= WORDS_MAX) break;
                boolean hit = com.xerocode.Search.fold(w.label()).contains(q)
                        || w.detail() != null && com.xerocode.Search.fold(w.detail()).contains(q);
                if (hit) out.add(new CodeQuick.Entry(w.label(), w.detail(), CodeTheme.kindMark(w.kind()),
                        CodeTheme.kindColor(w.kind()), () -> insertWord(w)));
            }
            return out;
        }));
    }

    private void insertWord(Service.Item w) {
        Doc d = area.doc();
        Doc.Caret c = d.primary();
        int at = d.offset(c.startLine(), c.startCol()), to = d.offset(c.endLine(), c.endCol());
        String body = w.snippet() != null ? w.snippet()
                : w.label().replace("\\", "\\\\").replace("$", "\\$") + (w.call() ? "($1)" : "");
        area.focus(true);
        area.insertSnippet(at, to, body, w.retrigger());
    }

    private void importFile() {
        FileDialog.open("Открыть файл кода", project.root.toString(), masks(), "код", file -> {
            if (file == null) return;
            try {
                String name = file.getFileName().toString();
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (Dialects.forFile(name) == null) {
                    say("такой файл не поддерживается: " + name);
                    return;
                }
                int dot = name.lastIndexOf('.');
                showFile(project.create(project.freeName(name.substring(0, dot), name.substring(dot + 1)), text));
            } catch (IOException e) {
                XeroCode.LOG.warn("[xerocode] файл кода не прочитан", e);
                say("файл не читается");
            }
        });
    }

    private static String[] masks() {
        List<String> out = new ArrayList<>();
        for (Dialect d : Dialects.all()) out.add("*." + d.extension());
        return out.toArray(new String[0]);
    }

    private void quickSymbols() {
        List<CodeQuick.Entry> out = new ArrayList<>();
        for (Service.Symbol s : outline)
            out.add(new CodeQuick.Entry(s.name(), null, "event".equals(s.kind()) ? "с" : "ф",
                    CodeTheme.kindColor("event".equals(s.kind()) ? "event" : "function"), () -> goOffset(s.nameStart())));
        openQuick(CodeQuick.list(font, "событие или функция…", out));
    }

    private void quickLine() {
        Doc d = area.doc();
        if (d == null) return;
        openQuick(CodeQuick.ask(font, "Перейти к строке 1–" + d.lines(), "номер", "", s -> {
            try {
                area.goTo(Integer.parseInt(s.strip()) - 1, 0);
            } catch (NumberFormatException ignored) {
            }
        }));
    }

    private void quickCommands() {
        List<CodeQuick.Entry> out = new ArrayList<>();
        command(out, "Новый файл", "", () -> newFileMenu(width / 2, topH() + 20));
        command(out, "Открыть файл", FILES_KEY, this::quickFiles);
        command(out, "Импортировать файл с диска", "", this::importFile);
        if (area.file() != null) command(out, "Сохранить файл на диск", "", () -> download(area.file()));
        command(out, "Каталог действий", "Ctrl+I", this::quickWords);
        command(out, "Значение: местоположение, предмет, звук", "Alt+Enter", this::valueHere);
        command(out, "К событию или функции", "Ctrl+Shift+O", this::quickSymbols);
        command(out, "К строке", "Ctrl+G", this::quickLine);
        command(out, "Найти", "Ctrl+F", () -> openFind(false));
        command(out, "Заменить", "Ctrl+H", () -> openFind(true));
        command(out, "Найти во всех файлах", "Ctrl+Shift+F", this::askGlobalFind);
        command(out, "Сохранить и проверить файл", "Ctrl+S", this::buildFile);
        command(out, "Проверить все файлы", "Ctrl+Shift+B", () -> build(null));
        command(out, "Файл в мир, заменив код", Settings.get().label(Settings.Hot.UPLOAD), () -> upload(area.file(), true));
        command(out, "Файл в мир, ничего не стирая", "", () -> upload(area.file(), false));
        command(out, "Все файлы в мир, заменив код", "", () -> upload(null, true));
        command(out, "Открыть JSON файлом или ссылкой", "", this::loadModule);
        command(out, "Код из мира", "", this::fromWorld);
        command(out, "Код с полотна", "", this::fromCanvas);
        command(out, "Открыть файл на полотне", "", this::toCanvas);
        command(out, "Показать JSON файла", "", this::showJson);
        for (Dialect d : Dialects.all())
            command(out, "Перевести файл на " + d.title(), "", () -> translate(d));
        command(out, "Формат", "Shift+Alt+F", this::formatFile);
        command(out, "Свернуть всё", "", () -> area.foldAll(true));
        command(out, "Развернуть всё", "", () -> area.foldAll(false));
        command(out, "В верхний регистр", "", () -> caseOf(true));
        command(out, "В нижний регистр", "", () -> caseOf(false));
        command(out, "Убрать пробелы в конце строк", "", () -> {
            if (area.doc() == null) return;
            Editing.trimTrailing(area.doc());
            area.edited();
        });
        command(out, "Показать или скрыть файлы", SIDE_KEY, () -> sideOpen = !sideOpen);
        command(out, "Показать или скрыть нижнюю панель", "Ctrl+J", () -> bottomOpen = !bottomOpen);
        command(out, "Шрифт кода", "", () -> fontMenu(width / 2, topH() + 20));
        command(out, "Авторы языков", "", this::quickAuthors);
        command(out, "Настройки", "", this::openSettings);
        if (!Env.browser()) {
            command(out, "Игра", Settings.get().label(Settings.Hot.PLAY), () -> world("play"));
            command(out, "Строительство", Settings.get().label(Settings.Hot.BUILD), () -> world("build"));
            command(out, "Перезапустить мир", Settings.get().label(Settings.Hot.RESTART), this::restartWorld);
        }
        openQuick(CodeQuick.list(font, "команда…", out));
    }

    private void restartWorld() {
        saveNow();
        XeroCode.restart();
    }

    private boolean worldKey(Settings.Hot hot, int key) {
        if (hot == null || Env.browser()) return false;
        if (key < GLFW.GLFW_KEY_ESCAPE && (Settings.get().mods(hot) & ~Settings.SHIFT) == 0) return false;
        switch (hot) {
            case PLAY -> world("play");
            case BUILD -> world("build");
            case RESTART -> restartWorld();
            case MODE -> switchMode(Settings.Mode.ORIGINAL);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void world(String command) {
        Minecraft mc = Minecraft.getInstance();
        saveNow();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
        XeroCode.canvasClosed();
        mc.gui.setScreen(null);
    }

    private void quickAuthors() {
        List<CodeQuick.Entry> out = new ArrayList<>();
        for (Dialect d : Dialects.all()) {
            if (d.author().isEmpty()) continue;
            out.add(new CodeQuick.Entry(d.title(), d.author(), null, 0, () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(d.link());
                say("ссылка скопирована: " + d.link());
            }));
        }
        openQuick(CodeQuick.list(font, "язык…", out));
    }

    private void caseOf(boolean upper) {
        if (area.doc() == null) return;
        Editing.toggleCase(area.doc(), upper);
        area.edited();
    }

    private static void command(List<CodeQuick.Entry> out, String label, String note, Runnable run) {
        out.add(new CodeQuick.Entry(label, note.isEmpty() ? null : note, null, 0, run));
    }

    private void askGlobalFind() {
        openQuick(CodeQuick.ask(font, "Найти во всех файлах", "что искать", "", this::globalFind));
    }

    private void globalFind(String query) {
        if (query.isBlank()) return;
        Match m = Match.of(query, false, false, false);
        List<CodeBottom.Result> out = new ArrayList<>();
        for (CodeFile f : project.files) {
            project.load(f);
            for (int l = 0; l < f.doc.lines() && out.size() < 5000; l++) {
                String t = f.doc.line(l);
                for (int[] r : m.ranges(t)) out.add(new CodeBottom.Result(f, l, r[0], r[1] - r[0], t));
            }
        }
        bottomOpen = true;
        bottom.results("«" + query + "»: " + out.size(), out);
    }

    private void openFind(boolean replace) {
        if (area.doc() == null) return;
        findOpen = true;
        finder.open(replace);
    }

    private void closeFind() {
        findOpen = false;
        finder.close();
        area.focus(true);
    }

    private void openSettings() {
        settings = new SettingsPanel(font, width, height);
        area.focus(false);
    }

    private void buildFile() { build(area.file()); }

    private CodeProject.Outcome build(CodeFile only) {
        project.saveAll();
        CodeProject.Outcome out = project.build(only);
        problems = out.problems;
        area.problems(out.problems);
        String said = out.ok()
                ? "собрано: " + Ui.plural(out.lines, "строка", "строки", "строк")
                + (out.warnings > 0 ? " · предупреждений " + out.warnings : "")
                : "ошибок: " + out.errors;
        say(said);
        if (!out.ok()) {
            bottomOpen = true;
            bottom.show(CodeBottom.PROBLEMS);
        }
        return out;
    }

    private void upload(CodeFile only, boolean replace) {
        CodeProject.Outcome out = build(only);
        if (!out.ok()) return;
        JsonObject json = JsonParser.parseString(Json.write(out.module)).getAsJsonObject();
        project.saveAll();
        if (minecraft != null) minecraft.gui.setScreen(new ExportScreen(json, out.lines, replace, this));
    }

    private void loadModule() {
        if (minecraft != null) minecraft.gui.setScreen(new LoadScreen(this, this::takeFile));
    }

    private void takeFile(Path file) {
        try {
            JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!root.has("handlers") || !root.get("handlers").isJsonArray()) {
                say("в файле нет «handlers» — это не код JustMC");
                return;
            }
            String stem = file.getFileName().toString().replaceFirst("(?i)\\.json$", "");
            takeHandlers(root.getAsJsonArray("handlers"), stem);
        } catch (IOException | RuntimeException e) {
            XeroCode.LOG.error("[xerocode] код не прочитан: {}", file, e);
            say("файл не читается");
        }
    }

    private Dialect target() {
        CodeFile f = area.file();
        if (f != null && f.dialect != null && !"json".equals(f.dialect.id())) return f.dialect;
        return Dialects.all().get(0);
    }

    private void takeHandlers(JsonArray handlers, String stem) {
        JsonObject root = new JsonObject();
        root.add("handlers", handlers);
        Dialect d = target();
        Map<String, Object> module = Json.object(Json.parse(root.toString()));
        try {
            Dialect.Decompiled got;
            synchronized (d) {
                got = d.decompile(module);
            }
            CodeFile f = project.create(project.freeName(stem, d.extension()), got.text());
            for (String note : got.notes()) bottom.say(note);
            showFile(f);
            say("открыто: " + Ui.plural(handlers.size(), "строка", "строки", "строк"));
        } catch (RuntimeException e) {
            XeroCode.LOG.error("[xerocode] код не перевёлся", e);
            say("код не перевёлся на " + d.title() + ": " + e.getMessage());
        }
    }

    private void fromWorld() {
        if (Env.browser()) {
            loadModule();
            return;
        }
        download = Download.start(Minecraft.getInstance());
        if (download == null) say("сервер не дал /editor download — открой мир в 2D и нажми «С полотна»");
        else say("читаю код мира…");
    }

    private void fromCanvas() {
        try {
            Script active = Tabs.activeScript();
            List<Script> filled = Tabs.filled(active);
            Exporter.Result r = filled.isEmpty() ? Exporter.export(active) : Exporter.export(filled);
            takeHandlers(r.json().getAsJsonArray("handlers"), "полотно");
        } catch (RuntimeException e) {
            XeroCode.LOG.error("[xerocode] полотно не перевелось", e);
            say("полотно не перевелось");
        }
    }

    private void toCanvas() {
        CodeProject.Outcome out = build(area.file());
        if (!out.ok()) return;
        if (Tabs.full()) {
            say("вкладок полотна слишком много");
            return;
        }
        CodeFile f = area.file();
        Script script = Tabs.add(f == null ? "код" : f.name());
        if (script == null) return;
        JsonObject json = JsonParser.parseString(Json.write(out.module)).getAsJsonObject();
        try {
            Importer.importInto(script, json.getAsJsonArray("handlers"), font);
        } catch (RuntimeException e) {
            XeroCode.LOG.error("[xerocode] код не лёг на полотно", e);
            say("код не лёг на полотно");
            return;
        }
        script.fitOnOpen = true;
        Tabs.shown(script);
        switchMode(Settings.Mode.CANVAS);
    }

    private void showJson() {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        Dialect.Built b;
        Set<String> seen = new HashSet<>();
        synchronized (f.dialect) {
            b = f.dialect.build(f.path, f.doc.text(), project.loader(seen));
        }
        if (!b.ok()) {
            say("в файле ошибки");
            return;
        }
        String name = project.freeName(f.name().replaceFirst("\\.[^.]+$", ""), "json");
        showFile(project.create(name, Json.pretty(b.module())));
    }

    private void translate(Dialect to) {
        CodeFile f = area.file();
        if (f == null || f.dialect == null) return;
        Dialect.Built b;
        synchronized (f.dialect) {
            b = f.dialect.build(f.path, f.doc.text(), project.loader(new HashSet<>()));
        }
        if (!b.ok()) {
            say("сначала исправь ошибки");
            return;
        }
        try {
            Dialect.Decompiled got;
            synchronized (to) {
                got = to.decompile(b.module());
            }
            String stem = f.name().replaceFirst("\\.[^.]+$", "");
            showFile(project.create(project.freeName(stem, to.extension()), got.text()));
            for (String note : got.notes()) bottom.say(note);
        } catch (RuntimeException e) {
            XeroCode.LOG.error("[xerocode] перевод упал", e);
            say("не перевелось: " + e.getMessage());
        }
    }

    private void switchMode(Settings.Mode mode) {
        area.remember();
        project.saveAll();
        Settings s = Settings.get();
        s.mode = mode;
        s.save();
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        if (mode == Settings.Mode.CANVAS) {
            mc.gui.setScreen(new EditorScreen(Tabs.activeScript()));
            return;
        }
        XeroCode.canvasClosed();
        mc.gui.setScreen(null);
        mc.gui.hud.setTimes(3, 50, 10);
        mc.gui.hud.setTitle(Component.literal("3D-кодинг"));
        mc.gui.hud.setSubtitle(Component.literal(s.label(Settings.Hot.OPEN) + " — назад"));
    }

    private void press(int id) {
        switch (id) {
            case ORIGINAL -> switchMode(Settings.Mode.ORIGINAL);
            case CANVAS -> switchMode(Settings.Mode.CANVAS);
            case SIDE -> sideOpen = !sideOpen;
            case UNDO -> area.undo();
            case REDO -> area.redo();
            case BUILD -> buildFile();
            case UPLOAD -> upload(area.file(), true);
            case LOAD -> fromWorld();
            case TO_CANVAS -> toCanvas();
            case FROM_CANVAS -> fromCanvas();
            case FIND -> {
                if (findOpen) closeFind();
                else openFind(false);
            }
            case COMMANDS -> quickCommands();
            case SETTINGS -> openSettings();
            case VALUE -> valueMenu(area.caretGuiX(), area.caretGuiY() + area.rowGuiH());
            default -> { }
        }
    }

    private String fontLabel() {
        Settings s = Settings.get();
        if (com.xerocode.code.CodeFonts.PIXEL.equals(s.codeFont)) return "Пиксельный";
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        int size = s.codeSize > 0 ? s.codeSize : CodeArea.defaultSize(guiScale);
        return com.xerocode.code.CodeFonts.title(s.codeFont) + " " + size;
    }

    private void fontMenu(int x, int y) {
        List<com.xerocode.code.CodeFonts.Entry> all = com.xerocode.code.CodeFonts.LIST;
        List<Menu.Item> items = new ArrayList<>();
        for (com.xerocode.code.CodeFonts.Entry e : all) items.add(new Menu.Item(e.title()));
        items.add(new Menu.Item("Свой файл .ttf…"));
        int checked = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(Settings.get().codeFont)) checked = i;
        menu = Menu.picker(width, height, x, y, font, "Шрифт кода", items, checked, i -> {
            if (i >= 0 && i < all.size()) setFont(all.get(i).id());
            else if (i == all.size()) pickFont();
        });
    }

    private void setFont(String id) {
        Settings s = Settings.get();
        s.codeFont = id;
        s.save();
        area.metrics();
        area.reveal();
    }

    private void pickFont() {
        FileDialog.open("Шрифт кода", project.root.toString(), new String[]{"*.ttf", "*.otf"}, "шрифт", file -> {
            if (file == null) return;
            try {
                Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode").resolve("fonts");
                if (!Files.isDirectory(dir)) Files.createDirectories(dir);
                Path kept = dir.resolve(file.getFileName().toString());
                Files.write(kept, Files.readAllBytes(file));
                setFont(com.xerocode.code.CodeFonts.CUSTOM + kept);
            } catch (IOException e) {
                XeroCode.LOG.warn("[xerocode] шрифт не сохранён", e);
                say("шрифт не сохранился");
            }
        });
    }

    private void languageMenu(int x, int y) {
        List<Dialect> all = new ArrayList<>(Dialects.all());
        List<Menu.Item> items = new ArrayList<>();
        for (Dialect d : all) items.add(new Menu.Item("Перевести на " + d.title()));
        menu = Menu.actions(width, height, x, y, font, items, i -> {
            if (i >= 0 && i < all.size()) translate(all.get(i));
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (valueEditor != null) {
            boolean inside = valueEditor.mouseClicked(click, doubled);
            if (valueEditor.isClosed() || !inside) finishValue();
            return true;
        }
        if (settings != null) {
            settings.mouseClicked(click, doubled);
            if (settings.isClosed()) closeSettings();
            return true;
        }
        if (menu != null) {
            Menu open = menu;
            menu = null;
            if (open.contains(mx, my)) open.mouseClicked(mx, my);
            return true;
        }
        if (quick != null) {
            quick.mouseClicked(click, doubled);
            if (quick != null && quick.done()) closeQuick();
            return true;
        }
        if (keyStrip(mx, my)) return true;
        if (findOpen && finder.contains(mx, my)) return finder.mouseClicked(click, doubled);
        if (area.doc() != null && area.hints.mouseClicked(mx, my)) return true;
        if (my < topH()) {
            for (int[] p : placed) {
                Btn b = button(p[0]);
                if (b.enabled && Ui.hit(mx, my, p[1], 0, p[2], topH())) {
                    press(b.id);
                    return true;
                }
            }
            return true;
        }
        if (my >= height - statusH()) {
            if (mx < width / 3) {
                bottomOpen = !bottomOpen || bottom.tab() != CodeBottom.PROBLEMS;
                bottom.show(CodeBottom.PROBLEMS);
            } else {
                String lang = area.file() == null || area.file().dialect == null ? "текст" : area.file().dialect.title();
                int lw = font.width(lang) + 10, lx = width - 8 - lw;
                int fw = font.width(fontLabel()) + 10, fx = lx - 4 - fw;
                if (mx >= lx) languageMenu((int) mx, (int) my - 60);
                else if (mx >= fx) fontMenu((int) mx, (int) my - 90);
            }
            return true;
        }
        if (sideOpen && Math.abs(mx - sideShown()) <= 2 && my >= topH()) {
            draggingSide = true;
            return true;
        }
        if (bottomOpen && mx >= sideShown() && Math.abs(my - (height - statusH() - bottomShown())) <= 2) {
            draggingBottom = true;
            return true;
        }
        if (sideOpen && side.contains(mx, my)) return side.mouseClicked(click);
        if (bottomOpen && bottom.contains(mx, my)) return bottom.mouseClicked(click);
        if (my < topH() + tabsH()) return tabClicked(mx, click.button());
        if (area.doc() == null) return welcomeClicked(mx, my);
        finder.blur();
        return area.mouseClicked(click, doubled);
    }

    private boolean welcomeClicked(double mx, double my) {
        int left = sideShown(), top = topH() + tabsH();
        int w = width - left, h = height - top - statusH() - bottomShown();
        List<Dialect> all = chooseable();
        int rowH = 34, total = rowH * all.size() + 24;
        int cw = Math.min(360, w - 20), cx = left + (w - cw) / 2, cy = top + Math.max(10, (h - total) / 2);
        for (int i = 0; i < all.size(); i++) {
            if (Ui.hit(mx, my, cx, cy + 18 + i * rowH, cw, rowH - 4)) {
                askNewFile(all.get(i));
                return true;
            }
        }
        return true;
    }

    private boolean tabClicked(double mx, int button) {
        for (int i = 0; i < tabXs.length && i < project.open.size(); i++) {
            if (mx < tabXs[i] || mx >= tabXs[i] + tabWs[i]) continue;
            CodeFile f = project.open.get(i);
            if (button == 2 || mx >= tabXs[i] + tabWs[i] - 15) closeTab(f);
            else showFile(f);
            return true;
        }
        return true;
    }

    private void closeTab(CodeFile f) {
        area.remember();
        project.close(f);
        area.open(project.active);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (valueEditor != null) return valueEditor.mouseDragged(click, dx, dy);
        if (settings != null) return settings.mouseDragged(click, dx, dy);
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (draggingSide) {
            sideW = (int) click.x();
            return true;
        }
        if (draggingBottom) {
            bottomH = height - statusH() - (int) click.y();
            return true;
        }
        if (side.mouseDragged(click)) return true;
        return area.mouseDragged(click);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (valueEditor != null) {
            valueEditor.mouseReleased();
            if (valueEditor.isClosed()) finishValue();
            return true;
        }
        if (settings != null) {
            settings.mouseReleased();
            if (settings.isClosed()) closeSettings();
            return true;
        }
        if (menu != null) menu.mouseReleased();
        if (draggingSide || draggingBottom) {
            Settings s = Settings.get();
            s.codeSideW = sideW;
            s.codeBottomH = bottomH;
            s.save();
        }
        draggingSide = false;
        draggingBottom = false;
        side.mouseReleased();
        area.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (valueEditor != null) return valueEditor.mouseScrolled(mx, my, dy);
        if (settings != null) return settings.mouseScrolled(mx, my, dy);
        if (menu != null && menu.mouseScrolled(mx, my, dy)) return true;
        if (quick != null) return quick.mouseScrolled(dy);
        if (area.doc() != null && area.hints.mouseScrolled(mx, my, dy)) return true;
        if (sideOpen && side.mouseScrolled(mx, my, dy)) return true;
        if (bottomOpen && bottom.mouseScrolled(mx, my, dy)) return true;
        return area.mouseScrolled(mx, my, dx, dy, modifiers());
    }

    private static int modifiers() {
        int m = 0;
        if (Env.keyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || Env.keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) m |= GLFW.GLFW_MOD_CONTROL;
        if (Env.keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || Env.keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) m |= GLFW.GLFW_MOD_SHIFT;
        return m;
    }

    private void closeSettings() {
        settings.dispose();
        settings = null;
        area.focus(true);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (valueEditor != null) {
            valueEditor.keyPressed(e);
            if (valueEditor.isClosed()) finishValue();
            return true;
        }
        if (settings != null) {
            settings.keyPressed(e);
            if (settings.isClosed()) closeSettings();
            return true;
        }
        if (menu != null) {
            if (menu.keyPressed(e)) return true;
            if (e.key() == GLFW.GLFW_KEY_ESCAPE) menu = null;
            return true;
        }
        if (quick != null) {
            quick.keyPressed(e);
            if (quick != null && quick.done()) closeQuick();
            return true;
        }
        int key = e.key(), m = e.modifiers();
        boolean ctrl = (m & GLFW.GLFW_MOD_CONTROL) != 0, shift = (m & GLFW.GLFW_MOD_SHIFT) != 0;
        if (findOpen && finder.focused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeFind();
                return true;
            }
            if (!(ctrl && (key == GLFW.GLFW_KEY_F || key == GLFW.GLFW_KEY_H))) return finder.keyPressed(e);
        }
        if (Settings.get().match(key, m) == Settings.Hot.UPLOAD) {
            upload(area.file(), true);
            return true;
        }
        if (Settings.get().match(key, m) == Settings.Hot.SETTINGS) {
            openSettings();
            return true;
        }
        if (worldKey(Settings.get().match(key, m), key)) return true;
        if (global(key, ctrl, shift)) return true;
        if (area.keyPressed(e)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && findOpen) {
            closeFind();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return true;
    }

    private boolean global(int key, boolean ctrl, boolean shift) {
        if (key == GLFW.GLFW_KEY_F3 && findOpen) {
            finder.step(shift ? -1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_F1) {
            quickCommands();
            return true;
        }
        if (!ctrl) return false;
        switch (key) {
            case GLFW.GLFW_KEY_S -> buildFile();
            case GLFW.GLFW_KEY_B -> {
                if (shift) build(null);
                else sideOpen = !sideOpen;
            }
            case GLFW.GLFW_KEY_E -> {
                if (shift) sideOpen = !sideOpen;
                else quickFiles();
            }
            case GLFW.GLFW_KEY_J -> bottomOpen = !bottomOpen;
            case GLFW.GLFW_KEY_F -> {
                if (shift) askGlobalFind();
                else openFind(false);
            }
            case GLFW.GLFW_KEY_H -> openFind(true);
            case GLFW.GLFW_KEY_G -> quickLine();
            case GLFW.GLFW_KEY_P -> {
                if (shift) quickCommands();
                else quickFiles();
            }
            case GLFW.GLFW_KEY_O -> {
                if (shift) quickSymbols();
                else return false;
            }
            case GLFW.GLFW_KEY_N -> newFileMenu(width / 2, topH() + 20);
            case GLFW.GLFW_KEY_I -> quickWords();
            case GLFW.GLFW_KEY_W -> {
                if (area.file() != null) closeTab(area.file());
            }
            case GLFW.GLFW_KEY_TAB -> cycleTab(shift ? -1 : 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void cycleTab(int dir) {
        int n = project.open.size();
        if (n < 2) return;
        int at = project.open.indexOf(project.active);
        showFile(project.open.get((at + dir + n) % n));
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (valueEditor != null) return valueEditor.charTyped(e);
        if (settings != null) {
            settings.charTyped(e);
            return true;
        }
        if (menu != null) {
            menu.charTyped(e);
            return true;
        }
        if (quick != null) return quick.charTyped(e);
        if (findOpen && finder.focused()) return finder.charTyped(e);
        return area.charTyped(e);
    }

    public void touchHold(double mx, double my) {
        if (area.doc() == null || !area.contains(mx, my)) return;
        area.mouseClicked(new MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        area.mouseReleased();
        Doc d = area.doc();
        Doc.Caret c = d.primary();
        Editing.selectWordAt(d, c.line, c.col);
    }

    public void saveNow() {
        area.remember();
        project.saveAll();
        rememberPanels();
    }

    private void rememberPanels() {
        Settings s = Settings.get();
        if (Env.touch() || s.codeSideOpen == sideOpen && s.codeBottomOpen == bottomOpen) return;
        s.codeSideOpen = sideOpen;
        s.codeBottomOpen = bottomOpen;
        s.save();
    }

    @Override
    public void removed() {
        saveNow();
        Env.textFocus(area, false);
        if (minecraft != null) Backstage.restore(minecraft);
    }

    @Override
    public void onClose() {
        saveNow();
        XeroCode.canvasClosed();
        if (minecraft != null) {
            Backstage.restore(minecraft);
            minecraft.gui.setScreen(null);
        }
    }

}
