package com.xerocode.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.xerocode.Env;
import com.xerocode.Settings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

final class Sizer {
    private static final int OUT = 3, IN = 2, CORNER = 9, DOT = 2, SLOP = 3;
    private static final int NONE = 0, RIGHT = 1, BOTTOM = 2, BOTH = 3;

    private final String key;
    private final int minW, minH;
    private final boolean centered, keepH;
    private int userW, userH;
    private int mode, fromW, fromH;
    private double fromX, fromY;
    private boolean movedX, movedY;

    Sizer(String key, int minW, int minH, boolean centered) {
        this(key, minW, minH, centered, true);
    }

    Sizer(String key, int minW, int minH, boolean centered, boolean keepH) {
        this.key = key;
        this.minW = minW;
        this.minH = minH;
        this.centered = centered;
        this.keepH = keepH;
        int[] stored = Settings.get().size(key);
        userW = stored[0];
        userH = keepH ? stored[1] : 0;
    }

    static boolean usable() { return !Env.touch(); }

    int w(int screenW, int auto) {
        return userW <= 0 || !usable() ? auto : fit(userW, minW, screenW);
    }

    int h(int screenH, int auto) {
        return userH <= 0 || !usable() ? auto : fit(userH, minH, screenH);
    }

    boolean sizedH() { return userH > 0 && usable(); }

    private static int fit(int want, int min, int screen) {
        int room = Math.max(60, screen - Ui.margin(screen) * 2);
        return Math.min(room, Math.max(min, want));
    }

    private int zone(double mx, double my, int x, int y, int w, int h) {
        if (!usable()) return NONE;
        boolean inX = mx >= x + w - CORNER && mx < x + w + OUT;
        boolean inY = my >= y + h - CORNER && my < y + h + OUT;
        if (inX && inY) return BOTH;
        if (mx >= x + w - IN && mx < x + w + OUT && my >= y && my < y + h) return RIGHT;
        if (my >= y + h - IN && my < y + h + OUT && mx >= x && mx < x + w) return BOTTOM;
        return NONE;
    }

    boolean over(double mx, double my, int x, int y, int w, int h) {
        return mode != NONE || zone(mx, my, x, y, w, h) != NONE;
    }

    boolean press(MouseButtonEvent click, boolean doubled, int x, int y, int w, int h) {
        if (click.button() != 0) return false;
        int z = zone(click.x(), click.y(), x, y, w, h);
        if (z == NONE) return false;
        if (doubled) {
            if (z != BOTTOM) userW = 0;
            if (z != RIGHT) userH = 0;
            Settings.get().size(key, userW, keepH ? userH : 0);
            mode = NONE;
            return true;
        }
        mode = z;
        movedX = false;
        movedY = false;
        fromX = click.x();
        fromY = click.y();
        fromW = w;
        fromH = h;
        return true;
    }

    boolean active() { return mode != NONE; }

    boolean drag(double mx, double my) {
        if (mode == NONE) return false;
        int k = centered ? 2 : 1;
        movedX |= Math.abs(mx - fromX) >= SLOP;
        movedY |= Math.abs(my - fromY) >= SLOP;
        if (mode != BOTTOM && movedX)
            userW = Math.max(minW, fromW + (int) Math.round((mx - fromX) * k));
        if (mode != RIGHT && movedY)
            userH = Math.max(minH, fromH + (int) Math.round((my - fromY) * k));
        return true;
    }

    boolean release() {
        if (mode == NONE) return false;
        mode = NONE;
        Settings.get().size(key, userW, keepH ? userH : 0);
        return true;
    }

    void draw(GuiGraphicsExtractor ctx, int mx, int my, int x, int y, int w, int h) {
        if (!usable()) return;
        int z = mode != NONE ? mode : zone(mx, my, x, y, w, h);
        if (z == BOTH) ctx.requestCursor(CursorTypes.RESIZE_ALL);
        else if (z == RIGHT) ctx.requestCursor(CursorTypes.RESIZE_EW);
        else if (z == BOTTOM) ctx.requestCursor(CursorTypes.RESIZE_NS);
        int ink = Draw.opaque(z != NONE ? Theme.ACCENT : Theme.TEXT_FAINT);
        int right = x + w - 3 - DOT, bottom = y + h - 3 - DOT;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col <= row; col++)
                Draw.rect(ctx, right - col * (DOT + 1), bottom - (2 - row) * (DOT + 1), DOT, DOT, ink);
    }
}
