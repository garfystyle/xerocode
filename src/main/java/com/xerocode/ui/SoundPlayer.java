package com.xerocode.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.xerocode.Audio;
import com.xerocode.Env;
import com.xerocode.Settings;
import com.xerocode.Value;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

public final class SoundPlayer implements CatalogPicker.Extra {
    public static final int H = 30;
    private static final int BTN = 16;
    private static final int PAD = 7;
    private static final String CLOCK = "00:00.0 / 00:00.0";
    private static final int VOL_W = 60, VOL_GAP = 3, SLIM_W = 360, WHEEL_STEP = 5;
    private static final String[] TUNE_CAPS = {"ГРОМКОСТЬ", "ТОН", "СИД"};
    private static final int SEED = 2;
    private static final int CAP_H = 9, COL_GAP = 4, ROW_GAP = 5, STEP_PX = 6, STEP_PX_COARSE = 10;
    private static final double[] STEPS = {0.01, 0.1, 1};
    private static int unmuted = Settings.PREVIEW_VOLUME;
    private static double wheel;

    private final Font tr;
    private final Value value;
    private final int accent;
    private String bound = "";
    private boolean dragging, volDragging;
    private String cue;
    private String hint;

    private EditBox[] tune;
    private final Ui.Grab grab = new Ui.Grab();
    private int tuneX, tuneY, tuneW;
    private boolean tuneShown;
    private int scrubField = -1;
    private double scrubFrom, scrubX0;
    private boolean scrubbing;

    public SoundPlayer(Font tr, Value value, int accent) {
        this.tr = tr;
        this.value = value;
        this.accent = accent;
    }

    public SoundPlayer tuned() {
        tune = new EditBox[]{
                Ui.field(tr, Value.num(value.volume), "0", 32),
                Ui.field(tr, Value.num(value.pitch2), "0", 32),
                Ui.field(tr, value.variation, "случайный", 256)};
        return this;
    }

    public String seed() { return tune == null ? value.variation : tune[SEED].getValue().trim(); }

    public double volume() { return tune == null ? value.volume : number(0); }

    public double pitch() { return tune == null ? value.pitch2 : number(1); }

