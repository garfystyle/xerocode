package com.xerocode.ui;

import com.xerocode.Env;
import com.xerocode.code.CodeFile;
import com.xerocode.code.CodeProject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import xero.lang.Diagnostic;

final class CodeBottom {
    static final int PROBLEMS = 0, OUTPUT = 1, RESULTS = 2;
    private static final int HEAD = 16, PAD = 6, OUTPUT_MAX = 500;
    private static final String[] TABS = {"ПРОБЛЕМЫ", "ВЫВОД", "НАЙДЕНО"};

    record Result(CodeFile file, int line, int col, int length, String text) {}

    interface Host {
        List<CodeProject.Problem> problems();
        void goTo(CodeFile file, int line, int col);
        void fixProblem(CodeProject.Problem p, int x, int y);
    }

    private final Font tr;
    private final Host host;
    private int x, y, w, h;
    private int tab;
    private int top;
    private final List<String> output = new ArrayList<>();
    private List<Result> results = List.of();
    private String resultsTitle = "";

    CodeBottom(Font tr, Host host) {
        this.tr = tr;
        this.host = host;
    }

    private static int rowH() { return Env.touch() ? 20 : 12; }

    void place(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    void show(int which) {
        tab = which;
        top = 0;
    }

    int tab() { return tab; }

    void say(String line) {
        output.add(line);
        while (output.size() > OUTPUT_MAX) output.remove(0);
        if (tab == OUTPUT) top = Integer.MAX_VALUE;
    }

    void results(String title, List<Result> found) {
        resultsTitle = title;
        results = found;
        show(RESULTS);
    }

    void draw(GuiGraphicsExtractor ctx, int mx, int my) {
        Draw.rect(ctx, x, y, w, h, Draw.opaque(Theme.PANEL));
        Draw.rect(ctx, x, y, w, 1, Draw.opaque(Theme.LINE));
        int tx = x + PAD;
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i] + count(i);
            int bw = tr.width(label) + 10;
            boolean over = Ui.hit(mx, my, tx, y + 1, bw, HEAD - 1);
            if (i == tab) Draw.rect(ctx, tx, y + HEAD - 2, bw, 1, Draw.opaque(Theme.ACCENT));
            Draw.text(ctx, tr, label, tx + 5, y + 5, i == tab ? Theme.TEXT : over ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
            tx += bw + 2;
        }
        ctx.enableScissor(x, y + HEAD, x + w, y + h);
        switch (tab) {
            case PROBLEMS -> problems(ctx, mx, my);
            case OUTPUT -> lines(ctx);
            default -> found(ctx, mx, my);
        }
        ctx.disableScissor();
    }

    private String count(int which) {
        return switch (which) {
            case PROBLEMS -> host.problems().isEmpty() ? "" : " " + host.problems().size();
            case RESULTS -> results.isEmpty() ? "" : " " + results.size();
            default -> "";
        };
    }

    private int viewRows() { return Math.max(1, (h - HEAD) / rowH()); }

    private void clampTop(int total) { top = Math.max(0, Math.min(Math.max(0, total - viewRows()), top)); }

    private void problems(GuiGraphicsExtractor ctx, int mx, int my) {
        List<CodeProject.Problem> list = host.problems();
        clampTop(list.size());
        int rh = rowH();
        if (list.isEmpty()) {
            Draw.text(ctx, tr, "проблем нет", x + PAD, y + HEAD + 3, Theme.TEXT_FAINT, false);
            return;
        }
        for (int i = top; i < list.size() && i < top + viewRows() + 1; i++) {
            CodeProject.Problem p = list.get(i);
            int ry = y + HEAD + (i - top) * rh;
            if (Ui.hit(mx, my, x, ry, w, rh)) Draw.rect(ctx, x, ry, w, rh, Draw.opaque(Theme.SURFACE_HOVER));
            Diagnostic d = p.diagnostic();
            int ty = ry + (rh - 8) / 2;
            Draw.dot(ctx, x + PAD, ry + rh / 2 - 1, Draw.opaque(d.error() ? Theme.DANGER : Theme.WARN));
            String where = p.file().name() + ":" + (p.line() + 1);
            int ww = tr.width(where);
            Draw.textFit(ctx, tr, d.message(), x + PAD + 8, ty, w - PAD * 2 - 16 - ww, Theme.TEXT, false);
            Draw.text(ctx, tr, where, x + w - PAD - ww, ty, Theme.TEXT_FAINT, false);
        }
    }

    private void lines(GuiGraphicsExtractor ctx) {
        clampTop(output.size());
        int rh = rowH();
        for (int i = top; i < output.size() && i < top + viewRows() + 1; i++)
            Draw.textFit(ctx, tr, output.get(i), x + PAD, y + HEAD + (i - top) * rh + (rh - 8) / 2, w - PAD * 2, Theme.TEXT_DIM, false);
    }

    private void found(GuiGraphicsExtractor ctx, int mx, int my) {
        clampTop(results.size() + 1);
        int rh = rowH();
        if (top == 0) Draw.textFit(ctx, tr, resultsTitle, x + PAD, y + HEAD + (rh - 8) / 2, w - PAD * 2, Theme.TEXT_FAINT, false);
        for (int i = Math.max(1, top); i < results.size() + 1 && i < top + viewRows() + 1; i++) {
            Result r = results.get(i - 1);
            int ry = y + HEAD + (i - top) * rh;
            if (Ui.hit(mx, my, x, ry, w, rh)) Draw.rect(ctx, x, ry, w, rh, Draw.opaque(Theme.SURFACE_HOVER));
            String where = r.file.name() + ":" + (r.line + 1);
            int ww = tr.width(where);
            Draw.textFit(ctx, tr, r.text.strip(), x + PAD, ry + (rh - 8) / 2, w - PAD * 2 - 8 - ww, Theme.TEXT, false);
            Draw.text(ctx, tr, where, x + w - PAD - ww, ry + (rh - 8) / 2, Theme.TEXT_FAINT, false);
        }
    }

    boolean contains(double mx, double my) { return Ui.hit(mx, my, x, y, w, h); }

    boolean mouseClicked(MouseButtonEvent click) {
        double mx = click.x(), my = click.y();
        if (!contains(mx, my)) return false;
        if (my < y + HEAD) {
            int tx = x + PAD;
            for (int i = 0; i < TABS.length; i++) {
                int bw = tr.width(TABS[i] + count(i)) + 10;
                if (mx >= tx && mx < tx + bw) show(i);
                tx += bw + 2;
            }
            return true;
        }
        int row = (int) ((my - y - HEAD) / rowH()) + top;
        if (tab == PROBLEMS) {
            List<CodeProject.Problem> list = host.problems();
            if (row >= 0 && row < list.size()) {
                CodeProject.Problem p = list.get(row);
                if (click.button() == 1) host.fixProblem(p, (int) mx, (int) my);
                else host.goTo(p.file(), p.line(), p.col());
            }
        } else if (tab == RESULTS && row >= 1 && row - 1 < results.size()) {
            Result r = results.get(row - 1);
            host.goTo(r.file, r.line, r.col);
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double dy) {
        if (!contains(mx, my)) return false;
        top -= (int) Math.round(dy * 2);
        if (top < 0) top = 0;
        return true;
    }
}
