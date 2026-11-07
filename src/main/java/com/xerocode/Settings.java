package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xerocode.code.Editing;
import com.xerocode.ui.Gamma;
import com.xerocode.ui.Theme;
import com.xerocode.ui.Ui;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class Settings {
    public enum Mode { CANVAS, ORIGINAL, CODE }

    public static final int BTN_SOFT = 0, BTN_FLAT = 1, BTN_ROUND = 2, BTN_OUTLINE = 3;
    public static final String[] BTN_NAMES = {"Мягкие", "Прямые", "Круглые", "Контурные"};

    public static final int GRID_LINES = 0, GRID_DOTS = 1, GRID_NONE = 2;
    public static final String[] GRID_NAMES = {"Линии", "Точки", "Нет"};

    public static final int THEME_DARK = 0, THEME_LIGHT = 1;
    public static final String[] THEME_NAMES = {"Тёмная", "Светлая"};

    public static final String[] CONTRAST_NAMES = {"Мягкий", "Обычный", "Высокий", "Полный"};
    public static final String[] BLOCK_TONE_NAMES = {"Приглушённые", "Обычные", "Яркие"};
    public static final String[] CODE_TONE_NAMES = {"Спокойная", "Обычная", "Яркая"};
    public static final String[] DIM_NAMES = {"Слабое", "Обычное", "Сильное"};
    public static final String[] GRID_STEP_NAMES = {"Мелкий", "Обычный", "Крупный"};
    public static final String[] MAP_NAMES = {"Малая", "Обычная", "Большая"};
    public static final String[] WHEEL_NAMES = {"Масштаб", "Прокрутка"};
    public static final String[] SPEED_NAMES = {"Медленно", "Обычно", "Быстро"};
    public static final String[] TOAST_NAMES = {"Коротко", "Обычно", "Долго"};
    public static final String[] UNDO_NAMES = {"40", "80", "200"};
    public static final double[] WHEEL_ZOOM = {1.05, 1.08, 1.16};
    public static final double[] WHEEL_STEP = {24, 48, 96};
    private static final int[] GRID_STEPS = {16, 26, 52};
    private static final double[] MAP_SCALES = {0.72, 1, 1.4};
    private static final int[] TOAST_MS = {1500, 2600, 5000};
    private static final int[] UNDO_STEPS = {40, 80, 200};
    private static final int NORMAL = 1, UNSET = -1;

    public static final String[] YES_NO = {"Есть", "Нет"};
    public static final String[] BLOCK_NAMES = {"Градиент", "Плоские"};
    public static final String[] CATALOG_NAMES = {"Список слева", "Сундуки"};
    public static final String[] SCALE_NAMES = {"Авто", "1", "2", "3", "4"};
    public static final int UPLOAD_XEROCODE = 0, UPLOAD_JUSTMC = 1;
    public static final String[] UPLOAD_NAMES = {"XeroCode", "JustMC"};

    public enum Hot {
        OPEN     ("open",      "Открыть кодинг",       GLFW.GLFW_KEY_G,      CTRL),
        SETTINGS ("settings",  "Настройки",            GLFW.GLFW_KEY_COMMA,  CTRL),
        MODE     ("mode",      "3D-кодинг",            GLFW.GLFW_KEY_M,      CTRL),
        UNDO     ("undo",      "Отменить",             GLFW.GLFW_KEY_Z,      CTRL),
        REDO     ("redo",      "Вернуть",              GLFW.GLFW_KEY_Y,      CTRL),
        SAVE     ("save",      "Сохранить",            GLFW.GLFW_KEY_S,      CTRL),
        SAVE_TAB ("saveTab",   "Вкладку в файл",       GLFW.GLFW_KEY_U,      CTRL | SHIFT),
        UPLOAD   ("upload",    "Сохранить на сервер",  GLFW.GLFW_KEY_S,      CTRL | SHIFT),
        SEARCH   ("search",    "Поиск блока",          GLFW.GLFW_KEY_F,      CTRL),
        FIND     ("find",      "Поиск по коду",        GLFW.GLFW_KEY_F,      CTRL | SHIFT),
        FIT      ("fit",       "Показать всё",         GLFW.GLFW_KEY_0,      CTRL),
        COPY     ("copy",      "Копировать стопку",    GLFW.GLFW_KEY_C,      CTRL),
        COPY_ONE ("copyOne",   "Копировать блок",      GLFW.GLFW_KEY_C,      CTRL | SHIFT),
        CUT      ("cut",       "Вырезать стопку",      GLFW.GLFW_KEY_X,      CTRL),
        CUT_ONE  ("cutOne",    "Вырезать блок",        GLFW.GLFW_KEY_X,      CTRL | SHIFT),
        PASTE    ("paste",     "Вставить",             GLFW.GLFW_KEY_V,      CTRL),
        DUPLICATE("duplicate", "Дублировать стопку",   GLFW.GLFW_KEY_D,      CTRL),
        DUP_ONE  ("dupOne",    "Дублировать блок",     GLFW.GLFW_KEY_D,      CTRL | SHIFT),
        WRAP     ("wrap",      "Обернуть в условие или цикл", GLFW.GLFW_KEY_C, SHIFT),
        DELETE   ("delete",    "Удалить блок",         MOUSE + GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 0),
        DEL_STACK("delStack",  "Удалить стопку",       MOUSE + GLFW.GLFW_MOUSE_BUTTON_MIDDLE, SHIFT),
        SELECT   ("select",    "Выделить всё",         GLFW.GLFW_KEY_A,      CTRL),
        BACKPACK ("backpack",  "Рюкзак кода",          GLFW.GLFW_KEY_R,      CTRL),
        MARKET   ("market",    "Модули",               GLFW.GLFW_KEY_E,      CTRL),
        STASH    ("stash",     "Убрать в рюкзак",      GLFW.GLFW_KEY_R,      CTRL | SHIFT),
        PLAY     ("play",      "Игра",                 GLFW.GLFW_KEY_P,      CTRL),
        BUILD    ("build",     "Строительство",        GLFW.GLFW_KEY_B,      CTRL),
        RESTART  ("restart",   "Перезапустить мир",    GLFW.GLFW_KEY_P,      CTRL | SHIFT),
        EDIT_LINE("editLine",  "Строку в редактор",    GLFW.GLFW_KEY_G,      CTRL | SHIFT),
        FOLD     ("fold",      "Свернуть тело блока",  GLFW.GLFW_KEY_MINUS, CTRL),
        FOLD_ALL ("foldAll",   "Свернуть все тела",    GLFW.GLFW_KEY_MINUS, CTRL | SHIFT),
        FOLD_STACK("foldStack", "Свернуть стопку",     GLFW.GLFW_KEY_MINUS, CTRL | ALT),
        FOLD_STACKS("foldStacks", "Свернуть все стопки", GLFW.GLFW_KEY_MINUS, CTRL | ALT | SHIFT),
        GO_DEF   ("goDef",     "К объявлению функции", MOUSE + GLFW.GLFW_MOUSE_BUTTON_LEFT, CTRL),
        GO_BACK  ("goBack",    "Вернуться назад",      GLFW.GLFW_KEY_LEFT,   ALT),
        USAGE_NEXT("usageNext", "Следующее использование", GLFW.GLFW_KEY_DOWN, ALT),
        USAGE_PREV("usagePrev", "Предыдущее использование", GLFW.GLFW_KEY_UP,  ALT),
        RENAME   ("rename",    "Переименовать функцию или переменную", GLFW.GLFW_KEY_F2, 0),
        QUICK_ADD("quickAdd",  "Быстро добавить блок", Env.browser() ? GLFW.GLFW_KEY_K : GLFW.GLFW_KEY_N, CTRL),
        PREV_LINE("prevLine",  "Предыдущая строка",    GLFW.GLFW_KEY_UP,     CTRL),
        NEXT_LINE("nextLine",  "Следующая строка",     GLFW.GLFW_KEY_DOWN,   CTRL),
        TIDY     ("tidy",      "Упорядочить строки",   GLFW.GLFW_KEY_L,      CTRL);

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
    public static final int MOUSE = 1000;
    private static final int MOUSE_BUTTONS = 8;

    public static int mouse(int button) { return MOUSE + button; }

    public static boolean isMouse(int code) { return code >= MOUSE && code < MOUSE + MOUSE_BUTTONS; }

    public static int button(int code) { return code - MOUSE; }

    public static boolean isModifier(int code) {
        return switch (canonical(code)) {
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_ALT,
                 GLFW.GLFW_KEY_LEFT_SUPER -> true;
            default -> false;
        };
    }

    public static int canonical(int code) {
        return switch (code) {
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case GLFW.GLFW_KEY_RIGHT_ALT -> GLFW.GLFW_KEY_LEFT_ALT;
            case GLFW.GLFW_KEY_RIGHT_SUPER -> GLFW.GLFW_KEY_LEFT_SUPER;
            default -> code;
        };
    }

    public static int twin(int code) {
        return switch (canonical(code)) {
            case GLFW.GLFW_KEY_LEFT_CONTROL -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case GLFW.GLFW_KEY_LEFT_ALT -> GLFW.GLFW_KEY_RIGHT_ALT;
            case GLFW.GLFW_KEY_LEFT_SUPER -> GLFW.GLFW_KEY_RIGHT_SUPER;
            default -> NONE;
        };
    }

    public static int ownMod(int code) {
        return switch (canonical(code)) {
            case GLFW.GLFW_KEY_LEFT_CONTROL -> CTRL;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> SHIFT;
            case GLFW.GLFW_KEY_LEFT_ALT -> ALT;
            default -> 0;
        };
    }

    private static int cleanMods(int code, int mods) {
        return mods & MOD_MASK & ~ownMod(code);
    }

    private static Settings INSTANCE;

    public Mode mode = Mode.CANVAS;

    public int codeUnit;
    public String codeFont = com.xerocode.code.CodeFonts.DEFAULT;
    public int codeSize;
    public int codeSideW = 150;
    public int codeBottomH = 90;
    public boolean codeSideOpen = true;
    public boolean codeBottomOpen = true;

    public int theme = THEME_DARK;
    public int buttons = BTN_SOFT;
    public boolean shadows = true;
    public boolean gradient = true;
    public int grid = GRID_LINES;
    public boolean smoothText = true;
    public boolean minimap = true;
    public boolean lineNumbers = true;
    public boolean chests = Env.touch();
    public boolean statusBar = false;
    public int webScale = 0;
    public int upload = UPLOAD_XEROCODE;
    public static final int PREVIEW_VOLUME = 50;
    private static final double LOUDNESS_POWER = Math.log(10) / Math.log(4);
    public int previewVolume = PREVIEW_VOLUME;

    public int gamma;
    public int gammaAccent = UNSET;
    public int gammaTint = UNSET;
    public int contrast = Gamma.CONTRAST_NORMAL;
    public int blockTone = Gamma.TONE_NORMAL;
    public int codeTone = Gamma.TONE_NORMAL;

    public int dim = NORMAL;
    public int gridSize = NORMAL;
    public int mapSize = NORMAL;

    public boolean wheelZooms = true;
    public boolean wheelInvert = false;
    public int wheelSpeed = NORMAL;
    public boolean glide = true;
    public boolean tooltips = true;
    public int toastTime = NORMAL;
    public int undoSteps = NORMAL;
    public boolean autoOpen = true;
    public boolean worldCheck = true;
    public boolean askExit = true;
    public boolean codePairs = true;
    public boolean codeHints = true;
    public boolean codeGuides = true;
    public boolean codeLine = true;
    public boolean codeWords = true;
    public boolean codeBlink = true;

    public String collabName = "";
    public String collabCode = "";
    public boolean collabCursors = true;

    public final Map<String, Integer> colors = new LinkedHashMap<>();
    public final List<String> symbols = new ArrayList<>();
    public final List<String> collapsed = new ArrayList<>();
    private final Map<String, int[]> sizes = new LinkedHashMap<>();

    public int[] size(String key) {
        int[] s = sizes.get(key);
        return s == null ? new int[2] : s.clone();
    }

    public void size(String key, int w, int h) {
        if (w <= 0 && h <= 0) sizes.remove(key);
        else sizes.put(key, new int[]{Math.max(0, w), Math.max(0, h)});
        save();
    }
    public boolean drawableOnly = true;

    private final Map<Hot, int[]> keys = new EnumMap<>(Hot.class);

    private Settings() {
        resetKeys();
    }

    private static final JsonObject DEFAULT_LOOK = new Settings().lookJson();
    private static final JsonObject DEFAULT_ACT = new Settings().actJson();

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
    public static boolean minimap()   { return get().minimap; }
    public static int gridStyle()     { return get().grid; }
    public static boolean outlined()  { return get().buttons == BTN_OUTLINE; }
    public static boolean lineNumbers(){ return get().lineNumbers; }
    public static boolean chests()    { return get().chests; }
    public static boolean statusBar() { return get().statusBar; }
    public static boolean canvasMode(){ return get().mode == Mode.CANVAS; }
    public static boolean codeMode(){ return get().mode == Mode.CODE; }
    public static double previewGain(){ return loudness(get().previewVolume); }
    public static int gridStep()      { return GRID_STEPS[get().gridSize]; }
    public static double mapScale()   { return MAP_SCALES[get().mapSize]; }
    public static int toastMs()       { return TOAST_MS[get().toastTime]; }

    static double loudness(int percent) {
        return Math.pow(Math.max(0, Math.min(100, percent)) / 100.0, LOUDNESS_POWER);
    }
    public static boolean uploadToJustmc() { return !Env.browser() && get().upload == UPLOAD_JUSTMC; }

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
        int c = canonical(code);
        keys.get(h)[0] = c;
        keys.get(h)[1] = cleanMods(c, mods);
    }

    public Hot match(int code, int mods) {
        if (code == NONE) return null;
        int c = canonical(code);
        int m = cleanMods(c, mods);
        for (Hot h : Hot.values()) {
            int[] k = keys.get(h);
            if (k[0] == c && k[1] == m) return h;
        }
        return redoTwin(c, m) ? Hot.REDO : null;
    }

    private boolean redoTwin(int code, int mods) {
        int undoMods = mods(Hot.UNDO);
        return code != NONE && code == code(Hot.UNDO) && (undoMods & SHIFT) == 0
                && mods == (undoMods | SHIFT);
    }

    public String twinLabel(Hot h) {
        if (h != Hot.REDO) return null;
        int code = code(Hot.UNDO), mods = mods(Hot.UNDO) | SHIFT;
        if (match(code, mods) != Hot.REDO || code == code(h) && mods == mods(h)) return null;
        return label(code, mods);
    }

    public String labels(Hot h) {
        String twin = twinLabel(h);
        return twin == null ? label(h) : label(h) + " или " + twin;
    }

    public boolean onMouse(Hot h) { return isMouse(code(h)); }


    public boolean clashes(Hot h) {
        int code = code(h), mods = mods(h);
        if (code == NONE) return false;
        for (Hot other : Hot.values()) {
            if (other == h) continue;
            if (code(other) == code && mods(other) == mods) return true;
        }
        return false;
    }

    public String label(Hot h) { return label(code(h), mods(h)); }

    private static String label(int code, int mods) {
        if (code == NONE) return "—";
        StringBuilder sb = new StringBuilder();
        if ((mods & CTRL) != 0) sb.append("Ctrl+");
        if ((mods & SHIFT) != 0) sb.append("Shift+");
        if ((mods & ALT) != 0) sb.append("Alt+");
        return sb.append(keyName(code)).toString();
    }

    public static String keyName(int code) {
        if (isMouse(code)) {
            return switch (button(code)) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "ЛКМ";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "ПКМ";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "СКМ";
                default -> "Мышь " + (button(code) + 1);
            };
        }
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) return String.valueOf((char) code);
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) return String.valueOf((char) code);
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25)
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9)
            return "Num " + (code - GLFW.GLFW_KEY_KP_0);
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_RIGHT -> "→";
            case GLFW.GLFW_KEY_LEFT -> "←";
            case GLFW.GLFW_KEY_DOWN -> "↓";
            case GLFW.GLFW_KEY_UP -> "↑";
            case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll Lock";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Num Lock";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print Screen";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_MENU -> "Menu";
            case GLFW.GLFW_KEY_KP_DECIMAL -> "Num .";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "Num /";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num *";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num -";
            case GLFW.GLFW_KEY_KP_ADD -> "Num +";
            case GLFW.GLFW_KEY_KP_ENTER -> "Num Enter";
            case GLFW.GLFW_KEY_KP_EQUAL -> "Num =";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Win";
            case GLFW.GLFW_KEY_WORLD_1 -> "World 1";
            case GLFW.GLFW_KEY_WORLD_2 -> "World 2";
            default -> "клавиша " + code;
        };
    }

    public void setColor(String category, int rgb) {
        colors.put(category, rgb & 0xFFFFFF);
        recolor();
    }

    public void clearColor(String category) {
        colors.remove(category);
        recolor();
    }

    public void applyPreset(Map<String, Integer> preset) {
        colors.clear();
        colors.putAll(preset);
        recolor();
    }

    private void recolor() {
        int tone = blockTone;
        Catalog.applyColors(colors, rgb -> Gamma.block(rgb, tone));
    }

    public int accentRgb() {
        return gamma == Gamma.CUSTOM ? gammaAccent : Gamma.PRESETS[gamma].accent();
    }

    public int tintRgb() {
        return gamma == Gamma.CUSTOM ? gammaTint : Gamma.tint(Gamma.PRESETS[gamma]);
    }

    public void pickGamma(int index) {
        if (index == Gamma.CUSTOM && gammaAccent == UNSET) keepCustom(accentRgb(), tintRgb());
        gamma = index;
    }

    public void customAccent(int rgb) { keepCustom(rgb & 0xFFFFFF, tintRgb()); }

    public void customTint(int rgb) { keepCustom(accentRgb(), rgb & 0xFFFFFF); }

    private void keepCustom(int accent, int tint) {
        gammaAccent = accent;
        gammaTint = tint;
        gamma = Gamma.CUSTOM;
    }

    public void plainAccent() { settleCustom(Gamma.PRESETS[0].accent(), gammaTint); }

    public void plainTint() { settleCustom(gammaAccent, Gamma.tint(Gamma.PRESETS[0])); }

    private void settleCustom(int accent, int tint) {
        keepCustom(accent, tint);
        if (accent != Gamma.PRESETS[0].accent() || tint != Gamma.tint(Gamma.PRESETS[0])) return;
        gamma = 0;
        gammaAccent = UNSET;
        gammaTint = UNSET;
    }

    public Gamma tone(int index) {
        boolean light = theme == THEME_LIGHT;
        if (Gamma.plain(index, contrast)) return null;
        int accent = index == Gamma.CUSTOM && gammaAccent == UNSET ? accentRgb() : gammaAccent;
        int tint = index == Gamma.CUSTOM && gammaTint == UNSET ? tintRgb() : gammaTint;
        return Gamma.of(index, accent, tint, contrast, light);
    }

    public boolean gammaDefault() {
        return gamma == 0 && contrast == Gamma.CONTRAST_NORMAL && blockTone == Gamma.TONE_NORMAL
                && codeTone == Gamma.TONE_NORMAL && gammaAccent == UNSET;
    }

    public void resetGamma() {
        gamma = 0;
        gammaAccent = UNSET;
        gammaTint = UNSET;
        contrast = Gamma.CONTRAST_NORMAL;
        blockTone = Gamma.TONE_NORMAL;
        codeTone = Gamma.TONE_NORMAL;
        apply();
    }

    public void apply() {
        Theme.paint(theme == THEME_LIGHT, tone(gamma), dim);
        switch (buttons) {
            case BTN_FLAT -> { Ui.R = 0; Ui.R_SM = 0; }
            case BTN_ROUND -> { Ui.R = 10; Ui.R_SM = 8; }
            default -> { Ui.R = 6; Ui.R_SM = 4; }
        }
        recolor();
        History.limit(UNDO_STEPS[undoSteps]);
        Editing.pairs = codePairs;
    }

    private static boolean taken(int code, int mods) {
        if ((mods & CTRL) == 0) return false;
        return code == GLFW.GLFW_KEY_N || code == GLFW.GLFW_KEY_T || code == GLFW.GLFW_KEY_W
                || code == GLFW.GLFW_KEY_TAB;
    }

    public boolean isDefault(Hot h) { return code(h) == h.defCode && mods(h) == h.defMods; }

    public void resetKey(Hot h) { keys.put(h, new int[]{h.defCode, h.defMods}); }

    public boolean keysDefault() {
        for (Hot h : Hot.values()) if (!isDefault(h)) return false;
        return true;
    }

    public int clashCount() {
        int n = 0;
        for (Hot h : Hot.values()) if (clashes(h)) n++;
        return n;
    }

    public void resetKeys() {
        for (Hot h : Hot.values()) resetKey(h);
    }

    public boolean lookDefault() { return lookJson().equals(DEFAULT_LOOK); }

    public void resetLook() {
        readLook(DEFAULT_LOOK);
        apply();
    }

    public boolean actDefault() { return actJson().equals(DEFAULT_ACT); }

    public void resetAct() {
        readAct(DEFAULT_ACT);
        apply();
    }

    private JsonObject actJson() {
        JsonObject act = new JsonObject();
        act.addProperty("wheelZooms", wheelZooms);
        act.addProperty("wheelInvert", wheelInvert);
        act.addProperty("wheelSpeed", wheelSpeed);
        act.addProperty("glide", glide);
        act.addProperty("tooltips", tooltips);
        act.addProperty("toastTime", toastTime);
        act.addProperty("undoSteps", undoSteps);
        act.addProperty("upload", upload);
        act.addProperty("autoOpen", autoOpen);
        act.addProperty("worldCheck", worldCheck);
        act.addProperty("askExit", askExit);
        act.addProperty("codePairs", codePairs);
        act.addProperty("codeHints", codeHints);
        act.addProperty("codeGuides", codeGuides);
        act.addProperty("codeLine", codeLine);
        act.addProperty("codeWords", codeWords);
        act.addProperty("codeBlink", codeBlink);
        return act;
    }

    private void readAct(JsonObject act) {
        wheelZooms = flag(act, "wheelZooms", wheelZooms);
        wheelInvert = flag(act, "wheelInvert", wheelInvert);
        wheelSpeed = clamp(num(act, "wheelSpeed", wheelSpeed), 0, SPEED_NAMES.length - 1);
        glide = flag(act, "glide", glide);
        tooltips = flag(act, "tooltips", tooltips);
        toastTime = clamp(num(act, "toastTime", toastTime), 0, TOAST_NAMES.length - 1);
        undoSteps = clamp(num(act, "undoSteps", undoSteps), 0, UNDO_NAMES.length - 1);
        upload = uploadOf(act, upload);
        autoOpen = flag(act, "autoOpen", autoOpen);
        worldCheck = flag(act, "worldCheck", worldCheck);
        askExit = flag(act, "askExit", askExit);
        codePairs = flag(act, "codePairs", codePairs);
        codeHints = flag(act, "codeHints", codeHints);
        codeGuides = flag(act, "codeGuides", codeGuides);
        codeLine = flag(act, "codeLine", codeLine);
        codeWords = flag(act, "codeWords", codeWords);
        codeBlink = flag(act, "codeBlink", codeBlink);
    }

    private static int uploadOf(JsonObject from, int def) {
        return clamp(num(from, "upload", def), 0, UPLOAD_NAMES.length - 1);
    }

    private JsonObject gammaJson() {
        JsonObject out = new JsonObject();
        out.addProperty("index", gamma);
        out.addProperty("contrast", contrast);
        out.addProperty("blocks", blockTone);
        out.addProperty("code", codeTone);
        if (gammaAccent != UNSET) out.addProperty("accent", String.format("#%06X", gammaAccent));
        if (gammaTint != UNSET) out.addProperty("tint", String.format("#%06X", gammaTint));
        return out;
    }

    private void readGamma(JsonObject in) {
        contrast = clamp(num(in, "contrast", contrast), 0, CONTRAST_NAMES.length - 1);
        blockTone = clamp(num(in, "blocks", blockTone), 0, BLOCK_TONE_NAMES.length - 1);
        codeTone = clamp(num(in, "code", codeTone), 0, CODE_TONE_NAMES.length - 1);
        gammaAccent = hex(in, "accent");
        gammaTint = hex(in, "tint");
        gamma = clamp(num(in, "index", gamma), 0, Gamma.COUNT - 1);
        if (gamma == Gamma.CUSTOM && (gammaAccent == UNSET || gammaTint == UNSET)) gamma = 0;
    }

    private static int hex(JsonObject o, String key) {
        if (!o.has(key)) return UNSET;
        try {
            return Integer.parseInt(o.get(key).getAsString().replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return UNSET;
        }
    }

    private JsonObject lookJson() {
        JsonObject look = new JsonObject();
        look.addProperty("theme", theme);
        look.addProperty("buttons", buttons);
        look.addProperty("grid", grid);
        look.addProperty("shadows", shadows);
        look.addProperty("gradient", gradient);
        look.addProperty("smoothText", smoothText);
        look.addProperty("minimap", minimap);
        look.addProperty("lineNumbers", lineNumbers);
        look.addProperty("chests", chests);
        look.addProperty("statusBar", statusBar);
        look.addProperty("webScale", webScale);
        look.addProperty("dim", dim);
        look.addProperty("gridSize", gridSize);
        look.addProperty("mapSize", mapSize);
        return look;
    }

    private void readLook(JsonObject look) {
        theme = clamp(num(look, "theme", theme), 0, THEME_NAMES.length - 1);
        buttons = clamp(num(look, "buttons", buttons), 0, BTN_NAMES.length - 1);
        grid = clamp(num(look, "grid", grid), 0, GRID_NAMES.length - 1);
        shadows = flag(look, "shadows", shadows);
        gradient = flag(look, "gradient", gradient);
        smoothText = flag(look, "smoothText", smoothText);
        minimap = flag(look, "minimap", minimap);
        lineNumbers = flag(look, "lineNumbers", lineNumbers);
        chests = flag(look, "chests", chests);
        statusBar = flag(look, "statusBar", statusBar);
        webScale = clamp(num(look, "webScale", webScale), 0, SCALE_NAMES.length - 1);
        dim = clamp(num(look, "dim", dim), 0, DIM_NAMES.length - 1);
        gridSize = clamp(num(look, "gridSize", gridSize), 0, GRID_STEP_NAMES.length - 1);
        mapSize = clamp(num(look, "mapSize", mapSize), 0, MAP_NAMES.length - 1);
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode/settings.json");
    }

    public JsonObject toJson() {
        JsonObject look = lookJson();
        look.addProperty("drawableOnly", drawableOnly);

        JsonObject hot = new JsonObject();
        for (Hot h : Hot.values()) {
            int[] k = keys.get(h);
            hot.addProperty(h.id, k[0] + ":" + k[1]);
        }

        JsonObject palette = new JsonObject();
        colors.forEach((name, rgb) -> palette.addProperty(name, String.format("#%06X", rgb)));

        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("mode", mode == Mode.ORIGINAL ? "original" : mode == Mode.CODE ? "code" : "canvas");
        root.addProperty("codeUnit", codeUnit);
        root.addProperty("codeFont", codeFont);
        root.addProperty("codeSize", codeSize);
        root.addProperty("codeSideW", codeSideW);
        root.addProperty("codeBottomH", codeBottomH);
        root.addProperty("codeSideOpen", codeSideOpen);
        root.addProperty("codeBottomOpen", codeBottomOpen);
        root.add("look", look);
        root.add("act", actJson());
        root.add("gamma", gammaJson());
        root.add("keys", hot);
        root.add("colors", palette);
        root.addProperty("previewVolume", previewVolume);
        JsonObject collab = new JsonObject();
        collab.addProperty("name", collabName);
        collab.addProperty("code", collabCode);
        collab.addProperty("cursors", collabCursors);

        root.add("symbols", strings(symbols));
        root.add("collapsed", strings(collapsed));
        root.add("collab", collab);
        JsonObject sized = new JsonObject();
        sizes.forEach((key, wh) -> {
            JsonArray pair = new JsonArray();
            pair.add(wh[0]);
            pair.add(wh[1]);
            sized.add(key, pair);
        });
        root.add("sizes", sized);
        return root;
    }

    private static JsonArray strings(List<String> list) {
        JsonArray out = new JsonArray();
        for (String s : list) out.add(s);
        return out;
    }

    private static void readStrings(JsonObject root, String key, List<String> into) {
        if (!root.has(key)) return;
        for (JsonElement el : root.getAsJsonArray(key)) into.add(el.getAsString());
    }

    public void save() {
        try {
            Json.write(file(), toJson());
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] не удалось записать настройки", e);
        }
    }

    private static Settings load() {
        try {
            Path p = file();
            if (!Files.exists(p)) return new Settings();
            return read(Json.read(p));
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] не удалось прочитать настройки", e);
            Json.setAside(file());
            return new Settings();
        }
    }

    static Settings read(JsonObject root) {
        Settings s = new Settings();
        if (root.has("mode") && "original".equals(root.get("mode").getAsString()))
            s.mode = Mode.ORIGINAL;
        if (root.has("mode") && "code".equals(root.get("mode").getAsString()))
            s.mode = Mode.CODE;
        s.codeUnit = num(root, "codeUnit", s.codeUnit);
        s.codeSize = num(root, "codeSize", s.codeSize);
        if (root.has("codeFont")) s.codeFont = root.get("codeFont").getAsString();
        s.codeSideW = num(root, "codeSideW", s.codeSideW);
        s.codeBottomH = num(root, "codeBottomH", s.codeBottomH);
        s.codeSideOpen = flag(root, "codeSideOpen", s.codeSideOpen);
        s.codeBottomOpen = flag(root, "codeBottomOpen", s.codeBottomOpen);
        if (root.has("look")) {
            JsonObject look = root.getAsJsonObject("look");
            s.readLook(look);
            s.upload = uploadOf(look, s.upload);
            s.drawableOnly = flag(look, "drawableOnly", s.drawableOnly);
        }
        if (root.has("act")) s.readAct(root.getAsJsonObject("act"));
        if (root.has("gamma")) s.readGamma(root.getAsJsonObject("gamma"));
        int version = num(root, "version", 1);
        if (version < 4 && Env.browser()) s.chests = Env.touch();
        if (root.has("keys")) {
            JsonObject hot = root.getAsJsonObject("keys");
            for (Hot h : Hot.values()) {
                if (!hot.has(h.id)) continue;
                String[] parts = hot.get(h.id).getAsString().split(":");
                if (parts.length != 2) continue;
                int code = Integer.parseInt(parts[0]), mods = Integer.parseInt(parts[1]) & MOD_MASK;
                if (Env.browser() && taken(code, mods)) continue;
                if (version < 3 && movedDefault(h, code, mods)) continue;
                if (version < 5 && h == Hot.UPLOAD && code == GLFW.GLFW_KEY_S
                        && mods == (CTRL | SHIFT)) continue;
                if (version < 6 && bracketFold(h, code, mods)) continue;
                if (version < 7 && swappedSave(h, code, mods)) continue;
                s.bind(h, code, mods);
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
        s.previewVolume = clamp(num(root, "previewVolume", s.previewVolume), 0, 100);
        if (version < 8 && s.previewVolume == 100) s.previewVolume = PREVIEW_VOLUME;
        readStrings(root, "symbols", s.symbols);
        readStrings(root, "collapsed", s.collapsed);
        if (root.has("sizes") && root.get("sizes").isJsonObject()) {
            JsonObject sized = root.getAsJsonObject("sizes");
            for (String key : sized.keySet()) {
                JsonArray pair = sized.getAsJsonArray(key);
                if (pair.size() == 2)
                    s.sizes.put(key, new int[]{pair.get(0).getAsInt(), pair.get(1).getAsInt()});
            }
        }
        if (root.has("collab")) {
            JsonObject collab = root.getAsJsonObject("collab");
            if (collab.has("name")) s.collabName = collab.get("name").getAsString();
            if (collab.has("code")) s.collabCode = collab.get("code").getAsString();
            s.collabCursors = flag(collab, "cursors", s.collabCursors);
        }
        return s;
    }

    private static final int VERSION = 8;

    private static boolean swappedSave(Hot h, int code, int mods) {
        if (mods != (CTRL | SHIFT)) return false;
        return h == Hot.UPLOAD && code == GLFW.GLFW_KEY_U || h == Hot.SAVE_TAB && code == GLFW.GLFW_KEY_S;
    }

    private static boolean bracketFold(Hot h, int code, int mods) {
        return switch (h) {
            case FOLD -> code == GLFW.GLFW_KEY_LEFT_BRACKET && mods == CTRL;
            case FOLD_ALL -> code == GLFW.GLFW_KEY_LEFT_BRACKET && mods == (CTRL | SHIFT);
            case FOLD_STACK -> code == GLFW.GLFW_KEY_RIGHT_BRACKET && mods == CTRL;
            case FOLD_STACKS -> code == GLFW.GLFW_KEY_RIGHT_BRACKET && mods == (CTRL | SHIFT);
            default -> false;
        };
    }

    private static boolean movedDefault(Hot h, int code, int mods) {
        if (code != GLFW.GLFW_KEY_DELETE) return false;
        return (h == Hot.DELETE && mods == 0) || (h == Hot.DEL_STACK && mods == SHIFT);
    }

    private static int num(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    private static boolean flag(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
