package com.xerocode.ui;

import com.xerocode.Env;
import com.xerocode.code.CodeFile;
import com.xerocode.code.CodeProject;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import xero.ide.Service;

final class CodeSide {
    private static final int HEAD = 16, PAD = 6, ICON = 12;

    interface Host {
        CodeProject project();
        List<Service.Symbol> outline();
        void openFile(CodeFile file);
        void newFile(int x, int y);
        void fileMenu(CodeFile file, int x, int y);
        void symbol(Service.Symbol symbol);
    }

    private final Font tr;
    private final Host host;
    private int x, y, w, h;
    private int fileTop, symbolTop;
    private int split = -1;
    private boolean draggingSplit;

    CodeSide(Font tr, Host host) {
        this.tr = tr;
        this.host = host;
    }

    private static int rowH() { return Env.touch() ? 20 : 13; }

    void place(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    private int splitY() {
        int at = split < 0 ? y + h * 3 / 5 : split;
        return Math.max(y + HEAD * 2, Math.min(y + h - HEAD * 2, at));
    }

    void draw(GuiGraphicsExtractor ctx, int mx, int my) {
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, x + w - 1, y, 1, h, Draw.opaque(Theme.LINE));
        int sy = splitY();
        header(ctx, "ФАЙЛЫ", y, mx, my, true);
        ctx.enableScissor(x, y + HEAD, x + w - 1, sy);
        drawFiles(ctx, mx, my, sy);
        ctx.disableScissor();
        Draw.rect(ctx, x, sy, w - 1, 1, Draw.opaque(Theme.LINE));
        header(ctx, "СТРУКТУРА", sy + 1, mx, my, false);
        ctx.enableScissor(x, sy + HEAD, x + w - 1, y + h);
        drawSymbols(ctx, mx, my, sy + HEAD);
        ctx.disableScissor();
    }

    private void header(GuiGraphicsExtractor ctx, String title, int top, int mx, int my, boolean plus) {
        Draw.textFit(ctx, tr, title, x + PAD, top + 4, w - PAD * 2 - ICON, Theme.TEXT_FAINT, false);
        if (!plus) return;
        int bx = x + w - ICON - 4, by = top + 2;
        if (Ui.hit(mx, my, bx, by, ICON, ICON)) Draw.round(ctx, bx, by, ICON, ICON, 2, Draw.opaque(Theme.SURFACE_HOVER));
        Draw.glyph(ctx, Draw.PLUS, bx + (ICON - Draw.glyphW(Draw.PLUS)) / 2, by + (ICON - Draw.glyphH(Draw.PLUS)) / 2,
                Theme.TEXT_DIM);
    }

