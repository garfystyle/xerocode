package com.xerocode.ui;

import com.google.gson.JsonArray;
import com.xerocode.Backpack;
import com.xerocode.Env;
import com.xerocode.History;
import com.xerocode.Importer;
import com.xerocode.ModuleActions;
import com.xerocode.Modules;
import com.xerocode.Script;
import com.xerocode.XeroCode;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

public final class ModulesScreen extends Screen {
    public interface Panel {
        void place(int x, int y, int w, int h);
        void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta);
        boolean click(MouseButtonEvent click, boolean doubled);
        default boolean key(KeyEvent in) { return false; }
        default boolean chars(CharacterEvent in) { return false; }
        default boolean drag(MouseButtonEvent click, double dx, double dy) { return false; }
        default boolean wheel(double mx, double my, double amount) { return false; }
        default void release() { }
        default String hint() { return ""; }
        default String title() { return ""; }
        default String action() { return ""; }
        default boolean actionOn() { return true; }
        default void act() { }
    }

    static final int HEAD_H = 34, FOOT_H = 30, PAD = 12;
    static final int RAIL_W = 150, RAIL_ROW = 17, RAIL_HEAD = 19;
    private static final int CARD_MIN = ModuleArt.CARD_MIN, CARD_GAP = 10;
    private static final int CARD_H = ModuleArt.CARD_H;
    private static final int NARROW = 560, CRUMB_GAP = 5, SHEET_W = 220;
    private static final String CRUMB = "Модули", REFRESH = "Обновить";

    private final Script script;
    private final Screen back;

    private EditBox search;
    private String query = "";
    private Modules.Sort sort = Modules.Sort.INSTALLS;
    private String category = "";
    private boolean approvedOnly, mineOnly;

    private List<Modules.Module> view = List.of();
    private String viewKey = "";
    private final Map<String, Integer> counts = new HashMap<>();
    private int countsVersion = -1;

    private int scroll, railScroll;
    private final Ui.Bar bar = new Ui.Bar(), railBar = new Ui.Bar();
    private final Ui.Grab grab = new Ui.Grab();
    private int hovered = -1, chosen = -1;
    private String grabbing = "";

    private Panel panel;
    private String toast = "";
    private long toastAt;
    private boolean railOpen;

    private boolean centerOn;
    private int centerX, centerY, centerW;

    private final List<Rail> rails = new ArrayList<>();
    private final List<Panel> trail = new ArrayList<>();

    private record Rail(String kind, String label, String value) {}

    public ModulesScreen(Script script, Screen back) {
        super(Component.literal("Модули JustMC"));
        this.script = script;
        this.back = back;
        Modules.open();
        buildRails();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        String typed = search == null ? query : search.getValue();
        search = Ui.field(font, 0, 0, 80, 10, "найти модуль…");
        search.setMaxLength(48);
        search.setValue(typed);
        search.setResponder(text -> {
            if (query.equals(text.trim())) return;
            query = text.trim();
            panel = null;
            trail.clear();
            scroll = 0;
            chosen = -1;
        });
        addWidget(search);
        placePanel();
    }

    private void buildRails() {
        rails.clear();
        for (Modules.Sort s : Modules.Sort.values()) rails.add(new Rail("sort", s.label, s.name()));
        rails.add(new Rail("head", "РАЗДЕЛЫ", ""));
        rails.add(new Rail("cat", "Все разделы", ""));
        for (Modules.Category c : Modules.CATEGORIES) rails.add(new Rail("cat", c.label(), c.id()));
        rails.add(new Rail("head", "ОТБОР", ""));
        rails.add(new Rail("flag", "Только проверенные", "approved"));
        if (!Env.browser()) {
            rails.add(new Rail("head", "МОЁ", ""));
            rails.add(new Rail("flag", "Только мои", "mine"));
            rails.add(new Rail("act", "Выложить свой…", "publish"));
        }
    }

    private boolean narrow() { return width < NARROW; }

    int railW() { return narrow() ? 0 : Math.min(RAIL_W, width / 4); }

    int bodyX() { return railW() + (narrow() ? 0 : 1); }
    int bodyY() { return HEAD_H + 1; }
    int bodyW() { return width - bodyX(); }
    int bodyH() { return Math.max(40, height - HEAD_H - FOOT_H - 1); }

    private void placePanel() {
        if (panel != null) panel.place(bodyX(), bodyY(), bodyW(), bodyH());
    }

    @Override
    public void resize(int w, int h) {
        super.resize(w, h);
        placePanel();
    }

    public void toast(String message) {
        toast = message;
        toastAt = System.currentTimeMillis();
    }

    public void show(Panel next) {
        if (panel != null) trail.add(panel);
        panel = next;
        placePanel();
        if (search != null) search.setFocused(false);
    }

    public void showList() {
        panel = null;
        trail.clear();
        if (search != null) search.setFocused(false);
    }

    public void back() {
        if (trail.isEmpty()) {
            showList();
            return;
        }
        panel = trail.remove(trail.size() - 1);
        placePanel();
    }

    public void openModule(Modules.Module module) { show(new ModulePage(this, module)); }

    public void openLook(Modules.Module module, JsonArray handlers) {
        show(new ModuleLook(this, module, handlers));
    }

    private void refreshView() {
        List<Modules.Module> all = Modules.all();
        if (countsVersion != Modules.version()) {
            countsVersion = Modules.version();
            counts.clear();
            for (Modules.Module m : all) counts.merge(m.category, 1, Integer::sum);
        }
        String key = Modules.version() + "|" + sort + "|" + category + "|" + approvedOnly + "|" + mineOnly + "|" + query;
        if (key.equals(viewKey)) return;
        viewKey = key;
        view = Modules.pick(all, sort, category, approvedOnly, query);
        if (mineOnly) {
            String me = ModuleActions.me();
            view = view.stream().filter(m -> m.author.equalsIgnoreCase(me)).toList();
        }
        if (chosen >= view.size()) chosen = -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        refreshView();
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        drawHeader(ctx, mouseX, mouseY, delta);
        if (!narrow()) drawRail(ctx, mouseX, mouseY);
        if (panel == null) drawList(ctx, mouseX, mouseY);
        else panel.draw(ctx, mouseX, mouseY, delta);
        drawFooter(ctx, mouseX, mouseY);
        if (railOpen) drawRailSheet(ctx, mouseX, mouseY);
        drawToast(ctx);
    }

    private int searchW() { return Math.max(70, Math.min(230, width / 3)); }

    private int searchX() { return width - PAD - searchW(); }

    private void drawHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, 0, 0, width, HEAD_H, Draw.opaque(Ui.HEAD));
        Draw.rect(ctx, 0, HEAD_H - 1, width, 1, Draw.opaque(Ui.LINE));
        int bw = Ui.buttonW(font, Draw.CHEVRON_LEFT, backLabel());
        Ui.glyphButton(ctx, font, mouseX, mouseY, PAD, 8, bw, 18,
                Draw.CHEVRON_LEFT, backLabel(), Ui.GHOST, true);
        int at = PAD + bw + 10;
        if (narrow() && panel == null) {
            int rw = sheetButtonW(at);
            Ui.glyphButton(ctx, font, mouseX, mouseY, at, 8, rw, 18,
                    Draw.CARET_DOWN, railLabel(), railOpen ? Ui.ACTIVE : Ui.GHOST, true);
        } else {
            Draw.glyph(ctx, Draw.SHOP, at, 12, Theme.ACCENT);
            int tx = at + Draw.glyphW(Draw.SHOP) + 7;
            int room = searchX() - tx - 8;
            if (panel == null || panel.title().isEmpty()) {
                Draw.textFit(ctx, font, "МОДУЛИ JUSTMC", tx, 13, room, Theme.TEXT, false);
            } else {
                boolean hot = Ui.hit(mouseX, mouseY, tx, 10, crumbW(), 16);
                Draw.textFit(ctx, font, CRUMB, tx, 13, room, hot ? Theme.TEXT : Theme.TEXT_FAINT, false);
                int sx = tx + crumbW() + CRUMB_GAP;
                if (sx + 20 < searchX()) {
                    Draw.text(ctx, font, "›", sx, 13, Theme.TEXT_FAINT, false);
                    int nameX = sx + font.width("›") + CRUMB_GAP;
                    Draw.textFit(ctx, font, panel.title(), nameX, 13,
                            Math.max(0, room - (nameX - tx)), Theme.TEXT, false);
                }
            }
        }
        Ui.input(ctx, searchX(), 8, searchW(), 18, search.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, searchX() + 6, 13, Theme.TEXT_FAINT);
        Ui.width(search, searchW() - 22);
        search.setX(searchX() + 16);
        search.setY(13);
        search.extractRenderState(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, font, search);
    }

    private int sheetButtonW(int at) {
        return Math.min(Ui.buttonW(font, Draw.CARET_DOWN, railLabel()), Math.max(40, searchX() - at - 8));
    }

    private String backLabel() {
        if (width < 420) return "";
        return panel == null ? "Полотно" : "Назад";
    }

    private int crumbW() { return font.width(CRUMB); }

    private int crumbX() {
        return PAD + Ui.buttonW(font, Draw.CHEVRON_LEFT, backLabel()) + 10
                + Draw.glyphW(Draw.SHOP) + 7;
    }

    private String railLabel() {
        return category.isEmpty() ? sort.label : Modules.category(category).label();
    }

    private int rowH(Rail row) { return row.kind().equals("head") ? RAIL_HEAD : RAIL_ROW; }

    private int railContentH() {
        int h = 8;
        for (Rail row : rails) h += rowH(row);
        return h;
    }

    private int railTop() { return bodyY(); }

    private int railH() { return bodyH(); }

    private int sheetW() { return Math.min(SHEET_W, width - 24); }

    private int sheetH() { return Math.min(railContentH(), height - HEAD_H - 20); }

    private int sheetY() { return HEAD_H + 4; }

    private int railMax() {
        return Math.max(0, railContentH() - (narrow() ? sheetH() : railH()));
    }

    private void clampRail(int visible) {
        railScroll = Math.max(0, Math.min(Math.max(0, railContentH() - visible), railScroll));
    }

    private void drawRail(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int w = railW(), y = railTop(), h = railH();
        Draw.rect(ctx, 0, y, w, h, Draw.opaque(Ui.RAIL));
        Ui.vline(ctx, w, y, h);
        clampRail(h);
        drawRailRows(ctx, mouseX, mouseY, 0, y, w, h);
    }

    private void drawRailSheet(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.nextStratum();
        int w = sheetW(), h = sheetH(), x = PAD, y = sheetY();
        Ui.panel(ctx, x, y, w, h);
        clampRail(h);
        drawRailRows(ctx, mouseX, mouseY, x, y, w, h);
    }

    private void drawRailRows(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                              int x, int y, int w, int h) {
        boolean inside = Ui.hit(mouseX, mouseY, x, y, w, h);
        ctx.enableScissor(x, y, x + w, y + h);
        int at = y + 4 - railScroll;
        for (Rail row : rails) {
            int rh = rowH(row);
            if (at + rh < y || at > y + h) {
                at += rh;
                continue;
            }
            if (row.kind().equals("head")) {
                Ui.caption(ctx, font, row.label(), x + 11, at + 7, w - 20);
                at += rh;
                continue;
            }
            boolean on = active(row);
            boolean hot = inside && Ui.hit(mouseX, mouseY, x + 4, at, w - 8, RAIL_ROW - 1);
            if (on && !row.kind().equals("flag")) {
                Draw.round(ctx, x + 4, at, w - 8, RAIL_ROW - 1, 3, Draw.opaque(Ui.BTN_ON));
                Draw.rect(ctx, x + 4, at + 2, 2, RAIL_ROW - 5, Draw.opaque(Theme.ACCENT));
            } else if (hot) {
                Draw.round(ctx, x + 4, at, w - 8, RAIL_ROW - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            }
            int ink = on || hot ? Theme.TEXT : Theme.TEXT_DIM;
            int left = x + 11, right = x + w - 10;
            if (row.kind().equals("cat")) {
                int color = row.value().isEmpty() ? Theme.TEXT_FAINT : ModuleArt.catColor(row.value());
                Draw.round(ctx, x + 10, at + 6, 5, 5, 2, Draw.opaque(color));
                left = x + 20;
                String n = String.valueOf(row.value().isEmpty() ? Modules.all().size()
                        : counts.getOrDefault(row.value(), 0));
                if (!Modules.all().isEmpty() && right - left > font.width(n) + 50) {
                    Draw.textRight(ctx, font, n, right, at + 5, Theme.TEXT_FAINT, false);
                    right -= font.width(n) + 6;
                }
            } else if (row.kind().equals("flag")) {
                int box = 9, bx = right - box;
                Draw.roundOutline(ctx, bx, at + 4, box, box, 2,
                        Draw.opaque(on ? Theme.ACCENT : Theme.TEXT_FAINT));
                if (on) Draw.glyph(ctx, Draw.CHECK, bx + (box - Draw.glyphW(Draw.CHECK)) / 2,
                        at + 4 + (box - Draw.glyphH(Draw.CHECK)) / 2, Theme.ACCENT);
                right = bx - 6;
            }
            Draw.textFit(ctx, font, row.label(), left, at + 5, Math.max(0, right - left), ink, false);
            at += rh;
        }
        ctx.disableScissor();
        railBar.draw(ctx, x + w - 5, y + 2, h - 4, railContentH(), h, railScroll, mouseX, mouseY);
    }

    private boolean active(Rail row) {
        return switch (row.kind()) {
            case "sort" -> sort.name().equals(row.value());
            case "cat" -> category.equals(row.value());
            case "flag" -> "mine".equals(row.value()) ? mineOnly : approvedOnly;
            default -> false;
        };
    }

    private int cols() {
        int room = bodyW() - PAD * 2;
        return Math.max(1, Math.min(6, (room + CARD_GAP) / (CARD_MIN + CARD_GAP)));
    }

    private int cardW() {
        int cols = cols();
        return (bodyW() - PAD * 2 - CARD_GAP * (cols - 1)) / cols;
    }

    private int cardX(int i) { return bodyX() + PAD + (i % cols()) * (cardW() + CARD_GAP); }

    private int cardY(int i) { return bodyY() + PAD + (i / cols()) * (CARD_H + CARD_GAP) - scroll; }

    private int contentH() {
        int rows = (view.size() + cols() - 1) / cols();
        return PAD * 2 + Math.max(0, rows * (CARD_H + CARD_GAP) - CARD_GAP);
    }

    private int maxScroll() { return Math.max(0, contentH() - bodyH()); }

    private int firstShown() {
        return Math.max(0, (scroll - PAD) / (CARD_H + CARD_GAP)) * cols();
    }

    private int lastShown() {
        int row = (scroll + bodyH()) / (CARD_H + CARD_GAP) + 1;
        return Math.min(view.size(), (row + 1) * cols());
    }

    private void drawList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x = bodyX(), y = bodyY(), w = bodyW(), h = bodyH();
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        hovered = cardAt(mouseX, mouseY);
        if (hovered >= 0) chosen = hovered;

        centerOn = false;
        if (Modules.all().isEmpty()) {
            if (Modules.loading()) drawSkeletons(ctx);
            else {
                String said = Modules.note().isEmpty() ? "каталог JustMC пуст" : Modules.note();
                centre(ctx, middle(ctx, said), mouseX, mouseY);
            }
            return;
        }
        if (view.isEmpty()) {
            middle(ctx, emptyWord());
            return;
        }

        ctx.enableScissor(x, y, x + w, y + h);
        ModuleArt.clip = new ScreenRectangle(x, y, w, h);
        int spot = hovered >= 0 ? hovered : chosen;
        for (int i = firstShown(); i < lastShown(); i++) {
            int cy = cardY(i);
            if (cy > y + h || cy + CARD_H < y) continue;
            Modules.Module m = view.get(i);
            boolean hot = i == spot;
            boolean acts = hot && m.open && grabW(m) >= 40;
            ModuleArt.card(ctx, font, m, cardX(i), cy, cardW(), hot, acts);
            if (acts) drawGrab(ctx, m, cardX(i), cy, cardW(), mouseX, mouseY);
        }
        ModuleArt.clip = null;
        ctx.disableScissor();
        bar.draw(ctx, x + w - 5, y + 2, h - 4, contentH(), h, scroll, mouseX, mouseY);
    }

    private String grabLabel(Modules.Module m) {
        return grabbing.equals(m.id) ? "Гружу…" : "На полотно";
    }

    private int grabW(Modules.Module m) {
        return Math.min(cardW() - 70, Ui.buttonW(font, grabLabel(m)) - 6);
    }

    private int cardBtnY(int cy) { return cy + CARD_H - ModuleArt.CARD_INSET - ModuleArt.CARD_BTN_H; }

    private int grabX(int cx, int cw, Modules.Module m) {
        return cx + cw - ModuleArt.CARD_INSET - grabW(m);
    }

    private int lookX(int cx, int cw, Modules.Module m) {
        return grabX(cx, cw, m) - 6 - ModuleArt.CARD_BTN_H;
    }

    private boolean roomForLook(int cw, Modules.Module m) {
        return cw - ModuleArt.CARD_INSET * 2 - grabW(m) - 6 - ModuleArt.CARD_BTN_H >= 44;
    }

    private void drawGrab(GuiGraphicsExtractor ctx, Modules.Module m, int cx, int cy, int cw,
                          int mouseX, int mouseY) {
        int by = cardBtnY(cy);
        if (roomForLook(cw, m))
            Ui.iconButton(ctx, mouseX, mouseY, lookX(cx, cw, m), by, ModuleArt.CARD_BTN_H,
                    Draw.SEARCH, Ui.GHOST, true);
        Ui.button(ctx, font, mouseX, mouseY, grabX(cx, cw, m), by, grabW(m),
                ModuleArt.CARD_BTN_H, grabLabel(m), Ui.ACCENT, grabbing.isEmpty());
    }

    private void drawSkeletons(GuiGraphicsExtractor ctx) {
        int x = bodyX(), y = bodyY(), w = bodyW(), h = bodyH();
        ctx.enableScissor(x, y, x + w, y + h);
        int rows = Math.max(1, (h - PAD) / (CARD_H + CARD_GAP) + 1);
        for (int i = 0; i < Math.min(12, rows * cols()); i++)
            ModuleArt.skeleton(ctx, cardX(i), bodyY() + PAD + (i / cols()) * (CARD_H + CARD_GAP),
                    cardW(), i);
        ctx.disableScissor();
    }

    private void centre(GuiGraphicsExtractor ctx, int at, int mouseX, int mouseY) {
        centerOn = true;
        centerW = Math.min(bodyW() - 20, Ui.buttonW(font, Draw.RESET, "Ещё раз"));
        centerX = bodyX() + (bodyW() - centerW) / 2;
        centerY = at + 8;
        Ui.glyphButton(ctx, font, mouseX, mouseY, centerX, centerY, centerW, 18,
                Draw.RESET, "Ещё раз", Ui.ACCENT, true);
    }

    private String emptyWord() {
        if (!query.isEmpty()) return "ничего не нашлось по запросу «" + query + "»";
        if (mineOnly) return "твоих публичных модулей тут нет — приватные JustMC в каталог не отдаёт";
        if (approvedOnly) return "проверенных модулей тут нет";
        return "в разделе «" + Modules.category(category).label() + "» пусто";
    }

    private int middle(GuiGraphicsExtractor ctx, String said) {
        int x = bodyX(), y = bodyY() + bodyH() / 2 - 30, w = bodyW();
        Draw.glyph(ctx, Draw.SHOP, x + (w - Draw.glyphW(Draw.SHOP)) / 2, y, Theme.LINE);
        int at = y + 20;
        for (String line : Ui.wrap(font, said, Math.max(40, Math.min(360, w - 40)), 3)) {
            Draw.textCenter(ctx, font, line, x, at, w, w - 40, Theme.TEXT_DIM, false);
            at += 11;
        }
        return at;
    }

    private int cardAt(double mx, double my) {
        if (panel != null || railOpen || mx < bodyX() || my < bodyY() || my >= bodyY() + bodyH())
            return -1;
        for (int i = firstShown(); i < lastShown(); i++)
            if (Ui.hit(mx, my, cardX(i), cardY(i), cardW(), CARD_H)) return i;
        return -1;
    }

    private String footLabel() {
        if (panel != null) return panel.action();
        return width < 360 ? "" : REFRESH;
    }

    private int footW() {
        if (panel != null)
            return panel.action().isEmpty() ? 0 : Math.max(96, Ui.buttonW(font, panel.action()));
        return Ui.buttonW(font, Draw.RESET, footLabel());
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int y = height - FOOT_H;
        Draw.rect(ctx, 0, y, width, FOOT_H, Draw.opaque(Ui.HEAD));
        Draw.rect(ctx, 0, y, width, 1, Draw.opaque(Ui.LINE));
        int bw = footW();
        if (panel == null)
            Ui.glyphButton(ctx, font, mouseX, mouseY, width - PAD - bw, y + 6, bw, 18,
                    Draw.RESET, footLabel(), Ui.GHOST, !Modules.loading());
        else if (bw > 0)
            Ui.button(ctx, font, mouseX, mouseY, width - PAD - bw, y + 6, bw, 18,
                    panel.action(), Ui.ACCENT, panel.actionOn());
        String said = panel != null && !panel.hint().isEmpty() ? panel.hint() : listHint();
        Draw.textFit(ctx, font, said, PAD, y + 11, width - PAD * 2 - bw - 14,
                Theme.TEXT_FAINT, false);
    }

    private String listHint() {
        if (!Modules.note().isEmpty() && (Modules.loading() || !Modules.all().isEmpty()))
            return Modules.note();
        if (Modules.loading()) {
            String got = Modules.got() > 0 ? " · " + Modules.got() : "";
            return (Modules.all().isEmpty() ? "гружу каталог JustMC…" : "обновляю каталог…") + got;
        }
        int total = Modules.all().size();
        if (total == 0) return "";
        String count = Ui.plural(view.size(), "модуль", "модуля", "модулей");
        if (view.size() != total) count += " из " + total;
        return count + " · " + age();
    }

    private String age() {
        long at = Modules.fetched();
        if (at <= 0) return "каталог JustMC";
        long minutes = (System.currentTimeMillis() - at) / 60_000L;
        if (minutes < 1) return "каталог только что обновлён";
        if (minutes < 60) return "каталог обновлён " + minutes + " мин назад";
        return "каталог обновлён " + Backpack.when(at);
    }

    private void drawToast(GuiGraphicsExtractor ctx) {
        if (toast.isEmpty()) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 4200) { toast = ""; return; }
        ctx.nextStratum();
        int w = Math.min(width - 40, font.width(toast) + 26);
        int x = (width - w) / 2, y = height - FOOT_H - 30;
        int alpha = age > 3600 ? (int) (0xE6 * (4200 - age) / 600) : 0xE6;
        Draw.round(ctx, x, y, w, 20, 6, Draw.argb(alpha, Ui.PANEL));
        Draw.roundOutline(ctx, x, y, w, 20, 6, Draw.argb(alpha, Ui.BORDER));
        Draw.textCenter(ctx, font, toast, x, y + 6, w, w - 16, Theme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (railOpen) {
            if (railBar.grabbed(mx, my, 1, railMax(), v -> railScroll = v)) return true;
            railOpen = false;
            railClicked(mx, my, PAD, sheetY(), sheetW(), sheetH());
            return true;
        }
        if (my < HEAD_H) return headClicked(click, doubled, mx, my);
        if (my >= height - FOOT_H) {
            int bw = footW();
            if (bw > 0 && Ui.hit(mx, my, width - PAD - bw, height - FOOT_H + 6, bw, 18)) {
                if (panel != null) {
                    if (panel.actionOn()) panel.act();
                } else if (Modules.refresh()) {
                    toast("обновляю каталог JustMC…");
                }
            }
            return true;
        }
        if (!narrow() && mx < railW()) {
            if (!railBar.grabbed(mx, my, 1, railMax(), v -> railScroll = v))
                railClicked(mx, my, 0, railTop(), railW(), railH());
            return true;
        }
        if (panel != null) return panel.click(click, doubled);
        if (bar.grabbed(mx, my, 1, maxScroll(), v -> scroll = v)) return true;
        if (centerOn && Ui.hit(mx, my, centerX, centerY, centerW, 18)) {
            Modules.refresh();
            return true;
        }
        int i = cardAt(mx, my);
        if (i >= 0) {
            Modules.Module m = view.get(i);
            int by = cardBtnY(cardY(i));
            if (m.open && grabW(m) >= 40) {
                if (grabbing.isEmpty() && Ui.hit(mx, my, grabX(cardX(i), cardW(), m), by, grabW(m),
                        ModuleArt.CARD_BTN_H)) {
                    grab(m);
                    return true;
                }
                if (roomForLook(cardW(), m) && Ui.hit(mx, my, lookX(cardX(i), cardW(), m), by,
                        ModuleArt.CARD_BTN_H, ModuleArt.CARD_BTN_H)) {
                    openLook(m, null);
                    return true;
                }
            }
            openModule(m);
            return true;
        }
        search.setFocused(false);
        return true;
    }

    private void grab(Modules.Module m) {
        if (!grabbing.isEmpty()) return;
        grabbing = m.id;
        toast("гружу «" + m.label + "»…");
        Modules.code(m.id, got -> {
            grabbing = "";
            install(m, got, false);
        }, said -> {
            grabbing = "";
            toast(said);
        });
    }

    private boolean headClicked(MouseButtonEvent click, boolean doubled, double mx, double my) {
        int bw = Ui.buttonW(font, Draw.CHEVRON_LEFT, backLabel());
        if (Ui.hit(mx, my, PAD, 8, bw, 18)) {
            if (panel != null) back();
            else onClose();
            return true;
        }
        if (panel != null && Ui.hit(mx, my, crumbX(), 10, crumbW(), 16)) {
            showList();
            return true;
        }
        if (narrow() && panel == null) {
            int at = PAD + bw + 10;
            if (Ui.hit(mx, my, at, 8, sheetButtonW(at), 18)) {
                railOpen = !railOpen;
                return true;
            }
        }
        if (Ui.hit(mx, my, searchX(), 8, searchW(), 18)) {
            search.setFocused(true);
            if (!search.mouseClicked(click, doubled)) search.onClick(click, doubled);
            grab.take(search);
            return true;
        }
        search.setFocused(false);
        return true;
    }

    private void railClicked(double mx, double my, int x, int y, int w, int h) {
        if (!Ui.hit(mx, my, x, y, w, h)) return;
        int at = y + 4 - railScroll;
        for (Rail row : rails) {
            int rh = rowH(row);
            if (!row.kind().equals("head") && Ui.hit(mx, my, x + 4, at, w - 8, RAIL_ROW - 1)) {
                chooseRail(row);
                return;
            }
            at += rh;
        }
    }

    private void chooseRail(Rail row) {
        switch (row.kind()) {
            case "sort" -> sort = Modules.Sort.valueOf(row.value());
            case "cat" -> category = row.value();
            case "flag" -> {
                if ("mine".equals(row.value())) mineOnly = !mineOnly;
                else approvedOnly = !approvedOnly;
            }
            case "act" -> {
                publishHint();
                return;
            }
            default -> { return; }
        }
        showList();
        scroll = 0;
        chosen = -1;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        bar.release();
        railBar.release();
        grab.release();
        if (panel != null) panel.release();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (bar.dragged(click.y(), 1, maxScroll(), v -> scroll = v)) return true;
        if (railBar.dragged(click.y(), 1, railMax(), v -> railScroll = v)) return true;
        if (panel != null && panel.drag(click, dx, dy)) return true;
        if (grab.drag(click, dx, dy)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (railOpen && Ui.hit(mx, my, PAD, sheetY(), sheetW(), sheetH())) {
            railScroll -= Ui.px(vAmount, 34);
            clampRail(sheetH());
            return true;
        }
        if (!narrow() && mx < railW() && my >= railTop() && my < railTop() + railH()) {
            railScroll -= Ui.px(vAmount, 34);
            clampRail(railH());
            return true;
        }
        if (panel != null && panel.wheel(mx, my, vAmount)) return true;
        if (panel == null) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - Ui.px(vAmount, 42)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent in) {
        if (in.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (railOpen) { railOpen = false; return true; }
            if (panel != null) { back(); return true; }
            onClose();
            return true;
        }
        if (panel != null && panel.key(in)) return true;
        if (in.key() == GLFW.GLFW_KEY_F && (in.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            search.setFocused(true);
            return true;
        }
        if (panel == null && !search.isFocused() && walked(in)) return true;
        if (search.isFocused() && search.keyPressed(in)) return true;
        return super.keyPressed(in);
    }

    private boolean walked(KeyEvent in) {
        if (view.isEmpty()) return false;
        int step = switch (in.key()) {
            case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_DOWN -> cols();
            case GLFW.GLFW_KEY_UP -> -cols();
            default -> 0;
        };
        if (step != 0) {
            chosen = Math.max(0, Math.min(view.size() - 1, chosen < 0 ? 0 : chosen + step));
            showChosen();
            return true;
        }
        if (chosen < 0 || chosen >= view.size()) return false;
        if (in.key() == GLFW.GLFW_KEY_ENTER || in.key() == GLFW.GLFW_KEY_KP_ENTER) {
            openModule(view.get(chosen));
            return true;
        }
        return false;
    }

    private void showChosen() {
        int top = cardY(chosen) + scroll - bodyY();
        if (top - scroll < PAD) scroll = Math.max(0, top - PAD);
        int bottom = top + CARD_H + PAD;
        if (bottom - scroll > bodyH()) scroll = Math.min(maxScroll(), bottom - bodyH());
    }

    @Override
    public boolean charTyped(CharacterEvent in) {
        if (panel != null && panel.chars(in)) return true;
        if (search.isFocused()) return search.charTyped(in);
        return super.charTyped(in);
    }

    @Override
    public void onClose() {
        if (back != null) {
            minecraft.gui.setScreen(back);
            return;
        }
        super.onClose();
    }

    private void publishHint() {
        String hint = "выдели стопки на полотне и ПКМ → «Выложить модулем JustMC…»";
        if (back instanceof EditorScreen editor) {
            minecraft.gui.setScreen(editor);
            editor.toast(hint);
            return;
        }
        toast(hint);
    }

    public void installOfficial(Modules.Module module) {
        ModuleActions.install(module, this, (ok, said) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() == this) toast(said);
            else mc.gui.hud.setOverlayMessage(Component.literal("«" + module.label + "» · " + said)
                    .withStyle(ok ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        });
    }

    public void starOfficial(Modules.Module module) {
        if (ModuleActions.busy()) {
            toast("подожди — меню /module ещё занято");
            return;
        }
        ModuleActions.star(module, this, (ok, said) -> toast(said));
    }

    public void install(Modules.Module module, JsonArray handlers, boolean toBackpack) {
        Script from = new Script();
        Importer.Result res;
        try {
            res = Importer.importInto(from, handlers.deepCopy(), font, (int) Math.round(freeX()));
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] модуль {} не разобрался", module.id, e);
            toast("модуль не разобрался — см. лог");
            return;
        }
        if (from.roots.isEmpty()) {
            toast("в модуле не оказалось кода");
            return;
        }
        if (toBackpack) {
            Backpack.Item item = Backpack.putAll(module.label, from.roots);
            toast(item == null ? "в модуле не оказалось стопок" : "«" + module.label + "» в рюкзаке · "
                    + Ui.plural(item.parts.size(), "стопка", "стопки", "стопок"));
            return;
        }
        if (back instanceof EditorScreen editor) {
            minecraft.gui.setScreen(editor);
            editor.holdModule(from.roots, module.label);
            return;
        }
        History.push(History.snapshot(script));
        script.roots.addAll(from.roots);
        script.fitOnOpen = true;
        toast("«" + module.label + "» на полотне · "
                + Ui.plural(res.blocks, "блок", "блока", "блоков") + " · Ctrl+Z вернёт");
    }

    private double freeX() {
        double right = 40;
        for (Script.Root root : script.roots) right = Math.max(right, root.x + 360);
        return right;
    }
}
