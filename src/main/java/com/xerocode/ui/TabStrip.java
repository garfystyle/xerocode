package com.xerocode.ui;

import com.xerocode.Env;
import com.xerocode.Tabs;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

final class TabStrip {
    private static int h() { return Env.touch() ? 24 : 18; }
    private static final int GAP = 4, PAD = 7, TEXT_MAX = 120, TEXT_MIN = 18, CROSS_ROOM = 13;

    interface Host {
        void select(int index);
        void add();
        void close(int index);
        void menu(int index, int x, int y);
    }

    private final Font tr;
    private final Host host;
    private int x, y, room;
    private int[] xs = new int[0], ws = new int[0];
    private int plusX;
    private int stamp = Integer.MIN_VALUE;
    private int renaming = -1;
    private EditBox field;

    TabStrip(Font tr, Host host) {
        this.tr = tr;
        this.host = host;
    }

    void place(int left, int top, int width) {
        x = left;
        y = top;
        room = Math.max(40, width);
    }

    int bottom() { return y + h(); }

    int top() { return y; }

    int left() { return x; }

    int right() {
        layout();
        return plusX + h();
    }

    boolean contains(double mx, double my) {
        layout();
        return my >= y && my < y + h() && mx >= x && mx < plusX + h();
    }

    private boolean closable(int i) { return i > 0; }

    private void layout() {
        List<Tabs.Tab> tabs = Tabs.all();
        int s = x * 31 + y * 17 + room * 7 + tabs.size();
        for (Tabs.Tab t : tabs) s = s * 31 + t.name.hashCode();
        if (s == stamp && xs.length == tabs.size()) return;
        stamp = s;
        int n = tabs.size();
        int[] text = new int[n];
        int total = h() + GAP * n;
        for (int i = 0; i < n; i++) {
            text[i] = Math.min(TEXT_MAX, tr.width(tabs.get(i).name));
            total += PAD * 2 + text[i] + (closable(i) ? CROSS_ROOM - 2 : 0);
        }
        int over = total - room;
        while (over > 0) {
            int widest = -1;
            for (int i = 0; i < n; i++)
                if (text[i] > TEXT_MIN && (widest < 0 || text[i] > text[widest])) widest = i;
            if (widest < 0) break;
            int cut = Math.min(over, text[widest] - TEXT_MIN);
            cut = Math.max(1, Math.min(cut, 8));
            text[widest] -= cut;
            over -= cut;
        }
        xs = new int[n];
        ws = new int[n];
        int at = x;
        for (int i = 0; i < n; i++) {
            xs[i] = at;
            ws[i] = PAD * 2 + text[i] + (closable(i) ? CROSS_ROOM - 2 : 0);
            at += ws[i] + GAP;
        }
        plusX = at;
    }

    private int indexAt(double mx, double my) {
        layout();
        if (my < y || my >= y + h()) return -1;
        for (int i = 0; i < xs.length; i++) if (mx >= xs[i] && mx < xs[i] + ws[i]) return i;
        return -1;
    }

    private boolean overCross(int i, double mx, double my) {
        return closable(i) && Ui.hit(mx, my, xs[i] + ws[i] - CROSS_ROOM, y, CROSS_ROOM, h());
    }

