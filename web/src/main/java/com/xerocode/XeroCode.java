package com.xerocode;

import com.xerocode.ui.EditorScreen;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class XeroCode {
    public static final Logger LOG = LoggerFactory.getLogger("xerocode");
    public static final String RESTART = "restart";

    private static Script script;
    private static String plot = "";

    private XeroCode() {}

    public static void init() {
        Catalog.load();
        Values.load();
        Pickers.load();
        Mapping.load();
        Placeholders.load();
        Components.load();
        Settings.get().apply();
    }

    public static Script script() {
        if (script == null) script = Script.load(plot);
        return script;
    }

    public static String plot() { return plot; }

    public static void switchPlot(String next) {
        if (next.equals(plot) && script != null) return;
        if (script != null) script.save();
        plot = next;
        script = Script.load(plot);
        History.clear();
        openCanvas(Minecraft.getInstance());
    }

    public static void restart() {}

    public static void cover(String label, Runnable done) {}

    public static void coverDismissed() {}

    public static void canvasClosed() {}

    public static void openCanvas(Minecraft client) {
        if (!Catalog.loaded()) Catalog.load();
        Settings settings = Settings.get();
        if (settings.mode != Settings.Mode.CANVAS) {
            settings.mode = Settings.Mode.CANVAS;
            settings.save();
        }
        client.gui.setScreen(new EditorScreen(script()));
    }

    public static void saveAll() {
        if (script == null) return;
        if (Minecraft.getInstance().gui.screen() instanceof EditorScreen editor) editor.rememberView();
        script.save();
    }
}