    private void drawFiles(GuiGraphicsExtractor ctx, int mx, int my, int bottom) {
        CodeProject p = host.project();
        int rh = rowH();
        int content = p.files.size() * rh + folderRows(p) * rh;
        int view = bottom - y - HEAD;
        fileTop = Math.max(0, Math.min(Math.max(0, content - view), fileTop));
        int ry = y + HEAD - fileTop;
        String folder = "";
        for (CodeFile f : p.files) {
            if (!f.folder().equals(folder)) {
                folder = f.folder();
                if (!folder.isEmpty()) {
                    Draw.textFit(ctx, tr, folder + "/", x + PAD, ry + (rh - 8) / 2, w - PAD * 2, Theme.TEXT_FAINT, false);
                    ry += rh;
                }
            }
            boolean active = f == p.active;
            boolean over = Ui.hit(mx, my, x, ry, w - 1, rh) && my >= y + HEAD && my < bottom;
            if (active) Draw.rect(ctx, x, ry, w - 1, rh, Draw.argb(0x40, Theme.ACCENT));
            else if (over) Draw.rect(ctx, x, ry, w - 1, rh, Draw.opaque(Theme.SURFACE_HOVER));
            int ix = x + PAD + (f.folder().isEmpty() ? 0 : 6);
            String mark = mark(f);
            Draw.text(ctx, tr, mark, ix, ry + (rh - 8) / 2, color(f), false);
            int tx = ix + tr.width(mark) + 4;
            boolean dirty = f.doc != null && f.doc.dirty();
            Draw.textFit(ctx, tr, f.name(), tx, ry + (rh - 8) / 2, x + w - 8 - tx - (dirty ? 6 : 0),
                    active ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (dirty) Draw.dot(ctx, x + w - 8, ry + rh / 2 - 1, Draw.opaque(Theme.WARN));
            ry += rh;
        }
    }

    private int folderRows(CodeProject p) {
        int n = 0;
        String folder = "";
        for (CodeFile f : p.files) {
            if (!f.folder().equals(folder)) {
                folder = f.folder();
                if (!folder.isEmpty()) n++;
            }
        }
        return n;
    }

    private static String mark(CodeFile f) {
        return f.dialect == null ? "·" : switch (f.dialect.id()) {
            case "xero" -> "X";
            case "jmcc" -> "J";
            case "jmscript" -> "S";
            case "justice" -> "I";
            case "json" -> "{";
            default -> "·";
        };
    }

    private static int color(CodeFile f) {
        return f.dialect == null ? Theme.TEXT_FAINT : switch (f.dialect.id()) {
            case "xero" -> Theme.ACCENT;
            case "jmcc" -> 0xF2A36B;
            case "jmscript" -> 0x6FDCC2;
            case "justice" -> 0xBFA4F5;
            default -> Theme.TEXT_DIM;
        };
    }

    private void drawSymbols(GuiGraphicsExtractor ctx, int mx, int my, int top) {
        List<Service.Symbol> list = host.outline();
        int rh = rowH();
        int view = y + h - top;
        symbolTop = Math.max(0, Math.min(Math.max(0, list.size() * rh - view), symbolTop));
        int ry = top - symbolTop;
        for (Service.Symbol s : list) {
            if (ry + rh >= top && ry < y + h) {
                boolean over = Ui.hit(mx, my, x, ry, w - 1, rh) && my >= top;
                if (over) Draw.rect(ctx, x, ry, w - 1, rh, Draw.opaque(Theme.SURFACE_HOVER));
                String mark = kindMark(s.kind());
                Draw.text(ctx, tr, mark, x + PAD, ry + (rh - 8) / 2, kindColor(s.kind()), false);
                Draw.textFit(ctx, tr, s.name(), x + PAD + 10, ry + (rh - 8) / 2, w - PAD * 2 - 11, Theme.TEXT_DIM, false);
            }
            ry += rh;
        }
    }

    private static String kindMark(String kind) {
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "event" -> "с";
            case "process" -> "п";
            default -> "ф";
        };
    }

    private static int kindColor(String kind) {
        return CodeTheme.kindColor("event".equals(kind) ? "event" : "function");
    }

    boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, w, h); }

    boolean mouseClicked(MouseButtonEvent click) {
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) return false;
        int sy = splitY();
        if (Math.abs(my - sy) <= 2) {
            draggingSplit = true;
            return true;
        }
        if (my < y + HEAD) {
            if (mx >= x + w - ICON - 6) host.newFile((int) mx, (int) my);
            return true;
        }
        int rh = rowH();
        if (my < sy) {
            CodeFile hit = fileAt(my, rh);
            if (hit == null) {
                if (click.button() == 1) host.newFile((int) mx, (int) my);
                return true;
            }
            if (click.button() == 1) host.fileMenu(hit, (int) mx, (int) my);
            else host.openFile(hit);
            return true;
        }
        int row = (int) ((my - sy - HEAD + symbolTop) / rh);
        List<Service.Symbol> list = host.outline();
        if (row >= 0 && row < list.size()) host.symbol(list.get(row));
        return true;
    }

    private CodeFile fileAt(double my, int rh) {
        CodeProject p = host.project();
        int ry = y + HEAD - fileTop;
        String folder = "";
        for (CodeFile f : p.files) {
            if (!f.folder().equals(folder)) {
                folder = f.folder();
                if (!folder.isEmpty()) ry += rh;
            }
            if (my >= ry && my < ry + rh) return f;
            ry += rh;
        }
        return null;
    }

    boolean mouseDragged(MouseButtonEvent click) {
        if (!draggingSplit) return false;
        split = (int) click.y();
        return true;
    }

    void mouseReleased() { draggingSplit = false; }

    boolean mouseScrolled(double mx, double my, double dy) {
        if (!contains(mx, my)) return false;
        int step = (int) Math.round(dy * rowH() * 2);
        if (my < splitY()) fileTop -= step;
        else symbolTop -= step;
        return true;
    }
}
