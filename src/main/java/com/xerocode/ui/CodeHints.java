package com.xerocode.ui;

import com.xerocode.Env;
import com.xerocode.Settings;
import com.xerocode.code.CodeProject;
import com.xerocode.code.Doc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;
import xero.dialect.Dialect;
import xero.dialect.Ink;
import xero.ide.Service;
import xero.lang.Diagnostic;

final class CodeHints {
    private static final int ROWS = 10, PAD = 4, DOC_W = 210, LIST_W = 250, DETAIL_MIN = 40;
    private static final String TRIGGERS = ".@:";

    private final Font tr;
    private final CodeArea area;

    private List<Service.Item> items = List.of();
    private int from, selected, top;
    private boolean open;

    private Service.Signature signature;

    private List<String> hover = List.of();
    private int hoverX, hoverY;

    CodeHints(Font tr, CodeArea area) {
        this.tr = tr;
        this.area = area;
    }

    private static int rowH() { return Env.touch() ? 18 : 12; }

    boolean open() { return open; }

    void close() {
        open = false;
        signature = null;
        hover = List.of();
    }

    private Dialect dialect() { return area.file() == null ? null : area.file().dialect; }

    private int offset() {
        Doc d = area.doc();
        Doc.Caret c = d.primary();
        return d.offset(c.line, c.col);
    }

    void ask() {
        Dialect dialect = dialect();
        if (dialect == null) return;
        Service.Completion got;
        try {
            synchronized (dialect) {
                got = dialect.complete(area.doc().text(), offset());
            }
        } catch (RuntimeException e) {
            open = false;
            return;
        }
        items = got == null ? List.of() : got.items();
        from = got == null ? offset() : got.from();
        selected = 0;
        top = 0;
        open = !items.isEmpty();
    }

    void askSignature() {
        Dialect dialect = dialect();
        if (dialect == null) return;
        try {
            synchronized (dialect) {
                signature = dialect.signature(area.doc().text(), offset());
            }
        } catch (RuntimeException e) {
            signature = null;
        }
    }

    void typed(String s) {
        hover = List.of();
        char c = s.isEmpty() ? 0 : s.charAt(s.length() - 1);
        Doc d = area.doc();
        if (d.carets().size() > 1) {
            open = false;
            return;
        }
        Doc.Caret caret = d.primary();
        int col = caret.col - 1;
        byte[] ink = d.ink(caret.line);
        boolean literal = col >= 0 && col < ink.length && (ink[col] == Ink.COMMENT || ink[col] == Ink.STRING);
        boolean offers = Settings.get().codeHints && (Ink.wordPart(c) && !literal || TRIGGERS.indexOf(c) >= 0);
        if (offers || open && Ink.wordPart(c)) ask();
        else open = false;
        if (c == '(' || c == ',') askSignature();
        else if (c == ')') signature = null;
        else if (signature != null) askSignature();
    }

    void deleted() {
        if (open) {
            int at = offset();
            if (at <= from) open = false;
            else ask();
        }
        if (signature != null) askSignature();
    }

    void moved() {
        if (open) {
            int at = offset();
            if (at < from) open = false;
            else ask();
        }
        if (signature != null) askSignature();
    }

    void hoverClear() { hover = List.of(); }

    void hoverAt(int line, int col, int mx, int my) {
        Dialect dialect = dialect();
        if (dialect == null || open) return;
        List<String> lines = new ArrayList<>();
        Doc d = area.doc();
        int at = d.offset(line, col);
        CodeProject project = CodeProject.current();
        for (CodeProject.Problem p : CodeScreen.problems()) {
            Diagnostic x = p.diagnostic();
            if (p.file() != area.file() || at < x.start() || at > Math.max(x.end(), x.start() + 1)) continue;
            lines.add((x.error() ? "§cОшибка: §r" : "§eВнимание: §r") + x.message());
            if (x.hint() != null) lines.add("§7" + x.hint());
        }
        try {
            String doc;
            synchronized (dialect) {
                doc = dialect.hover(d.text(), at);
            }
            if (doc != null && !doc.isBlank()) {
                if (!lines.isEmpty()) lines.add("");
                lines.addAll(plain(doc));
            }
        } catch (RuntimeException ignored) {
        }
        if (project == null || lines.isEmpty()) return;
        hover = lines;
        hoverX = mx;
        hoverY = my;
    }

