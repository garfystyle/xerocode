package com.xerocode.ui;

import com.xerocode.Env;
import com.xerocode.Placeholders;
import com.xerocode.Search;
import com.xerocode.Values;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;

public final class NamePick {
    public record Known(String name, String scope, boolean here, int uses) {}

    public interface Pick { void apply(String name, String scope); }

    private record Found(Known k, int rank) {}

    private static final int PAD = 3, ROWS = 7, MAX = 40, MIN_W = 120, MAX_W = 260;
    private final int rowH = Env.touch() ? 22 : 12;
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
        String exact = f.getValue().trim().toLowerCase(Locale.ROOT);
        String typed = Search.fold(exact);
        String bare = Placeholders.strip(typed);
        boolean loose = !bare.isBlank() && !bare.equals(typed);
        List<Found> same = new ArrayList<>(), other = new ArrayList<>();
        for (Known k : pool) {
            String name = Search.fold(k.name());
            String nameBare = loose ? Placeholders.strip(name) : name;
            boolean named = !scoped && Search.fold(k.scope()).contains(typed);
            boolean found = name.contains(typed) || loose && nameBare.contains(bare);
            if (!typed.isEmpty() && !found && !named) continue;
            boolean mine = !scoped || k.scope().equals(scope);
            if (mine && k.name().toLowerCase(Locale.ROOT).equals(exact)) continue;
            int rank = name.startsWith(typed) ? 0 : loose && nameBare.startsWith(bare) ? 1 : 2;
            if (mine) same.add(new Found(k, rank));
            else if (!typed.isEmpty()) other.add(new Found(k, rank));
        }
        Comparator<Found> order = (a, b) -> {
            if (a.k().here() != b.k().here()) return a.k().here() ? -1 : 1;
            if (a.rank() != b.rank()) return a.rank() - b.rank();
            return b.k().uses() - a.k().uses();
        };
        same.sort(order);
        other.sort(order);
        for (Found h : same) if (hits.size() < MAX) hits.add(h.k());
        for (Found h : other) if (hits.size() < MAX) hits.add(h.k());
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
        h = PAD * 2 + rows * rowH;
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
        scroll = Math.max(0, Math.min(hits.size() - rows, scroll - Ui.rows(amount, 1, rowH)));
        return true;
    }

    private int rowAt(double my) {
        int rows = Math.min(ROWS, hits.size());
        int rel = (int) (my - (y + PAD));
        if (rel < 0 || rel >= rows * rowH) return -1;
        return scroll + rel / rowH;
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
            int ry = y + PAD + r * rowH;
            boolean on = i == sel;
            if (on) Draw.round(ctx, x + 2, ry, w - 4, rowH - 1, 3,
                    Draw.opaque(Draw.mix(Ui.BTN_HOVER, Theme.ACCENT, 0.35f)));
            else if (i == hover) Draw.round(ctx, x + 2, ry, w - 4, rowH - 1, 3, Draw.opaque(Ui.BTN_HOVER));
            Values.Scope s = scopedList ? Values.scope(k.scope()) : null;
            int dot = s == null ? Theme.TEXT_FAINT : s.color();
            Draw.dot(ctx, x + 6, ry + (rowH - 6) / 2, Draw.opaque(dot));
            String tag = tag(k, scopedList);
            int nameW = Math.min(tr.width(k.name()), w - 20);
            int room = w - 20 - nameW - 6;
            int tagW = tag.isEmpty() || room < 16 ? 0 : Math.min(tr.width(tag), room);
            if (tagW > 0)
                Draw.textFit(ctx, tr, tag, x + w - 6 - tagW, ry + (rowH - 8) / 2, tagW, Theme.TEXT_FAINT, false);
            Draw.textFit(ctx, tr, k.name(), x + 14, ry + (rowH - 8) / 2, w - 20 - (tagW == 0 ? 0 : tagW + 6),
                    on ? Theme.TEXT : Theme.TEXT_DIM, false);
        }
        if (hits.size() > rows) {
            int listH = rows * rowH;
            int barH = Math.max(6, listH * rows / hits.size());
            int barY = y + PAD + (listH - barH) * scroll / Math.max(1, hits.size() - rows);
            Draw.round(ctx, x + w - 3, barY, 2, barH, 1, Draw.opaque(Ui.BORDER));
        }
    }
}
