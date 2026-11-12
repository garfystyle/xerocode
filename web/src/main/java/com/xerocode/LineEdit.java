package com.xerocode;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class LineEdit {
    private LineEdit() {}

    public record Where(BlockPos event, int floor, int line) {}

    public static Where where(BlockPos pos) { return null; }

    public static void pressed(Minecraft client) {}

    public static void tick(Minecraft client) {}

    public static String toHand(List<Script.Node> chain) { return "в браузере нет мира"; }

    public static boolean hasPlace(Script scratch) { return false; }

    public static String putBack(Script scratch, List<Script.Node> chain) { return "в браузере нет мира"; }
}
