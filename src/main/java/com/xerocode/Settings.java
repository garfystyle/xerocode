package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.ui.Theme;
import com.xerocode.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Settings {
    public enum Mode { CANVAS, ORIGINAL }

    public static final int BTN_SOFT = 0, BTN_FLAT = 1, BTN_ROUND = 2, BTN_OUTLINE = 3;
    public static final String[] BTN_NAMES = {"Мягкие", "Прямые", "Круглые", "Контурные"};

    public static final int GRID_LINES = 0, GRID_DOTS = 1, GRID_NONE = 2;
    public static final String[] GRID_NAMES = {"Линии", "Точки", "Нет"};

    public static final int THEME_DARK = 0, THEME_LIGHT = 1;
    public static final String[] THEME_NAMES = {"Тёмная", "Светлая"};

    public static final String[] YES_NO = {"Есть", "Нет"};
    public static final String[] BLOCK_NAMES = {"Градиент", "Плоские"};

    public enum Hot {
        OPEN     ("open",      "Открыть кодинг",       GLFW.GLFW_KEY_G,      CTRL),
        SETTINGS ("settings",  "Настройки",            GLFW.GLFW_KEY_COMMA,  CTRL),
        MODE     ("mode",      "3D-кодинг",            GLFW.GLFW_KEY_M,      CTRL),
        UNDO     ("undo",      "Отменить",             GLFW.GLFW_KEY_Z,      CTRL),
        REDO     ("redo",      "Вернуть",              GLFW.GLFW_KEY_Y,      CTRL),
        SAVE     ("save",      "Сохранить",            GLFW.GLFW_KEY_S,      CTRL),
        UPLOAD   ("upload",    "Сохранить на сервер",  GLFW.GLFW_KEY_S,      CTRL | SHIFT),
        SEARCH   ("search",    "Поиск блока",          GLFW.GLFW_KEY_F,      CTRL),
        FIT      ("fit",       "Показать всё",         GLFW.GLFW_KEY_0,      CTRL),
        DUPLICATE("duplicate", "Дублировать стопку",   GLFW.GLFW_KEY_D,      CTRL),
        DELETE   ("delete",    "Удалить блок",         GLFW.GLFW_KEY_DELETE, 0),
        PLAY     ("play",      "Игра",                 GLFW.GLFW_KEY_P,      CTRL),
        BUILD    ("build",     "Строительство",        GLFW.GLFW_KEY_B,      CTRL);

        public final String id, label;
        public final int defCode, defMods;

        Hot(String id, String label, int defCode, int defMods) {
            this.id = id; this.label = label;
            this.defCode = defCode; this.defMods = defMods;
        }
    }

    public static final int CTRL = GLFW.GLFW_MOD_CONTROL;
    public static final int SHIFT = GLFW.GLFW_MOD_SHIFT;
    public static final int ALT = GLFW.GLFW_MOD_ALT;
    public static final int MOD_MASK = CTRL | SHIFT | ALT;
    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

    private static Settings INSTANCE;

    public Mode mode = Mode.CANVAS;

    public int theme = THEME_DARK;
    public int buttons = BTN_SOFT;
    public boolean shadows = true;
    public boolean gradient = true;
    public int grid = GRID_LINES;
    public boolean smoothText = true;

    public final Map<String, Integer> colors = new LinkedHashMap<>();
    public final List<String> symbols = new ArrayList<>();
    public boolean drawableOnly = true;

    private final Map<Hot, int[]> keys = new EnumMap<>(Hot.class);

    private Settings() {
        for (Hot h : Hot.values()) keys.put(h, new int[]{h.defCode, h.defMods});
    }

    public static Settings get() {
        if (INSTANCE == null) {
            INSTANCE = load();
            INSTANCE.apply();
        }
        return INSTANCE;
    }

    public static boolean shadows()   { return get().shadows; }
    public static boolean gradient()  { return get().gradient; }
    public static boolean smoothText(){ return get().smoothText; }
    public static int gridStyle()     { return get().grid; }
    public static boolean outlined()  { return get().buttons == BTN_OUTLINE; }
    public static boolean canvasMode(){ return get().mode == Mode.CANVAS; }

    public static int radius(int h) {
        int r = switch (get().buttons) {
            case BTN_FLAT -> 0;
            case BTN_ROUND -> h;
            default -> Ui.R_SM;
        };
        return Math.max(0, Math.min(r, h / 2));
    }

    public int code(Hot h) { return keys.get(h)[0]; }
    public int mods(Hot h) { return keys.get(h)[1]; }

    public void bind(Hot h, int code, int mods) {
        keys.get(h)[0] = code;
        keys.get(h)[1] = mods & MOD_MASK;
    }

    public Hot match(int code, int mods) {
        if (code == NONE) return null;
        int m = mods & MOD_MASK;
        for (Hot h : Hot.values()) {
            int[] k = keys.get(h);
            if (k[0] == code && k[1] == m) return h;
        }
        return null;
    }

    public boolean clashes(Hot h) {
        int code = code(h), mods = mods(h);
        if (code == NONE) return false;
        for (Hot other : Hot.values()) {
            if (other == h) continue;
            if (code(other) == code && mods(other) == mods) return true;
        }
        return false;
    }

    public String label(Hot h) {
        int code = code(h);
        if (code == NONE) return "—";
        StringBuilder sb = new StringBuilder();
        int mods = mods(h);
        if ((mods & CTRL) != 0) sb.append("Ctrl+");
        if ((mods & SHIFT) != 0) sb.append("Shift+");
        if ((mods & ALT) != 0) sb.append("Alt+");
        return sb.append(keyName(code)).toString();
    }

    public static String keyName(int code) {
        try {
            return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
        } catch (Throwable e) {
            return "клавиша " + code;
        }
    }

    public void setColor(String category, int rgb) {
        colors.put(category, rgb & 0xFFFFFF);
        Catalog.applyColors(colors);
    }

    public void clearColor(String category) {
        colors.remove(category);
        Catalog.applyColors(colors);
    }

    public void applyPreset(Map<String, Integer> preset) {
        colors.clear();
        colors.putAll(preset);
        Catalog.applyColors(colors);
    }

    public void apply() {
        if (theme == THEME_LIGHT) Theme.light(); else Theme.dark();
        switch (buttons) {
            case BTN_FLAT -> { Ui.R = 0; Ui.R_SM = 0; }
            case BTN_ROUND -> { Ui.R = 10; Ui.R_SM = 8; }
            default -> { Ui.R = 6; Ui.R_SM = 4; }
        }
        Catalog.applyColors(colors);
    }

    public void reset() {
        for (Hot h : Hot.values()) keys.put(h, new int[]{h.defCode, h.defMods});
        theme = THEME_DARK;
        buttons = BTN_SOFT;
        grid = GRID_LINES;
        shadows = true;
        gradient = true;
        smoothText = true;
        colors.clear();
        apply();
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("xerocode/settings.json");
    }

    public JsonObject toJson() {
        JsonObject look = new JsonObject();
        look.addProperty("theme", theme);
        look.addProperty("buttons", buttons);
        look.addProperty("grid", grid);
        look.addProperty("shadows", shadows);
        look.addProperty("gradient", gradient);
        look.addProperty("smoothText", smoothText);
        look.addProperty("drawableOnly", drawableOnly);

        JsonObject hot = new JsonObject();
        for (Hot h : Hot.values()) {
            int[] k = keys.get(h);
            hot.addProperty(h.id, k[0] + ":" + k[1]);
        }

        JsonObject palette = new JsonObject();
        colors.forEach((name, rgb) -> palette.addProperty(name, String.format("#%06X", rgb)));

        JsonArray marks = new JsonArray();
        for (String s : symbols) marks.add(s);

        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.addProperty("mode", mode == Mode.ORIGINAL ? "original" : "canvas");
        root.add("look", look);
        root.add("keys", hot);
        root.add("colors", palette);
        root.add("symbols", marks);
        return root;
    }

    public void save() {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                w.write(toJson().toString());
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] не удалось записать настройки", e);
        }
    }

    private static Settings load() {
        Settings s = new Settings();
        try {
            Path p = file();
            if (!Files.exists(p)) return s;
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                if (root.has("mode") && "original".equals(root.get("mode").getAsString()))
                    s.mode = Mode.ORIGINAL;
                if (root.has("look")) {
                    JsonObject look = root.getAsJsonObject("look");
                    s.theme = clamp(num(look, "theme", s.theme), 0, THEME_NAMES.length - 1);
                    s.buttons = clamp(num(look, "buttons", s.buttons), 0, BTN_NAMES.length - 1);
                    s.grid = clamp(num(look, "grid", s.grid), 0, GRID_NAMES.length - 1);
                    s.shadows = flag(look, "shadows", s.shadows);
                    s.gradient = flag(look, "gradient", s.gradient);
                    s.smoothText = flag(look, "smoothText", s.smoothText);
                    s.drawableOnly = flag(look, "drawableOnly", s.drawableOnly);
                }
                if (root.has("keys")) {
                    JsonObject hot = root.getAsJsonObject("keys");
                    for (Hot h : Hot.values()) {
                        if (!hot.has(h.id)) continue;
                        String[] parts = hot.get(h.id).getAsString().split(":");
                        if (parts.length != 2) continue;
                        s.keys.put(h, new int[]{Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]) & MOD_MASK});
                    }
                }
                if (root.has("colors")) {
                    JsonObject palette = root.getAsJsonObject("colors");
                    for (String name : palette.keySet()) {
                        String hex = palette.get(name).getAsString().replace("#", "");
                        try {
                            s.colors.put(name, Integer.parseInt(hex, 16) & 0xFFFFFF);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (root.has("symbols"))
                    for (JsonElement el : root.getAsJsonArray("symbols"))
                        s.symbols.add(el.getAsString());
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] не удалось прочитать настройки", e);
        }
        return s;
    }

    private static int num(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    private static boolean flag(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
