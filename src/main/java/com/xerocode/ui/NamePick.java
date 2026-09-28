package com.xerocode.ui;

import com.xerocode.Values;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;

public final class NamePick {
    public record Known(String name, String scope, boolean here, int uses) {}

    public interface Pick { void apply(String name, String scope); }

    private static final int ROW_H = 12, PAD = 3, ROWS = 7, MAX = 40, MIN_W = 120, MAX_W = 260;
    private static final String HERE = "в этой стопке";

    private final List<Known> hits = new ArrayList<>();
    private EditBox field;
    private String dismissed;
    private int sel, scroll;
    private boolean moved;
    private int x, y, w, h;

    public boolean active() {
        return field != null && field.isFocused() && !hits.isEmpty()
                && !field.getValue().equals(dismissed);
    }

    public void reset() {
        field = null;
        hits.clear();
        dismissed = null;
        moved = false;
    }

    public void update(EditBox f, List<Known> pool, String scope, boolean scoped, Font tr,
                       int screenW, int screenH, int panelX, int panelW) {
        field = f;
        hits.clear();
        if (f == null || !f.isFocused()) return;
        String typed = f.getValue().trim().toLowerCase(Locale.ROOT);
        boolean local = "local".equals(scope) || "line".equals(scope);
        List<Known> same = new ArrayList<>(), other = new ArrayList<>();
        for (Known k : pool) {
            String name = k.name().toLowerCase(Locale.ROOT);
            boolean named = !scoped && k.scope().toLowerCase(Locale.ROOT).contains(typed);
            if (!typed.isEmpty() && !name.contains(typed) && !named) continue;
            boolean mine = !scoped || k.scope().equals(scope);
            if (mine && name.equals(typed)) continue;
            if (mine) { if (!local || k.here() || !scoped) same.add(k); }
            else if (!typed.isEmpty()) other.add(k);
        }
        java.util.Comparator<Known> order = (a, b) -> {
            if (a.here() != b.here()) return a.here() ? -1 : 1;
            boolean sa = a.name().toLowerCase(Locale.ROOT).startsWith(typed);
            boolean sb = b.name().toLowerCase(Locale.ROOT).startsWith(typed);
            if (sa != sb) return sa ? -1 : 1;
            return b.uses() - a.uses();
        };
        same.sort(order);
        other.sort(order);
        for (Known k : same) if (hits.size() < MAX) hits.add(k);
        for (Known k : other) if (hits.size() < MAX) hits.add(k);
        if (sel >= hits.size()) sel = 0;
        if (!moved) sel = 0;
        place(tr, screenW, screenH, scoped, panelX, panelW);
    }

    private String tag(Known k, boolean scoped) {
        if (!scoped) return k.here() ? HERE : k.scope();
        Values.Scope s = Values.scope(k.scope());
        String scope = s == null ? k.scope() : s.name().toLowerCase(Locale.ROOT);
        return k.here() ? scope + " · здесь" : scope;
    }

    private boolean scopedList;

    private void place(Font tr, int screenW, int screenH, boolean scoped, int panelX, int panelW) {
        scopedList = scoped;
        int textW = MIN_W;
        for (Known k : hits) textW = Math.max(textW, tr.width(k.name()) + tr.width(tag(k, scoped)) + 30);
        w = Math.min(Math.min(MAX_W, Math.max(40, screenW - 4)), textW);
        int rows = Math.min(ROWS, hits.size());
        scroll = Math.max(0, Math.min(scroll, hits.size() - rows));
        if (sel < scroll) scroll = sel;
        if (sel >= scroll + rows) scroll = sel - rows + 1;
        h = PAD * 2 + rows * ROW_H;
        int top = field.getY() - 6;
        y = Math.max(2, Math.min(top, screenH - h - 2));
        if (panelX + panelW + 4 + w <= screenW - 2) { x = panelX + panelW + 4; return; }
        if (panelX - 4 - w >= 2) { x = panelX - 4 - w; return; }
        int below = field.getY() + field.getHeight() + 4;
        x = Math.max(2, Math.min(field.getX() - 6, screenW - w - 2));
        y = below + h > screenH - 2 ? Math.max(2, field.getY() - h - 6) : below;
    }

