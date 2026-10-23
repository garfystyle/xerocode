package com.xerocode;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.screens.Screen;

public final class ModuleActions {
    private static final String GAME_ONLY = "это делается только в игре, на сервере JustMC";

    private ModuleActions() {}

    public static boolean busy() { return false; }

    public static String me() { return ""; }

    public static boolean starred(Modules.Module m) { return false; }

    public static int stars(Modules.Module m) { return m.stars; }

    public static String idOf(Modules.Module m) { return null; }

    public static void star(Modules.Module m, Screen home, BiConsumer<Boolean, String> done) {
        done.accept(false, GAME_ONLY);
    }

    public static void install(Modules.Module m, Screen home, BiConsumer<Boolean, String> done) {
        done.accept(false, GAME_ONLY);
    }
}
