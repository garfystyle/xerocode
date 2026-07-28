package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Values {
    public record Kind(String id, String name, String item) {}

    public record Scope(String id, String name, int color) {}

    public record Parsing(String id, String name) {}

    public record Selector(String id, String name) {}

    public static final class GameValue {
        public final String id, name, category, item, description, returns, returnsNote;
        GameValue(String id, String name, String category, String item,
                  String description, String returns, String returnsNote) {
            this.id = id; this.name = name; this.category = category; this.item = item;
            this.description = description; this.returns = returns; this.returnsNote = returnsNote;
        }
    }

    public static final List<Kind> KINDS = new ArrayList<>();
    public static final List<Scope> SCOPES = new ArrayList<>();
    public static final List<Parsing> PARSINGS = new ArrayList<>();
    public static final List<Selector> SELECTORS = new ArrayList<>();
    public static final List<GameValue> GAME_VALUES = new ArrayList<>();
    public static final Map<String, List<GameValue>> GAME_VALUE_CATEGORIES = new java.util.LinkedHashMap<>();

    private static final Map<String, Kind> KIND_BY_ID = new HashMap<>();
    private static final Map<String, GameValue> GV_BY_ID = new HashMap<>();

    public static final List<String> EDITABLE = List.of(
            Value.TEXT, Value.NUMBER, Value.VARIABLE, Value.GAME_VALUE,
            Value.LOCATION, Value.VECTOR, Value.PARAMETER,
            Value.SOUND, Value.PARTICLE, Value.POTION,
            Value.ARRAY, Value.MAP, Value.ITEM);

    private static final Kind ITEM_KIND = new Kind(Value.ITEM, "Предмет", "minecraft:item_frame");

    private static final Map<String, Integer> KIND_COLORS = Map.ofEntries(
            Map.entry(Value.TEXT,       0x3AB3DA),
            Map.entry(Value.NUMBER,     0xE03C3C),
            Map.entry(Value.LOCATION,   0x7FDB3A),
            Map.entry(Value.VECTOR,     0x2FBFBF),
            Map.entry(Value.SOUND,      0x9B4FD1),
            Map.entry(Value.PARTICLE,   0xF08FC0),
            Map.entry(Value.POTION,     0xD45BD4),
            Map.entry(Value.GAME_VALUE, 0xEFA13C),
            Map.entry(Value.VARIABLE,   0x3FA13F),
            Map.entry(Value.PARAMETER,  0x63C9E0),
            Map.entry(Value.ARRAY,      0xE8D33A),
            Map.entry(Value.MAP,        0x8B5A2B),
            Map.entry(Value.ITEM,       0xE08A1E));

    public static int color(String kindId) { return KIND_COLORS.getOrDefault(kindId, 0xAAAAAA); }

    public static final List<String> PARAM_TYPES = List.of(
            "any", "text", "number", "location", "vector", "block", "item",
            "sound", "particle", "potion", "array", "map", "variable");

    private static final List<String> PARAM_TYPE_NAMES = List.of(
            "любое", "текст", "число", "место", "вектор", "блок", "предмет",
            "звук", "частицы", "зелье", "список", "словарь", "переменная");

    public static String paramTypeName(String id) {
        int i = PARAM_TYPES.indexOf(id);
        return i < 0 ? id : PARAM_TYPE_NAMES.get(i);
    }

    public static List<String> paramTypeNames() { return PARAM_TYPE_NAMES; }

    public static boolean loaded() { return !KINDS.isEmpty(); }

    public static void load() {
        if (loaded()) return;
        try (InputStream in = Values.class.getResourceAsStream("/assets/xerocode/values.json")) {
            if (in == null) { XeroCode.LOG.error("[xerocode] values.json not found in the jar"); return; }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            for (JsonElement e : root.getAsJsonArray("kinds")) {
                JsonObject o = e.getAsJsonObject();
                Kind k = new Kind(o.get("id").getAsString(), o.get("n").getAsString(),
                        o.get("i").getAsString());
                KINDS.add(k);
                KIND_BY_ID.put(k.id(), k);
            }
            for (JsonElement e : root.getAsJsonArray("scopes")) {
                JsonObject o = e.getAsJsonObject();
                SCOPES.add(new Scope(o.get("id").getAsString(), o.get("n").getAsString(),
                        o.get("c").getAsInt()));
            }
            for (JsonElement e : root.getAsJsonArray("parsings")) {
                JsonObject o = e.getAsJsonObject();
                PARSINGS.add(new Parsing(o.get("id").getAsString(), o.get("n").getAsString()));
            }
            for (JsonElement e : root.getAsJsonArray("selectors")) {
                JsonObject o = e.getAsJsonObject();
                SELECTORS.add(new Selector(o.get("id").getAsString(), o.get("n").getAsString()));
            }
            for (JsonElement e : root.getAsJsonArray("gameValues")) {
                JsonObject o = e.getAsJsonObject();
                GameValue g = new GameValue(o.get("id").getAsString(), o.get("n").getAsString(),
                        o.get("c").getAsString(), o.get("i").getAsString(),
                        str(o, "d"), str(o, "r"), str(o, "rd"));
                GAME_VALUES.add(g);
                GV_BY_ID.put(g.id, g);
                GAME_VALUE_CATEGORIES.computeIfAbsent(g.category, k -> new ArrayList<>()).add(g);
            }
            KINDS.add(ITEM_KIND);
            KIND_BY_ID.put(ITEM_KIND.id(), ITEM_KIND);
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] failed to read values.json", e);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    public static Kind kind(String id) { return KIND_BY_ID.get(id); }

    public static String kindName(String id) {
        Kind k = KIND_BY_ID.get(id);
        return k == null ? id : k.name();
    }

    public static String kindItem(String id) {
        Kind k = KIND_BY_ID.get(id);
        return k == null ? "minecraft:paper" : k.item();
    }

    public static GameValue gameValue(String id) { return GV_BY_ID.get(id); }

    public static String gameValueName(String id) {
        GameValue g = GV_BY_ID.get(id);
        return g == null ? (id == null || id.isEmpty() ? "" : id) : g.name;
    }

    public static Scope scope(String id) {
        for (Scope s : SCOPES) if (s.id().equals(id)) return s;
        return SCOPES.isEmpty() ? new Scope("game", "Игровая", 0xABC4D6) : SCOPES.get(0);
    }

    public static String parsingName(String id) {
        for (Parsing p : PARSINGS) if (p.id().equals(id)) return p.name();
        return id;
    }

    public static String selectorName(String id) {
        for (Selector s : SELECTORS) if (s.id().equals(id)) return s.name();
        return id;
    }

    public static String defaultKind(String argType) {
        if (argType == null) return Value.TEXT;
        return switch (argType) {
            case "Число" -> Value.NUMBER;
            case "Местоположение" -> Value.LOCATION;
            case "Вектор" -> Value.VECTOR;
            case "Переменная" -> Value.VARIABLE;
            case "Звук" -> Value.SOUND;
            case "Эффект частиц" -> Value.PARTICLE;
            case "Зелье" -> Value.POTION;
            case "Список" -> Value.ARRAY;
            case "Словарь" -> Value.MAP;
            case "Параметр" -> Value.PARAMETER;
            case "Предмет", "Блок" -> Value.ITEM;
            default -> Value.TEXT;
        };
    }

    public static List<GameValue> search(String query, int limit) {
        if (query.isBlank())
            return new ArrayList<>(GAME_VALUES.subList(0, Math.min(limit, GAME_VALUES.size())));
        return Search.rank(GAME_VALUES, query, limit,
                g -> new Search.Fields(g.name, g.id, g.category, g.description));
    }

    private Values() {}
}