    void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        List<Tabs.Tab> tabs = Tabs.all();
        int active = Tabs.active();
        int hover = renaming >= 0 ? -1 : indexAt(mouseX, mouseY);
        for (int i = 0; i < tabs.size() && i < xs.length; i++) {
            boolean on = i == active;
            int fill = on ? Draw.opaque(Draw.mix(Ui.PANEL, Theme.ACCENT, 0.22f))
                    : Draw.argb(0xEE, i == hover ? Theme.SURFACE_HOVER : Ui.PANEL);
            Draw.shadow(ctx, xs[i], y, ws[i], h(), Ui.R_SM);
            Draw.card(ctx, xs[i], y, ws[i], h(), Ui.R_SM, fill,
                    Draw.opaque(on ? Theme.ACCENT : Ui.BORDER));
            int textRoom = ws[i] - PAD * 2 - (closable(i) ? CROSS_ROOM - 2 : 0);
            if (i == renaming && field != null) {
                Ui.width(field, fieldW(i));
                field.setX(xs[i] + PAD);
                field.setY(y + (h() - Ui.TEXT_H) / 2);
                field.extractRenderState(ctx, mouseX, mouseY, delta);
                continue;
            }
            Draw.textFit(ctx, tr, tabs.get(i).name, xs[i] + PAD, y + (h() - Ui.TEXT_H) / 2 + 1, textRoom,
                    on ? Theme.TEXT : i == hover ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (closable(i)) {
                boolean hot = i == hover && overCross(i, mouseX, mouseY);
                int cx = xs[i] + ws[i] - CROSS_ROOM + (CROSS_ROOM - Draw.glyphW(Draw.CROSS)) / 2 - 2;
                Draw.glyph(ctx, Draw.CROSS, cx, y + (h() - Draw.glyphH(Draw.CROSS)) / 2,
                        hot ? Theme.TEXT : Theme.TEXT_FAINT);
            }
        }
        boolean full = Tabs.full();
        boolean plusHot = !full && renaming < 0 && Ui.hit(mouseX, mouseY, plusX, y, h(), h());
        Draw.shadow(ctx, plusX, y, h(), h(), Ui.R_SM);
        Draw.card(ctx, plusX, y, h(), h(), Ui.R_SM,
                Draw.argb(0xEE, plusHot ? Theme.SURFACE_HOVER : Ui.PANEL), Draw.opaque(Ui.BORDER));
        Draw.glyph(ctx, Draw.PLUS, plusX + (h() - Draw.glyphW(Draw.PLUS)) / 2,
                y + (h() - Draw.glyphH(Draw.PLUS)) / 2,
                full ? Theme.TEXT_FAINT : plusHot ? Theme.TEXT : Theme.TEXT_DIM);
    }

    boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (renaming >= 0) {
            if (renaming < xs.length && Ui.hit(mx, my, xs[renaming], y, ws[renaming], h())) {
                if (!field.mouseClicked(click, doubled)) field.onClick(click, doubled);
                return true;
            }
            commit();
        }
        if (!contains(mx, my)) return false;
        if (Ui.hit(mx, my, plusX, y, h(), h())) {
            if (click.button() == 0) host.add();
            return true;
        }
        int i = indexAt(mx, my);
        if (i < 0) return true;
        if (click.button() == 1) { host.menu(i, (int) mx, y + h() + 2); return true; }
        if (click.button() == 2) { if (closable(i)) host.close(i); return true; }
        if (click.button() != 0) return true;
        if (overCross(i, mx, my)) { host.close(i); return true; }
        if (doubled && i == Tabs.active()) { rename(i); return true; }
        host.select(i);
        return true;
    }

    void rename(int i) {
        List<Tabs.Tab> tabs = Tabs.all();
        if (i < 0 || i >= tabs.size()) return;
        layout();
        renaming = i;
        field = Ui.field(tr, xs[i] + PAD, y + (h() - Ui.TEXT_H) / 2, fieldW(i), Ui.TEXT_H + 2,
                "имя вкладки");
        field.setMaxLength(Tabs.NAME_MAX);
        field.setValue(tabs.get(i).name);
        field.setFocused(true);
        field.moveCursorToEnd(false);
        field.setHighlightPos(0);
    }

    private int fieldW(int i) { return ws[i] - PAD * 2 + 2; }

    boolean renaming() { return renaming >= 0; }

    void commit() {
        if (renaming < 0) return;
        String typed = field == null ? "" : field.getValue().trim();
        if (!typed.isEmpty()) Tabs.rename(renaming, typed);
        renaming = -1;
        field = null;
        stamp = Integer.MIN_VALUE;
    }

    void cancel() {
        renaming = -1;
        field = null;
    }

    boolean keyPressed(KeyEvent input) {
        if (renaming < 0) return false;
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commit(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { cancel(); return true; }
        field.keyPressed(input);
        return true;
    }

    boolean charTyped(CharacterEvent input) {
        if (renaming < 0) return false;
        field.charTyped(input);
        return true;
    }

    void invalidate() { stamp = Integer.MIN_VALUE; }
}