    static List<String> plain(String markdown) {
        List<String> out = new ArrayList<>();
        for (String raw : markdown.split("\n")) {
            if (raw.startsWith("```")) continue;
            out.add(raw.replace("**", "").replace("`", "").replace("\\_", "_").stripTrailing());
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
        return out;
    }

    boolean keyPressed(int key, boolean ctrl) {
        if (!open) {
            if (key == GLFW.GLFW_KEY_ESCAPE && signature != null) {
                signature = null;
                return true;
            }
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                open = false;
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                move(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                move(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                move(-ROWS);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                move(ROWS);
                return true;
            }
            case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (ctrl) return false;
                accept();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void move(int by) {
        if (items.isEmpty()) return;
        int n = items.size();
        selected = by == 1 || by == -1 ? (selected + by + n) % n : Math.max(0, Math.min(n - 1, selected + by));
        if (selected < top) top = selected;
        if (selected >= top + ROWS) top = selected - ROWS + 1;
    }

    private void accept() {
        Service.Item item = items.get(selected);
        open = false;
        int to = offset();
        String body = item.snippet();
        if (body == null) {
            body = escape(item.label());
            if (item.call()) body += "($1)";
        }
        area.insertSnippet(from, to, body, item.retrigger());
        if (item.call() || body.contains("(")) askSignature();
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("$", "\\$"); }

    boolean mouseClicked(double mx, double my) {
        if (!open) return false;
        Box b = box();
        if (mx < b.x || mx >= b.x + b.w || my < b.y || my >= b.y + b.h) {
            open = false;
            return false;
        }
        int row = (int) ((my - b.y - PAD) / rowH()) + top;
        if (row >= 0 && row < items.size()) {
            selected = row;
            accept();
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (!open) return false;
        Box b = box();
        if (mx < b.x || mx >= b.x + b.w + DOC_W || my < b.y || my >= b.y + b.h) return false;
        top = Math.max(0, Math.min(Math.max(0, items.size() - ROWS), top - (int) Math.signum(amount)));
        return true;
    }

    private record Box(int x, int y, int w, int h, boolean above) {}

    private Box box() {
        int shown = Math.min(ROWS, items.size());
        int h = shown * rowH() + PAD * 2;
        int w = Math.min(LIST_W, area.right() - area.left() - 8);
        int cx = area.caretGuiX(), cy = area.caretGuiY(), rh = area.rowGuiH();
        int bx = Math.max(area.left() + 2, Math.min(area.right() - w - 2, cx - 10));
        boolean above = cy + rh + h > area.bottom() && cy - h > area.top();
        return new Box(bx, above ? cy - h - 1 : cy + rh + 1, w, h, above);
    }

    void draw(GuiGraphicsExtractor ctx, int mx, int my, int screenW, int screenH) {
        if (open && !items.isEmpty()) drawList(ctx, mx, my, screenW);
        if (signature != null) drawSignature(ctx, screenW);
        if (!hover.isEmpty() && !open) drawHover(ctx, screenW, screenH);
    }

    private void drawList(GuiGraphicsExtractor ctx, int mx, int my, int screenW) {
        Box b = box();
        int rh = rowH();
        Draw.shadow(ctx, b.x, b.y, b.w, b.h, 3);
        Draw.round(ctx, b.x, b.y, b.w, b.h, 3, Draw.opaque(Theme.PANEL_RAISED));
        Draw.roundOutline(ctx, b.x, b.y, b.w, b.h, 3, Draw.opaque(Theme.LINE));
        int shown = Math.min(ROWS, items.size() - top);
        for (int i = 0; i < shown; i++) {
            Service.Item item = items.get(top + i);
            int ry = b.y + PAD + i * rh;
            if (top + i == selected) Draw.round(ctx, b.x + 2, ry, b.w - 4, rh, 2, Draw.argb(0x55, Theme.ACCENT));
            int ty = ry + (rh - 8) / 2;
            int color = CodeTheme.kindColor(item.kind());
            Draw.text(ctx, tr, CodeTheme.kindMark(item.kind()), b.x + 6, ty, color, false);
            int labelW = tr.width(item.label());
            int room = b.w - 26;
            Draw.textFit(ctx, tr, item.label(), b.x + 17, ty, room, Theme.TEXT, false);
            String detail = item.detail();
            int free = room - labelW - 8;
            if (detail != null && free >= DETAIL_MIN) {
                String fit = Draw.fit(tr, detail, free);
                Draw.text(ctx, tr, fit, b.x + b.w - 7 - tr.width(fit), ty, Theme.TEXT_FAINT, false);
            }
        }
        if (items.size() > ROWS) {
            int track = b.h - 4, thumb = Math.max(8, track * ROWS / items.size());
            int at = b.y + 2 + (track - thumb) * top / Math.max(1, items.size() - ROWS);
            Draw.rect(ctx, b.x + b.w - 3, at, 2, thumb, Draw.argb(0x70, Theme.TEXT));
        }
        drawDoc(ctx, b, screenW);
    }

    private void drawDoc(GuiGraphicsExtractor ctx, Box b, int screenW) {
        Service.Item item = items.get(selected);
        String text = item.doc() != null ? item.doc() : item.detail();
        if (text == null || text.isBlank()) return;
        int w = DOC_W;
        boolean right = b.x + b.w + 2 + w <= screenW;
        if (!right && b.x - w - 2 < 0) return;
        int x = right ? b.x + b.w + 2 : b.x - w - 2;
        List<String> wrapped = new ArrayList<>();
        for (String line : plain(text)) {
            if (line.isBlank()) wrapped.add("");
            else wrapped.addAll(Ui.wrap(tr, line, w - PAD * 3, 12));
            if (wrapped.size() > 16) break;
        }
        int h = Math.min(wrapped.size(), 16) * 10 + PAD * 2;
        int y = b.above ? b.y + b.h - h : b.y;
        Draw.shadow(ctx, x, y, w, h, 3);
        Draw.round(ctx, x, y, w, h, 3, Draw.opaque(Theme.PANEL_RAISED));
        Draw.roundOutline(ctx, x, y, w, h, 3, Draw.opaque(Theme.LINE));
        for (int i = 0; i < wrapped.size() && i < 16; i++)
            Draw.text(ctx, tr, wrapped.get(i), x + PAD + 2, y + PAD + i * 10, i == 0 ? Theme.TEXT : Theme.TEXT_DIM, false);
    }

    private void drawSignature(GuiGraphicsExtractor ctx, int screenW) {
        Service.Signature s = signature;
        String label = s.label();
        int w = Math.min(screenW - 8, tr.width(label) + PAD * 3);
        int h = 13;
        int x = Math.max(2, Math.min(screenW - w - 2, area.caretGuiX() - 12));
        int y = area.caretGuiY() - h - 2;
        if (y < area.top() + 1) y = area.caretGuiY() + area.rowGuiH() + 2;
        if (open && box().above) y = area.caretGuiY() + area.rowGuiH() + 2;
        else if (open) y = area.caretGuiY() - h - 2;
        Draw.shadow(ctx, x, y, w, h, 3);
        Draw.round(ctx, x, y, w, h, 3, Draw.opaque(Theme.PANEL_RAISED));
        Draw.roundOutline(ctx, x, y, w, h, 3, Draw.opaque(Theme.LINE));
        int tx = x + PAD + 2, ty = y + 3;
        String shown = Draw.fit(tr, label, w - PAD * 3);
        Draw.text(ctx, tr, shown, tx, ty, Theme.TEXT_DIM, false);
        if (s.params() != null && s.active() >= 0 && s.active() < s.params().size()) {
            Service.Span p = s.params().get(s.active());
            if (p.end() <= shown.length()) {
                int ax = tx + tr.width(label.substring(0, p.start()));
                Draw.text(ctx, tr, label.substring(p.start(), p.end()), ax, ty, Theme.ACCENT, false);
            }
        }
    }

    private void drawHover(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        int w = 0;
        List<String> wrapped = new ArrayList<>();
        int limit = Math.min(300, screenW - 16);
        for (String line : hover) {
            if (line.isBlank()) wrapped.add("");
            else wrapped.addAll(Ui.wrap(tr, line, limit, 6));
            if (wrapped.size() >= 18) break;
        }
        for (String line : wrapped) w = Math.max(w, tr.width(line));
        w += PAD * 3;
        int h = Math.min(18, wrapped.size()) * 10 + PAD * 2;
        int x = Math.max(2, Math.min(screenW - w - 2, hoverX));
        int y = hoverY - h - 6;
        if (y < 2) y = Math.min(screenH - h - 2, hoverY + 12);
        Draw.shadow(ctx, x, y, w, h, 3);
        Draw.round(ctx, x, y, w, h, 3, Draw.opaque(Theme.PANEL_RAISED));
        Draw.roundOutline(ctx, x, y, w, h, 3, Draw.opaque(Theme.LINE));
        for (int i = 0; i < wrapped.size() && i < 18; i++)
            Draw.text(ctx, tr, wrapped.get(i), x + PAD + 2, y + PAD + i * 10, Theme.TEXT, false);
    }
}
