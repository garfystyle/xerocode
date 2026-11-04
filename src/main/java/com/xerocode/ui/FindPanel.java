package com.xerocode.ui;

import com.xerocode.Finder;
import com.xerocode.Functions;
import com.xerocode.Match;
import com.xerocode.Script;
import com.xerocode.Settings;
import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class FindPanel {
    public interface Jump { void to(Finder.Hit hit); }

    public static final int WANT_W = 246, MIN_W = 168;
    private static final int HEAD_H = 25, INPUT_H = 20, STAT_H = 17, ROW_H = 27, FOOT_H = 18;
    private static final int PAD = 8, ICON = 12, TEXT_X = 26, STEP_W = 15;
    private static final int TOGGLE_W = 15, TOGGLE_H = 14, CLEAR_W = 14;
    private static final int CHIP_H = 14, CHIP_GAP = 3, CHIP_PAD = 12, DOT_W = 7, GROUP_GAP = 4;

    private static final int CODE = 0, VARIABLES = 1;
    private static final int G_MODE = 0, G_SCOPE = 1, G_WHERE = 2, G_ACCESS = 3;
    private static final String[] GROUPS = {"ИСКАТЬ", "ОБЛАСТЬ", "ИМЯ", "ДОСТУП"};
    private static final String[] MODES = {"Код", "Переменные"};
    private static final String[] WHERE = {"Содержит", "Начинается", "Кончается", "Равно"};
    private static final int[] WHERE_IDS = {Match.ANYWHERE, Match.START, Match.END, Match.WHOLE};
    private static final String[] ACCESS = {"Всё", "Запись", "Чтение"};
    private static final String ALL_SCOPES = "Все";
    private static final String[] TOGGLE_TEXT = {"Аа", "аб", ".*"};
    private static final String[] TOGGLE_TIP = {"Учитывать регистр", "Слово целиком", "Регулярное выражение"};
    private static final String[] TOGGLE_KEY = {"Alt+C", "Alt+W", "Alt+R"};
    private static final int[] TOGGLE_GLFW = {GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_R};
    private static final String HINT_CODE = "имя блока, текст, переменная…", HINT_VARS = "имя переменной…";

    private static final boolean[] toggles = new boolean[3];
    private static int mode = CODE, scopePick, wherePick, accessPick;

    private final Font tr;
    private final Script script;
    private final Jump jump;

    private int screenW, screenH, x, y, w, h;
    private EditBox field;
    private final Ui.Pane pane = new Ui.Pane();
    private final Ui.Bar bar = new Ui.Bar();
    private final Ui.Grab grab = new Ui.Grab();

    private final List<int[]> chips = new ArrayList<>();
    private final List<String> chipText = new ArrayList<>();
    private final int[] groupY = new int[GROUPS.length];
    private int labelW, statusY;

    private List<Finder.Hit> hits = List.of();
    private Supplier<List<Finder.Hit>> fixed;
    private String fixedName = "";
    private boolean outline = true;
    private String error;
    private int stamp = Integer.MIN_VALUE;
    private List<Finder.Hit> pool;
    private int poolStamp;
    private String typed = "", query = "";
    private int sel = -1, hover = -1;
    private boolean closed;

    public FindPanel(Font tr, Script script, int screenW, int screenH, Jump jump) {
        this.tr = tr;
        this.script = script;
        this.jump = jump;
        resize(screenW, screenH);
    }

    public void resize(int screenW, int screenH) {
        int room = Math.max(1, screenW - (Settings.chests() ? 0 : Theme.PALETTE_W));
        int want = Math.min(WANT_W, Math.max(MIN_W, room));
        if (screenW == this.screenW && screenH == this.screenH && want == w && field != null) return;
        this.screenW = screenW;
        this.screenH = screenH;
        this.w = Math.min(want, room);
        this.x = screenW - w;
        this.y = Theme.topH();
        String kept = field == null ? "" : field.getValue();
        int fx = x + PAD + 16;
        field = Ui.field(tr, fx, y + HEAD_H + 5 + (INPUT_H - 10) / 2, Math.max(8, clearX() - 2 - fx), 10,
                hint());
        field.setMaxLength(64);
        field.setValue(kept);
        field.setResponder(this::retype);
        field.setFocused(true);
        place();
        rebuild();
    }

    private String hint() { return mode == VARIABLES ? HINT_VARS : HINT_CODE; }

    private void place() {
        chips.clear();
        chipText.clear();
        labelW = 0;
        for (String g : GROUPS) labelW = Math.max(labelW, tr.width(g));
        labelW += 6;
        int top = flow(G_MODE, MODES, HEAD_H + 5 + INPUT_H + 6);
        if (mode == VARIABLES) {
            List<String> scopes = new ArrayList<>();
            scopes.add(ALL_SCOPES);
            for (Values.Scope s : Values.SCOPES) scopes.add(s.name());
            top = flow(G_SCOPE, scopes.toArray(new String[0]), top + GROUP_GAP);
            top = flow(G_WHERE, WHERE, top + GROUP_GAP);
            top = flow(G_ACCESS, ACCESS, top + GROUP_GAP);
        }
        statusY = top + 5;
        this.h = Math.max(statusY + STAT_H + ROW_H, screenH - y);
    }

    private int flow(int group, String[] labels, int top) {
        int left = x + PAD + labelW, right = x + w - PAD;
        int room = Math.max(10, right - left);
        int cx = left, cy = top;
        groupY[group] = top;
        for (int i = 0; i < labels.length; i++) {
            int cw = Math.min(room, tr.width(labels[i]) + CHIP_PAD + (dotted(group, i) ? DOT_W : 0));
            if (cx > left && cx + cw > right) {
                cx = left;
                cy += CHIP_H + CHIP_GAP;
            }
            chips.add(new int[]{group, i, cx, cy, cw});
            chipText.add(labels[i]);
            cx += cw + CHIP_GAP;
        }
        return cy + CHIP_H;
    }

    private static boolean dotted(int group, int index) { return group == G_SCOPE && index > 0; }

    public int x() { return x; }
    public int width() { return w; }
    public boolean isClosed() { return closed; }
    public void close() { closed = true; }
    public List<Finder.Hit> hits() { return hits; }
    public boolean listing() { return outline; }

    public Finder.Hit current() {
        return sel >= 0 && sel < hits.size() ? hits.get(sel) : null;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void refocus() {
        if (field != null) field.setFocused(true);
    }

    public void blur() {
        if (field != null) field.setFocused(false);
    }

    public boolean focused() { return field != null && field.isFocused(); }

    public String highlighted() { return field == null ? "" : field.getHighlighted(); }

    public void setQuery(String q) {
        if (field == null) return;
        field.setValue(q == null ? "" : q);
        field.moveCursorToEnd(false);
        if (!hits.isEmpty()) select(0, true);
    }

    public void showUsages(String name, Supplier<List<Finder.Hit>> source, Script.Node at) {
        if (field == null) return;
        field.setValue("");
        fixed = source;
        fixedName = name;
        pane.scroll = 0;
        rebuild();
        for (int i = 0; i < hits.size(); i++)
            if (hits.get(i).node == at) { select(i, true); return; }
        if (!hits.isEmpty()) select(0, true);
    }

    public boolean usages() { return fixed != null; }

    public void showVariable(String name, String scope) {
        mode = VARIABLES;
        scopePick = 0;
        for (int i = 0; i < Values.SCOPES.size(); i++)
            if (Values.SCOPES.get(i).id().equals(scope)) scopePick = i + 1;
        wherePick = indexOf(WHERE_IDS, Match.WHOLE);
        accessPick = 0;
        restyle();
        setQuery(name);
    }

    private static int indexOf(int[] ids, int id) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
        return 0;
    }

    private void restyle() {
        field.setMessage(Component.literal(hint()));
        place();
        rebuild();
        pane.scroll = 0;
    }

    private void retype(String text) {
        fixed = null;
        typed = text == null ? "" : text;
        query = typed.trim();
        rebuild();
        pane.scroll = 0;
        sel = -1;
    }

    public void sync(int scriptStamp) {
        if (scriptStamp == stamp) return;
        stamp = scriptStamp;
        rebuild();
    }

    private boolean precise() { return toggles[0] || toggles[1] || toggles[2]; }

    private List<Finder.Hit> pool() {
        if (pool == null || poolStamp != stamp) {
            pool = Finder.all(script);
            poolStamp = stamp;
        }
        return pool;
    }

    private void rebuild() {
        Script.Node kept = current() == null ? null : current().node;
        Match match = Match.of(precise() ? typed : query, toggles[0], toggles[1], toggles[2]);
        error = match.error;
        if (fixed != null) {
            outline = false;
            error = null;
            hits = fixed.get();
        } else if (mode == VARIABLES) {
            outline = false;
            String scope = scopePick <= 0 || scopePick > Values.SCOPES.size() ? null
                    : Values.SCOPES.get(scopePick - 1).id();
            int access = accessPick == 1 ? Finder.WRITES : accessPick == 2 ? Finder.READS : Finder.ANY;
            hits = Finder.variables(script, match, scope, WHERE_IDS[wherePick], access);
        } else {
            outline = query.isEmpty();
            hits = outline ? Finder.outline(script)
                    : precise() ? Finder.exact(script, match) : Finder.search(pool(), query);
        }
        sel = -1;
        if (kept != null)
            for (int i = 0; i < hits.size(); i++)
                if (hits.get(i).node == kept) { sel = i; break; }
    }

    private int listTop() { return statusY + STAT_H + 1; }
    private int listBottom() { return h - FOOT_H; }
    private int rows() { return Math.max(1, (listBottom() - listTop()) / ROW_H); }

    private void select(int index, boolean go) {
        if (hits.isEmpty()) { sel = -1; return; }
        sel = Math.max(0, Math.min(hits.size() - 1, index));
        show(sel);
        if (go) jump.to(hits.get(sel));
    }

    private void show(int index) {
        int top = index * ROW_H, bottom = top + ROW_H;
        int viewH = listBottom() - listTop();
        if (pane.scroll > top) pane.scroll = top;
        else if (pane.scroll + viewH < bottom) pane.scroll = bottom - viewH;
        pane.clamp();
    }

    public void step(int delta) {
        if (hits.isEmpty()) return;
        int from = sel < 0 ? (delta > 0 ? -1 : hits.size()) : sel;
        int next = from + delta;
        if (next < 0) next = hits.size() - 1;
        if (next >= hits.size()) next = 0;
        select(next, true);
    }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Draw.hgrad(ctx, x - 5, y, 5, h, Draw.argb(0, 0x000000), Theme.SHADOW);
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.PANEL));
        Draw.rect(ctx, x, y, 1, h, Draw.opaque(Theme.LINE));

        Draw.rect(ctx, x + 1, y, w - 1, HEAD_H, Draw.opaque(Ui.HEAD));
        Draw.glyph(ctx, Draw.SEARCH, x + PAD, y + (HEAD_H - Draw.glyphH(Draw.SEARCH)) / 2,
                Theme.ACCENT);
        Draw.textFit(ctx, tr, fixed != null ? "ИСПОЛЬЗОВАНИЯ «" + fixedName + "»" : "ПОИСК ПО КОДУ",
                x + PAD + 11, y + (HEAD_H - Ui.TEXT_H) / 2,
                w - PAD * 2 - 11 - 18, Theme.TEXT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, y + (HEAD_H - 14) / 2, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 1);

        int iy = y + HEAD_H + 5;
        Ui.input(ctx, x + PAD, iy, w - PAD * 2, INPUT_H, field.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, x + PAD + 6, iy + (INPUT_H - Draw.glyphH(Draw.SEARCH)) / 2,
                typed.isEmpty() ? Theme.TEXT_FAINT : Theme.ACCENT);
        field.extractRenderState(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, field);
        if (!typed.isEmpty()) {
            boolean hov = Ui.hit(mouseX, mouseY, clearX(), iy, CLEAR_W, INPUT_H);
            Draw.glyph(ctx, Draw.CROSS, clearX() + 4,
                    iy + (INPUT_H - Draw.glyphH(Draw.CROSS)) / 2,
                    hov ? Theme.TEXT : Theme.TEXT_FAINT);
        }
        for (int i = 0; i < TOGGLE_TEXT.length; i++) drawToggle(ctx, i, mouseX, mouseY);

        drawGroups(ctx, mouseX, mouseY);
        drawStatus(ctx, mouseX, mouseY);
        drawList(ctx, mouseX, mouseY);
        drawFoot(ctx);
    }

    private int toggleX(int i) { return x + w - PAD - 3 - (TOGGLE_TEXT.length - i) * TOGGLE_W; }

    private int toggleY() { return y + HEAD_H + 5 + (INPUT_H - TOGGLE_H) / 2; }

    private int toggleAt(double mx, double my) {
        for (int i = 0; i < TOGGLE_TEXT.length; i++)
            if (Ui.hit(mx, my, toggleX(i), toggleY(), TOGGLE_W - 1, TOGGLE_H)) return i;
        return -1;
    }

    private void drawToggle(GuiGraphicsExtractor ctx, int i, int mouseX, int mouseY) {
        int bx = toggleX(i), by = toggleY(), bw = TOGGLE_W - 1;
        boolean on = toggles[i], hov = toggleAt(mouseX, mouseY) == i;
        if (on) {
            Draw.round(ctx, bx, by, bw, TOGGLE_H, Ui.R_SM,
                    Draw.opaque(Draw.mix(Ui.BTN_ON, Theme.ACCENT, 0.25f)));
            Draw.roundOutline(ctx, bx, by, bw, TOGGLE_H, Ui.R_SM, Draw.opaque(Theme.ACCENT));
        } else if (hov) {
            Draw.round(ctx, bx, by, bw, TOGGLE_H, Ui.R_SM, Draw.opaque(Theme.SURFACE_HOVER));
        }
        int ink = on ? Theme.TEXT : hov ? Theme.TEXT : Theme.TEXT_FAINT;
        String s = TOGGLE_TEXT[i];
        int tw = tr.width(s);
        int tx = bx + (bw - tw + 1) / 2, ty = by + (i == 1 ? 2 : 3);
        Draw.text(ctx, tr, s, tx, ty, ink, false);
        if (i == 1) {
            int ux = bx + 2, uw = bw - 4, uy = by + TOGGLE_H - 3;
            Draw.rect(ctx, ux, uy, uw, 1, Draw.opaque(ink));
            Draw.rect(ctx, ux, uy - 1, 1, 1, Draw.opaque(ink));
            Draw.rect(ctx, ux + uw - 1, uy - 1, 1, 1, Draw.opaque(ink));
        }
    }

    private int clearX() { return toggleX(0) - CLEAR_W - 1; }

    private int stepX(int i) { return x + w - PAD - STEP_W * (2 - i) - (i == 0 ? 3 : 0); }

    private boolean picked(int group, int index) {
        return switch (group) {
            case G_MODE -> fixed == null && mode == index;
            case G_SCOPE -> scopePick == index;
            case G_WHERE -> wherePick == index;
            default -> accessPick == index;
        };
    }

    private int chipAt(double mx, double my) {
        for (int k = 0; k < chips.size(); k++) {
            int[] c = chips.get(k);
            if (Ui.hit(mx, my, c[2], y + c[3], c[4], CHIP_H)) return k;
        }
        return -1;
    }

    private void drawGroups(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int hov = chipAt(mouseX, mouseY);
        int groups = mode == VARIABLES ? GROUPS.length : 1;
        for (int g = 0; g < groups; g++)
            Ui.caption(ctx, tr, GROUPS[g], x + PAD, y + groupY[g] + (CHIP_H - Ui.TEXT_H) / 2 + 1, labelW - 4);
        for (int k = 0; k < chips.size(); k++) {
            int[] c = chips.get(k);
            int group = c[0], index = c[1], cx = c[2], cy = y + c[3], cw = c[4];
            boolean on = picked(group, index), over = k == hov;
            if (!dotted(group, index)) {
                Ui.chip(ctx, tr, cx, cy, cw, CHIP_H, chipText.get(k), on, over, Theme.ACCENT);
                continue;
            }
            int color = Values.SCOPES.get(index - 1).color();
            Ui.chip(ctx, tr, cx, cy, cw, CHIP_H, "", on, over, color);
            String s = Draw.fit(tr, chipText.get(k), cw - CHIP_PAD - DOT_W + 6);
            int tx = cx + (cw - tr.width(s) - DOT_W) / 2;
            Draw.dot(ctx, tx, cy + (CHIP_H - 4) / 2, Draw.opaque(color));
            Draw.text(ctx, tr, s, tx + DOT_W, cy + (CHIP_H - Ui.TEXT_H) / 2,
                    on ? (Settings.outlined() ? Theme.TEXT : Theme.ON_ACCENT)
                            : over ? Theme.TEXT : Theme.TEXT_DIM, false);
        }
    }

    private void drawStatus(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int sy = y + statusY;
        String note = error != null ? error
                : outline ? Ui.plural(hits.size(), "строка", "строки", "строк") + " кода"
                : hits.isEmpty() ? "ничего не нашлось"
                : sel < 0 ? counted()
                : (sel + 1) + " из " + hits.size();
        int room = w - PAD * 2 - (hits.isEmpty() ? 0 : STEP_W * 2 + 6);
        int ink = error != null ? Theme.DANGER
                : hits.isEmpty() && !outline ? Theme.TEXT_FAINT : Theme.TEXT_DIM;
        Ui.hairline(ctx, x + 1, sy - 3, w - 1);
        Draw.textFit(ctx, tr, note, x + PAD, sy + (STAT_H - Ui.TEXT_H) / 2, room, ink, false);
        if (!hits.isEmpty()) {
            for (int i = 0; i < 2; i++) {
                String[] glyph = i == 0 ? Draw.CARET_UP : Draw.CARET_DOWN;
                int bx = stepX(i);
                boolean hov = Ui.hit(mouseX, mouseY, bx, sy, STEP_W, STAT_H);
                Draw.round(ctx, bx, sy, STEP_W, STAT_H - 1, Ui.R_SM,
                        Draw.opaque(hov ? Theme.SURFACE_HOVER : Theme.SURFACE));
                Draw.glyph(ctx, glyph, bx + (STEP_W - Draw.glyphW(glyph)) / 2,
                        sy + (STAT_H - 1 - Draw.glyphH(glyph)) / 2, hov ? Theme.TEXT : Theme.TEXT_DIM);
            }
        }
        Ui.hairline(ctx, x + 1, sy + STAT_H, w - 1);
    }

    private void drawList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int top = y + listTop(), bottom = y + listBottom();
        pane.fit(listTop(), listBottom(), listTop() + hits.size() * ROW_H);
        hover = indexAt(mouseX, mouseY);
        if (hits.isEmpty()) {
            drawEmpty(ctx, top, bottom);
            return;
        }
        ScreenRectangle area = new ScreenRectangle(x + 1, top, w - 1, Math.max(0, bottom - top));
        ctx.enableScissor(x + 1, top, x + w, bottom);
        Draw.batch(Batch.open(ctx, area, area, 512));
        for (int i = 0; i < hits.size(); i++) {
            int ry = y + pane.at(listTop() + i * ROW_H);
            if (ry + ROW_H < top || ry > bottom) continue;
            drawRow(ctx, hits.get(i), ry, i == hover, i == sel);
        }
        Draw.batch(null);
        ctx.disableScissor();
        pane.drawBar(ctx, bar, x + w - 5, y, mouseX, mouseY);
    }

    private void drawEmpty(GuiGraphicsExtractor ctx, int top, int bottom) {
        if (bottom - top < 20) return;
        String said = error != null ? error : outline ? "полотно пусто" : "ничего не нашлось";
        Draw.textCenter(ctx, tr, said, x + PAD, top + (bottom - top) / 2 - 6,
                w - PAD * 2, w - PAD * 2, Theme.TEXT_DIM, false);
    }

    private int rowX() { return x + 4; }
    private int rowW() { return w - 9; }
    private int titleRoom(Finder.Hit hit) {
        return rowX() + rowW() - 10 - tr.width(String.valueOf(hit.line)) - rowX() - TEXT_X;
    }
    private int secondRoom() { return rowW() - 8 - TEXT_X; }

    private void drawRow(GuiGraphicsExtractor ctx, Finder.Hit hit, int ry, boolean hov, boolean cur) {
        int rx = rowX(), rw = rowW();
        if (cur) Draw.round(ctx, rx, ry + 1, rw, ROW_H - 2, Ui.R_SM,
                Draw.opaque(Draw.mix(Ui.PANEL, Theme.ACCENT, 0.24f)));
        else if (hov) Draw.round(ctx, rx, ry + 1, rw, ROW_H - 2, Ui.R_SM,
                Draw.opaque(Theme.SURFACE));
        Draw.round(ctx, rx + 3, ry + 5, 2, ROW_H - 11, 1, Draw.opaque(hit.color()));

        String num = String.valueOf(hit.line);
        int numW = tr.width(num);
        Draw.text(ctx, tr, num, rx + rw - 6 - numW, ry + 5,
                cur ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);

        Draw.item(ctx, iconOf(hit.node), rx + 8, ry + 4, ICON);
        int tx = rx + TEXT_X;
        Draw.textFit(ctx, tr, indent(hit) + hit.title, tx, ry + 5, titleRoom(hit), Theme.TEXT, false);
        Draw.textFit(ctx, tr, second(hit), tx, ry + 15, secondRoom(),
                cur ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
    }

    private static String indent(Finder.Hit hit) {
        return hit.depth <= 0 ? "" : "· ".repeat(Math.min(4, hit.depth));
    }

    private String second(Finder.Hit hit) {
        if (outline) {
            String kind = hit.kind();
            String count = Ui.plural(hit.blocks, "блок", "блока", "блоков");
            return kind.isEmpty() ? count : kind + "  ·  " + count;
        }
        if (!hit.detail.isEmpty()) return hit.detail;
        if (!hit.path.isEmpty()) return hit.path;
        return hit.kind();
    }

    private static ItemStack iconOf(Script.Node node) {
        if (node.declares()) {
            Value own = Functions.iconOf(node);
            if (own != null) return Stacks.preview(own);
        }
        return node.action.icon();
    }

    private void drawFoot(GuiGraphicsExtractor ctx) {
        int fy = y + h - FOOT_H;
        Ui.hairline(ctx, x + 1, fy, w - 1);
        if (hits.isEmpty()) return;
        Draw.textFit(ctx, tr, counted(), x + PAD, fy + (FOOT_H - Ui.TEXT_H) / 2 + 1, w - PAD * 2,
                Theme.TEXT_FAINT, false);
    }

    private String counted() {
        if (fixed != null) return Ui.plural(hits.size(), "использование", "использования", "использований");
        return mode == VARIABLES ? Ui.plural(hits.size(), "блок", "блока", "блоков")
                : Ui.plural(hits.size(), "совпадение", "совпадения", "совпадений");
    }

    public boolean tooltip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        List<Component> tip = new ArrayList<>();
        String head = fixed != null ? "ИСПОЛЬЗОВАНИЯ «" + fixedName + "»" : "";
        if (!head.isEmpty() && Ui.hit(mouseX, mouseY, x, y, w - PAD - 14, HEAD_H)
                && tr.width(head) > w - PAD * 2 - 11 - 18)
            tip.add(Component.literal(head));
        int t = toggleAt(mouseX, mouseY);
        if (t >= 0) {
            tip.add(Component.literal(TOGGLE_TIP[t]));
            tip.add(Component.literal("§7" + TOGGLE_KEY[t]));
        }
        int k = chipAt(mouseX, mouseY);
        if (k >= 0 && tr.width(chipText.get(k)) + CHIP_PAD > chips.get(k)[4])
            tip.add(Component.literal(chipText.get(k)));
        int i = indexAt(mouseX, mouseY);
        if (i >= 0) {
            Finder.Hit hit = hits.get(i);
            String title = indent(hit) + hit.title, detail = second(hit);
            boolean cut = tr.width(title) > titleRoom(hit) || tr.width(detail) > secondRoom();
            if (cut) {
                tip.add(Component.literal(hit.title));
                if (!detail.isEmpty()) tip.add(Component.literal("§7" + detail));
            }
        }
        if (tip.isEmpty()) return true;
        ctx.setComponentTooltipForNextFrame(tr, tip, mouseX, mouseY);
        return true;
    }

    private int indexAt(double mx, double my) {
        if (mx < x + 1 || mx >= x + w) return -1;
        if (my < y + listTop() || my >= y + listBottom()) return -1;
        int rel = (int) (my - y - listTop()) + pane.scroll;
        int i = rel / ROW_H;
        return i >= 0 && i < hits.size() ? i : -1;
    }

    private void flip(int toggle) {
        toggles[toggle] = !toggles[toggle];
        rebuild();
        pane.scroll = 0;
    }

    private void pick(int group, int index) {
        if (picked(group, index) && fixed == null) return;
        fixed = null;
        switch (group) {
            case G_MODE -> mode = index;
            case G_SCOPE -> scopePick = index;
            case G_WHERE -> wherePick = index;
            default -> accessPick = index;
        }
        restyle();
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) return false;
        field.setFocused(true);
        if (click.button() != 0) return true;
        if (Ui.hit(mx, my, x + w - PAD - 14, y + (HEAD_H - 14) / 2, 14, 14)) {
            closed = true;
            return true;
        }
        int iy = y + HEAD_H + 5;
        int t = toggleAt(mx, my);
        if (t >= 0) { flip(t); return true; }
        if (!typed.isEmpty() && Ui.hit(mx, my, clearX(), iy, CLEAR_W, INPUT_H)) {
            field.setValue("");
            field.setFocused(true);
            return true;
        }
        if (Ui.hit(mx, my, x + PAD, iy, w - PAD * 2, INPUT_H)) {
            field.setFocused(true);
            if (!field.mouseClicked(click, doubled)) field.onClick(click, doubled);
            grab.take(field);
            return true;
        }
        int k = chipAt(mx, my);
        if (k >= 0) {
            int[] c = chips.get(k);
            pick(c[0], c[1]);
            return true;
        }
        int sy = y + statusY;
        if (!hits.isEmpty() && my >= sy && my < sy + STAT_H) {
            for (int i = 0; i < 2; i++)
                if (Ui.hit(mx, my, stepX(i), sy, STEP_W, STAT_H)) { step(i == 0 ? -1 : 1); return true; }
            return true;
        }
        if (bar.press(mx, my)) return true;
        int i = indexAt(mx, my);
        if (i >= 0) select(i, true);
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent click) {
        if (bar.dragging()) {
            bar.dragged(click.y(), 1, pane.max(), v -> { pane.scroll = v; pane.clamp(); });
            return true;
        }
        return grab.drag(click, 0, 0);
    }

    public void mouseReleased() {
        bar.release();
        grab.release();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        pane.wheel(amount);
        return true;
    }

    public boolean toggleKey(KeyEvent input) {
        if ((input.modifiers() & Settings.MOD_MASK) != Settings.ALT) return false;
        for (int i = 0; i < TOGGLE_GLFW.length; i++) {
            if (input.key() != TOGGLE_GLFW[i]) continue;
            flip(i);
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (toggleKey(input)) return true;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (typed.isEmpty()) closed = true;
                else field.setValue("");
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> { step(1); return true; }
            case GLFW.GLFW_KEY_UP -> { step(-1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_F3 -> {
                step(shift ? -1 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { select(sel + rows(), true); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { select(Math.max(0, sel - rows()), true); return true; }
            default -> { }
        }
        return field.isFocused() && field.keyPressed(input);
    }

    public boolean charTyped(CharacterEvent input) {
        return field.isFocused() && field.charTyped(input);
    }
}
