package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Catalog {
    public static final class Arg {
        public final String type, purpose;
        public final boolean list;
        public final int capacity;
        public final int[] slots;
        Arg(String type, String purpose, boolean list, int capacity, int[] slots) {
            this.type = type; this.purpose = purpose;
            this.list = list; this.capacity = capacity; this.slots = slots;
        }

        public static Arg cell(String purpose) {
            return new Arg("any", purpose, false, 1, new int[0]);
        }
    }

    public static final class Setting {
        public final String label, def;
        public final List<String> options;
        public final int slot;
        public final boolean quiet;

        Setting(String label, List<String> options, String def, int slot) {
            this(label, options, def, slot, false);
        }

        Setting(String label, List<String> options, String def, int slot, boolean quiet) {
            this.label = label; this.options = options; this.def = def; this.slot = slot;
            this.quiet = quiet;
        }
    }

    public static final class Action {
        public final int id;
        public final String name, item, description;
        public final List<Arg> args;
        public final List<Setting> settings;
        public final boolean unavailable;
        public Category category;
        public String subcategory;
        Action(int id, String name, String item, String description, List<Arg> args,
               List<Setting> settings, boolean unavailable) {
            this.id = id; this.name = name; this.item = item; this.description = description;
            this.args = args; this.settings = settings; this.unavailable = unavailable;
        }
        public ItemStack icon() { return stackOf(item); }
    }

    public static final class Category {
        public final String name, kind, block;
        public int color;
        public final int baseColor;
        public final List<String> subNames = new ArrayList<>();
        public final List<List<Action>> subActions = new ArrayList<>();
        Category(String name, String kind, String block, int color) {
            this.name = name; this.kind = kind; this.block = block;
            this.color = color; this.baseColor = color;
        }
        public boolean isEvent()     { return "event".equals(kind); }
        public boolean isCondition() { return "condition".equals(kind); }
        public boolean wraps()       { return isCondition() || WRAPPING.contains(name); }
        public ItemStack icon()      { return stackOf(block); }
        public int count() {
            int n = 0;
            for (List<Action> l : subActions) n += l.size();
            return n;
        }
    }

    private static final List<String> WRAPPING = List.of("Повторение", "Контроллер", "Иначе");

    private static final Map<String, Integer> COLORS_TEXTURE = Map.ofEntries(
            Map.entry("Событие игрока",          0x44EBF1),
            Map.entry("Событие мира",            0xF7767C),
            Map.entry("Событие сущности",        0xEED04B),
            Map.entry("Если игрок",              0xE79B2F),
            Map.entry("Если сущность",           0xCE5A20),
            Map.entry("Если переменная",         0x2D0B7A),
            Map.entry("Если в мире",             0x51190C),
            Map.entry("Иначе",                   0xA1BE00),
            Map.entry("Повторение",              0x16684F),
            Map.entry("Контроль действий",       0x7B004A),
            Map.entry("Контроллер",              0x053B29),
            Map.entry("Действие над игроком",    0x1154AB),
            Map.entry("Действие над сущностью",  0x85F13D),
            Map.entry("Действие над миром",      0x963210),
            Map.entry("Действие с переменной",   0xFDE2CE),
            Map.entry("Выбрать цель",            0x771797),
            Map.entry("Значения",                0x69A1F6),
            Map.entry("Функция",                 0x64A1FB),
            Map.entry("Вызвать функцию",         0x5C7E9E),
            Map.entry("Процесс",                 0x118D00),
            Map.entry("Запустить процесс",       0x2FB274));

    private static final Map<String, Integer> COLORS_CLASSIC = Map.ofEntries(
            Map.entry("Событие игрока",          0xF2C037),
            Map.entry("Событие мира",            0xD9A125),
            Map.entry("Событие сущности",        0xF7D96B),
            Map.entry("Если игрок",              0xF08C3A),
            Map.entry("Если сущность",           0xE2703C),
            Map.entry("Если переменная",         0xD95F5F),
            Map.entry("Если в мире",             0xEDA45C),
            Map.entry("Иначе",                   0xA8785A),
            Map.entry("Повторение",              0xE05C8A),
            Map.entry("Контроль действий",       0xC4544E),
            Map.entry("Контроллер",              0xEF6EA8),
            Map.entry("Действие над игроком",    0x4A90E2),
            Map.entry("Действие над сущностью",  0xA96BE0),
            Map.entry("Действие над миром",      0x4CB05F),
            Map.entry("Действие с переменной",   0x2FB3A3),
            Map.entry("Выбрать цель",            0x38A8C9),
            Map.entry("Значения",                0x8B7BE8));

    private static final Map<String, Integer> COLORS = COLORS_TEXTURE;

    public static final Map<String, Integer> TYPE_COLORS = Map.ofEntries(
            Map.entry("Текст",           0x3AB3DA),
            Map.entry("Число",           0xE03C3C),
            Map.entry("Предмет",         0xE08A1E),
            Map.entry("Блок",            0xC97C1B),
            Map.entry("Местоположение",  0x7FDB3A),
            Map.entry("Переменная",      0x3FA13F),
            Map.entry("Любое значение",  0xD8D8D8),
            Map.entry("Зелье",           0xD45BD4),
            Map.entry("Вектор",          0x2FBFBF),
            Map.entry("Звук",            0x9B4FD1),
            Map.entry("Эффект частиц",   0xF08FC0),
            Map.entry("Список",          0xE8D33A),
            Map.entry("Словарь",         0x8B5A2B),
            Map.entry("Параметр",        0x63C9E0));

    public static final List<Category> CATEGORIES = new ArrayList<>();
    public static final List<Action> ACTIONS = new ArrayList<>();
    private static final Map<String, Action> BY_KEY = new HashMap<>();

    public static boolean loaded() { return !ACTIONS.isEmpty(); }

    public static Category category(String name) {
        for (Category c : CATEGORIES) if (c.name.equals(name)) return c;
        return null;
    }

    public static void applyColors(Map<String, Integer> overrides) {
        for (Category c : CATEGORIES) {
            Integer color = overrides == null ? null : overrides.get(c.name);
            c.color = color == null ? c.baseColor : color;
        }
    }

    public static Map<String, Integer> classicPalette() {
        return COLORS_CLASSIC;
    }

    public static void load() {
        if (loaded()) return;
        try (InputStream in = Catalog.class.getResourceAsStream("/assets/xerocode/catalog.json")) {
            if (in == null) { XeroCode.LOG.error("[xerocode] catalog.json not found in the jar"); return; }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray acts = root.getAsJsonArray("acts");
            for (int i = 0; i < acts.size(); i++) ACTIONS.add(parseAction(i, acts.get(i).getAsJsonObject()));
            for (JsonElement ce : root.getAsJsonArray("cats")) {
                JsonObject co = ce.getAsJsonObject();
                String name = co.get("n").getAsString();
                String kind = co.get("k").getAsString();
                if ("value".equals(kind)) continue;
                Category cat = new Category(name, kind,
                        co.get("b").getAsString(), COLORS.getOrDefault(name, 0x7A7A7A));
                for (JsonElement se : co.getAsJsonArray("subs")) {
                    JsonObject so = se.getAsJsonObject();
                    JsonElement sn = so.get("n");
                    List<Action> list = new ArrayList<>();
                    for (JsonElement ai : so.getAsJsonArray("a")) {
                        Action a = ACTIONS.get(ai.getAsInt());
                        a.category = cat;
                        a.subcategory = sn.isJsonNull() ? null : sn.getAsString();
                        list.add(a);
                        BY_KEY.put(key(cat.name, a.name), a);
                    }
                    cat.subNames.add(sn.isJsonNull() ? null : sn.getAsString());
                    cat.subActions.add(list);
                }
                CATEGORIES.add(cat);
            }
            addHandBuiltBlocks();
            ACTIONS.removeIf(a -> a.category == null);
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] failed to read catalog.json", e);
        }
    }

    public static Action FUNCTION, CALL, PROCESS, START_PROCESS, ELSE;

    public static final int FN_NAME = 0, FN_PARAMS = 1, FN_DESC = 2, FN_ICON = 3,
            FN_DISPLAY = 4;
    public static final int CALL_NAME = 0;

    public static final String SHOW_IN_CALL = "Отображать в меню вызова",
            SHOWN = "Отображать", HIDDEN = "Скрыть";

    public static final int MAX_PARAMS = 45;
    public static final int MAX_DESC = 14;

    public static final String DISPLAY_NAME = "display_name",
            DISPLAY_DESC = "display_description";

    public static String localizedField(int arg) {
        return switch (arg) {
            case FN_DISPLAY -> DISPLAY_NAME;
            case FN_DESC -> DISPLAY_DESC;
            default -> null;
        };
    }

    private static void addHandBuiltBlocks() {
        Category fn = new Category("Функция", "event", "minecraft:lapis_block",
                COLORS.getOrDefault("Функция", 0x64A1FB));
        FUNCTION = new Action(ACTIONS.size(), "Функция", "minecraft:lapis_block",
                "Хранит строку кода, которую можно вызвать блоком «Вызвать функцию». "
                        + "Параметры функции становятся строчными переменными с теми же именами.",
                head("функции"),
                List.of(new Setting(SHOW_IN_CALL, List.of(SHOWN, HIDDEN), SHOWN, -1)),
                false);
        register(fn, FUNCTION);

        Category call = new Category("Вызвать функцию", "action", "minecraft:lapis_ore",
                COLORS.getOrDefault("Вызвать функцию", 0x5C7E9E));
        CALL = new Action(ACTIONS.size(), "Вызвать функцию", "minecraft:lapis_ore",
                "Вызывает строку кода функции. Эта строка не продолжится, пока функция не "
                        + "закончит работу. Аргументы блок берёт у выбранной функции.",
                List.of(new Arg("Текст", "Название функции", false, 1, new int[0])),
                List.of(), false);
        register(call, CALL);

        Category pr = new Category("Процесс", "event", "minecraft:emerald_block",
                COLORS.getOrDefault("Процесс", 0x118D00));
        PROCESS = new Action(ACTIONS.size(), "Процесс", "minecraft:emerald_block",
                "Хранит строку кода, которую можно запустить блоком «Запустить процесс». "
                        + "В отличие от функции, процесс живёт своей строкой: вызвавшая его строка "
                        + "продолжает работу, и «Контроль действий» её не останавливает.",
                head("процесса"),
                List.of(new Setting(SHOW_IN_CALL, List.of(SHOWN, HIDDEN), SHOWN, -1)),
                false);
        register(pr, PROCESS);

        Category start = new Category("Запустить процесс", "action", "minecraft:emerald_ore",
                COLORS.getOrDefault("Запустить процесс", 0x2FB274));
        START_PROCESS = new Action(ACTIONS.size(), "Запустить процесс", "minecraft:emerald_ore",
                "Запускает строку кода процесса. Эта строка продолжает работу, не дожидаясь "
                        + "его окончания. Аргументы блок берёт у выбранного процесса.",
                List.of(new Arg("Текст", "Название процесса", false, 1, new int[0])),
                List.of(new Setting("Режим переменных",
                                List.of("Не дублировать", "Дублировать", "Общие"),
                                "Не дублировать", -1),
                        new Setting("Цель процесса",
                                List.of("Цель события", "Текущая цель", "Без цели",
                                        "Каждая цель в выборке"),
                                "Цель события", -1)),
                false);
        register(start, START_PROCESS);

        offstage("Событие игрока", new Action(ACTIONS.size(), "Пустое событие",
                "minecraft:structure_void",
                "Не запускается никогда: это место, куда паркуют код. Строка живёт в мире, но "
                        + "вызвать её нечем. Сервер подписывает её «...», а компилятор JMCC вешает "
                        + "на неё код, не привязанный ни к одному событию.",
                List.of(), List.of(), false));

        offstage("Если игрок", new Action(ACTIONS.size(), "Пустое условие",
                "minecraft:structure_void",
                "Условие-заглушка: сервер подписывает его «...». Тело у него есть, а проверять "
                        + "ему нечего.",
                List.of(), List.of(), false));

        ELSE = new Action(ACTIONS.size(), "Иначе", "minecraft:end_stone",
                "Выполняет код внутри себя, если условие предыдущего блока не выполнилось. "
                        + "Ставится только сразу за блоком условия.",
                List.of(), List.of(), false);
        prepend("Иначе", ELSE);
    }

    private static List<Arg> head(String what) {
        return List.of(new Arg("Текст", "Имя " + what, false, 1, new int[0]),
                new Arg("Параметр", "Параметры", true, MAX_PARAMS, new int[0]),
                new Arg("Текст", "Описание", true, MAX_DESC, new int[0]),
                new Arg("Предмет", "Значок", false, 1, new int[0]),
                new Arg("Текст", "Отображаемое имя", false, 1, new int[0]));
    }

    private static void register(Category cat, Action action) {
        action.category = cat;
        cat.subNames.add(null);
        cat.subActions.add(List.of(action));
        ACTIONS.add(action);
        CATEGORIES.add(cat);
        BY_KEY.put(key(cat.name, action.name), action);
    }

    private static void offstage(String category, Action action) {
        Category cat = category(category);
        if (cat == null) {
            XeroCode.LOG.warn("[xerocode] категории «{}» нет — блок «{}» некуда приписать",
                    category, action.name);
            return;
        }
        action.category = cat;
        BY_KEY.put(key(cat.name, action.name), action);
    }

    private static void prepend(String category, Action action) {
        for (Category cat : CATEGORIES) {
            if (!cat.name.equals(category)) continue;
            action.category = cat;
            List<Action> first = new ArrayList<>();
            first.add(action);
            if (!cat.subActions.isEmpty()) first.addAll(cat.subActions.get(0));
            if (cat.subActions.isEmpty()) { cat.subNames.add(null); cat.subActions.add(first); }
            else cat.subActions.set(0, first);
            ACTIONS.add(action);
            BY_KEY.put(key(cat.name, action.name), action);
            return;
        }
        XeroCode.LOG.warn("[xerocode] категории «{}» нет в каталоге — блок «{}» некуда положить",
                category, action.name);
    }

    public static Arg paramArg(Value parameter) {
        String label = parameter.name.isBlank() ? "без имени" : parameter.name;
        boolean plural = Value.PLURAL.equals(parameter.typeKey);
        return new Arg(typeOfParam(parameter), label, plural, plural ? MAX_PARAMS : 1, new int[0]);
    }

    public static String typeOfParam(Value parameter) {
        return switch (parameter.valueType) {
            case "text" -> "Текст";
            case "number" -> "Число";
            case "location" -> "Местоположение";
            case "vector" -> "Вектор";
            case "sound" -> "Звук";
            case "particle" -> "Эффект частиц";
            case "potion" -> "Зелье";
            case "item" -> "Предмет";
            case "block" -> "Блок";
            case "array" -> "Список";
            case "map" -> "Словарь";
            case "variable" -> "Переменная";
            default -> "Любое значение";
        };
    }

    public static final String TARGET = "Цель", TARGET_DEFAULT = "По умолчанию";
    public static final String INVERT = "Инверсия", INVERT_OFF = "Нет", INVERT_ON = "НЕ";

    private static final Map<Action, List<Setting>> EXTRAS = new java.util.IdentityHashMap<>();

    public static List<Setting> extraSettings(Action action) {
        if (!Mapping.loaded()) return List.of();
        List<Setting> cached = EXTRAS.get(action);
        if (cached != null) return cached;
        List<Setting> made = new ArrayList<>(2);
        List<Mapping.Sel> targets = Mapping.targets(action);
        if (!targets.isEmpty()) {
            List<String> options = new ArrayList<>(targets.size() + 1);
            options.add(TARGET_DEFAULT);
            for (Mapping.Sel s : targets) options.add(s.name());
            made.add(new Setting(TARGET, options, TARGET_DEFAULT, -1, true));
        }
        if (invertible(action))
            made.add(new Setting(INVERT, List.of(INVERT_OFF, INVERT_ON), INVERT_OFF, -1, true));
        List<Setting> result = List.copyOf(made);
        EXTRAS.put(action, result);
        return result;
    }

    private static boolean invertible(Action action) {
        return action != ELSE && Mapping.invertible(action);
    }

    private static Action parseAction(int id, JsonObject o) {
        List<Arg> args = new ArrayList<>();
        if (o.has("a")) {
            for (JsonElement ae : o.getAsJsonArray("a")) {
                JsonObject ao = ae.getAsJsonObject();
                JsonArray sl = ao.getAsJsonArray("s");
                int[] slots = new int[sl.size()];
                for (int i = 0; i < slots.length; i++) slots[i] = sl.get(i).getAsInt();
                args.add(new Arg(ao.get("t").getAsString(), ao.get("p").getAsString(),
                        ao.get("l").getAsBoolean(), ao.get("c").getAsInt(), slots));
            }
        }
        List<Setting> settings = new ArrayList<>();
        if (o.has("s")) {
            for (JsonElement se : o.getAsJsonArray("s")) {
                JsonObject so = se.getAsJsonObject();
                List<String> opts = new ArrayList<>();
                for (JsonElement oe : so.getAsJsonArray("o")) opts.add(oe.getAsString());
                JsonElement def = so.get("d");
                settings.add(new Setting(so.get("l").getAsString(), opts,
                        def.isJsonNull() ? (opts.isEmpty() ? "" : opts.get(0)) : def.getAsString(),
                        so.get("s").getAsInt()));
            }
        }
        boolean unavailable = false;
        if (o.has("f")) for (JsonElement fe : o.getAsJsonArray("f"))
            if ("unavailable_in_world".equals(fe.getAsString())) unavailable = true;
        return new Action(id, o.get("n").getAsString(), o.get("i").getAsString(),
                o.has("d") ? o.get("d").getAsString() : "", args, settings, unavailable);
    }

    public static String key(String category, String action) { return category + "|" + action; }
    public static Action byKey(String key) { return BY_KEY.get(key); }
    public static String keyOf(Action a) { return key(a.category.name, a.name); }

    public record Slots(int size, int cols, int hotbar, String title) {}

    private static final Map<String, Slots> SLOTTED = Map.of(
            key("Действие над игроком", "Установить предметы"),
            new Slots(36, 9, 9, "ИНВЕНТАРЬ ИГРОКА"),
            key("Действие над игроком", "Установить содержимое Эндер-сундука"),
            new Slots(27, 9, 0, "ЭНДЕР-СУНДУК"),
            key("Действие над игроком", "Показать меню"),
            new Slots(27, 9, 0, "МЕНЮ"),
            key("Действие над игроком", "Расширить меню"),
            new Slots(27, 9, 0, "МЕНЮ"),
            key("Действие над миром", "Установить предметы в контейнере"),
            new Slots(27, 9, 0, "КОНТЕЙНЕР"),
            key("Действие над миром", "Установить предметы события"),
            new Slots(36, 9, 0, "ПРЕДМЕТЫ СОБЫТИЯ"));

    public static Slots slots(Action action, int argIndex) {
        if (action == null || action.category == null || argIndex != 0) return null;
        return SLOTTED.get(keyOf(action));
    }

    public static List<Action> search(String query, int limit) {
        return rank(ACTIONS, query, limit);
    }

    public static List<Action> searchIn(Category category, String query, int limit) {
        List<Action> pool = new ArrayList<>();
        for (List<Action> l : category.subActions) pool.addAll(l);
        return rank(pool, query, limit);
    }

    private static List<Action> rank(List<Action> pool, String query, int limit) {
        return Search.rank(pool, query, limit, a -> new Search.Fields(a.name, "",
                a.category == null ? "" : a.category.name, a.description));
    }

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    public static ItemStack stackOf(String id) {
        ItemStack cached = ICONS.get(id);
        if (cached != null) return cached;
        ItemStack made = new ItemStack(Items.STONE);
        try {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null) {
                var item = Registries.ITEM.getOptionalValue(ident);
                if (item.isPresent()) made = new ItemStack(item.get());
            }
        } catch (Exception ignored) {}
        ICONS.put(id, made);
        return made;
    }

    private Catalog() {}
}
