package com.xerocode.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.xerocode.Env;
import com.xerocode.Importer;
import com.xerocode.Settings;
import com.xerocode.Stacks;
import com.xerocode.Value;
import com.xerocode.Values;
import com.xerocode.code.CodeFile;
import com.xerocode.code.CodeProject;
import com.xerocode.code.Doc;
import com.xerocode.code.Editing;
import com.xerocode.code.Snippet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xero.dialect.Dialect;
import xero.dialect.Ink;
import xero.json.Json;
import xero.lang.Diagnostic;

final class CodeArea {
    private static final int BLINK = 530, BAR = 5, MIN_THUMB = 16;
    private static final int FOLD_GUI = 10, SPOT_GUI = 12;
    private static final long DOUBLE_MS = 350;
    private static final int UNIT_MAX = 8, SIZE_MIN = 8, SIZE_MAX = 64;

    record Found(int line, int col, int length) {}

    interface Host {
        void edited();
        void definition(int line, int col);
        void references();
        void rename();
        void fix();
        void format();
        void back();
        void forward();
        void nextProblem(int direction);
        void contextMenu(int x, int y);
        void zoomChanged();
        void editValue();
        void editValueOn(int line, int start, int end);
    }

    private final Font tr;
    private final Host host;
    final CodeHints hints;
    private CodeFile file;
    private Doc doc;

    private int x, y, w, h;
    private float scale = 1;
    private int cell = CodeInk.PIXEL_CELL, row = CodeInk.PIXEL_ROW, stroke = 1, textY = 1;
    private int gutter, foldW, numPad, textPad, spotW;
    private final java.util.Map<Integer, CodeInk.Line> numbers = new java.util.HashMap<>();
    private int numbersStamp;
    private int scrollX, scrollY;
    private boolean focused = true;
    private long caretAt = System.currentTimeMillis();
    private final byte[] wide = new byte[65536];
    private int stamp;

    private boolean selectingText, draggingBar, draggingHBar;
    private int selectionWord;
    private int anchorLine, anchorCol, anchorEndCol;
    private double mouseX, mouseY;
    private double restX, restY;
    private long restAt;
    private long lastClick;
    private int clickCount, clickLine, clickCol;

    private List<CodeProject.Problem> problems = List.of();
    private List<Found> found = List.of();
    private int foundCurrent = -1;

    private Snippet.Stop[] stops;
    private int stopAt;
    private int stopBase, stopLength;

    CodeArea(Font tr, Host host) {
        this.tr = tr;
        this.host = host;
        this.hints = new CodeHints(tr, this);
    }

