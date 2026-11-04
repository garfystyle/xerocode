package com.xerocode.ui;

import com.xerocode.Env;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

public final class TextLines implements Overlay {
    private static final int PAD = 10, HEAD_H = 26, FOOT_H = 26, ROW = 16, WIDTH = 420, BOX_H = 108;
    private static final String TITLE = "Многострочный текст";
    private static final String HINT = "Shift+Enter — новая строка · Enter — готово · Esc — отмена";
    public static final String BREAK = "\\n";

    public interface Done { void apply(String text, int cursor); }

    private final Font tr;
    private final Done done;
    private final int accent;
    private final WrapField.Box input;
    private final WrapField area;
    private final Sizer sizer = new Sizer("lines", 240, 120, true);
    private int screenW, screenH;
    private int x, y, w, h;
    private boolean closed;
    private Click outside;

    public record Click(MouseButtonEvent event, boolean doubled) {}

    public TextLines(Font tr, int screenW, int screenH, String text, int cursor, String parsing,
                     int accent, Done done) {
        this.tr = tr;
        this.done = done;
        this.accent = accent;
        input = new WrapField.Box(tr, text, "строка за строкой");
        input.setFocused(true);
        input.moveCursorTo(Math.max(0, Math.min(text.length(), cursor)), false);
        area = new WrapField(tr, input, () -> parsing);
        resize(screenW, screenH);
        area.follow();
    }

    public void resize(int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        w = sizer.w(screenW, Math.min(WIDTH, Math.max(200, screenW - 2 * Ui.margin(screenW))));
        h = sizer.h(screenH, Math.min(HEAD_H + PAD + BOX_H + PAD + Ui.TEXT_H + 6 + FOOT_H,
                screenH - 8));
        x = Ui.midX(screenW, w);
        y = Ui.midY(screenH, h);
        area.place(x + PAD, boxY(), inner(), boxH());
    }

    public boolean isClosed() { return closed; }

    public Click outside() { return outside; }

    private int inner() { return w - 2 * PAD; }

    private int boxY() { return y + HEAD_H + PAD; }

    private int boxH() { return Math.max(40, h - HEAD_H - PAD - PAD - Ui.TEXT_H - 6 - FOOT_H); }

    private int footY() { return y + h - FOOT_H; }

    private int headY(int hgt) { return y + 1 + (HEAD_H - 1 - hgt) / 2; }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent);
        Draw.round(ctx, x + PAD, headY(10), 3, 10, 1, Draw.opaque(accent));
        String text = input.getValue();
        int count = text.isEmpty() ? 0 : text.split(java.util.regex.Pattern.quote(BREAK), -1).length;
        Draw.textFit(ctx, tr, TITLE, x + PAD + 9, headY(Ui.TEXT_H), inner() - 90, Theme.TEXT, false);
        Draw.textRight(ctx, tr, Ui.plural(count, "строка", "строки", "строк"), x + w - PAD - 22,
                headY(Ui.TEXT_H), Theme.TEXT_FAINT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, headY(14), 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);
        area.place(x + PAD, boxY(), inner(), boxH());
        area.render(ctx, mouseX, mouseY, delta);
        if (!Env.touch())
            Draw.textFit(ctx, tr, HINT, x + PAD, boxY() + boxH() + 4, inner(), Theme.TEXT_FAINT, false);
        Ui.hairline(ctx, x + 1, footY() - 1, w - 2);
        int fy = footY() + (FOOT_H - ROW) / 2;
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 58, fy, 58, ROW, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 118, fy, 56, ROW, "Отмена", Ui.GHOST);
        sizer.draw(ctx, mouseX, mouseY, x, y, w, h);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (sizer.press(click, doubled, x, y, w, h)) {
            if (!sizer.active()) resize(screenW, screenH);
            return true;
        }
        int fy = footY() + (FOOT_H - ROW) / 2;
        if (Ui.hit(mx, my, x + w - PAD - 14, headY(14), 14, 14)) { closed = true; return true; }
        if (Ui.hit(mx, my, x + w - PAD - 58, fy, 58, ROW)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 118, fy, 56, ROW)) { closed = true; return true; }
        if (area.press(click, doubled)) {
            input.setFocused(true);
            return true;
        }
        if (!Ui.hit(mx, my, x, y, w, h)) {
            outside = new Click(click, doubled);
            finish();
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (sizer.drag(click.x(), click.y())) {
            resize(screenW, screenH);
            return true;
        }
        return area.drag(click.x(), click.y());
    }

    @Override
    public void mouseReleased() {
        sizer.release();
        area.release();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        area.wheel(mx, my, amount);
        return true;
    }

    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { closed = true; return true; }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && ((input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 || Env.touch())) {
            this.input.insertText(BREAK);
            return true;
        }
        if (enter) { finish(); return true; }
        if (area.key(input)) return true;
        this.input.keyPressed(input);
        return true;
    }

    public boolean charTyped(CharacterEvent input) {
        this.input.charTyped(input);
        return true;
    }

    private void finish() {
        done.apply(input.getValue(), input.getCursorPosition());
        closed = true;
    }
}
