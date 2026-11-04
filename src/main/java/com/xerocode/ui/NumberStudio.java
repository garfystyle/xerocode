package com.xerocode.ui;

import com.xerocode.Ticks;
import com.xerocode.Value;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

final class NumberStudio implements Overlay {
    private static final int PAD = 10, HEAD_H = 26, FOOT_H = 26, BTN = 16, ICON = 14;
    private static final String HINT = "Enter — готово · Esc — отмена · свернуть — кнопкой в шапке";

    interface Done { void apply(String text, int cursor); }

    private final Font tr;
    private final String title;
    private final int accent;
    private final Ticks.Unit unit;
    private final Done done;
    private final WrapField.Box input;
    private final WrapField area;
    private final Complete complete = new Complete();
    private int screenW, screenH, x, y, w, h;
    private boolean closed;

    NumberStudio(Font tr, int screenW, int screenH, String title, String text, int cursor,
                 int accent, Ticks.Unit unit, Done done) {
        this.tr = tr;
        this.title = title;
        this.accent = accent;
        this.unit = unit;
        this.done = done;
        input = new WrapField.Box(tr, text, "число или %math(…)");
        input.setFocused(true);
        input.moveCursorTo(Math.max(0, Math.min(text.length(), cursor)), false);
        area = new WrapField(tr, input, () -> null);
        resize(screenW, screenH);
        area.follow();
    }

    void resize(int sw, int sh) {
        screenW = sw;
        screenH = sh;
        int m = Ui.margin(sw);
        x = m;
        y = Ui.margin(sh);
        w = Math.max(60, sw - 2 * m);
        h = Math.max(60, sh - 2 * y);
        area.place(x + PAD, boxY(), w - 2 * PAD, boxH());
        complete.reset();
    }

    public boolean isClosed() { return closed; }

    private int boxY() { return y + HEAD_H + PAD; }

    private int boxH() {
        return Math.max(WrapField.LINE * 2, h - HEAD_H - 2 * PAD - 2 * (Ui.TEXT_H + 4) - FOOT_H);
    }

    private int footY() { return y + h - FOOT_H; }

    private int toggleX() { return x + w - PAD - ICON - 6 - ICON; }

    private int headY(int hgt) { return y + 1 + (HEAD_H - 1 - hgt) / 2; }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Ui.dim(ctx, screenW, screenH);
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent);
        Draw.round(ctx, x + PAD, headY(10), 3, 10, 1, Draw.opaque(accent));
        Draw.textFit(ctx, tr, title, x + PAD + 9, headY(Ui.TEXT_H), toggleX() - x - PAD - 16,
                Theme.TEXT, false);
        Ui.iconButton(ctx, mouseX, mouseY, toggleX(), headY(ICON), ICON, Draw.FIT, Ui.ACCENT, true);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - ICON, headY(ICON), ICON);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);

        area.place(x + PAD, boxY(), w - 2 * PAD, boxH());
        area.render(ctx, mouseX, mouseY, delta);
        int ny = boxY() + boxH() + 4;
        String note = note();
        if (!note.isEmpty())
            Draw.textFit(ctx, tr, note, x + PAD + 2, ny, w - 2 * PAD - 4,
                    note.startsWith("=") ? Theme.OK : Theme.DANGER, false);
        Draw.textFit(ctx, tr, HINT, x + PAD + 2, ny + Ui.TEXT_H + 4, w - 2 * PAD - 4,
                Theme.TEXT_FAINT, false);

        Ui.hairline(ctx, x + 1, footY() - 1, w - 2);
        int fy = footY() + (FOOT_H - BTN) / 2;
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 58, fy, 58, BTN, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 118, fy, 56, BTN, "Отмена", Ui.GHOST);
        if (complete.active()) {
            ctx.nextStratum();
            complete.render(ctx, tr, mouseX, mouseY);
        }
    }

    private String note() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return "";
        Double time = Ticks.convert(text, unit);
        if (time != null) return "= " + Value.num(time);
        if (text.indexOf('%') >= 0 || number(text)) return "";
        return "не число — сохранится 0";
    }

    private static boolean number(String s) {
        try {
            Double.parseDouble(s.replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (complete.mouseClicked(mx, my)) { sync(); return true; }
        int fy = footY() + (FOOT_H - BTN) / 2;
        if (Ui.hit(mx, my, toggleX(), headY(ICON), ICON, ICON)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - ICON, headY(ICON), ICON, ICON)) {
            closed = true;
            return true;
        }
        if (Ui.hit(mx, my, x + w - PAD - 58, fy, 58, BTN)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 118, fy, 56, BTN)) { closed = true; return true; }
        if (area.press(click, doubled)) {
            input.setFocused(true);
            sync();
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        return area.drag(click.x(), click.y());
    }

    @Override
    public void mouseReleased() { area.release(); }

    public boolean mouseScrolled(double mx, double my, double amount) {
        area.wheel(mx, my, amount);
        return true;
    }

    public boolean keyPressed(KeyEvent in) {
        if (complete.keyPressed(in)) { sync(); return true; }
        int key = in.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { closed = true; return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { finish(); return true; }
        if (!area.key(in)) input.keyPressed(in);
        sync();
        return true;
    }

    public boolean charTyped(CharacterEvent in) {
        input.charTyped(in);
        sync();
        return true;
    }

    private void sync() {
        area.follow();
        complete.update(input, tr, screenW, screenH, true, false);
    }

    private void finish() {
        done.apply(input.getValue(), input.getCursorPosition());
        closed = true;
    }
}