    void place(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    static int defaultSize(int guiScale) { return Math.max(11, Math.round(guiScale * 7.5f)); }

    void metrics() {
        Settings st = Settings.get();
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        int size = st.codeSize > 0 ? st.codeSize : defaultSize(guiScale);
        CodeInk.use(st.codeFont, size);
        cell = CodeInk.cell();
        row = CodeInk.row();
        if (CodeInk.pixel()) {
            int unit = Math.max(1, Math.min(UNIT_MAX, st.codeUnit > 0 ? st.codeUnit : guiScale));
            scale = unit / (float) guiScale;
            stroke = 1;
            textY = 1;
            numPad = 6;
            textPad = 5;
        } else {
            scale = 1f / guiScale;
            stroke = Math.max(1, Math.round(size / 13f));
            textY = 0;
            numPad = cell;
            textPad = cell;
        }
        foldW = (int) Math.ceil(FOLD_GUI / scale);
        spotW = file != null && file.dialect != null && file.dialect.editsValues() ? (int) Math.ceil(SPOT_GUI / scale) : 0;
    }

    private void zoom(int delta) {
        Settings st = Settings.get();
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        if (CodeInk.pixel()) {
            int now = st.codeUnit > 0 ? st.codeUnit : guiScale;
            st.codeUnit = Math.max(1, Math.min(UNIT_MAX, now + delta));
        } else {
            int now = st.codeSize > 0 ? st.codeSize : defaultSize(guiScale);
            st.codeSize = Math.max(SIZE_MIN, Math.min(SIZE_MAX, now + delta));
        }
        metrics();
        clampScroll();
        host.zoomChanged();
    }

    void open(CodeFile next) {
        if (file != null) {
            file.scrollX = scrollX;
            file.scrollY = scrollY;
        }
        file = next;
        doc = next == null ? null : next.doc;
        stops = null;
        hints.close();
        if (next != null) {
            scrollX = next.scrollX;
            scrollY = next.scrollY;
        }
        found = List.of();
        foundCurrent = -1;
    }

    void remember() {
        if (file == null) return;
        file.scrollX = scrollX;
        file.scrollY = scrollY;
    }

    CodeFile file() { return file; }

    Doc doc() { return doc; }

    void focus(boolean on) {
        focused = on;
        Env.textFocus(this, on);
        if (on) caretAt = System.currentTimeMillis();
        else hints.close();
    }

    boolean focused() { return focused; }

    void problems(List<CodeProject.Problem> list) { problems = list; }

    void found(List<Found> list, int current) {
        found = list;
        foundCurrent = current;
    }

    int viewW() { return (int) ((w - BAR) / scale) - gutter; }

    int viewH() { return (int) (h / scale); }

    private int contentH() { return doc.visible().length * row; }

    private int maxScrollY() { return Math.max(0, contentH() - row * 2); }

    private void clampScroll() {
        if (doc == null) return;
        scrollY = Math.max(0, Math.min(maxScrollY(), scrollY));
        scrollX = Math.max(0, scrollX);
    }

    private int width(int cp) {
        if (cp < 128 || !CodeInk.pixel()) return cell;
        if (cp < 65536) {
            int known = wide[cp];
            if (known != 0) return known;
            int measured = Math.max(cell, Math.min(100, tr.width(new String(Character.toChars(cp)))));
            wide[cp] = (byte) measured;
            return measured;
        }
        return Math.max(cell, tr.width(new String(Character.toChars(cp))));
    }

    int[] xs(int line) {
        Doc.Row r = doc.row(line);
        if (r.xs != null && r.xsStamp == stamp) return r.xs;
        String t = r.text;
        int n = t.length();
        int[] out = new int[n + 1];
        int at = 0;
        for (int i = 0; i < n; ) {
            char c = t.charAt(i);
            out[i] = at;
            if (c == '\t') {
                int stop = cell * Doc.TAB;
                at = (at / stop + 1) * stop;
                i++;
            } else if (Character.isHighSurrogate(c) && i + 1 < n) {
                out[i + 1] = at;
                at += width(t.codePointAt(i));
                i += 2;
            } else {
                at += width(c);
                i++;
            }
        }
        out[n] = at;
        r.xs = out;
        r.xsStamp = stamp;
        return out;
    }

    int xAt(int line, int col) {
        int[] xs = xs(line);
        return xs[Math.max(0, Math.min(xs.length - 1, col))];
    }

    int colAt(int line, int px) {
        int[] xs = xs(line);
        int lo = 0, hi = xs.length - 1;
        if (px <= 0) return 0;
        if (px >= xs[hi]) return hi;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= px) lo = mid;
            else hi = mid;
        }
        return px - xs[lo] < xs[hi] - px ? lo : hi;
    }

    private int visibleIndex(int line) {
        int[] vis = doc.visible();
        int at = Arrays.binarySearch(vis, line);
        return at >= 0 ? at : Math.max(0, -at - 2);
    }

    int caretGuiX() {
        Doc.Caret c = doc.primary();
        return x + Math.round((gutter + xAt(c.line, c.col) - scrollX) * scale);
    }

    int caretGuiY() {
        Doc.Caret c = doc.primary();
        return y + Math.round((visibleIndex(c.line) * row - scrollY) * scale);
    }

    int rowGuiH() { return Math.max(1, Math.round(row * scale)); }

    int left() { return x; }

    int top() { return y; }

    int right() { return x + w; }

    int bottom() { return y + h; }

    void reveal() {
        if (doc == null) return;
        Doc.Caret c = doc.primary();
        doc.reveal(c.line);
        int vi = visibleIndex(c.line);
        int py = vi * row, view = Math.max(row, viewH());
        if (py < scrollY) scrollY = py;
        else if (py + row > scrollY + view) scrollY = py + row - view;
        int cx = xAt(c.line, c.col), room = Math.max(cell * 4, viewW() - textPad);
        if (cx < scrollX) scrollX = Math.max(0, cx - cell * 6);
        else if (cx > scrollX + room - cell) scrollX = cx - room + cell * 6;
    }

    void center(int line) {
        doc.reveal(line);
        int py = visibleIndex(line) * row;
        scrollY = Math.max(0, py - viewH() / 3);
    }

    void edited() { changed(); }

    private void changed() {
        caretAt = System.currentTimeMillis();
        reveal();
        host.edited();
    }

    private void moved() {
        caretAt = System.currentTimeMillis();
        reveal();
        hints.moved();
        checkSnippet();
    }

    void draw(GuiGraphicsExtractor ctx, int mx, int my) {
        if (doc == null) return;
        metrics();
        stamp = CodeTheme.stamp() * 1000003 + CodeInk.stamp();
        int digits = Math.max(3, String.valueOf(doc.lines()).length());
        gutter = numPad + digits * cell + spotW + foldW + textPad;
        clampScroll();
        mouseX = mx;
        mouseY = my;

        Draw.rect(ctx, x, y, w, h, Draw.opaque(Theme.CANVAS));
        int[] vis = doc.visible();
        int rows = viewH() / row + 2;
        int first = Math.max(0, scrollY / row);
        int last = Math.min(vis.length, first + rows);
        int gutterGui = (int) Math.ceil(gutter * scale);
        int textLeft = x + gutterGui;
        ScreenRectangle clip = new ScreenRectangle(textLeft, y, Math.max(1, x + w - BAR - textLeft), h);
        int ox = gutter - scrollX, oy = -scrollY;

        ctx.enableScissor(textLeft, y, x + w - BAR, y + h);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        Settings look = Settings.get();
        String word = look.codeWords ? highlighted() : null;
        int[] pair = pairAt();
        for (int vi = first; vi < last; vi++) rowBack(ctx, vis[vi], ox, oy + vi * row, word);
        if (look.codeGuides) indentGuides(ctx, vis, first, last, ox, oy);
        if (pair != null) {
            box(ctx, pair[0], pair[1], ox, oy);
            box(ctx, pair[2], pair[3], ox, oy);
        }
        ctx.pose().popMatrix();
        ctx.disableScissor();
        Draw.rect(ctx, x, y, gutterGui, h, Draw.opaque(Theme.PANEL));

        ctx.nextStratum();
        ctx.enableScissor(textLeft, y, x + w - BAR, y + h);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        CodeInk.begin(ctx, clip);
        for (int vi = first; vi < last; vi++) CodeInk.draw(ctx, baked(vis[vi]), ox, oy + vi * row + textY);
        CodeInk.end();
        for (int vi = first; vi < last; vi++) if (doc.row(vis[vi]).folded) foldTail(ctx, vis[vi], ox, oy + vi * row);
        squiggles(ctx, vis, first, last, ox, oy);
        carets(ctx, ox, oy);
        ctx.pose().popMatrix();
        ctx.disableScissor();

        gutter(ctx, vis, first, last, oy, mx, my, gutterGui);
        bars(ctx, vis);
        cursorShape(ctx, mx, my);
        hover(mx, my);
        if (focused) reportText();
    }

    private void reportText() {
        String selected = doc.anySelection() ? doc.selectedAll() : "";
        Env.textDrawn(this, caretGuiX(), caretGuiY() + rowGuiH(), selected.length() > 20000 ? "" : selected, "");
    }

    private void cursorShape(GuiGraphicsExtractor ctx, int mx, int my) {
        boolean over = mx >= x + gutter * scale && mx < x + w - BAR && my >= y && my < y + h;
        if (over) ctx.requestCursor(CursorTypes.IBEAM);
    }

    private void hover(int mx, int my) {
        if (mx != restX || my != restY) {
            restX = mx;
            restY = my;
            restAt = System.currentTimeMillis();
            hints.hoverClear();
            return;
        }
        if (restAt == 0 || System.currentTimeMillis() - restAt < 450 || selectingText) return;
        restAt = 0;
        int[] at = position(mx, my, false);
        if (at == null) return;
        hints.hoverAt(at[0], at[1], mx, my);
    }

    private int[] position(double mx, double my, boolean clamp) {
        if (!clamp && (mx < x + gutter * scale || mx >= x + w - BAR || my < y || my >= y + h)) return null;
        int fy = (int) Math.floor((my - y) / scale) + scrollY;
        int[] vis = doc.visible();
        int vi = Math.max(0, Math.min(vis.length - 1, fy / row));
        if (fy < 0) vi = 0;
        int line = vis[vi];
        int fx = (int) Math.floor((mx - x) / scale) - gutter + scrollX;
        if (!clamp && fx > xAt(line, doc.length(line)) + cell) return null;
        return new int[]{line, colAt(line, fx)};
    }

    private CodeInk.Line baked(int line) {
        Doc.Row r = doc.row(line);
        byte[] ink = doc.ink(line);
        if (r.baked != null && r.bakedStamp == stamp) return (CodeInk.Line) r.baked;
        int[] palette = CodeTheme.palette();
        int n = r.text.length();
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) colors[i] = palette[i < ink.length ? ink[i] : Ink.PLAIN];
        int[] over = r.overlay;
        if (over != null) {
            for (int i = 0; i + 2 < over.length; i += 3)
                for (int k = over[i]; k < over[i + 1] && k < n; k++) colors[k] = over[i + 2];
        }
        CodeInk.Line baked = CodeInk.bake(tr, r.text, colors, xs(line));
        r.baked = baked;
        r.bakedStamp = stamp;
        return baked;
    }

    private String highlighted() {
        Doc.Caret c = doc.primary();
        if (doc.carets().size() > 1) return null;
        if (c.selecting()) {
            if (c.line != c.anchorLine) return null;
            String s = doc.selected(c);
            return s.isBlank() || s.length() > 80 ? null : s;
        }
        String t = doc.line(c.line);
        int a = c.col, b = c.col;
        while (a > 0 && Ink.wordPart(t.charAt(a - 1))) a--;
        while (b < t.length() && Ink.wordPart(t.charAt(b))) b++;
        if (a == b) return null;
        byte[] ink = doc.ink(c.line);
        if (a < ink.length && (ink[a] == Ink.COMMENT || ink[a] == Ink.STRING)) return null;
        return t.substring(a, b);
    }

    private int[] pairAt() {
        Doc.Caret c = doc.primary();
        if (c.selecting()) return null;
        return doc.matching(c.line, c.col);
    }

    private void box(GuiGraphicsExtractor ctx, int line, int col, int ox, int oy) {
        int vi = visibleIndex(line);
        int px = ox + xAt(line, col), py = oy + vi * row;
        int cw = xAt(line, col + 1) - xAt(line, col);
        Draw.rect(ctx, px, py, cw, row, Draw.argb(0x40, Theme.ACCENT));
    }

    private void rowBack(GuiGraphicsExtractor ctx, int line, int ox, int py, String word) {
        boolean current = false;
        for (Doc.Caret c : doc.carets()) if (c.line == line) current = true;
        if (current && !doc.anySelection() && Settings.get().codeLine)
            Draw.rect(ctx, 0, py, (int) (w / scale) + 1, row, Draw.argb(0x18, Theme.TEXT));
        for (int i = rangeFrom(line); i < found.size() && found.get(i).line == line; i++) {
            Found f = found.get(i);
            int a = xAt(line, f.col), b = xAt(line, f.col + f.length);
            Draw.rect(ctx, ox + a, py, Math.max(2, b - a), row,
                    Draw.argb(i == foundCurrent ? 0x90 : 0x48, Theme.WARN));
        }
        if (word != null) {
            String t = doc.line(line);
            int at = t.indexOf(word);
            while (at >= 0) {
                boolean whole = (at == 0 || !Ink.wordPart(t.charAt(at - 1)))
                        && (at + word.length() >= t.length() || !Ink.wordPart(t.charAt(at + word.length())));
                if (whole) {
                    int a = xAt(line, at), b = xAt(line, at + word.length());
                    Draw.rect(ctx, ox + a, py, b - a, row, Draw.argb(0x26, Theme.TEXT));
                }
                at = t.indexOf(word, at + word.length());
            }
        }
        for (Doc.Caret c : doc.carets()) {
            if (!c.selecting() || line < c.startLine() || line > c.endLine()) continue;
            int from = line == c.startLine() ? c.startCol() : 0;
            int to = line == c.endLine() ? c.endCol() : doc.length(line);
            int a = xAt(line, from), b = xAt(line, to);
            if (line != c.endLine()) b += cell / 2;
            Draw.rect(ctx, ox + a, py, Math.max(b - a, 1), row, Draw.argb(0x66, Theme.ACCENT));
        }
    }

    private int rangeFrom(int line) {
        int lo = 0, hi = found.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (found.get(mid).line < line) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private void indentGuides(GuiGraphicsExtractor ctx, int[] vis, int first, int last, int ox, int oy) {
        int color = Draw.argb(0x22, Theme.TEXT);
        int step = cell * Doc.TAB;
        for (int vi = first; vi < last; vi++) {
            int depth = depth(vis, vi);
            for (int k = 1; k < depth; k++) Draw.rect(ctx, ox + k * step, oy + vi * row, stroke, row, color);
        }
    }

    private int depth(int[] vis, int vi) {
        String t = doc.line(vis[vi]);
        if (!t.isBlank()) return Editing.indentOf(t).length() / Doc.TAB + 1;
        for (int k = vi + 1; k < vis.length && k < vi + 20; k++) {
            String next = doc.line(vis[k]);
            if (!next.isBlank()) return Editing.indentOf(next).length() / Doc.TAB + 1;
        }
        return 0;
    }

    private void foldTail(GuiGraphicsExtractor ctx, int line, int ox, int py) {
        int px = ox + xAt(line, doc.length(line)) + cell;
        Draw.rect(ctx, px, py + 1, cell * 3, row - 2, Draw.argb(0x44, Theme.ACCENT));
        int dot = Math.max(1, cell / 5), dy = py + row - row / 3;
        for (int i = 0; i < 3; i++) Draw.rect(ctx, px + cell / 2 + i * cell * 3 / 4, dy, dot, dot, Draw.opaque(Theme.TEXT_DIM));
    }

    private void squiggles(GuiGraphicsExtractor ctx, int[] vis, int first, int last, int ox, int oy) {
        if (problems.isEmpty() || vis.length == 0) return;
        int lowLine = vis[first], highLine = vis[Math.max(first, Math.min(vis.length, last) - 1)];
        for (CodeProject.Problem p : problems) {
            if (p.file() != file || p.line() < lowLine || p.line() > highLine) continue;
            Diagnostic d = p.diagnostic();
            int line = p.line(), a = Math.min(p.col(), doc.length(line));
            int b = Math.min(doc.length(line), a + Math.max(1, d.end() - d.start()));
            if (doc.lineAt(Math.max(d.start(), d.end() - 1)) != line) b = doc.length(line);
            int px = ox + xAt(line, a), width = Math.max(cell, xAt(line, b) - xAt(line, a));
            int py = oy + visibleIndex(line) * row + row - stroke;
            int color = Draw.opaque(d.error() ? Theme.DANGER : Theme.WARN);
            int step = stroke * 2;
            for (int i = 0; i < width; i += step) Draw.rect(ctx, px + i, py - ((i / step) & 1) * stroke, step, stroke, color);
        }
    }

    private void carets(GuiGraphicsExtractor ctx, int ox, int oy) {
        if (!focused) return;
        long since = System.currentTimeMillis() - caretAt;
        if (Settings.get().codeBlink && (since / BLINK) % 2 != 0) return;
        int color = Draw.opaque(Theme.TEXT);
        for (Doc.Caret c : doc.carets())
            Draw.rect(ctx, ox + xAt(c.line, c.col) - stroke, oy + visibleIndex(c.line) * row, stroke + (stroke > 1 ? 1 : 0), row, color);
    }

    private CodeInk.Line number(int line, boolean current) {
        if (numbersStamp != stamp) {
            numbers.clear();
            numbersStamp = stamp;
        }
        int key = line * 2 + (current ? 1 : 0);
        CodeInk.Line known = numbers.get(key);
        if (known != null) return known;
        String s = String.valueOf(line + 1);
        int[] xs = new int[s.length() + 1], colors = new int[s.length()];
        int color = Draw.opaque(current ? Theme.TEXT : Theme.TEXT_FAINT);
        for (int i = 0; i < s.length(); i++) {
            xs[i] = i * cell;
            colors[i] = color;
        }
        CodeInk.Line made = CodeInk.bake(tr, s, colors, xs);
        if (numbers.size() > 4000) numbers.clear();
        numbers.put(key, made);
        return made;
    }

    private int numbersRight() { return gutter - textPad - foldW - spotW; }

    private record RowSpots(List<Dialect.Spot> spots, ItemStack icon) {}

    private RowSpots rowSpots(int line) {
        if (spotW == 0) return null;
        Doc.Row r = doc.row(line);
        byte[] ink = doc.ink(line);
        if (r.spots != null && r.spotsInk == ink) return (RowSpots) r.spots;
        List<Dialect.Spot> found = file.dialect.spots(r.text, ink);
        ItemStack icon = ItemStack.EMPTY;
        if (!found.isEmpty()) icon = stackOf(found.get(0), r.text);
        RowSpots made = new RowSpots(found, icon);
        r.spots = made;
        r.spotsInk = ink;
        return made;
    }

    private ItemStack stackOf(Dialect.Spot s, String text) {
        if ("potion".equals(s.kind())) return Stacks.stack("minecraft:potion");
        boolean stacked = "item".equals(s.kind()) || "block".equals(s.kind());
        if (!stacked || s.end() < 0) return ItemStack.EMPTY;
        try {
            Map<String, Object> json;
            synchronized (file.dialect) {
                json = file.dialect.valueOf(text.substring(s.start(), s.end()));
            }
            Value v = json == null ? null : Importer.fromServer(Json.write(json));
            if (v == null) return ItemStack.EMPTY;
            return Value.ITEM.equals(v.type) ? Stacks.preview(v) : Stacks.stack(v.block);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static String[] kindIcon(String kind) {
        return switch (kind) {
            case "location" -> Draw.PIN;
            case "vector" -> Draw.ARROW_RIGHT;
            case "sound" -> Draw.SPEAKER;
            case "particle" -> Draw.STAR;
            default -> Draw.PACK;
        };
    }

    private void spotIcon(GuiGraphicsExtractor ctx, int line, int gx, int gy, int gh, int mx, int my) {
        RowSpots spots = rowSpots(line);
        if (spots == null || spots.spots.isEmpty()) return;
        Dialect.Spot s = spots.spots.get(0);
        boolean over = Ui.hit(mx, my, gx - 1, gy, SPOT_GUI, gh);
        if (over) {
            Draw.round(ctx, gx - 1, gy, SPOT_GUI, gh, 2, Draw.opaque(Theme.SURFACE_HOVER));
            ctx.setComponentTooltipForNextFrame(tr, List.of(Component.literal("Изменить значение  Alt+Enter")), mx, my);
        }
        if (!spots.icon.isEmpty()) {
            int size = Math.min(SPOT_GUI - 2, gh);
            Draw.item(ctx, spots.icon, gx, gy + (gh - size) / 2, size);
            return;
        }
        String[] icon = kindIcon(s.kind());
        Draw.glyph(ctx, icon, gx + (SPOT_GUI - 2 - Draw.glyphW(icon)) / 2, gy + (gh - Draw.glyphH(icon)) / 2,
                Values.color(s.kind()));
    }

    int[] valueRange(int line, int col) {
        if (file == null || file.dialect == null) return null;
        String t = doc.line(line);
        byte[] ink = doc.ink(line);
        for (Dialect.Spot s : file.dialect.spots(t, ink)) {
            int[] whole = spotRange(line, s);
            boolean inside = Doc.compare(line, col, line, s.start()) >= 0
                    && Doc.compare(line, col, whole[2], whole[3]) <= 0;
            if (inside) return whole;
        }
        int a = Math.min(col, ink.length - 1);
        if (a < 0 || !literal(ink[a])) a = Math.min(col - 1, ink.length - 1);
        if (a < 0 || !literal(ink[a])) return null;
        int from = a, to = a;
        while (from > 0 && literal(ink[from - 1])) from--;
        while (to + 1 < ink.length && literal(ink[to + 1])) to++;
        while (from > 0 && ink[from - 1] == Ink.ESCAPE) from--;
        return new int[]{line, from, line, to + 1};
    }

    private static boolean literal(byte kind) {
        return kind == Ink.STRING || kind == Ink.ESCAPE || kind == Ink.PLACEHOLDER;
    }

    private int[] spotRange(int line, Dialect.Spot s) {
        if (s.end() >= 0) return new int[]{line, s.start(), line, s.end()};
        String t = doc.line(line);
        int open = s.start();
        while (open < t.length() && t.charAt(open) != '(' && t.charAt(open) != '{') open++;
        int[] pair = doc.matching(line, open + 1);
        return pair == null ? new int[]{line, s.start(), line, t.length()} : new int[]{line, s.start(), pair[2], pair[3] + 1};
    }

    private void gutter(GuiGraphicsExtractor ctx, int[] vis, int first, int last, int oy, int mx, int my, int gutterGui) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        Doc.Caret main = doc.primary();
        int numbersRight = gutter - textPad - foldW - spotW;
        CodeInk.begin(ctx, new ScreenRectangle(x, y, Math.max(1, gutterGui), h));
        for (int vi = first; vi < last; vi++) {
            int line = vis[vi];
            int digits = String.valueOf(line + 1).length();
            CodeInk.draw(ctx, number(line, line == main.line), numbersRight - digits * cell, oy + vi * row + textY);
        }
        CodeInk.end();
        ctx.pose().popMatrix();
        ctx.enableScissor(x, y, x + gutterGui, y + h);
        boolean hoverGutter = mx >= x && mx < x + gutterGui;
        for (int vi = first; vi < last; vi++) {
            int line = vis[vi];
            int gy = y + Math.round((oy + vi * row) * scale), gh = rowGuiH();
            int mark = mark(line);
            if (mark != 0) Draw.rect(ctx, x + 1, gy + 1, 2, Math.max(1, gh - 2), Draw.opaque(mark));
            spotIcon(ctx, line, x + (int) (numbersRight() * scale) + 1, gy, gh, mx, my);
            if ((hoverGutter || doc.row(line).folded) && doc.foldable(line)) {
                String[] icon = doc.row(line).folded ? Draw.CARET_RIGHT : Draw.CARET_DOWN;
                Draw.glyph(ctx, icon, x + gutterGui - Math.round(textPad * scale) - FOLD_GUI + 2,
                        gy + (gh - Draw.glyphH(icon)) / 2, doc.row(line).folded ? Theme.ACCENT : Theme.TEXT_DIM);
            }
        }
        ctx.disableScissor();
    }

    private int mark(int line) {
        int color = 0;
        for (CodeProject.Problem p : problems) {
            if (p.file() != file || p.line() != line) continue;
            if (p.diagnostic().error()) return Theme.DANGER;
            color = Theme.WARN;
        }
        return color;
    }

    private void bars(GuiGraphicsExtractor ctx, int[] vis) {
        int bx = x + w - BAR;
        Draw.rect(ctx, bx, y, BAR, h, Draw.opaque(Theme.PANEL));
        int content = contentH() + viewH() / 2;
        int view = viewH();
        if (content > 0) {
            for (CodeProject.Problem p : problems) {
                if (p.file() != file) continue;
                int at = y + (int) ((long) visibleIndex(Math.min(p.line(), doc.lines() - 1)) * row * h / content);
                Draw.rect(ctx, bx, at, BAR, 2, Draw.opaque(p.diagnostic().error() ? Theme.DANGER : Theme.WARN));
            }
            for (int i = 0; i < found.size() && i < 2000; i += Math.max(1, found.size() / 400)) {
                int at = y + (int) ((long) visibleIndex(found.get(i).line) * row * h / content);
                Draw.rect(ctx, bx + 1, at, BAR - 2, 1, Draw.opaque(Theme.WARN));
            }
        }
        if (content <= view) return;
        int thumb = Math.max(MIN_THUMB, (int) ((long) h * view / content));
        int at = y + (int) ((long) (h - thumb) * scrollY / Math.max(1, content - view));
        boolean hover = mouseX >= bx - 1 && mouseX <= x + w && mouseY >= y && mouseY < y + h;
        Draw.rect(ctx, bx + 1, at, BAR - 2, thumb, Draw.argb(draggingBar || hover ? 0x90 : 0x50, Theme.TEXT));
    }

    boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (doc == null) return false;
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) return false;
        focus(true);
        int mods = click.modifiers();
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        if (mx >= x + w - BAR - 1) {
            draggingBar = true;
            scrollToBar(my);
            return true;
        }
        if (click.button() == 1) {
            int[] at = position(mx, my, true);
            boolean inside = false;
            for (Doc.Caret c : doc.carets()) {
                if (c.selecting() && Doc.compare(at[0], at[1], c.startLine(), c.startCol()) >= 0
                        && Doc.compare(at[0], at[1], c.endLine(), c.endCol()) <= 0) inside = true;
            }
            if (!inside) doc.single(at[0], at[1]);
            moved();
            host.contextMenu((int) mx, (int) my);
            return true;
        }
        if (click.button() != 0) return true;
        if (mx < x + gutter * scale) {
            gutterClick(mx, my, shift);
            return true;
        }
        int[] at = position(mx, my, true);
        long now = System.currentTimeMillis();
        boolean near = at[0] == clickLine && Math.abs(at[1] - clickCol) <= 1;
        clickCount = near && now - lastClick < DOUBLE_MS ? clickCount + 1 : 1;
        lastClick = now;
        clickLine = at[0];
        clickCol = at[1];
        if (ctrl && clickCount == 1) {
            doc.single(at[0], at[1]);
            moved();
            host.definition(at[0], at[1]);
            return true;
        }
        hints.close();
        if (alt && clickCount == 1) {
            doc.addCaret(at[0], at[1]);
            selectingText = false;
            caretAt = now;
            return true;
        }
        selectingText = true;
        selectionWord = 0;
        if (clickCount == 2) {
            Editing.selectWordAt(doc, at[0], at[1]);
            Doc.Caret c = doc.primary();
            anchorLine = c.startLine();
            anchorCol = c.startCol();
            anchorEndCol = c.endCol();
            selectionWord = 1;
        } else if (clickCount >= 3) {
            int end = Math.min(doc.lines() - 1, at[0] + 1);
            doc.select(at[0], 0, end, end == at[0] ? doc.length(at[0]) : 0);
            anchorLine = at[0];
            selectionWord = 2;
        } else if (shift) {
            doc.singleKeep();
            doc.primary().drag(at[0], at[1]);
            doc.broke();
        } else doc.single(at[0], at[1]);
        moved();
        return true;
    }

    private void gutterClick(double mx, double my, boolean shift) {
        int[] at = position(mx, my, true);
        int line = at[0];
        double fx = (mx - x) / scale;
        RowSpots spots = rowSpots(line);
        if (spots != null && !spots.spots.isEmpty() && fx >= numbersRight() && fx < numbersRight() + spotW) {
            int[] range = spotRange(line, spots.spots.get(0));
            doc.select(range[0], range[1], range[2], range[3]);
            moved();
            host.editValueOn(range[0], range[1], range[3]);
            return;
        }
        if (fx >= gutter - textPad - foldW && doc.foldable(line)) {
            Doc.Row r = doc.row(line);
            r.folded = !r.folded;
            doc.foldChanged();
            return;
        }
        int end = Math.min(doc.lines() - 1, line + 1);
        doc.select(line, 0, end, end == line ? doc.length(line) : 0);
        anchorLine = line;
        selectionWord = 2;
        selectingText = true;
        moved();
    }

    boolean mouseDragged(MouseButtonEvent click) {
        if (draggingBar) {
            scrollToBar(click.y());
            return true;
        }
        if (!selectingText || doc == null) return false;
        mouseX = click.x();
        mouseY = click.y();
        dragTo(mouseX, mouseY);
        return true;
    }

    private void dragTo(double mx, double my) {
        int[] at = position(mx, my, true);
        Doc.Caret c = doc.primary();
        if (selectionWord == 1) {
            String t = doc.line(at[0]);
            int a = at[1], b = at[1];
            while (a > 0 && Ink.wordPart(t.charAt(a - 1))) a--;
            while (b < t.length() && Ink.wordPart(t.charAt(b))) b++;
            boolean before = Doc.compare(at[0], at[1], anchorLine, anchorCol) < 0;
            if (before) {
                c.anchorLine = anchorLine;
                c.anchorCol = anchorEndCol;
                c.drag(at[0], a);
            } else {
                c.anchorLine = anchorLine;
                c.anchorCol = anchorCol;
                c.drag(at[0], b);
            }
        } else if (selectionWord == 2) {
            boolean before = at[0] < anchorLine;
            int end = Math.min(doc.lines() - 1, (before ? anchorLine : at[0]) + 1);
            int start = before ? at[0] : anchorLine;
            int endCol = end == (before ? anchorLine : at[0]) ? doc.length(end) : 0;
            if (before) doc.select(end, endCol, start, 0);
            else doc.select(start, 0, end, endCol);
        } else c.drag(at[0], at[1]);
        caretAt = System.currentTimeMillis();
    }

    void frame() {
        if (!selectingText || doc == null) return;
        if (!Env.leftHeld()) {
            selectingText = false;
            return;
        }
        int step = Math.max(1, row / 2);
        if (mouseY < y) scrollY -= step;
        else if (mouseY >= y + h) scrollY += step;
        else if (mouseX < x + gutter * scale) scrollX = Math.max(0, scrollX - cell * 2);
        else if (mouseX >= x + w - BAR) scrollX += cell * 2;
        else return;
        clampScroll();
        dragTo(mouseX, mouseY);
    }

    void mouseReleased() {
        selectingText = false;
        draggingBar = false;
    }

    private void scrollToBar(double my) {
        int content = contentH() + viewH() / 2, view = viewH();
        if (content <= view) return;
        int thumb = Math.max(MIN_THUMB, (int) ((long) h * view / content));
        double t = (my - y - thumb / 2.0) / Math.max(1, h - thumb);
        scrollY = (int) (Math.max(0, Math.min(1, t)) * (content - view));
        clampScroll();
    }

    boolean mouseScrolled(double mx, double my, double dx, double dy, int mods) {
        if (doc == null || !contains(mx, my)) return false;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            zoom(dy > 0 ? 1 : -1);
            return true;
        }
        hints.hoverClear();
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0 || dx != 0 && Math.abs(dx) > Math.abs(dy)) {
            double amount = dx != 0 ? dx : dy;
            scrollX = Math.max(0, scrollX - (int) Math.round(amount * cell * 6));
            return true;
        }
        scrollY -= (int) Math.round(dy * row * 3);
        clampScroll();
        return true;
    }

    private static boolean has(int mods, int bit) { return (mods & bit) != 0; }

    boolean keyPressed(KeyEvent e) {
        if (doc == null || !focused) return false;
        int key = e.key(), m = e.modifiers();
        boolean ctrl = has(m, GLFW.GLFW_MOD_CONTROL), shift = has(m, GLFW.GLFW_MOD_SHIFT), alt = has(m, GLFW.GLFW_MOD_ALT);
        if (hints.keyPressed(key, ctrl)) return true;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (alt && !shift && !ctrl) { host.back(); return true; }
                horizontal(-1, ctrl, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (alt && !shift && !ctrl) { host.forward(); return true; }
                horizontal(1, ctrl, shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                int dir = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                if (ctrl && alt) {
                    Editing.addCaretVertical(doc, dir);
                    moved();
                } else if (alt && shift) {
                    Editing.duplicateRows(doc, dir < 0);
                    changed();
                } else if (alt) {
                    Editing.moveRows(doc, dir);
                    changed();
                } else if (ctrl) {
                    scrollY += dir * row;
                    clampScroll();
                } else vertical(dir, shift, 1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                vertical(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1, shift, Math.max(1, viewH() / row - 1));
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                home(ctrl, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                end(ctrl, shift);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                Editing.backspace(doc, ctrl);
                changed();
                hints.deleted();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (shift) {
                    cut();
                    return true;
                }
                Editing.delete(doc, ctrl);
                changed();
                hints.deleted();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (alt) {
                    host.editValue();
                    return true;
                }
                if (ctrl && shift) Editing.newLineAbove(doc);
                else if (ctrl) Editing.newLineBelow(doc);
                else Editing.enter(doc);
                stops = null;
                changed();
                hints.close();
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (stops != null) {
                    nextStop(shift ? -1 : 1);
                    return true;
                }
                if (shift) Editing.shift(doc, -1);
                else Editing.tab(doc);
                changed();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (stops != null) {
                    stops = null;
                    return true;
                }
                if (doc.carets().size() > 1) {
                    doc.singleKeep();
                    return true;
                }
                if (doc.primary().selecting()) {
                    Doc.Caret c = doc.primary();
                    c.place(c.line, c.col);
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_F2 -> {
                host.rename();
                return true;
            }
            case GLFW.GLFW_KEY_F8 -> {
                host.nextProblem(shift ? -1 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_F12 -> {
                Doc.Caret c = doc.primary();
                if (shift) host.references();
                else host.definition(c.line, c.col);
                return true;
            }
            default -> { }
        }
        if (shift && alt && key == GLFW.GLFW_KEY_F) {
            host.format();
            return true;
        }
        if (!ctrl) return false;
        switch (key) {
            case GLFW.GLFW_KEY_Z -> {
                if (shift) redo();
                else undo();
                return true;
            }
            case GLFW.GLFW_KEY_Y -> {
                redo();
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                int last = doc.lines() - 1;
                doc.select(0, 0, last, doc.length(last));
                return true;
            }
            case GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_INSERT -> {
                copy();
                return true;
            }
            case GLFW.GLFW_KEY_X -> {
                cut();
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                paste(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            case GLFW.GLFW_KEY_D -> {
                Editing.selectNext(doc);
                moved();
                return true;
            }
            case GLFW.GLFW_KEY_L -> {
                if (shift) {
                    Editing.selectAllMatches(doc);
                    moved();
                    return true;
                }
                Doc.Caret c = doc.primary();
                int end = Math.min(doc.lines() - 1, c.endLine() + 1);
                doc.select(c.startLine(), 0, end, end == c.endLine() ? doc.length(end) : 0);
                moved();
                return true;
            }
            case GLFW.GLFW_KEY_K -> {
                if (shift) {
                    Editing.deleteRows(doc);
                    changed();
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_SLASH -> {
                Editing.toggleComment(doc);
                changed();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_LEFT_BRACKET -> {
                if (shift) fold(key == GLFW.GLFW_KEY_LEFT_BRACKET);
                else {
                    Editing.shift(doc, key == GLFW.GLFW_KEY_RIGHT_BRACKET ? 1 : -1);
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                if (shift) hints.askSignature();
                else hints.ask();
                return true;
            }
            case GLFW.GLFW_KEY_PERIOD -> {
                host.fix();
                return true;
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                zoom(1);
                return true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                zoom(-1);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    boolean charTyped(CharacterEvent e) {
        if (doc == null || !focused) return false;
        int cp = e.codepoint();
        if (cp < 32 || cp == 127) return false;
        typeText(new String(Character.toChars(cp)));
        return true;
    }

    void typeText(String s) {
        Editing.type(doc, s);
        changed();
        trackStop();
        hints.typed(s);
    }

    private void fold(boolean close) {
        Doc.Caret c = doc.primary();
        for (int l = c.line; l >= 0 && l >= c.line - 400; l--) {
            if (!doc.foldable(l)) continue;
            if (doc.foldEnd(l) < c.line && l != c.line) continue;
            Doc.Row r = doc.row(l);
            if (r.folded == close) continue;
            r.folded = close;
            doc.foldChanged();
            if (close) {
                doc.single(l, doc.length(l));
                moved();
            }
            return;
        }
    }

    void foldAll(boolean close) {
        for (int l = 0; l < doc.lines(); l++) {
            if (close && Editing.indentOf(doc.line(l)).isEmpty() && doc.foldable(l)) doc.row(l).folded = true;
            else if (!close) doc.row(l).folded = false;
        }
        doc.foldChanged();
        Doc.Caret c = doc.primary();
        doc.single(c.line, c.col);
        moved();
    }

    void undo() {
        if (doc.undo()) changed();
        stops = null;
        hints.close();
    }

    void redo() {
        if (doc.redo()) changed();
        stops = null;
        hints.close();
    }

    void copy() { Minecraft.getInstance().keyboardHandler.setClipboard(Editing.copy(doc)); }

    void cut() {
        Minecraft.getInstance().keyboardHandler.setClipboard(Editing.cut(doc));
        changed();
    }

    void paste(String text) {
        if (text == null || text.isEmpty()) return;
        Editing.paste(doc, text);
        changed();
        hints.close();
    }

    private void horizontal(int dir, boolean word, boolean extend) {
        for (Doc.Caret c : doc.carets()) {
            if (!extend && !word && c.selecting()) {
                int l = dir < 0 ? c.startLine() : c.endLine();
                int k = dir < 0 ? c.startCol() : c.endCol();
                c.place(l, k);
                continue;
            }
            int line = c.line, col = c.col;
            String t = doc.line(line);
            if (dir < 0) {
                if (col == 0) {
                    if (line > 0) {
                        line--;
                        col = doc.length(line);
                    }
                } else if (word) col = Editing.wordLeft(t, col);
                else col -= col >= 2 && Character.isLowSurrogate(t.charAt(col - 1)) ? 2 : 1;
            } else {
                if (col >= t.length()) {
                    if (line + 1 < doc.lines()) {
                        line++;
                        col = 0;
                    }
                } else if (word) col = Editing.wordRight(t, col);
                else col += Character.isHighSurrogate(t.charAt(col)) && col + 1 < t.length() ? 2 : 1;
            }
            if (extend) c.drag(line, col);
            else c.place(line, col);
        }
        doc.merge();
        doc.broke();
        moved();
    }

    private void vertical(int dir, boolean extend, int steps) {
        int[] vis = doc.visible();
        for (Doc.Caret c : doc.carets()) {
            int goal = c.goalX >= 0 ? c.goalX : xAt(c.line, c.col);
            int vi = visibleIndex(c.line);
            int next = Math.max(0, Math.min(vis.length - 1, vi + dir * steps));
            int line = vis[next];
            int col = next == vi ? (dir < 0 ? 0 : doc.length(line)) : colAt(line, goal);
            if (extend) c.drag(line, col);
            else c.place(line, col);
            c.goalX = goal;
        }
        doc.merge();
        doc.broke();
        moved();
    }

    private void home(boolean ctrl, boolean extend) {
        for (Doc.Caret c : doc.carets()) {
            int line = ctrl ? 0 : c.line;
            int lead = Editing.indentOf(doc.line(line)).length();
            int col = ctrl ? 0 : c.col == lead ? 0 : lead;
            if (extend) c.drag(line, col);
            else c.place(line, col);
        }
        doc.merge();
        doc.broke();
        moved();
    }

    private void end(boolean ctrl, boolean extend) {
        for (Doc.Caret c : doc.carets()) {
            int line = ctrl ? doc.lines() - 1 : c.line;
            int col = doc.length(line);
            if (extend) c.drag(line, col);
            else c.place(line, col);
        }
        doc.merge();
        doc.broke();
        moved();
    }

    void insertSnippet(int from, int to, String body, boolean again) {
        int line = doc.lineAt(from);
        String lead = Editing.indentOf(doc.line(line));
        Snippet s = Snippet.parse(body, lead);
        doc.begin(Doc.OTHER);
        doc.singleKeep();
        doc.replace(line, doc.colAt(from), doc.lineAt(to), doc.colAt(to), s.text);
        doc.single(doc.lineAt(from + s.text.length()), doc.colAt(from + s.text.length()));
        doc.end();
        stops = null;
        if (!s.stops.isEmpty()) {
            for (Snippet.Stop st : s.stops) {
                st.start += from;
                st.end += from;
            }
            boolean onlyEnd = s.stops.size() == 1 && s.stops.get(0).order == 0;
            if (onlyEnd) {
                Snippet.Stop st = s.stops.get(0);
                doc.single(doc.lineAt(st.start), doc.colAt(st.start));
            } else {
                stops = s.stops.toArray(new Snippet.Stop[0]);
                stopAt = 0;
                goToStop();
            }
        }
        changed();
        if (again) hints.ask();
    }

    private void goToStop() {
        Snippet.Stop st = stops[stopAt];
        doc.select(doc.lineAt(st.start), doc.colAt(st.start), doc.lineAt(st.end), doc.colAt(st.end));
        stopBase = doc.text().length();
        stopLength = st.end - st.start;
        if (st.order == 0) stops = null;
        reveal();
    }

    private void nextStop(int dir) {
        int next = stopAt + dir;
        if (next < 0 || next >= stops.length) {
            stops = null;
            return;
        }
        stopAt = next;
        goToStop();
    }

    private void trackStop() {
        if (stops == null) return;
        int delta = doc.text().length() - stopBase;
        if (delta == 0) return;
        stopBase += delta;
        Snippet.Stop st = stops[stopAt];
        st.end += delta;
        for (int i = 0; i < stops.length; i++) {
            if (i == stopAt) continue;
            Snippet.Stop other = stops[i];
            if (other.start >= st.start) {
                other.start += delta;
                other.end += delta;
            }
        }
    }

    private void checkSnippet() {
        if (stops == null) return;
        Doc.Caret c = doc.primary();
        int at = doc.offset(c.line, c.col);
        Snippet.Stop st = stops[stopAt];
        if (at < st.start || at > st.end) stops = null;
    }

    void goTo(int line, int col) {
        int l = doc.clampLine(line);
        doc.single(l, col);
        doc.reveal(l);
        center(l);
        caretAt = System.currentTimeMillis();
        hints.close();
    }

    void select(int line, int col, int endLine, int endCol) {
        doc.select(line, col, endLine, endCol);
        doc.reveal(line);
        reveal();
    }

    void applyEdits(List<int[]> ranges, List<String> texts) {
        doc.begin(Doc.OTHER);
        for (int i = ranges.size() - 1; i >= 0; i--) {
            int[] r = ranges.get(i);
            doc.replace(doc.lineAt(r[0]), doc.colAt(r[0]), doc.lineAt(r[1]), doc.colAt(r[1]), texts.get(i));
        }
        doc.end();
        changed();
    }
}
