package com.xerocode.ui;

import com.xerocode.Audio;
import com.xerocode.Value;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

public final class SoundPlayer implements CatalogPicker.Extra {
    public static final int H = 30;
    private static final int BTN = 16;
    private static final int PAD = 7;
    private static final String CLOCK = "00:00.0 / 00:00.0";

    private final TextRenderer tr;
    private final Value value;
    private final int accent;
    private String bound = "";
    private boolean dragging;
    private String hint;

    public SoundPlayer(TextRenderer tr, Value value, int accent) {
        this.tr = tr;
        this.value = value;
        this.accent = accent;
    }

    @Override
    public int height() { return H; }

    @Override
    public void select(String id) { bound = id == null ? "" : id; }

    @Override
    public String hint() { return hint; }

    @Override
    public boolean keyPressed(KeyInput in) {
        if (in.key() != GLFW.GLFW_KEY_SPACE
                || (in.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return false;
        Audio.toggle();
        return true;
    }

    private record Bar(int row, int playX, int rewX, int loopX, int barX, int barW, int barY,
                       int clockRight) {}

    private Bar layout(int x, int y, int w, int h) {
        int row = y + (h - BTN) / 2;
        int left = x + PAD;
        int loopX = x + w - PAD - BTN;
        int clockRight = loopX - 7;
        int barX = left + 2 * (BTN + 3) + 4;
        int barW = Math.max(20, clockRight - tr.getWidth(CLOCK) - 8 - barX);
        return new Bar(row, left, left + BTN + 3, loopX, barX, barW, y + h / 2 - 2, clockRight);
    }

    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        render(ctx, x, y, w, h, mouseX, mouseY, false);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY,
                       boolean flush) {
        hint = null;
        Audio.want(bound);
        if (value != null) Audio.mix(value.volume, value.pitch2, value.source);
        Audio.tick();

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

    private void drawTrack(DrawContext ctx, Bar b, int mouseX, int mouseY, int accent, int textY) {
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
        int clockW = tr.getWidth(clock);
        if (muted) {
            Draw.glyph(ctx, Draw.WARN, b.clockRight() - clockW - 11, textY, 0xFFE066);
            hint = "громкость 0 — ничего не будет слышно";
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
        if (Audio.ready() && Ui.hit(mx, my, b.barX() - 3, b.barY() - 6, b.barW() + 6, 16)) {
            dragging = true;
            scrub(mx, b);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(int mx, int x, int y, int w, int h) {
        if (!dragging) return false;
        scrub(mx, layout(x, y, w, h));
        return true;
    }

    @Override
    public void mouseReleased() { dragging = false; }

    private void scrub(int mx, Bar b) {
        double t = (mx - b.barX()) / (double) Math.max(1, b.barW());
        Audio.seek(Audio.duration() * Math.max(0, Math.min(1, t)));
    }
}
