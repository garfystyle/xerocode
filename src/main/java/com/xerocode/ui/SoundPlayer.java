package com.xerocode.ui;

import com.xerocode.Audio;
import com.xerocode.Settings;
import com.xerocode.Value;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class SoundPlayer implements CatalogPicker.Extra {
    public static final int H = 30;
    private static final int BTN = 16;
    private static final int PAD = 7;
    private static final String CLOCK = "00:00.0 / 00:00.0";
    private static final int VOL_W = 60, VOL_GAP = 3, SLIM_W = 360, WHEEL_STEP = 5;
    private static int unmuted = Settings.PREVIEW_VOLUME;
    private static double wheel;

    private final Font tr;
    private final Value value;
    private final int accent;
    private String bound = "";
    private boolean dragging, volDragging;
    private String cue;
    private String hint;

    public SoundPlayer(Font tr, Value value, int accent) {
        this.tr = tr;
        this.value = value;
        this.accent = accent;
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
        if (in.key() != GLFW.GLFW_KEY_SPACE
                || (in.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return false;
        Audio.toggle();
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
        if (value != null) Audio.mix(value.volume, value.pitch2, value.source);
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
        Draw.glyph(ctx, Draw.SPEAKER, b.volX(), iconY,
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
