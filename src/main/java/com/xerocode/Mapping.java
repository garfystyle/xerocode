package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Mapping {
    public static final class Setting {
        public final int index;
        public final Map<String, String> options;
        Setting(int index, Map<String, String> options) {
            this.index = index;
            this.options = options;
        }
        public String option(String serverId) {
            return options.get(serverId.toLowerCase(Locale.ROOT));
        }
    }

    public static final class Act {
        public final String key;
        public final Map<String, Integer> args;
        public final Map<String, Setting> settings;
        public String selections;
        public boolean container;
        public final Set<String> plural;

        public final Map<Integer, String> argNames = new HashMap<>();
        public final Map<Integer, String> settingNames = new HashMap<>();
        public final Map<Integer, Map<String, String>> optionIds = new HashMap<>();

        Act(String key, Map<String, Integer> args, Map<String, Setting> settings,
            Set<String> plural) {
            this.key = key; this.args = args; this.settings = settings; this.plural = plural;
            args.forEach((id, index) -> argNames.put(index, id));
            settings.forEach((id, setting) -> {
                settingNames.put(setting.index, id);
                Map<String, String> ids = new HashMap<>();
                setting.options.forEach((enumId, russian) ->
                        ids.put(russian, enumId.toUpperCase(Locale.ROOT)));
                optionIds.put(setting.index, ids);
            });
        }

        public boolean isPlural(String argId) { return plural.contains(argId); }
    }

    public record Sel(String id, String name) {}

    private static final Map<String, List<Sel>> SELECTIONS = new HashMap<>();

    private static final Map<String, String> EVENTS = new HashMap<>();
    private static final Map<String, Act> ACTIONS = new HashMap<>();
    private static final Map<String, String> EXPORT_ACTIONS = new HashMap<>();
    private static final Map<String, String> EXPORT_EVENTS = new HashMap<>();

    public static boolean loaded() { return !ACTIONS.isEmpty(); }

    public static void load() {
        if (loaded()) return;
        try (InputStream in = Mapping.class.getResourceAsStream("/assets/xerocode/mapping.json")) {
            if (in == null) { XeroCode.LOG.error("[xerocode] mapping.json not found in the jar"); return; }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            JsonObject events = root.getAsJsonObject("events");
            for (String id : events.keySet()) {
                String key = events.get(id).getAsString();
                EVENTS.put(id, key);
                EXPORT_EVENTS.putIfAbsent(key, id);
            }

            JsonObject actions = root.getAsJsonObject("actions");
            for (String id : actions.keySet()) {
                JsonObject o = actions.getAsJsonObject(id);
                Map<String, Integer> args = new HashMap<>();
                if (o.has("a")) {
                    JsonObject a = o.getAsJsonObject("a");
                    for (String arg : a.keySet()) args.put(arg, a.get(arg).getAsInt());
                }
                Map<String, Setting> settings = new HashMap<>();
                if (o.has("s")) {
                    JsonObject s = o.getAsJsonObject("s");
                    for (String arg : s.keySet()) {
                        JsonObject so = s.getAsJsonObject(arg);
                        Map<String, String> options = new HashMap<>();
                        JsonObject e = so.getAsJsonObject("e");
                        for (String key : e.keySet()) options.put(key, e.get(key).getAsString());
                        settings.put(arg, new Setting(so.get("i").getAsInt(), options));
                    }
                }
                Set<String> plural = new HashSet<>();
                if (o.has("p")) for (var pe : o.getAsJsonArray("p")) plural.add(pe.getAsString());
                Act act = new Act(o.get("k").getAsString(), args, settings, plural);
                act.selections = o.has("t") ? o.get("t").getAsString() : family(act.key);
                act.container = o.has("c");
                ACTIONS.put(id, act);
            }

            if (root.has("selections")) {
                JsonObject sel = root.getAsJsonObject("selections");
                for (String family : sel.keySet()) {
                    List<Sel> list = new ArrayList<>();
                    for (var row : sel.getAsJsonArray(family)) {
                        var pair = row.getAsJsonArray();
                        list.add(new Sel(pair.get(0).getAsString(), pair.get(1).getAsString()));
                    }
                    SELECTIONS.put(family, list);
                }
            }

            if (root.has("export")) {
                JsonObject export = root.getAsJsonObject("export");
                for (String key : export.keySet())
                    EXPORT_ACTIONS.put(key, export.get(key).getAsString());
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] failed to read mapping.json", e);
        }
    }

    public static Catalog.Action event(String id) {
        String key = EVENTS.get(id);
        return key == null ? null : Catalog.byKey(key);
    }

    public static Act action(String id) { return ACTIONS.get(id); }

    public static String actionId(Catalog.Action action) {
        return action == null || action.category == null
                ? null : EXPORT_ACTIONS.get(Catalog.keyOf(action));
    }

    public static String eventId(Catalog.Action action) {
        return action == null || action.category == null
                ? null : EXPORT_EVENTS.get(Catalog.keyOf(action));
    }

    public static Act forExport(Catalog.Action action) {
        String id = actionId(action);
        if (id == null) return elseCondAct(action);
        return ACTIONS.get(id);
    }

    private static final Map<String, String> ELSE_IF = new HashMap<>();
    private static final Map<String, Catalog.Action> ELSE_BY_ID = new HashMap<>();
    private static boolean elseBuilt;

    private static void buildElse() {
        if (elseBuilt || EXPORT_ACTIONS.isEmpty() || !Catalog.loaded()) return;
        Catalog.Category cat = Catalog.category(Catalog.ELSE_CATEGORY);
        if (cat == null) return;
        elseBuilt = true;
        Map<String, List<String>> byCondKey = new HashMap<>();
        ACTIONS.forEach((id, act) ->
                byCondKey.computeIfAbsent(act.key, k -> new ArrayList<>()).add(id));
        for (List<Catalog.Action> list : cat.subActions)
            for (Catalog.Action a : list) {
                if (a == Catalog.ELSE || a.subcategory == null) continue;
                int cut = a.subcategory.indexOf(" / ");
                String from = cut < 0 ? a.subcategory : a.subcategory.substring(0, cut);
                String condKey = Catalog.key(from, a.name);
                List<String> ids = byCondKey.get(condKey);
                if (ids == null || ids.isEmpty()) continue;
                String id = EXPORT_ACTIONS.get(condKey);
                ELSE_IF.put(Catalog.keyOf(a), ids.contains(id) ? id : ids.get(0));
                for (String sid : ids) ELSE_BY_ID.putIfAbsent(sid, a);
            }
    }

    public static String elseCondId(Catalog.Action action) {
        if (action == null || action == Catalog.ELSE || action.category == null
                || !Catalog.ELSE_CATEGORY.equals(action.category.name)) return null;
        buildElse();
        return ELSE_IF.get(Catalog.keyOf(action));
    }

    public static Act elseCondAct(Catalog.Action action) {
        String id = elseCondId(action);
        return id == null ? null : ACTIONS.get(id);
    }

    public static Catalog.Action elseFor(String condId) {
        if (condId == null) return null;
        buildElse();
        return ELSE_BY_ID.get(condId);
    }

    private static String family(String key) {
        int cut = key.indexOf('|');
        return switch (cut < 0 ? key : key.substring(0, cut)) {
            case "Действие над игроком", "Если игрок" -> "p";
            case "Действие над сущностью", "Если сущность" -> "e";
            default -> null;
        };
    }

    public static List<Sel> targets(Catalog.Action action) {
        Act act = forExport(action);
        if (act == null || act.selections == null) return List.of();
        return SELECTIONS.getOrDefault(act.selections, List.of());
    }

    public static String targetName(Catalog.Action action, String id) {
        for (Sel s : targets(action)) if (s.id().equals(id)) return s.name();
        return null;
    }

    public static String targetId(Catalog.Action action, String name) {
        for (Sel s : targets(action)) if (s.name().equals(name)) return s.id();
        return null;
    }

    private static final List<String> COND_PLAYER =
            List.of("Если игрок", "Если переменная", "Если в мире");
    private static final List<String> COND_ENTITY =
            List.of("Если сущность", "Если переменная", "Если в мире");
    private static final List<String> COND_ALL =
            List.of("Если игрок", "Если сущность", "Если переменная", "Если в мире");

    private static final Map<String, List<String>> CONDITIONAL = Map.of(
            "select_player_by_conditional", COND_PLAYER,
            "select_entity_by_conditional", COND_ENTITY,
            "select_add_player_by_conditional", COND_PLAYER,
            "select_add_entity_by_conditional", COND_ENTITY,
            "select_filter_by_conditional", COND_ALL,
            "repeat_while", COND_ALL);

    public static boolean hasConditional(Catalog.Action action) {
        String id = actionId(action);
        return id != null && CONDITIONAL.containsKey(id);
    }

    public static List<String> conditionCategories(Catalog.Action action) {
        String id = actionId(action);
        List<String> out = id == null ? null : CONDITIONAL.get(id);
        return out == null ? List.of() : out;
    }

    public static boolean invertible(Catalog.Action action) {
        if (action == null || action.category == null) return false;
        Act act = forExport(action);
        return act != null && act.container || action.category.wraps();
    }

    private Mapping() {}
}