    public boolean keyPressed(KeyEvent input, Pick pick) {
        if (!active()) return false;
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { dismissed = field.getValue(); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { sel = Math.floorMod(sel + 1, hits.size()); moved = true; return true; }
        if (key == GLFW.GLFW_KEY_UP) { sel = Math.floorMod(sel - 1, hits.size()); moved = true; return true; }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (key == GLFW.GLFW_KEY_TAB && (input.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0 || enter && moved) {
            accept(sel, pick);
            return true;
        }
        return false;
    }

    private void accept(int i, Pick pick) {
        if (i < 0 || i >= hits.size()) return;
        Known k = hits.get(i);
        moved = false;
        dismissed = k.name();
        pick.apply(k.name(), k.scope());
    }

    public boolean mouseClicked(double mx, double my, Pick pick) {
        if (!active() || mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int i = rowAt(my);
        if (i >= 0) accept(i, pick);
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!active() || mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int rows = Math.min(ROWS, hits.size());
        scroll = Math.max(0, Math.min(hits.size() - rows, scroll - Ui.rows(amount, 1, ROW_H)));
        return true;
    }

    private int rowAt(double my) {
        int rows = Math.min(ROWS, hits.size());
        int rel = (int) (my - (y + PAD));
        if (rel < 0 || rel >= rows * ROW_H) return -1;
        return scroll + rel / ROW_H;
    }

    public void render(GuiGraphicsExtractor ctx, Font tr, int mouseX, int mouseY) {
        if (!active()) return;
        Draw.shadow(ctx, x, y, w, h, 4);
        Draw.card(ctx, x, y, w, h, 4, Draw.opaque(Ui.PANEL), Draw.opaque(Ui.BORDER));
        int rows = Math.min(ROWS, hits.size());
        int hover = mouseX >= x && mouseX < x + w ? rowAt(mouseY) : -1;
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i >= hits.size()) break;
            Known k = hits.get(i);
            int ry = y + PAD + r * ROW_H;
            boolean on = i == sel;
            if (on) Draw.round(ctx, x + 2, ry, w - 4, ROW_H - 1, 3,
                    Draw.opaque(Draw.mix(Ui.BTN_HOVER, Theme.ACCENT, 0.35f)));
            else if (i == hover) Draw.round(ctx, x + 2, ry, w - 4, ROW_H - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            Values.Scope s = scopedList ? Values.scope(k.scope()) : null;
            int dot = s == null ? Theme.TEXT_FAINT : s.color();
            Draw.dot(ctx, x + 6, ry + 3, Draw.opaque(dot));
            String tag = tag(k, scopedList);
            int nameW = Math.min(tr.width(k.name()), w - 20);
            int room = w - 20 - nameW - 6;
            int tagW = tag.isEmpty() || room < 16 ? 0 : Math.min(tr.width(tag), room);
            if (tagW > 0)
                Draw.textFit(ctx, tr, tag, x + w - 6 - tagW, ry + 2, tagW, Theme.TEXT_FAINT, false);
            Draw.textFit(ctx, tr, k.name(), x + 14, ry + 2, w - 20 - (tagW == 0 ? 0 : tagW + 6),
                    on ? Theme.TEXT : Theme.TEXT_DIM, false);
        }
        if (hits.size() > rows) {
            int listH = rows * ROW_H;
            int barH = Math.max(6, listH * rows / hits.size());
            int barY = y + PAD + (listH - barH) * scroll / Math.max(1, hits.size() - rows);
            Draw.round(ctx, x + w - 3, barY, 2, barH, 1, Draw.opaque(Ui.BORDER));
        }
    }
}
