package com.xerocode.ui;

import com.google.gson.JsonObject;
import com.xerocode.Backpack;
import com.xerocode.History;
import com.xerocode.Market;
import com.xerocode.Script;
import com.xerocode.XeroCode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class MarketScreen extends Screen {
    public interface Panel {
        void place(int x, int y, int w, int h);
        void draw(DrawContext ctx, int mouseX, int mouseY, float delta);
        boolean click(Click click, boolean doubled);
        default boolean key(KeyInput in) { return false; }
        default boolean chars(CharInput in) { return false; }
        default boolean drag(Click click, double dx, double dy) { return false; }
        default boolean wheel(double mx, double my, double amount) { return false; }
        default void release() { }
        default String hint() { return ""; }
        default String title() { return ""; }
        default String action() { return ""; }
        default boolean actionOn() { return true; }
        default void act() { }
    }

    static final int HEAD_H = 34, FOOT_H = 30, PAD = 12;
    static final int RAIL_W = 150, RAIL_ROW = 17;
    private static final int CARD_MIN = MarketArt.CARD_MIN, CARD_GAP = 10;
    private static final int CARD_H = MarketArt.CARD_H;
    private static final int NARROW = 560;

    private final Script script;
    private final Screen back;

    private TextFieldWidget search;
    private String query = "";
    private String tab = Market.TAB_HOT;
    private String category = "";

    private int scroll;
    private final Ui.Bar bar = new Ui.Bar();
    private final Ui.Grab grab = new Ui.Grab();
    private int hovered = -1, chosen = -1;
    private String grabbing = "";

    private Panel panel;
    private PickerPanel picker;
    private Menu menu;

    private String toast = "";
    private long toastAt;
    private boolean railOpen;

    private long typedAt;
    private boolean typing;

    private String centerAct = "";
    private int centerX, centerY, centerW;

    private final List<Rail> rails = new ArrayList<>();
    private final List<Panel> trail = new ArrayList<>();

    private record Rail(String kind, String label, String value) {}

    public MarketScreen(Script script, Screen back) {
        super(Text.literal("Магазин модулей"));
        this.script = script;
        this.back = back;
        Market.start();
    }

    public Script script() { return script; }
    public MinecraftClient mc() { return client; }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        String typed = search == null ? query : search.getText();
        search = Ui.field(textRenderer, 0, 0, 80, 10, "найти модуль…");
        search.setMaxLength(48);
        search.setText(typed);
        search.setChangedListener(text -> {
            if (query.equals(text.trim())) return;
            query = text.trim();
            typing = true;
            typedAt = System.currentTimeMillis();
        });
        addSelectableChild(search);
        buildRails();
        placePanel();
        if (Market.LIST.items.isEmpty() && !Market.LIST.loading) reload();
    }

    private int railCats = -1;

    private void buildRails() {
        rails.clear();
        railCats = Market.categories().size();
        rails.add(new Rail("tab", "Обзор", Market.TAB_HOT));
        rails.add(new Rail("tab", "Новинки", Market.TAB_NEW));
        rails.add(new Rail("tab", "Лучшее", Market.TAB_TOP));
        if (!Market.categories().isEmpty()) {
            rails.add(new Rail("head", "РАЗДЕЛЫ", ""));
            for (String cat : Market.categories()) rails.add(new Rail("cat", cat, cat));
        }
        rails.add(new Rail("head", "МОЁ", ""));
        rails.add(new Rail("tab", "Мои модули", Market.TAB_MINE));
        rails.add(new Rail("tab", "Избранное", Market.TAB_LIKED));
        rails.add(new Rail("view", "Профиль", "profile"));
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
        if (picker != null) picker.resize(w, h);
        placePanel();
    }

    public void toast(String message) {
        toast = message;
        toastAt = System.currentTimeMillis();
    }

    public void reload() {
        scroll = 0;
        chosen = -1;
        Market.load(tab, query, category, 0, null);
    }

    private void more() {
        Market.Page page = Market.LIST;
        if (page.loading || !page.more) return;
        Market.load(tab, query, category, page.items.size(), null);
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

    public void openModule(Market.Module module) { show(new MarketPage(this, module)); }

    public void openForm(Market.Module editing) { show(new MarketForm(this, editing)); }

    public void openProfile() { show(new MarketProfile(this)); }

    public void openLook(Market.Module module, JsonObject payload) {
        show(new MarketLook(this, module, payload));
    }

    public void openPicker(PickerPanel what) { picker = what; }

    public void pickOnCanvas(MarketForm form) {
        if (!(back instanceof EditorScreen editor)) {
            toast("полотно не открыто — выкладывай из рюкзака");
            return;
        }
        picker = null;
        menu = null;
        client.setScreen(editor);
        editor.pickForModule(this, form::tookFromCanvas);
    }

    public void openMenu(Menu what) { menu = what; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Market.tick();
        if (railCats != Market.categories().size()) buildRails();
        if (typing && System.currentTimeMillis() - typedAt > 260) {
            typing = false;
            reload();
        }
        Draw.rect(ctx, 0, 0, width, height, Draw.opaque(Theme.CANVAS));
        drawHeader(ctx, mouseX, mouseY, delta);
        if (!narrow()) drawRail(ctx, mouseX, mouseY);
        if (panel == null) drawList(ctx, mouseX, mouseY);
        else panel.draw(ctx, mouseX, mouseY, delta);
        drawFooter(ctx, mouseX, mouseY);
        if (railOpen) drawRailSheet(ctx, mouseX, mouseY);
        if (menu != null) {
            ctx.createNewRootLayer();
            menu.render(ctx, textRenderer, mouseX, mouseY);
        }
        if (picker != null) {
            ctx.createNewRootLayer();
            picker.render(ctx, mouseX, mouseY, delta);
            if (picker.isClosed()) picker = null;
        }
        drawToast(ctx);
    }

    private int accountW() {
        Market.Me me = Market.me();
        String name = me == null ? "войти" : me.name;
        return 18 + 5 + Math.min(90, textRenderer.getWidth(name)) + (me != null && me.verified
                ? Draw.glyphW(Draw.CHECK) + 4 : 0) + 10;
    }

    private int accountX() { return width - PAD - accountW(); }

    private int searchW() { return Math.max(70, Math.min(230, width / 3)); }

    private int searchX() { return accountX() - 8 - searchW(); }

    private void drawHeader(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.rect(ctx, 0, 0, width, HEAD_H, Draw.opaque(Ui.HEAD));
        Draw.rect(ctx, 0, HEAD_H - 1, width, 1, Draw.opaque(Ui.LINE));
        int bw = Ui.buttonW(textRenderer, Draw.CHEVRON_LEFT, backLabel());
        Ui.glyphButton(ctx, textRenderer, mouseX, mouseY, PAD, 8, bw, 18,
                Draw.CHEVRON_LEFT, backLabel(), Ui.GHOST, true);
        int at = PAD + bw + 10;
        if (narrow() && panel == null) {
            int rw = Ui.buttonW(textRenderer, Draw.CARET_DOWN, railLabel());
            rw = Math.min(rw, Math.max(40, searchX() - at - 8));
            Ui.glyphButton(ctx, textRenderer, mouseX, mouseY, at, 8, rw, 18,
                    Draw.CARET_DOWN, railLabel(), railOpen ? Ui.ACTIVE : Ui.GHOST, true);
        } else {
            Draw.glyph(ctx, Draw.SHOP, at, 12, Theme.ACCENT);
            int tx = at + Draw.glyphW(Draw.SHOP) + 7;
            int room = searchX() - tx - 8;
            if (panel == null || panel.title().isEmpty()) {
                Draw.textFit(ctx, textRenderer, "МАГАЗИН МОДУЛЕЙ", tx, 13, room,
                        Theme.TEXT, false);
            } else {
                boolean hot = Ui.hit(mouseX, mouseY, tx, 10, crumbW(), 16);
                Draw.textFit(ctx, textRenderer, "Магазин", tx, 13, room,
                        hot ? Theme.TEXT : Theme.TEXT_FAINT, false);
                int sx = tx + crumbW() + CRUMB_GAP;
                Draw.text(ctx, textRenderer, "›", sx, 13, Theme.TEXT_FAINT, false);
                int nameX = sx + textRenderer.getWidth("›") + CRUMB_GAP;
                Draw.textFit(ctx, textRenderer, panel.title(), nameX, 13,
                        Math.max(20, room - (nameX - tx)), Theme.TEXT, false);
            }
        }
        Ui.input(ctx, searchX(), 8, searchW(), 18, search.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, searchX() + 6, 13, Theme.TEXT_FAINT);
        Ui.width(search, searchW() - 22);
        search.setX(searchX() + 16);
        search.setY(13);
        search.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, textRenderer, search);
        drawAccount(ctx, mouseX, mouseY);
    }

    private String backLabel() {
        if (width < 420) return "";
        return panel == null ? "Полотно" : "Назад";
    }

    private static final int CRUMB_GAP = 5;

    private int crumbW() { return textRenderer.getWidth("Магазин"); }

    private int crumbX() {
        return PAD + Ui.buttonW(textRenderer, Draw.CHEVRON_LEFT, backLabel()) + 10
                + Draw.glyphW(Draw.SHOP) + 7;
    }

    private String railLabel() {
        if (!category.isEmpty()) return category;
        for (Rail r : rails) if (r.kind().equals("tab") && r.value().equals(tab)) return r.label();
        return "Разделы";
    }

    private void drawAccount(DrawContext ctx, int mouseX, int mouseY) {
        int x = accountX(), w = accountW();
        boolean hot = Ui.hit(mouseX, mouseY, x, 6, w, 22);
        Market.Me me = Market.me();
        if (hot) Draw.round(ctx, x, 6, w, 22, Ui.R_SM, Draw.opaque(Ui.BTN_HOVER));
        if (me == null) {
            MarketArt.avatar(ctx, "", x + 3, 8, 18, Theme.LINE, "?", textRenderer);
            Draw.textFit(ctx, textRenderer, Market.joining() ? "вход…" : "войти",
                    x + 26, 13, w - 32, hot ? Theme.TEXT : Theme.TEXT_DIM, false);
            return;
        }
        MarketArt.avatar(ctx, me.icon, me.name, x + 3, 8, 18, Theme.ACCENT, me.name,
                textRenderer);
        int tick = me.verified ? Draw.glyphW(Draw.CHECK) + 4 : 0;
        Draw.textFit(ctx, textRenderer, me.name, x + 26, 13, w - 32 - tick,
                hot ? Theme.TEXT : Theme.TEXT_DIM, false);
        if (me.verified)
            Draw.glyph(ctx, Draw.CHECK, x + w - Draw.glyphW(Draw.CHECK) - 6, 13, Theme.OK);
    }

    private void drawRail(DrawContext ctx, int mouseX, int mouseY) {
        int w = railW(), y = bodyY(), h = bodyH() + FOOT_H;
        Draw.rect(ctx, 0, y, w, h, Draw.opaque(Ui.RAIL));
        Ui.vline(ctx, w, y, h);
        drawRailRows(ctx, mouseX, mouseY, 0, y + 4, w, h - 8);
    }

    private void drawRailRows(DrawContext ctx, int mouseX, int mouseY,
                              int x, int y, int w, int h) {
        int at = y;
        for (Rail row : rails) {
            if (row.kind().equals("gap")) {
                Ui.hairline(ctx, x + 8, at + 3, w - 16);
                at += 8;
                continue;
            }
            if (row.kind().equals("head")) {
                at += 7;
                Ui.caption(ctx, textRenderer, row.label(), x + 11, at, w - 20);
                at += 12;
                continue;
            }
            if (at + RAIL_ROW > y + h) break;
            boolean on = active(row);
            boolean hot = Ui.hit(mouseX, mouseY, x + 4, at, w - 8, RAIL_ROW - 1);
            if (on) {
                Draw.round(ctx, x + 4, at, w - 8, RAIL_ROW - 1, 3, Draw.opaque(Ui.BTN_ON));
                Draw.rect(ctx, x + 4, at + 2, 2, RAIL_ROW - 5, Draw.opaque(Theme.ACCENT));
            } else if (hot) {
                Draw.round(ctx, x + 4, at, w - 8, RAIL_ROW - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            }
            int ink = on || hot ? Theme.TEXT : Theme.TEXT_DIM;
            if (row.kind().equals("cat"))
                Draw.round(ctx, x + 10, at + 6, 5, 5, 2,
                        Draw.opaque(MarketArt.catColor(row.value())));
            Draw.textFit(ctx, textRenderer, row.label(),
                    x + (row.kind().equals("cat") ? 20 : 11), at + 5,
                    w - (row.kind().equals("cat") ? 30 : 20), ink, false);
            at += RAIL_ROW;
        }
    }

    private boolean active(Rail row) {
        return switch (row.kind()) {
            case "tab" -> panel == null && category.isEmpty() && tab.equals(row.value());
            case "cat" -> panel == null && category.equals(row.value());
            case "view" -> panel instanceof MarketProfile;
            default -> false;
        };
    }

    private int railSheetH() {
        int h = 8;
        for (Rail row : rails)
            h += switch (row.kind()) {
                case "gap" -> 8;
                case "head" -> 19;
                default -> RAIL_ROW;
            };
        return h;
    }

    private void drawRailSheet(DrawContext ctx, int mouseX, int mouseY) {
        ctx.createNewRootLayer();
        int w = Math.min(220, width - 24), h = Math.min(railSheetH(), height - HEAD_H - 20);
        int x = PAD, y = HEAD_H + 4;
        Ui.panel(ctx, x, y, w, h);
        drawRailRows(ctx, mouseX, mouseY, x, y + 4, w, h - 8);
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
        int rows = (Market.LIST.items.size() + cols() - 1) / cols();
        return PAD * 2 + Math.max(0, rows * (CARD_H + CARD_GAP) - CARD_GAP) + 18;
    }

    private int maxScroll() { return Math.max(0, contentH() - bodyH()); }

    private void drawList(DrawContext ctx, int mouseX, int mouseY) {
        int x = bodyX(), y = bodyY(), w = bodyW(), h = bodyH();
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        Market.Page page = Market.LIST;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        hovered = cardAt(mouseX, mouseY);
        if (hovered >= 0) chosen = hovered;

        centerAct = "";
        if (!Market.ready()) {
            if (Market.trouble().isEmpty()) drawSkeletons(ctx);
            else {
                int low = middle(ctx, "магазин не отвечает: " + Market.trouble(), false);
                centre(ctx, low, "retry", "Ещё раз", mouseX, mouseY);
            }
            return;
        }
        if (page.items.isEmpty()) {
            if (page.loading) drawSkeletons(ctx);
            else if (!page.trouble.isEmpty()) {
                int low = middle(ctx, page.trouble, false);
                centre(ctx, low, "retry", "Ещё раз", mouseX, mouseY);
            } else {
                int low = middle(ctx, emptyWord(), false);
                if (emptyInvites()) centre(ctx, low, "publish", publishLabel(), mouseX, mouseY);
            }
            return;
        }

        ctx.enableScissor(x, y, x + w, y + h);
        int spot = hovered >= 0 ? hovered : chosen;
        for (int i = 0; i < page.items.size(); i++) {
            int cy = cardY(i);
            if (cy > y + h || cy + CARD_H < y) continue;
            boolean hot = i == spot;
            MarketArt.card(ctx, textRenderer, page.items.get(i), cardX(i), cy, cardW(),
                    hot, !hot);
            if (hot) drawGrab(ctx, page.items.get(i), cardX(i), cy, cardW(), mouseX, mouseY);
        }
        if (page.more) {
            int by = cardY(page.items.size() - 1) + CARD_H + CARD_GAP;
            Draw.textCenter(ctx, textRenderer, page.loading ? "гружу…" : "ещё есть",
                    x, by + 2, w, w - 40, Theme.TEXT_FAINT, false);
        }
        ctx.disableScissor();
        bar.draw(ctx, x + w - 5, y + 2, h - 4, contentH(), h, scroll, mouseX, mouseY);
    }

    private String grabLabel(Market.Module m) {
        return grabbing.equals(m.id) ? "Гружу…" : "На полотно";
    }

    private int grabW(Market.Module m) {
        return Math.min(cardW() - 70, Ui.buttonW(textRenderer, grabLabel(m)) - 6);
    }

    private int cardBtnY(int cy) { return cy + CARD_H - MarketArt.CARD_INSET
            - MarketArt.CARD_BTN_H; }

    private int grabX(int cx, int cw, Market.Module m) {
        return cx + cw - MarketArt.CARD_INSET - grabW(m);
    }

    private int lookX(int cx, int cw, Market.Module m) {
        return grabX(cx, cw, m) - 6 - MarketArt.CARD_BTN_H;
    }

    private boolean roomForLook(int cw, Market.Module m) {
        return cw - MarketArt.CARD_INSET * 2 - grabW(m) - 6 - MarketArt.CARD_BTN_H >= 44;
    }

    private void drawGrab(DrawContext ctx, Market.Module m, int cx, int cy, int cw,
                          int mouseX, int mouseY) {
        int bw = grabW(m);
        if (bw < 40) return;
        int by = cardBtnY(cy);
        if (roomForLook(cw, m))
            Ui.iconButton(ctx, mouseX, mouseY, lookX(cx, cw, m), by, MarketArt.CARD_BTN_H,
                    Draw.SEARCH, Ui.GHOST, true);
        Ui.button(ctx, textRenderer, mouseX, mouseY, grabX(cx, cw, m), by, bw,
                MarketArt.CARD_BTN_H, grabLabel(m), Ui.ACCENT, grabbing.isEmpty());
    }

    private void drawSkeletons(DrawContext ctx) {
        int x = bodyX(), y = bodyY(), w = bodyW(), h = bodyH();
        ctx.enableScissor(x, y, x + w, y + h);
        int rows = Math.max(1, (h - PAD) / (CARD_H + CARD_GAP) + 1);
        for (int i = 0; i < Math.min(12, rows * cols()); i++)
            MarketArt.skeleton(ctx, cardX(i), bodyY() + PAD + (i / cols()) * (CARD_H + CARD_GAP),
                    cardW(), i);
        ctx.disableScissor();
    }

    private void centre(DrawContext ctx, int at, String what, String label,
                        int mouseX, int mouseY) {
        centerAct = what;
        centerW = Ui.buttonW(textRenderer, Draw.PLUS, label);
        centerX = bodyX() + (bodyW() - centerW) / 2;
        centerY = at + 8;
        Ui.glyphButton(ctx, textRenderer, mouseX, mouseY, centerX, centerY, centerW, 18,
                what.equals("retry") ? Draw.RESET : Draw.PLUS, label, Ui.ACCENT, true);
    }

    private boolean emptyInvites() {
        return query.isEmpty() && !tab.equals(Market.TAB_LIKED);
    }

    private String emptyWord() {
        if (!query.isEmpty()) return "ничего не нашлось по запросу «" + query + "»";
        if (tab.equals(Market.TAB_MINE)) return "ты ещё ничего не выкладывал";
        if (tab.equals(Market.TAB_LIKED)) return "тут будут отмеченные модули";
        if (!category.isEmpty()) return "в разделе «" + category + "» пока пусто";
        return "в магазине пока пусто — будь первым";
    }

    private int middle(DrawContext ctx, String said, boolean quiet) {
        int x = bodyX(), y = bodyY() + bodyH() / 2 - 30, w = bodyW();
        Draw.glyph(ctx, Draw.SHOP, x + (w - Draw.glyphW(Draw.SHOP)) / 2, y, Theme.LINE);
        int at = y + 20;
        for (String line : Ui.wrap(textRenderer, said, Math.min(360, w - 40), 3)) {
            Draw.textCenter(ctx, textRenderer, line, x, at, w, w - 40,
                    quiet ? Theme.TEXT_FAINT : Theme.TEXT_DIM, false);
            at += 11;
        }
        return at;
    }

    private int cardAt(double mx, double my) {
        if (panel != null || mx < bodyX() || my < bodyY() || my >= bodyY() + bodyH()) return -1;
        List<Market.Module> items = Market.LIST.items;
        for (int i = 0; i < items.size(); i++)
            if (Ui.hit(mx, my, cardX(i), cardY(i), cardW(), CARD_H)) return i;
        return -1;
    }

    private String publishLabel() { return width < 480 ? "Выложить" : "Выложить модуль"; }

    private int publishW() {
        if (panel != null)
            return panel.action().isEmpty() ? 0
                    : Math.max(96, Ui.buttonW(textRenderer, panel.action()));
        return Ui.buttonW(textRenderer, Draw.PLUS, publishLabel());
    }

    private void drawFooter(DrawContext ctx, int mouseX, int mouseY) {
        int y = height - FOOT_H;
        Draw.rect(ctx, 0, y, width, FOOT_H, Draw.opaque(Ui.HEAD));
        Draw.rect(ctx, 0, y, width, 1, Draw.opaque(Ui.LINE));
        int bw = publishW();
        if (panel == null)
            Ui.glyphButton(ctx, textRenderer, mouseX, mouseY, width - PAD - bw, y + 6, bw, 18,
                    Draw.PLUS, publishLabel(), Ui.ACCENT, Market.me() != null);
        else if (!panel.action().isEmpty())
            Ui.button(ctx, textRenderer, mouseX, mouseY, width - PAD - bw, y + 6, bw, 18,
                    panel.action(), Ui.ACCENT, panel.actionOn());
        String said = panel != null ? panel.hint() : listHint();
        Draw.textFit(ctx, textRenderer, said, PAD, y + 11, width - PAD * 2 - bw - 14,
                Theme.TEXT_FAINT, false);
    }

    private String listHint() {
        if (!Market.joinNote().isEmpty()) return Market.joinNote();
        Market.Page page = Market.LIST;
        if (page.loading && page.items.isEmpty()) return "гружу…";
        if (page.total > 0) return Ui.plural(page.total, "модуль", "модуля", "модулей");
        return "в магазине " + Ui.plural(Market.publicModules(), "модуль", "модуля", "модулей");
    }

    private void drawToast(DrawContext ctx) {
        if (toast.isEmpty()) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 4200) { toast = ""; return; }
        ctx.createNewRootLayer();
        int w = Math.min(width - 40, textRenderer.getWidth(toast) + 26);
        int x = (width - w) / 2, y = height - FOOT_H - 30;
        int alpha = age > 3600 ? (int) (0xE6 * (4200 - age) / 600) : 0xE6;
        Draw.round(ctx, x, y, w, 20, 6, Draw.argb(alpha, Ui.PANEL));
        Draw.roundOutline(ctx, x, y, w, 20, 6, Draw.argb(alpha, Ui.BORDER));
        Draw.textCenter(ctx, textRenderer, toast, x, y + 6, w, w - 16, Theme.TEXT, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (picker != null) {
            picker.mouseClicked(click, doubled);
            if (picker.isClosed()) picker = null;
            return true;
        }
        if (menu != null) {
            menu.mouseClicked(mx, my);
            if (menu.isClosed()) menu = null;
            return true;
        }
        if (railOpen) {
            railOpen = false;
            if (railClicked(mx, my, PAD, HEAD_H + 8, Math.min(220, width - 24))) return true;
        }
        if (my < HEAD_H) return headClicked(click, doubled, mx, my);
        if (my >= height - FOOT_H) {
            int bw = publishW();
            if (bw > 0 && Ui.hit(mx, my, width - PAD - bw, height - FOOT_H + 6, bw, 18)) {
                if (panel == null) startPublish();
                else if (panel.actionOn()) panel.act();
                return true;
            }
            return true;
        }
        if (!narrow() && mx < railW()) return railClicked(mx, my, 0, bodyY() + 4, railW());
        if (panel != null) return panel.click(click, doubled);
        if (bar.grabbed(mx, my, 1, maxScroll(), v -> scroll = v)) return true;
        if (!centerAct.isEmpty() && Ui.hit(mx, my, centerX, centerY, centerW, 18)) {
            if (centerAct.equals("publish")) startPublish();
            else {
                Market.start();
                reload();
            }
            return true;
        }
        int i = cardAt(mx, my);
        if (i >= 0) {
            Market.Module m = Market.LIST.items.get(i);
            int bw = grabW(m), by = cardBtnY(cardY(i));
            if (bw >= 40) {
                if (grabbing.isEmpty() && Ui.hit(mx, my, grabX(cardX(i), cardW(), m), by, bw,
                        MarketArt.CARD_BTN_H)) {
                    grab(m);
                    return true;
                }
                if (roomForLook(cardW(), m) && Ui.hit(mx, my, lookX(cardX(i), cardW(), m), by,
                        MarketArt.CARD_BTN_H, MarketArt.CARD_BTN_H)) {
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

    private void grab(Market.Module m) {
        if (!grabbing.isEmpty()) return;
        grabbing = m.id;
        toast("гружу «" + m.name + "»…");
        Market.payload(m.id, got -> {
            grabbing = "";
            install(m, got, false);
        }, (said, code) -> {
            grabbing = "";
            toast(said);
        });
    }

    private boolean headClicked(Click click, boolean doubled, double mx, double my) {
        int bw = Ui.buttonW(textRenderer, Draw.CHEVRON_LEFT, backLabel());
        if (Ui.hit(mx, my, PAD, 8, bw, 18)) {
            if (panel != null) back();
            else close();
            return true;
        }
        if (panel != null && Ui.hit(mx, my, crumbX(), 10, crumbW(), 16)) {
            showList();
            return true;
        }
        if (narrow() && panel == null) {
            int at = PAD + bw + 10;
            int rw = Math.min(Ui.buttonW(textRenderer, Draw.CARET_DOWN, railLabel()),
                    Math.max(40, searchX() - at - 8));
            if (Ui.hit(mx, my, at, 8, rw, 18)) { railOpen = !railOpen; return true; }
        }
        if (Ui.hit(mx, my, searchX(), 8, searchW(), 18)) {
            search.setFocused(true);
            if (!search.mouseClicked(click, doubled)) search.onClick(click, doubled);
            grab.take(search);
            return true;
        }
        if (Ui.hit(mx, my, accountX(), 6, accountW(), 22)) {
            if (Market.me() == null) Market.hello();
            else openProfile();
            return true;
        }
        search.setFocused(false);
        return true;
    }

    private boolean railClicked(double mx, double my, int x, int y, int w) {
        int at = y;
        for (Rail row : rails) {
            if (row.kind().equals("gap")) { at += 8; continue; }
            if (row.kind().equals("head")) { at += 19; continue; }
            if (Ui.hit(mx, my, x + 4, at, w - 8, RAIL_ROW - 1)) {
                chooseRail(row);
                return true;
            }
            at += RAIL_ROW;
        }
        return false;
    }

    private void chooseRail(Rail row) {
        switch (row.kind()) {
            case "tab" -> {
                if ((row.value().equals(Market.TAB_MINE) || row.value().equals(Market.TAB_LIKED))
                        && Market.me() == null) {
                    Market.hello();
                    toast("сначала заведём аккаунт — это одно нажатие");
                    return;
                }
                showList();
                tab = row.value();
                category = "";
                reload();
            }
            case "cat" -> {
                showList();
                category = category.equals(row.value()) ? "" : row.value();
                if (tab.equals(Market.TAB_MINE) || tab.equals(Market.TAB_LIKED))
                    tab = Market.TAB_HOT;
                reload();
            }
            case "view" -> {
                if (Market.me() == null) {
                    Market.hello();
                    toast("завожу аккаунт…");
                    return;
                }
                openProfile();
            }
            default -> { }
        }
    }

    private void startPublish() {
        if (Market.me() == null) {
            Market.hello();
            toast("сначала заведём аккаунт — это одно нажатие");
            return;
        }
        openForm(null);
    }

    @Override
    public boolean mouseReleased(Click click) {
        bar.release();
        grab.release();
        if (menu != null) menu.mouseReleased();
        if (picker != null) picker.mouseReleased();
        if (panel != null) panel.release();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (picker != null) return picker.mouseDragged(click, dx, dy);
        if (menu != null && menu.mouseDragged(click.y())) return true;
        if (bar.dragged(click.y(), 1, maxScroll(), v -> scroll = v)) return true;
        if (panel != null && panel.drag(click, dx, dy)) return true;
        if (grab.drag(click, dx, dy)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (picker != null) return picker.mouseScrolled(mx, my, vAmount);
        if (menu != null && menu.mouseScrolled(mx, my, vAmount)) return true;
        if (panel != null && panel.wheel(mx, my, vAmount)) return true;
        if (panel == null) {
            scroll = Math.max(0, Math.min(maxScroll(),
                    scroll - (int) Math.signum(vAmount) * 42));
            if (scroll >= maxScroll() - 4) more();
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyInput in) {
        if (picker != null) {
            picker.keyPressed(in);
            if (picker.isClosed()) picker = null;
            return true;
        }
        if (menu != null) {
            if (in.key() == GLFW.GLFW_KEY_ESCAPE) menu = null;
            return true;
        }
        if (in.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (railOpen) { railOpen = false; return true; }
            if (panel != null) { back(); return true; }
            close();
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

    private boolean walked(KeyInput in) {
        List<Market.Module> items = Market.LIST.items;
        if (items.isEmpty()) return false;
        int step = switch (in.key()) {
            case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_DOWN -> cols();
            case GLFW.GLFW_KEY_UP -> -cols();
            default -> 0;
        };
        if (step != 0) {
            chosen = Math.max(0, Math.min(items.size() - 1, chosen < 0 ? 0 : chosen + step));
            showChosen();
            return true;
        }
        if (chosen < 0 || chosen >= items.size()) return false;
        if (in.key() == GLFW.GLFW_KEY_ENTER || in.key() == GLFW.GLFW_KEY_KP_ENTER) {
            openModule(items.get(chosen));
            return true;
        }
        return false;
    }

    private void showChosen() {
        int top = cardY(chosen) + scroll - bodyY();
        if (top - scroll < PAD) scroll = Math.max(0, top - PAD);
        int bottom = top + CARD_H + PAD;
        if (bottom - scroll > bodyH()) scroll = Math.min(maxScroll(), bottom - bodyH());
        if (chosen >= Market.LIST.items.size() - cols()) more();
    }

    @Override
    public boolean charTyped(CharInput in) {
        if (picker != null) return picker.charTyped(in);
        if (panel != null && panel.chars(in)) return true;
        if (search.isFocused()) return search.charTyped(in);
        return super.charTyped(in);
    }

    @Override
    public void close() {
        if (back != null) {
            client.setScreen(back);
            return;
        }
        super.close();
    }

    public void install(Market.Module module, JsonObject payload, boolean toBackpack) {
        Script from;
        try {
            from = Script.fromJson(payload);
        } catch (Throwable e) {
            XeroCode.LOG.error("[xerocode] модуль не разобрался", e);
            toast("модуль не разобрался — см. лог");
            return;
        }
        if (from.roots.isEmpty()) {
            toast("в модуле не оказалось кода");
            return;
        }
        if (toBackpack) {
            int n = 0;
            for (Script.Root root : from.roots) {
                if (root.chain.isEmpty()) continue;
                String name = from.roots.size() == 1 ? module.name
                        : module.name + " · " + (n + 1);
                if (Backpack.put(name, root.chain) != null) n++;
            }
            toast(n == 0 ? "в модуле не оказалось стопок"
                    : "в рюкзак уехало " + Ui.plural(n, "кусок", "куска", "кусков"));
            return;
        }
        History.push(History.snapshot(script));
        double dx = freeX(), dy = 40;
        int blocks = 0;
        for (Script.Root root : from.roots) {
            Script.Root made = new Script.Root(root.x + dx, root.y + dy);
            made.chain.addAll(root.chain);
            script.roots.add(made);
            blocks += Script.blocks(root.chain);
        }
        script.fitOnOpen = true;
        toast("«" + module.name + "» на полотне · "
                + Ui.plural(blocks, "блок", "блока", "блоков") + " · Ctrl+Z вернёт");
        if (back != null) client.setScreen(back);
    }

    private double freeX() {
        double right = 40;
        for (Script.Root root : script.roots) right = Math.max(right, root.x + 360);
        return right;
    }
}
