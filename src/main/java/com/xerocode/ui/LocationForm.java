package com.xerocode.ui;

import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class LocationForm extends Screen {
    private final Ui.Grab grab = new Ui.Grab();
    private static final int WANT_W = 300;
    private static final int PAD = 10, FIELD_H = 16, BTN_H = 20, GAP = 4;

    private static final int[] INK = {0xF0605E, 0x8FD94F, 0x5B8CF5, 0xFFD54A, 0xFFD54A};
    private static final String[] CAPS = {"X", "Y", "Z", "yaw", "pitch"};

    private final List<EditBox> fields = new ArrayList<>();
    private int focus;
    private int x, y, h, W;

    private int scrubField = -1;
    private double scrubFrom, scrubX0;
    private boolean scrubbing;

    public LocationForm() { super(Component.literal("Местоположение")); }

    @Override
    protected void init() {
        W = Ui.fitW(width, WANT_W);
        h = PAD + 11 + 6 + (9 + FIELD_H) + GAP + (9 + FIELD_H) + 8 + BTN_H + PAD;
        x = Ui.midX(width, W);
        y = Ui.midY(height, h);
        List<String> typed = new ArrayList<>();
        for (EditBox f : fields) typed.add(f.getValue());
        int was = focus;
        fields.clear();
        double[] v = LocationPick.values();
        for (int i = 0; i < 5; i++) {
            int cw = colW();
            EditBox f = Ui.field(font, fieldX(i) + 7, fieldY(i) + 9 + 4,
                    cw - 14, 12, "0");
            f.setMaxLength(24);
            f.setValue(i < typed.size() ? typed.get(i) : fmt(v[i]));
            fields.add(f);
        }
        focus(was);
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
        fields.get(focus).moveCursorToEnd(false);
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
        LocationPick.setValues(parse(fields.get(0).getValue()), parse(fields.get(1).getValue()),
                parse(fields.get(2).getValue()), parse(fields.get(3).getValue()),
                parse(fields.get(4).getValue()));
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
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        SmoothText.clip(null);
        Draw.batch(null);
        Ui.panel(ctx, x, y, W, h);

        int cy = y + PAD;
        Draw.textFit(ctx, font, "МЕСТОПОЛОЖЕНИЕ", x + PAD, cy, W - PAD * 2 - 90,
                Theme.TEXT, false);
        Draw.textRight(ctx, font, "Tab — дальше", x + W - PAD, cy, Theme.TEXT_FAINT, false);

        for (int i = 0; i < 5; i++) {
            int cw = colW(), fx = fieldX(i), fy = fieldY(i);
            if (Ui.hit(mouseX, mouseY, fx, fy, cw, 9)) ctx.requestCursor(CursorTypes.RESIZE_EW);
            else if (Ui.hit(mouseX, mouseY, fx, fy + 9, cw, FIELD_H))
                ctx.requestCursor(CursorTypes.IBEAM);
            Draw.textFit(ctx, font, CAPS[i], fx + 2, fy, cw - 4, INK[i], false);
            Ui.input(ctx, fx, fy + 9, cw, FIELD_H, i == focus);
            Draw.roundRect(ctx, fx + 1, fy + 10, 3, FIELD_H - 2,
                    Ui.R_SM - 1, 0, 0, Ui.R_SM - 1, Draw.opaque(INK[i]));
            fields.get(i).extractRenderState(ctx, mouseX, mouseY, delta);
            Ui.placeholder(ctx, font, fields.get(i));
        }

        String[] labels = {"Отмена", "В мир", "Готово"};
        for (int i = 0; i < 3; i++)
            Ui.button(ctx, font, mouseX, mouseY, buttonX(i), buttonY(), buttonW(i), BTN_H,
                    labels[i], i == 2 ? Ui.ACCENT : Ui.GHOST);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (int i = 0; i < 5; i++) {
            int cw = colW(), fx = fieldX(i), fy = fieldY(i);
            if (Ui.hit(mx, my, fx, fy, cw, 9)) {
                scrubField = i;
                scrubFrom = parse(fields.get(i).getValue());
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
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (scrubField >= 0) {
            double moved = click.x() - scrubX0;
            if (!scrubbing && Math.abs(moved) < 3) return true;
            if (!scrubbing) {
                scrubbing = true;
                fields.get(scrubField).moveCursorToEnd(false);
            }
            double step = mod(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL) ? 1.0
                    : mod(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 0.01 : 0.05;
            EditBox f = fields.get(scrubField);
            f.setValue(fmt(scrubFrom + moved * step));
            f.moveCursorToEnd(false);
            push();
            return true;
        }
        if (fields.get(focus).mouseDragged(click, dx, dy)) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        scrubField = -1;
        scrubbing = false;
        grab.release();
        return super.mouseReleased(click);
    }

    private static boolean mod(int left, int right) {
        var window = Minecraft.getInstance().getWindow();
        return window != null && (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, left)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, right));
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
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
    public boolean charTyped(CharacterEvent input) {
        if (fields.get(focus).charTyped(input)) { push(); return true; }
        return super.charTyped(input);
    }

    private void toWorld() {
        push();
        onClose();
    }

    private void cancel() {
        LocationPick.undoForm();
        onClose();
    }

    private void done() {
        push();
        LocationPick.doneFromForm();
    }

    @Override
    public void onClose() {
        Minecraft client = Minecraft.getInstance();
        LocationPick.formClosed();
        client.gui.setScreen(null);
    }
}
