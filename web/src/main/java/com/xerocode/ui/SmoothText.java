package com.xerocode.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FormattedCharSequence;

public final class SmoothText {
    private SmoothText() {}

    public static void clip(ScreenRectangle area) {}

    public static boolean draw(GuiGraphicsExtractor ctx, Font tr, FormattedCharSequence text, int x, int y, int argb,
                               boolean shadow) {
        return false;
    }

    public static boolean draw(GuiGraphicsExtractor ctx, Font tr, String text, int x, int y, int argb, boolean shadow) {
        return false;
    }

    static int flags(GuiGraphicsExtractor ctx) { return 0; }
}
