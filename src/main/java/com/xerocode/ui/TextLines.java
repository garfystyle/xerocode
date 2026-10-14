package com.xerocode.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class TextLines {
    private static final int PAD = 10, HEAD_H = 26, FOOT_H = 26, ROW = 16, WIDTH = 420, BOX_H = 108;
    private static final String TITLE = "Многострочный текст";
    private static final String HINT = "Shift+Enter — новая строка · Enter — готово · Esc — отмена";
    public static final String BREAK = "\\n";

    public interface Done { void apply(String text); }

    private final Font tr;
    private final Done done;
    private final int accent;
    private final MultiLineEditBox box;
    private int x, y, w, h;
    private boolean closed;

    public TextLines(Font tr, int screenW, int screenH, String text, int cursor, int accent, Done done) {
        this.tr = tr;
        this.done = done;
        this.accent = accent;
        this.w = Math.min(WIDTH, Math.max(200, screenW - 2 * Ui.margin(screenW)));
        this.h = Math.min(HEAD_H + PAD + BOX_H + PAD + Ui.TEXT_H + 6 + FOOT_H, screenH - 8);
        this.x = Ui.midX(screenW, w);
        this.y = Ui.midY(screenH, h);
        box = MultiLineEditBox.builder()
                .setPlaceholder(Component.literal("строка за строкой").withColor(Theme.TEXT_FAINT))
                .setTextColor(Draw.opaque(Theme.TEXT))
                .setTextShadow(false)
                .setCursorColor(Draw.opaque(Theme.ACCENT))
                .setShowBackground(false)
                .setShowDecorations(false)
                .build(tr, w - 2 * PAD - 8, boxH() - 6, Component.literal(TITLE));
        box.setCharacterLimit(Ui.TEXT_MAX);
        String shown = lines(text);
        box.setValue(shown, true);
        box.setFocused(true);
        int at = Math.max(0, Math.min(shown.length(), lines(text.substring(0, Math.max(0,
                Math.min(text.length(), cursor)))).length()));
        for (int i = shown.length(); i > at; i--)
            box.keyPressed(new KeyEvent(GLFW.GLFW_KEY_LEFT, 0, 0));
        place();
    }

    public void resize(int screenW, int screenH) {
        w = Math.min(WIDTH, Math.max(200, screenW - 2 * Ui.margin(screenW)));
        h = Math.min(HEAD_H + PAD + BOX_H + PAD + Ui.TEXT_H + 6 + FOOT_H, screenH - 8);
        x = Ui.midX(screenW, w);
        y = Ui.midY(screenH, h);
        place();
    }

    public static String lines(String text) { return text.replace(BREAK, "\n"); }

    public static String joined(String text) { return text.replace("\r", "").replace("\n", BREAK); }

    public boolean isClosed() { return closed; }

    private int inner() { return w - 2 * PAD; }

    private int boxY() { return y + HEAD_H + PAD; }

    private int boxH() { return Math.max(40, h - HEAD_H - PAD - PAD - Ui.TEXT_H - 6 - FOOT_H); }

    private int footY() { return y + h - FOOT_H; }

    private void place() {
        box.setX(x + PAD + 4);
        box.setY(boxY() + 3);
        box.setWidth(inner() - 8);
        box.setHeight(boxH() - 6);
    }

    private int headY(int hgt) { return y + 1 + (HEAD_H - 1 - hgt) / 2; }

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, x, y, w, h);
        Ui.headerStrip(ctx, x, y, w, HEAD_H, accent);
        Draw.round(ctx, x + PAD, headY(10), 3, 10, 1, Draw.opaque(accent));
        int count = box.getValue().isEmpty() ? 0 : box.getValue().split("\n", -1).length;
        Draw.textFit(ctx, tr, TITLE, x + PAD + 9, headY(Ui.TEXT_H), inner() - 90, Theme.TEXT, false);
        Draw.textRight(ctx, tr, Ui.plural(count, "строка", "строки", "строк"), x + w - PAD - 22,
                headY(Ui.TEXT_H), Theme.TEXT_FAINT, false);
        Ui.closeButton(ctx, mouseX, mouseY, x + w - PAD - 14, headY(14), 14);
        Ui.hairline(ctx, x + 1, y + HEAD_H, w - 2);
        Ui.input(ctx, x + PAD, boxY(), inner(), boxH(), box.isFocused());
        box.extractRenderState(ctx, mouseX, mouseY, delta);
        Draw.textFit(ctx, tr, HINT, x + PAD, boxY() + boxH() + 4, inner(), Theme.TEXT_FAINT, false);
        Ui.hairline(ctx, x + 1, footY() - 1, w - 2);
        int fy = footY() + (FOOT_H - ROW) / 2;
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 58, fy, 58, ROW, "Готово", Ui.ACCENT);
        Ui.button(ctx, tr, mouseX, mouseY, x + w - PAD - 118, fy, 56, ROW, "Отмена", Ui.GHOST);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int fy = footY() + (FOOT_H - ROW) / 2;
        if (Ui.hit(mx, my, x + w - PAD - 14, headY(14), 14, 14)) { closed = true; return true; }
        if (Ui.hit(mx, my, x + w - PAD - 58, fy, 58, ROW)) { finish(); return true; }
        if (Ui.hit(mx, my, x + w - PAD - 118, fy, 56, ROW)) { closed = true; return true; }
        if (Ui.hit(mx, my, x + PAD, boxY(), inner(), boxH())) {
            box.setFocused(true);
            box.mouseClicked(click, doubled);
            return true;
        }
        if (!Ui.hit(mx, my, x, y, w, h)) finish();
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        return box.mouseDragged(click, dx, dy);
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return box.mouseScrolled(mx, my, 0, amount);
    }

    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { closed = true; return true; }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && (input.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) { finish(); return true; }
        box.keyPressed(input);
        return true;
    }

    public boolean charTyped(CharacterEvent input) {
        box.charTyped(input);
        return true;
    }

    private void finish() {
        done.apply(joined(box.getValue()));
        closed = true;
    }
}
