package com.xerocode.ui;

import com.xerocode.Finder;
import com.xerocode.Functions;
import com.xerocode.Script;
import com.xerocode.Stacks;
import com.xerocode.Value;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class FindPanel {
    public interface Jump { void to(Finder.Hit hit); }

    public static final int WANT_W = 246, MIN_W = 168;
    private static final int HEAD_H = 25, INPUT_H = 20, STAT_H = 17, ROW_H = 27, FOOT_H = 18;
    private static final int PAD = 8, ICON = 12, TEXT_X = 26, STEP_W = 15;

    private final TextRenderer tr;
    private final Script script;
    private final Jump jump;

    private int screenW, screenH, x, y, w, h;
    private TextFieldWidget field;
    private final Ui.Pane pane = new Ui.Pane();
    private final Ui.Bar bar = new Ui.Bar();
    private final Ui.Grab grab = new Ui.Grab();

    private List<Finder.Hit> hits = List.of();
    private boolean outline = true;
    private int stamp = Integer.MIN_VALUE;
    private String query = "";
    private int sel = -1, hover = -1;
    private boolean closed;

    public FindPanel(TextRenderer tr, Script script, int screenW, int screenH, Jump jump) {
        this.tr = tr;
        this.script = script;
        this.jump = jump;
        resize(screenW, screenH);
    }

    public void resize(int screenW, int screenH) {
        int room = Math.max(1, screenW - Theme.PALETTE_W);
        int want = Math.min(WANT_W, Math.max(MIN_W, room));
        if (screenW == this.screenW && screenH == this.screenH && want == w && field != null) return;
        this.screenW = screenW;
        this.screenH = screenH;
        this.w = Math.min(want, room);
        this.x = screenW - w;
        this.y = Theme.TOPBAR_H;
        this.h = Math.max(HEAD_H + INPUT_H + STAT_H + ROW_H, screenH - y);
        String typed = field == null ? "" : field.getText();
        field = Ui.field(tr, x + PAD + 16, y + HEAD_H + 5 + (INPUT_H - 10) / 2,
                w - PAD * 2 - 16 - 16, 10, "имя блока, текст, переменная…");
        field.setMaxLength(64);
        field.setText(typed);
        field.setChangedListener(this::retype);
        field.setFocused(true);
        rebuild();
    }

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

    public void setQuery(String q) {
        if (field == null) return;
        field.setText(q == null ? "" : q);
        field.setCursorToEnd(false);
        if (!hits.isEmpty()) select(0, true);
    }

    private void retype(String text) {
        query = text == null ? "" : text.trim();
        rebuild();
        pane.scroll = 0;
        sel = -1;
    }

    public void sync(int scriptStamp) {
        if (scriptStamp == stamp) return;
        stamp = scriptStamp;
        rebuild();
    }

    private void rebuild() {
        Script.Node kept = current() == null ? null : current().node;
        outline = query.isEmpty();
        hits = outline ? Finder.outline(script) : Finder.search(script, query);
        sel = -1;
        if (kept != null)
            for (int i = 0; i < hits.size(); i++)
                if (hits.get(i).node == kept) { sel = i; break; }
    }

    private int listTop() { return HEAD_H + 5 + INPUT_H + 5 + STAT_H + 1; }
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

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.hgrad(ctx, x - 5, y, 5, h, Draw.argb(0, 0x000000), Theme.SHADOW);
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.PANEL));
        Draw.rect(ctx, x, y, 1, h, Draw.opaque(Theme.LINE));

        Draw.rect(ctx, x + 1, y, w - 1, HEAD_H, Draw.opaque(Ui.HEAD));
        Draw.glyph(ctx, Draw.SEARCH, x + PAD, y + (HEAD_H - Draw.glyphH(Draw.SEARCH)) / 2,
                Theme.ACCENT);
        Draw.textFit(ctx, tr, "ПОИСК ПО КОДУ", x + PAD + 11, y + (HEAD_H - Ui.TEXT_H) / 2,
                w - PAD * 2 - 11 - 18, Theme.TEXT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, y + (HEAD_H - 14) / 2, 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 1);

        int iy = y + HEAD_H + 5;
        Ui.input(ctx, x + PAD, iy, w - PAD * 2, INPUT_H, field.isFocused());
        Draw.glyph(ctx, Draw.SEARCH, x + PAD + 6, iy + (INPUT_H - Draw.glyphH(Draw.SEARCH)) / 2,
                query.isEmpty() ? Theme.TEXT_FAINT : Theme.ACCENT);
        field.render(ctx, mouseX, mouseY, delta);
        Ui.placeholder(ctx, tr, field);
        if (!query.isEmpty()) {
            boolean hov = Ui.hit(mouseX, mouseY, clearX(), iy, 14, INPUT_H);
            Draw.glyph(ctx, Draw.CROSS, clearX() + 4,
                    iy + (INPUT_H - Draw.glyphH(Draw.CROSS)) / 2,
                    hov ? Theme.TEXT : Theme.TEXT_FAINT);
        }

        drawStatus(ctx, mouseX, mouseY);
        drawList(ctx, mouseX, mouseY);
        drawFoot(ctx);
    }

    private int clearX() { return x + w - PAD - 14; }

    private int stepX(int i) { return x + w - PAD - STEP_W * (2 - i) - (i == 0 ? 3 : 0); }

    private void drawStatus(DrawContext ctx, int mouseX, int mouseY) {
        int sy = y + HEAD_H + 5 + INPUT_H + 5;
        String note = outline
                ? Ui.plural(hits.size(), "строка", "строки", "строк") + " кода"
                : hits.isEmpty() ? "ничего не нашлось"
                : sel < 0 ? Ui.plural(hits.size(), "совпадение", "совпадения", "совпадений")
                : (sel + 1) + " из " + hits.size();
        int room = w - PAD * 2 - (hits.isEmpty() ? 0 : STEP_W * 2 + 6);
        Draw.textFit(ctx, tr, note, x + PAD, sy + (STAT_H - Ui.TEXT_H) / 2, room,
                hits.isEmpty() && !outline ? Theme.TEXT_FAINT : Theme.TEXT_DIM, false);
        if (hits.isEmpty()) return;
        for (int i = 0; i < 2; i++) {
            String[] glyph = i == 0 ? Draw.CARET_UP : Draw.CARET_DOWN;
            int bx = stepX(i);
            boolean hov = Ui.hit(mouseX, mouseY, bx, sy, STEP_W, STAT_H);
            Draw.round(ctx, bx, sy, STEP_W, STAT_H - 1, Ui.R_SM,
                    Draw.opaque(hov ? Theme.SURFACE_HOVER : Theme.SURFACE));
            Draw.glyph(ctx, glyph, bx + (STEP_W - Draw.glyphW(glyph)) / 2,
                    sy + (STAT_H - 1 - Draw.glyphH(glyph)) / 2, hov ? Theme.TEXT : Theme.TEXT_DIM);
        }
        Ui.hairline(ctx, x + 1, sy + STAT_H, w - 1);
    }

    private void drawList(DrawContext ctx, int mouseX, int mouseY) {
        int top = y + listTop(), bottom = y + listBottom();
        pane.fit(listTop(), listBottom(), listTop() + hits.size() * ROW_H);
        hover = indexAt(mouseX, mouseY);
        if (hits.isEmpty()) {
            drawEmpty(ctx, top, bottom);
            return;
        }
        ScreenRect area = new ScreenRect(x + 1, top, w - 1, Math.max(0, bottom - top));
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

    private void drawEmpty(DrawContext ctx, int top, int bottom) {
        String[] lines = outline
                ? new String[]{"полотно пусто", "перетащи блок из палитры слева"}
                : new String[]{"ничего не нашлось", "ищется имя блока, текст, имя переменной,",
                        "звук, предмет и цель блока"};
        int ty = top + (bottom - top) / 2 - lines.length * 6;
        for (int i = 0; i < lines.length; i++)
            Draw.textCenter(ctx, tr, lines[i], x + PAD, ty + i * 11, w - PAD * 2, w - PAD * 2,
                    i == 0 ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
    }

    private void drawRow(DrawContext ctx, Finder.Hit hit, int ry, boolean hov, boolean cur) {
        int rx = x + 4, rw = w - 9;
        if (cur) Draw.round(ctx, rx, ry + 1, rw, ROW_H - 2, Ui.R_SM,
                Draw.opaque(Draw.mix(Ui.PANEL, Theme.ACCENT, 0.24f)));
        else if (hov) Draw.round(ctx, rx, ry + 1, rw, ROW_H - 2, Ui.R_SM,
                Draw.opaque(Theme.SURFACE));
        Draw.round(ctx, rx + 3, ry + 5, 2, ROW_H - 11, 1, Draw.opaque(hit.color()));

        String num = String.valueOf(hit.line);
        int numW = tr.getWidth(num);
        Draw.text(ctx, tr, num, rx + rw - 6 - numW, ry + 5,
                cur ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);

        Draw.item(ctx, iconOf(hit.node), rx + 8, ry + 4, ICON);
        int tx = rx + TEXT_X;
        int room = rx + rw - 10 - numW - tx;
        Draw.textFit(ctx, tr, indent(hit) + hit.title, tx, ry + 5, room,
                cur ? Theme.TEXT : Theme.TEXT, false);
        Draw.textFit(ctx, tr, second(hit), tx, ry + 15, rx + rw - 8 - tx,
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

    private void drawFoot(DrawContext ctx) {
        int fy = y + h - FOOT_H;
        Ui.hairline(ctx, x + 1, fy, w - 1);
        if (hits.isEmpty()) return;
        Draw.textFit(ctx, tr, Ui.plural(hits.size(), "совпадение", "совпадения", "совпадений"),
                x + PAD, fy + (FOOT_H - Ui.TEXT_H) / 2 + 1, w - PAD * 2, Theme.TEXT_FAINT, false);
    }

    private int indexAt(double mx, double my) {
        if (mx < x + 1 || mx >= x + w) return -1;
        if (my < y + listTop() || my >= y + listBottom()) return -1;
        int rel = (int) (my - y - listTop()) + pane.scroll;
        int i = rel / ROW_H;
        return i >= 0 && i < hits.size() ? i : -1;
    }

    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) return false;
        field.setFocused(true);
        if (click.button() != 0) return true;
        if (Ui.hit(mx, my, x + w - PAD - 14, y + (HEAD_H - 14) / 2, 14, 14)) {
            closed = true;
            return true;
        }
        int iy = y + HEAD_H + 5;
        if (!query.isEmpty() && Ui.hit(mx, my, clearX(), iy, 14, INPUT_H)) {
            field.setText("");
            field.setFocused(true);
            return true;
        }
        if (Ui.hit(mx, my, x + PAD, iy, w - PAD * 2, INPUT_H)) {
            field.setFocused(true);
            if (!field.mouseClicked(click, doubled)) field.onClick(click, doubled);
            grab.take(field);
            return true;
        }
        int sy = y + HEAD_H + 5 + INPUT_H + 5;
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

    public boolean mouseDragged(Click click) {
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

    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (query.isEmpty()) closed = true;
                else field.setText("");
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

    public boolean charTyped(CharInput input) {
        return field.isFocused() && field.charTyped(input);
    }
}