    private double number(int i) {
        try {
            return Double.parseDouble(tune[i].getValue().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int fieldH() { return Env.touch() ? 26 : 20; }

    private int colW() { return (tuneW - COL_GAP) / 2; }

    private int colX(int i) { return i == SEED ? tuneX : tuneX + i * (colW() + COL_GAP); }

    private int colWidth(int i) { return i == SEED ? tuneW : colW(); }

    private int rowY(int i) { return i == SEED ? tuneY + CAP_H + fieldH() + ROW_GAP : tuneY; }

    @Override
    public int sideH() { return tune == null ? 0 : 2 * (CAP_H + fieldH()) + ROW_GAP; }

    @Override
    public void side(GuiGraphicsExtractor ctx, int x, int y, int w, int mouseX, int mouseY,
                     boolean shown) {
        tuneShown = shown && tune != null;
        if (!tuneShown) {
            if (tune != null) for (EditBox f : tune) f.setFocused(false);
            return;
        }
        tuneX = x;
        tuneY = y;
        tuneW = w;
        int fh = fieldH();
        for (int i = 0; i < tune.length; i++) {
            int cx = colX(i), cw = colWidth(i), ry = rowY(i);
            EditBox f = tune[i];
            f.setX(cx + 6);
            f.setY(ry + CAP_H + (fh - 12) / 2 + 2);
            Ui.width(f, cw - 12);
            if (i != SEED && Ui.hit(mouseX, mouseY, cx, ry, cw, CAP_H))
                ctx.requestCursor(CursorTypes.RESIZE_EW);
            else if (Ui.hit(mouseX, mouseY, cx, ry + CAP_H, cw, fh)) ctx.requestCursor(CursorTypes.IBEAM);
            Draw.textFit(ctx, tr, TUNE_CAPS[i], cx + 2, ry, cw - 4, Theme.TEXT_FAINT, false);
            Ui.input(ctx, cx, ry + CAP_H, cw, fh, f.isFocused());
            f.extractRenderState(ctx, mouseX, mouseY, 0);
            Ui.placeholder(ctx, tr, f);
        }
    }

    private int tuneAt(double mx, double my, boolean caption) {
        if (!tuneShown) return -1;
        int h = caption ? CAP_H : fieldH();
        for (int i = 0; i < tune.length; i++) {
            int top = rowY(i) + (caption ? 0 : CAP_H);
            if (Ui.hit(mx, my, colX(i), top, colWidth(i), h)) return i;
        }
        return -1;
    }

    private int focusedTune() {
        if (!tuneShown) return -1;
        for (int i = 0; i < tune.length; i++) if (tune[i].isFocused()) return i;
        return -1;
    }

    private void focusTune(int i) {
        for (int k = 0; k < tune.length; k++) tune[k].setFocused(k == i);
    }

    @Override
    public boolean sideClicked(MouseButtonEvent click, boolean doubled) {
        if (tune == null) return false;
        int mx = (int) click.x(), my = (int) click.y();
        int cap = tuneAt(mx, my, true);
        if (cap == SEED) {
            focusTune(SEED);
            tune[SEED].moveCursorToEnd(false);
            return true;
        }
        if (cap >= 0) {
            focusTune(cap);
            scrubField = cap;
            scrubFrom = number(cap);
            scrubX0 = click.x();
            scrubbing = false;
            return true;
        }
        int at = tuneAt(mx, my, false);
        if (at < 0) {
            focusTune(-1);
            return false;
        }
        focusTune(at);
        EditBox f = tune[at];
        if (!f.mouseClicked(click, doubled)) f.onClick(click, doubled);
        grab.take(f);
        return true;
    }

    @Override
    public boolean sideDragged(MouseButtonEvent click, double dx, double dy) {
        if (scrubField >= 0) {
            double moved = click.x() - scrubX0;
            if (!scrubbing && Math.abs(moved) < 3) return true;
            scrubbing = true;
            int n = (int) (moved / (held(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)
                    ? STEP_PX_COARSE : STEP_PX));
            setTune(scrubField, snap(scrubFrom, n, step()));
            return true;
        }
        return grab.drag(click, dx, dy);
    }

    @Override
    public boolean sideScrolled(double mx, double my, double amount) {
        if (tune == null || amount == 0 || Env.touch()) return false;
        int i = tuneAt(mx, my, true);
        if (i < 0) i = tuneAt(mx, my, false);
        if (i < 0 || i == SEED) return false;
        setTune(i, snap(number(i), amount > 0 ? 1 : -1, step()));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent in) {
        int i = focusedTune();
        if (i < 0) return false;
        tune[i].charTyped(in);
        return true;
    }

    private void setTune(int i, double to) {
        tune[i].setValue(Value.num(Math.max(0, to)));
        tune[i].moveCursorToEnd(false);
    }

    private static double step() {
        if (held(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) return STEPS[0];
        return held(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL) ? STEPS[2] : STEPS[1];
    }

    private static double snap(double from, int n, double step) {
        if (n == 0) return from;
        double base = n > 0 ? Math.floor(from / step + 1e-9) : Math.ceil(from / step - 1e-9);
        return Math.round((base + n) * step * 1e6) / 1e6;
    }

    private static boolean held(int left, int right) {
        var window = Minecraft.getInstance().getWindow();
        return window != null && (InputConstants.isKeyDown(window, left)
                || InputConstants.isKeyDown(window, right));
    }

    @Override
    public int height() { return H; }

    @Override
    public void select(String id) { bound = id == null ? "" : id; }

    @Override
    public void picked(String id) {
        select(id);
        cue = bound.isEmpty() ? null : bound;
    }

    @Override
    public String hint() { return hint; }

    @Override
    public boolean keyPressed(KeyEvent in) {
        int key = in.key();
        if (key == GLFW.GLFW_KEY_SPACE && (in.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            Audio.toggle();
            return true;
        }
        int i = focusedTune();
        if (i < 0 || key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER) return false;
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean back = (in.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            int next = Math.floorMod(i + (back ? -1 : 1), tune.length);
            focusTune(next);
            tune[next].moveCursorToEnd(false);
            tune[next].setHighlightPos(0);
            return true;
        }
        tune[i].keyPressed(in);
        return true;
    }

    private record Bar(int row, int playX, int rewX, int loopX, int volX, int sliderX, int sliderW,
                       int barX, int barW, int barY, int clockRight) {}

    private Bar layout(int x, int y, int w, int h) {
        int row = y + (h - BTN) / 2;
        int left = x + PAD;
        int loopX = x + w - PAD - BTN;
        int sliderW = w < SLIM_W ? 0 : VOL_W;
        int sliderX = loopX - 8 - sliderW;
        int volX = sliderX - (sliderW == 0 ? 0 : VOL_GAP) - Draw.glyphW(Draw.SPEAKER);
        int clockRight = volX - 8;
        int barX = left + 2 * (BTN + 3) + 4;
        int barW = Math.max(20, clockRight - tr.width(CLOCK) - 8 - barX);
        return new Bar(row, left, left + BTN + 3, loopX, volX, sliderX, sliderW, barX, barW,
                y + h / 2 - 2, clockRight);
    }

    public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        render(ctx, x, y, w, h, mouseX, mouseY, false);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY,
                       boolean flush) {
        hint = null;
        Audio.want(bound);
        if (value != null) Audio.mix(volume(), pitch(), value.source);
        Audio.tick();
        if (cue != null) {
            Audio.State st = Audio.state();
            if (!cue.equals(bound) || st == Audio.State.MISSING || st == Audio.State.EMPTY) cue = null;
            else if (Audio.ready()) {
                Audio.restart();
                cue = null;
            }
        }

        if (flush) Draw.rect(ctx, x, y, w, h, Draw.opaque(Ui.WELL));
        else Ui.well(ctx, x, y, w, h);
        Bar b = layout(x, y, w, h);
        boolean ready = Audio.ready();
        boolean playing = Audio.playing();

        if (Ui.iconButton(ctx, mouseX, mouseY, b.playX(), b.row(), BTN,
                playing ? Draw.PAUSE : Draw.PLAY, Ui.ACCENT, ready))
            hint = playing ? "пауза" : "прослушать";
        if (Ui.iconButton(ctx, mouseX, mouseY, b.rewX(), b.row(), BTN, Draw.REWIND, Ui.GHOST,
                ready)) hint = "к началу";
        if (Ui.iconButton(ctx, mouseX, mouseY, b.loopX(), b.row(), BTN, Draw.LOOP,
                Audio.loop() ? Ui.ACTIVE : Ui.GHOST, true))
            hint = Audio.loop() ? "не повторять" : "повторять";
        drawVolume(ctx, b, mouseX, mouseY);

        int textY = b.row() + (BTN - Ui.TEXT_H) / 2;
        int textW = b.clockRight() - b.barX();
        switch (Audio.state()) {
            case EMPTY -> Draw.textFit(ctx, tr, "звук не выбран", b.barX(), textY, textW,
                    Theme.TEXT_FAINT, false);
            case LOADING -> Draw.textFit(ctx, tr, "читаю файл…", b.barX(), textY, textW,
                    Theme.TEXT_FAINT, false);
            case MISSING -> Draw.textFit(ctx, tr, Audio.broken()
                            ? "звук в клиенте недоступен" : "файл этого звука не найден",
                    b.barX(), textY, textW, Theme.DANGER, false);
            case READY -> drawTrack(ctx, b, mouseX, mouseY, accent, textY);
        }
    }

    private static boolean onIcon(int mx, int my, Bar b) {
        int right = b.sliderW() == 0 ? b.sliderX() + 2 : b.sliderX() - 2;
        return Ui.hit(mx, my, b.volX() - 2, b.row(), right - (b.volX() - 2), BTN);
    }

    private static boolean onSlider(int mx, int my, Bar b) {
        return b.sliderW() > 0 && Ui.hit(mx, my, b.sliderX() - 2, b.barY() - 6, b.sliderW() + 5, 16);
    }

    private void drawVolume(GuiGraphicsExtractor ctx, Bar b, int mouseX, int mouseY) {
        int pct = Settings.get().previewVolume;
        boolean onIcon = !volDragging && onIcon(mouseX, mouseY, b);
        boolean onSlider = volDragging || onSlider(mouseX, mouseY, b);
        int iconY = b.row() + (BTN - Draw.glyphH(Draw.SPEAKER)) / 2;
        Draw.glyph(ctx, pct == 0 ? Draw.SPEAKER_OFF : Draw.SPEAKER, b.volX(), iconY,
                onIcon ? Theme.TEXT : pct == 0 ? Theme.TEXT_FAINT : Theme.TEXT_DIM);
        if (b.sliderW() > 0) {
            int sw = b.sliderW();
            int fill = Math.round(sw * pct / 100f);
            Draw.round(ctx, b.sliderX(), b.barY(), sw, 4, 2, Draw.opaque(Ui.INPUT));
            if (fill > 0) Draw.round(ctx, b.sliderX(), b.barY(), fill, 4, 2, Draw.opaque(Theme.TEXT_DIM));
            int knob = Math.max(b.sliderX(), Math.min(b.sliderX() + sw - 3, b.sliderX() + fill - 1));
            Draw.round(ctx, knob, b.barY() - 3, 3, 10, 1,
                    Draw.opaque(onSlider ? 0xFFFFFF : Theme.TEXT));
        }
        if (onIcon) hint = "громкость прослушивания " + pct + " % · " + Ui.click()
                + (pct == 0 ? " — включить" : " — выключить");
        else if (onSlider) hint = "громкость прослушивания " + pct + " % — в значение не пишется";
    }

    private void drawTrack(GuiGraphicsExtractor ctx, Bar b, int mouseX, int mouseY, int accent, int textY) {
        double duration = Audio.duration();
        double position = Audio.position();
        boolean hot = dragging || Ui.hit(mouseX, mouseY, b.barX() - 3, b.barY() - 6,
                b.barW() + 6, 16);
        if (hot) hint = "перемотать — клик или потянуть";

        boolean muted = Audio.muted();
        int fill = duration <= 0 ? 0
                : (int) Math.round(b.barW() * Math.min(1, position / duration));
        Draw.round(ctx, b.barX(), b.barY(), b.barW(), 4, 2, Draw.opaque(Ui.INPUT));
        if (fill > 0)
            Draw.round(ctx, b.barX(), b.barY(), fill, 4, 2,
                    Draw.opaque(muted ? 0x4A5364 : accent));
        int knob = Math.max(b.barX(), Math.min(b.barX() + b.barW() - 3, b.barX() + fill - 1));
        Draw.round(ctx, knob, b.barY() - 3, 3, 10, 1,
                Draw.opaque(hot ? 0xFFFFFF : (muted ? Theme.TEXT_DIM : Theme.TEXT)));

        String clock = time(position) + " / " + time(duration);
        int clockW = tr.width(clock);
        if (muted) {
            Draw.glyph(ctx, Draw.WARN, b.clockRight() - clockW - 13, textY, 0xFFE066);
            if (hint == null) hint = "громкость 0 — ничего не будет слышно";
        }
        Draw.textRight(ctx, tr, clock, b.clockRight(), textY,
                Audio.playing() ? Theme.TEXT_DIM : Theme.TEXT_FAINT, false);
    }

    private static String time(double seconds) {
        int tenths = (int) Math.max(0, Math.round(seconds * 10));
        return (tenths / 600) + ":" + String.format("%02d.%d", tenths / 10 % 60, tenths % 10);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int x, int y, int w, int h) {
        Bar b = layout(x, y, w, h);
        if (Ui.hit(mx, my, b.playX(), b.row(), BTN, BTN)) { Audio.toggle(); return true; }
        if (Ui.hit(mx, my, b.rewX(), b.row(), BTN, BTN)) { Audio.rewind(); return true; }
        if (Ui.hit(mx, my, b.loopX(), b.row(), BTN, BTN)) {
            Audio.setLoop(!Audio.loop());
            return true;
        }
        if (onIcon(mx, my, b)) {
            Settings s = Settings.get();
            if (s.previewVolume > 0) {
                unmuted = s.previewVolume;
                s.previewVolume = 0;
            } else s.previewVolume = Math.max(1, unmuted);
            s.save();
            return true;
        }
        if (onSlider(mx, my, b)) {
            volDragging = true;
            volume(mx, b);
            return true;
        }
        if (Audio.ready() && Ui.hit(mx, my, b.barX() - 3, b.barY() - 6, b.barW() + 6, 16)) {
            dragging = true;
            scrub(mx, b);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(int mx, int x, int y, int w, int h) {
        if (volDragging) {
            volume(mx, layout(x, y, w, h));
            return true;
        }
        if (!dragging) return false;
        scrub(mx, layout(x, y, w, h));
        return true;
    }

    @Override
    public void mouseReleased() {
        dragging = false;
        scrubField = -1;
        scrubbing = false;
        grab.release();
        if (!volDragging) return;
        volDragging = false;
        Settings.get().save();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount, int x, int y, int w, int h) {
        Bar b = layout(x, y, w, h);
        if (!onIcon((int) mx, (int) my, b) && !onSlider((int) mx, (int) my, b)) return false;
        if (amount == 0) return true;
        if (Math.signum(amount) != Math.signum(wheel)) wheel = 0;
        wheel += amount * WHEEL_STEP;
        int step = (int) wheel;
        if (step == 0) return true;
        wheel -= step;
        Settings s = Settings.get();
        int now = Math.max(0, Math.min(100, s.previewVolume + step));
        if (now != s.previewVolume) {
            s.previewVolume = now;
            s.save();
        }
        return true;
    }

    private void volume(int mx, Bar b) {
        double t = (mx - b.sliderX()) / (double) Math.max(1, b.sliderW() - 1);
        Settings.get().previewVolume = (int) Math.round(Math.max(0, Math.min(1, t)) * 100);
    }

    private void scrub(int mx, Bar b) {
        double t = (mx - b.barX()) / (double) Math.max(1, b.barW());
        Audio.seek(Audio.duration() * Math.max(0, Math.min(1, t)));
    }
}
