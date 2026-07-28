package com.xerocode.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class LocationForm extends Screen {
    private static final int WANT_W = 300;
    private static final int PAD = 10, FIELD_H = 16, BTN_H = 20, GAP = 4;

    private static final int[] INK = {0xF0605E, 0x8FD94F, 0x5B8CF5, 0xFFD54A, 0xFFD54A};
    private static final String[] CAPS = {"X", "Y", "Z", "yaw", "pitch"};

    private final List<TextFieldWidget> fields = new ArrayList<>();
    private int focus;
    private int x, y, h, W;

    private int scrubField = -1;
    private double scrubFrom, scrubX0;
    private boolean scrubbing;

    public LocationForm() { super(Text.literal("Местоположение")); }

    @Override
    protected void init() {
        W = Ui.fitW(width, WANT_W);
        h = PAD + 11 + 6 + (9 + FIELD_H) + GAP + (9 + FIELD_H) + 8 + BTN_H + PAD;
        x = Ui.midX(width, W);
        y = Ui.midY(height, h);
        fields.clear();
        double[] v = LocationPick.values();
        for (int i = 0; i < 5; i++) {
            int cw = colW();
            TextFieldWidget f = Ui.field(textRenderer, fieldX(i) + 7, fieldY(i) + 9 + 4,
                    cw - 14, 12, "0");
            f.setMaxLength(24);
            f.setText(fmt(v[i]));
            fields.add(f);
        }
        focus(0);
    }

    private int rowY(int row) { return y + PAD + 11 + 6 + row * (9 + FIELD_H + GAP); }

    private int colW() { return (W - PAD * 2 - 2 * GAP) / 3; }

    private static String fmt(double d) {
        double r = Math.round(d * 1000.0) / 1000.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private void focus(int i) {
        focus = Math.max(0, Math.min(fields.size() - 1, i));
        for (int k = 0; k < fields.size(); k++) fields.get(k).setFocused(k == focus);
        fields.get(focus).setCursorToEnd(false);
    }

    private static double parse(String s) {
        try {
            String t = s.trim().replace(',', '.');
            return t.isEmpty() || t.equals("-") ? 0 : Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void push() {
        LocationPick.setValues(parse(fields.get(0).getText()), parse(fields.get(1).getText()),
                parse(fields.get(2).getText()), parse(fields.get(3).getText()),
                parse(fields.get(4).getText()));
    }

    private int fieldX(int i) { return x + PAD + (i < 3 ? i : i - 3) * (colW() + GAP); }
    private int fieldY(int i) { return rowY(i < 3 ? 0 : 1); }

    private int buttonY() { return y + h - PAD - BTN_H; }
    private int buttonX(int i) { return x + PAD + ((W - PAD * 2 - GAP * 2) / 3 + GAP) * i; }
    private int buttonW(int i) {
        int third = (W - PAD * 2 - GAP * 2) / 3;
        return i == 2 ? W - PAD * 2 - (third + GAP) * 2 : third;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        SmoothText.clip(null);
        Draw.batch(null);
        Ui.panel(ctx, x, y, W, h);

        int cy = y + PAD;
        Draw.textFit(ctx, textRenderer, "МЕСТОПОЛОЖЕНИЕ", x + PAD, cy, W - PAD * 2 - 90,
                Theme.TEXT, false);
        Draw.textRight(ctx, textRenderer, "Tab — дальше", x + W - PAD, cy, Theme.TEXT_FAINT, false);

        for (int i = 0; i < 5; i++) {
            int cw = colW(), fx = fieldX(i), fy = fieldY(i);
            if (Ui.hit(mouseX, mouseY, fx, fy, cw, 9)) ctx.setCursor(StandardCursors.RESIZE_EW);
            else if (Ui.hit(mouseX, mouseY, fx, fy + 9, cw, FIELD_H))
                ctx.setCursor(StandardCursors.IBEAM);
            Draw.textFit(ctx, textRenderer, CAPS[i], fx + 2, fy, cw - 4, INK[i], false);
            Ui.input(ctx, fx, fy + 9, cw, FIELD_H, i == focus);
            Draw.roundRect(ctx, fx + 1, fy + 10, 3, FIELD_H - 2,
                    Ui.R_SM - 1, 0, 0, Ui.R_SM - 1, Draw.opaque(INK[i]));
            fields.get(i).render(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, textRenderer, fields.get(i));
        }

        String[] labels = {"Отмена", "В мир", "Готово"};
        for (int i = 0; i < 3; i++)
            Ui.button(ctx, textRenderer, mouseX, mouseY, buttonX(i), buttonY(), buttonW(i), BTN_H,
                    labels[i], i == 2 ? Ui.ACCENT : Ui.GHOST);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (int i = 0; i < 5; i++) {
            int cw = colW(), fx = fieldX(i), fy = fieldY(i);
            if (Ui.hit(mx, my, fx, fy, cw, 9)) {
                scrubField = i;
                scrubFrom = parse(fields.get(i).getText());
                scrubX0 = click.x();
                scrubbing = false;
                focus(i);
                return true;
            }
            if (!Ui.hit(mx, my, fx, fy + 9, cw, FIELD_H)) continue;
            focus(i);
            if (!fields.get(i).mouseClicked(click, doubled)) fields.get(i).onClick(click, doubled);
            return true;
        }
        if (Ui.hit(mx, my, buttonX(0), buttonY(), buttonW(0), BTN_H)) { cancel(); return true; }
        if (Ui.hit(mx, my, buttonX(1), buttonY(), buttonW(1), BTN_H)) { toWorld(); return true; }
        if (Ui.hit(mx, my, buttonX(2), buttonY(), buttonW(2), BTN_H)) { done(); return true; }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (scrubField >= 0) {
            double moved = click.x() - scrubX0;
            if (!scrubbing && Math.abs(moved) < 3) return true;
            if (!scrubbing) {
                scrubbing = true;
                fields.get(scrubField).setCursorToEnd(false);
            }
            double step = mod(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL) ? 1.0
                    : mod(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 0.01 : 0.05;
            TextFieldWidget f = fields.get(scrubField);
            f.setText(fmt(scrubFrom + moved * step));
            f.setCursorToEnd(false);
            push();
            return true;
        }
        if (fields.get(focus).mouseDragged(click, dx, dy)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        scrubField = -1;
        scrubbing = false;
        return super.mouseReleased(click);
    }

    private static boolean mod(int left, int right) {
        var window = MinecraftClient.getInstance().getWindow();
        return window != null && (net.minecraft.client.util.InputUtil.isKeyPressed(window, left)
                || net.minecraft.client.util.InputUtil.isKeyPressed(window, right));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { cancel(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { toWorld(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean back = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            focus((focus + (back ? 4 : 1)) % 5);
            return true;
        }
        if (fields.get(focus).keyPressed(input)) { push(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (fields.get(focus).charTyped(input)) { push(); return true; }
        return super.charTyped(input);
    }

    private void toWorld() {
        push();
        close();
    }

    private void cancel() {
        LocationPick.undoForm();
        close();
    }

    private void done() {
        push();
        LocationPick.doneFromForm();
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        LocationPick.formClosed();
        client.setScreen(null);
    }
}
