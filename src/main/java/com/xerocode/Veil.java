package com.xerocode;

import com.xerocode.ui.Draw;
import com.xerocode.ui.Theme;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class Veil {
    private static Screen home;
    private static Field tooltip;
    private static boolean tooltipLooked;

    private Veil() {}

    public static void show(Screen screen) { home = screen; }

    public static void lift() { home = null; }

    static void attach(Screen screen) {
        Screen cover = home;
        if (cover == null || screen == cover) return;
        ScreenEvents.afterExtract(screen).register((s, ctx, mouseX, mouseY, delta) -> {
            Screen now = home;
            if (now == null || XeroCode.retired()) return;
            hideTooltip(ctx);
            Draw.rect(ctx, 0, 0, s.width, s.height, Draw.opaque(Theme.CANVAS));
            now.extractRenderState(ctx, mouseX, mouseY, delta);
        });
        ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> home == null);
        ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> home == null);
        ScreenMouseEvents.allowMouseDrag(screen).register((s, e, dx, dy) -> home == null);
        ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> home == null);
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> home == null);
        ScreenKeyboardEvents.allowKeyRelease(screen).register((s, e) -> home == null);
        ScreenKeyboardEvents.allowCharType(screen).register((s, e) -> home == null);
    }

    static void tick(Minecraft mc) {
        Screen cover = home;
        if (cover != null && mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(cover);
    }

    private static void hideTooltip(GuiGraphicsExtractor ctx) {
        try {
            if (!tooltipLooked) {
                tooltipLooked = true;
                tooltip = GuiGraphicsExtractor.class.getDeclaredField("deferredTooltip");
                tooltip.setAccessible(true);
            }
            if (tooltip != null) tooltip.set(ctx, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            tooltip = null;
            XeroCode.LOG.warn("[xerocode] подсказку под занавесом не убрать: {}", e.toString());
        }
    }
}
