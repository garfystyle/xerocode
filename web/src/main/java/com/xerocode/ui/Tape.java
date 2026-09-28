package com.xerocode.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.item.ItemStack;

public final class Tape {
    private static int epoch;

    private Tape() {}

    static int epoch() { return epoch; }

    public static void register() {}

    static void scaled(int x, int y, int s) {}

    static void unscaled() {}

    boolean valid(long key) { return false; }

    static void begin(long key) {}

    static Tape end(Batch batch) { return null; }

    static void abort() {}

    static void text(boolean smooth, Object text, int x, int y, int argb, boolean shadow) {}

    static void item(ItemStack stack, int x, int y, int size) {}

    void replay(GuiGraphicsExtractor ctx, Font font, ScreenRectangle area) {}
}
