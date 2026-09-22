package com.xerocode.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;

public final class Backstage {
    private static final double LIFT = 4096;
    private static final float SKY = -90f;
    private static Marker marker;
    private static boolean broken;

    private Backstage() {}

    static void hide(Minecraft mc) {
        if (broken || mc.player == null || mc.level == null || LocationPick.active()) return;
        Entity cam = mc.getCameraEntity();
        if (cam != mc.player && !filming(mc)) return;
        try {
            if (marker == null || marker.level() != mc.level) marker = new Marker(EntityTypes.MARKER, mc.level);
            marker.snapTo(mc.player.getX(), mc.player.getY() + LIFT, mc.player.getZ(), mc.player.getYRot(), SKY);
            if (cam != marker) mc.setCameraEntity(marker);
        } catch (Throwable e) {
            broken = true;
            restore(mc);
        }
    }

    private static boolean filming(Minecraft mc) {
        return marker != null && mc.getCameraEntity() == marker;
    }

    public static void restore(Minecraft mc) {
        if (!filming(mc)) return;
        try {
            if (mc.player != null) mc.setCameraEntity(mc.player);
        } catch (Throwable ignored) {
        }
    }

    public static void guard(Minecraft mc) {
        if (filming(mc) && !(mc.gui.screen() instanceof EditorScreen)) restore(mc);
    }
}
