package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.ui.Layout;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Importer {
    public static final class Result {
        public int lines, blocks, values, markers;
        public int skippedValues, skippedItems;
        public int brokenLines;
        public int items;
        public final Set<String> unknown = new LinkedHashSet<>();
        public int unknownCount;
    }

    private static final int GAP = 40, COLUMN_MAX_H = 1600, COLUMN_GAP = 60;

    private static final Set<String> OWN_FIELDS = Set.of("action", "type", "event", "name",
            "position", "values", "operations", "selection", "is_inverted");

    private static void readBlockFields(JsonObject op, Script.Node node, Result result) {
        if (op.has("is_inverted") && op.get("is_inverted").isJsonPrimitive()
                && op.get("is_inverted").getAsBoolean()) {
            if (node.settingIndex(Catalog.INVERT) >= 0) {
                node.setSetting(Catalog.INVERT, Catalog.INVERT_ON);
                result.markers++;
            } else {
                keepFields(op, node);
            }
        }
        if (!op.has("selection") || !op.get("selection").isJsonObject()) return;
        String id = str(op.getAsJsonObject("selection"), "type");
        String name = id.isEmpty() ? null : Mapping.targetName(node.action, id);
        if (name == null) {
            unknown(result, "цель " + id);
            raw(node).add("selection", op.get("selection"));
        } else {
            node.setSetting(Catalog.TARGET, name);
            result.markers++;
        }
    }

    private static void keepFields(JsonObject op, Script.Node node) {
        for (String field : op.keySet()) {
            if (OWN_FIELDS.contains(field)) continue;
            raw(node).add(field, op.get(field));
        }
    }

    private static void keepValue(JsonObject entry, Script.Node node) {
        JsonObject raw = raw(node);
        JsonArray kept = raw.has("values") ? raw.getAsJsonArray("values") : new JsonArray();
        kept.add(entry);
        raw.add("values", kept);
    }

    private static JsonObject raw(Script.Node node) {
        if (node.raw == null) node.raw = new JsonObject();
        return node.raw;
    }

    public static Result importInto(Script script, JsonArray handlers, TextRenderer tr) {
        Result result = new Result();
        int x = 40, y = 40, columnW = 0;

        int line = -1;
        for (JsonElement he : handlers) {
            if (!he.isJsonObject()) continue;
            line++;
            JsonObject handler = he.getAsJsonObject();

            try {
                List<Script.Node> chain = chainOf(handler, result, line);
                if (chain.isEmpty()) continue;

                Layout layout = Layout.ofChain(chain, 0, 0, tr);
                int h = Layout.chainHeight(chain, tr);
                int w = 0;
                for (Layout.Box box : layout.boxes) w = Math.max(w, box.x + box.w);

                if (y > 40 && y + h > COLUMN_MAX_H) {
                    x += columnW + COLUMN_GAP;
                    y = 40;
                    columnW = 0;
                }
                Script.Root root = new Script.Root(x, y);
                root.chain.addAll(chain);
                script.roots.add(root);
                result.lines++;
                y += h + GAP;
                columnW = Math.max(columnW, w);
            } catch (Throwable e) {
                XeroCode.LOG.error("[xerocode] строка {} не разобралась", line, e);
                result.brokenLines++;
            }
        }
        return result;
    }

    public static List<Script.Node> chainOf(JsonObject handler, Result result) {
        return chainOf(handler, result, 0);
    }

    public static List<Script.Node> chainOf(JsonObject handler, Result result, int line) {
        List<Script.Node> chain = new ArrayList<>();
        String type = str(handler, "type");
        String where = "строка " + line;
        if ("event".equals(type)) {
            Catalog.Action hat = Mapping.event(str(handler, "event"));
            if (hat != null) { chain.add(new Script.Node(hat)); result.blocks++; }
            else {
                unknown(result, "событие " + str(handler, "event"));
            }
        } else if ("function".equals(type)) {
            chain.add(declarationHat(handler, Catalog.FUNCTION, result, where));
        } else if ("process".equals(type)) {
            chain.add(declarationHat(handler, Catalog.PROCESS, result, where));
        } else if (!type.isEmpty()) {
            unknown(result, type);
        }
        readOperations(handler, chain, result, where);
        return chain;
    }

    private static Script.Node declarationHat(JsonObject handler, Catalog.Action action,
                                              Result result, String where) {
        Script.Node node = new Script.Node(action);
        Value name = new Value(Value.TEXT);
        name.text = str(handler, "name");
        name.parsing = "plain";
        node.valuesOf(Catalog.FN_NAME).add(name);
        result.blocks++;
        if (!handler.has("values") || !handler.get("values").isJsonArray()) return node;

        for (JsonElement ve : handler.getAsJsonArray("values")) {
            if (!ve.isJsonObject()) continue;
            JsonObject entry = ve.getAsJsonObject();
            JsonObject value = entry.has("value") && entry.get("value").isJsonObject()
                    ? entry.getAsJsonObject("value") : null;
            if (value == null || value.isEmpty()) continue;
            switch (str(entry, "name")) {
                case "parameters" -> {
                    for (JsonObject cell : cells(value)) {
                        Value p = read(cell, result);
                        if (p == null || !Value.PARAMETER.equals(p.type)) {
                            result.skippedValues++;
                            continue;
                        }
                        node.valuesOf(Catalog.FN_PARAMS).add(p);
                        result.values++;
                    }
                }
                case "display_name" -> {
                    Value text = localized(value, node, "display_name");
                    if (text != null) { node.valuesOf(Catalog.FN_DISPLAY).add(text); result.values++; }
                }
                case "display_description" -> {
                    Value text = localized(value, node, "display_description");
                    if (text == null) break;
                    for (Value line : descLines(text)) {
                        node.valuesOf(Catalog.FN_DESC).add(line);
                        result.values++;
                    }
                }
                case "description" -> {
                    for (JsonObject cell : cells(value)) {
                        Value line = read(cell, result);
                        if (line == null) { result.skippedValues++; continue; }
                        node.valuesOf(Catalog.FN_DESC).add(line);
                        result.values++;
                    }
                }
                case "icon" -> {
                    Value icon = item(value, result, where, "значок");
                    if (icon != null) { node.valuesOf(Catalog.FN_ICON).add(icon); result.values++; }
                }
                case "is_hidden" -> {
                    boolean hidden = "TRUE".equalsIgnoreCase(str(value, "enum"));
                    node.markers.put(0, hidden ? "Скрыть" : "Отображать");
                    result.markers++;
                }
                default -> {
                    unknown(result, "настройка " + str(entry, "name"));
                    keepValue(entry, node);
                }
            }
        }
        return node;
    }

    private static Script.Node invokeNode(JsonObject op, Catalog.Action action, Result result,
                                          String where) {
        Script.Node node = new Script.Node(action);
        List<String> keys = new ArrayList<>();
        keys.add("");
        for (Slot slot : slots(op)) {
            JsonObject entry = slot.entry();
            String name = slot.name();
            JsonObject value = slot.value();
            if ("function_name".equals(name) || "process_name".equals(name)) {
                Value v = read(value, result);
                if (v != null) { node.valuesOf(Catalog.CALL_NAME).add(v); result.values++; }
                continue;
            }
            if ("local_variables_mode".equals(name) || "target_mode".equals(name)) {
                int index = "local_variables_mode".equals(name) ? 0 : 1;
                String option = staticOption(action, index, str(value, "enum"));
                if (option == null) {
                    result.skippedValues++;
                    keepValue(entry, node);
                } else {
                    node.markers.put(index, option);
                    result.markers++;
                }
                continue;
            }
            if (!"args".equals(name) || !value.has("values")
                    || !value.get("values").isJsonObject()) {
                keepValue(entry, node);
                continue;
            }
            JsonObject args = value.getAsJsonObject("values");
            for (String key : args.keySet()) {
                String param = paramName(key);
                if (param.isEmpty() || !args.get(key).isJsonObject()) { result.skippedValues++; continue; }
                JsonObject passed = args.getAsJsonObject(key);
                keys.add(param);
                int index = keys.size() - 1;
                if (Value.ARRAY.equals(str(passed, "type")) && passed.has("values")
                        && passed.get("values").isJsonArray()) {
                    for (JsonObject cell : cells(passed)) {
                        Value v = read(cell, result);
                        if (v == null) { result.skippedValues++; continue; }
                        node.valuesOf(index).add(v);
                        result.values++;
                    }
                } else {
                    Value v = read(passed, result);
                    if (v == null) { result.skippedValues++; continue; }
                    node.valuesOf(index).add(v);
                    result.values++;
                }
            }
        }
        node.dynKeys = keys;
        return node;
    }

    private static String staticOption(Catalog.Action action, int index, String serverId) {
        if (index >= action.settings.size() || serverId.isEmpty()) return null;
        String want = SERVER_OPTIONS.get(serverId.toUpperCase(Locale.ROOT));
        List<String> options = action.settings.get(index).options;
        return want != null && options.contains(want) ? want : null;
    }

    private static final Map<String, String> SERVER_OPTIONS = Map.of(
            "DONT_COPY", "Не дублировать",
            "COPY", "Дублировать",
            "SHARE", "Общие",
            "CURRENT_TARGET", "Цель события",
            "CURRENT_SELECTION", "Текущая цель",
            "NO_TARGET", "Без цели",
            "FOR_EACH_IN_SELECTION", "Каждая цель в выборке");

    private static String paramName(String key) {
        try {
            JsonObject o = JsonParser.parseString(key).getAsJsonObject();
            return str(o, "text");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void readOperations(JsonObject owner, List<Script.Node> into, Result result,
                                       String where) {
        if (!owner.has("operations") || !owner.get("operations").isJsonArray()) return;
        for (JsonElement oe : owner.getAsJsonArray("operations")) {
            if (!oe.isJsonObject()) continue;
            JsonObject op = oe.getAsJsonObject();
            String id = str(op, "action");

            if ("call_function".equals(id) || "start_process".equals(id)) {
                Script.Node call = invokeNode(op,
                        "call_function".equals(id) ? Catalog.CALL : Catalog.START_PROCESS,
                        result, where);
                keepFields(op, call);
                readOperations(op, call.body, result, where);
                into.add(call);
                result.blocks++;
                continue;
            }
            if ("empty".equals(id)) continue;

            if ("else".equals(id)) {
                Script.Node node = new Script.Node(Catalog.ELSE);
                keepFields(op, node);
                readOperations(op, node.body, result, where);
                into.add(node);
                result.blocks++;
                continue;
            }

            Mapping.Act act = Mapping.action(id);
            Catalog.Action action = act == null ? null : Catalog.byKey(act.key);
            if (action == null) {
                unknown(result, id);
                readOperations(op, into, result, where);
                continue;
            }
            Script.Node node = new Script.Node(action);
            keepFields(op, node);
            if (!id.equals(Mapping.actionId(action))) raw(node).addProperty(KEPT_ID, id);
            readBlockFields(op, node, result);
            Script.Node holder = node;
            Mapping.Act valueAct = act;
            Catalog.Action valueAction = action;
            if (op.has("conditional") && op.get("conditional").isJsonObject()) {
                JsonObject c = op.getAsJsonObject("conditional");
                Cond cond = condition(c);
                if (cond == null) {
                    unknown(result, "условие " + str(c, "action"));
                } else {
                    node.cond = cond.node();
                    if (node.raw != null) node.raw.remove("conditional");
                    holder = cond.node();
                    valueAct = cond.act();
                    valueAction = cond.action();
                }
            }
            readValues(op, holder, valueAct, valueAction, result, where);
            readOperations(op, node.body, result, where);
            into.add(node);
            result.blocks++;
        }
    }

    private record Slot(String name, JsonObject value, JsonObject entry) {}

    private static List<Slot> slots(JsonObject op) {
        List<Slot> out = new ArrayList<>();
        if (!op.has("values") || !op.get("values").isJsonArray()) return out;
        for (JsonElement ve : op.getAsJsonArray("values")) {
            if (!ve.isJsonObject()) continue;
            JsonObject entry = ve.getAsJsonObject();
            JsonObject value = entry.has("value") && entry.get("value").isJsonObject()
                    ? entry.getAsJsonObject("value") : null;
            if (value == null || value.isEmpty()) continue;
            out.add(new Slot(str(entry, "name"), value, entry));
        }
        return out;
    }

    private record Cond(Script.Node node, Mapping.Act act, Catalog.Action action) {}

    private static Cond condition(JsonObject c) {
        Mapping.Act act = Mapping.action(str(c, "action"));
        Catalog.Action action = act == null ? null : Catalog.byKey(act.key);
        if (action == null) return null;
        Script.Node node = new Script.Node(action);
        if (c.has("is_inverted") && c.get("is_inverted").getAsBoolean())
            node.setSetting(Catalog.INVERT, Catalog.INVERT_ON);
        return new Cond(node, act, action);
    }

    public static boolean adoptConditional(Script.Node node) {
        if (node.cond != null || node.raw == null) return false;
        if (!node.raw.has("conditional") || !node.raw.get("conditional").isJsonObject()) return false;
        Cond cond = condition(node.raw.getAsJsonObject("conditional"));
        if (cond == null) return false;
        JsonObject op = new JsonObject();
        if (node.raw.has("values")) op.add("values", node.raw.get("values"));
        readValues(op, cond.node(), cond.act(), cond.action(), new Result(), "перенос");
        node.cond = cond.node();
        node.raw.remove("conditional");
        node.raw.remove("values");
        if (node.raw.isEmpty()) node.raw = null;
        return true;
    }

    private static Value localized(JsonObject value, Script.Node node, String field) {
        String data = str(value, "data");
        if (data.isBlank()) return null;
        JsonObject translations = Localized.translations(data);
        if (!translations.isEmpty()) raw(node).add(TRANSLATIONS + field, translations);
        Value text = Value.of(Value.TEXT);
        text.text = Localized.text(data);
        text.parsing = Localized.parsing(data);
        return text;
    }

    public static final String TRANSLATIONS = "__tr_";
    public static final String KEPT_ID = "__id";

    private static List<Value> descLines(Value text) {
        List<Value> out = new ArrayList<>();
        String[] parts = text.text.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            Value line = Value.of(Value.TEXT);
            line.parsing = text.parsing;
            boolean last = out.size() == Catalog.MAX_DESC - 1;
            StringBuilder sb = new StringBuilder(parts[i]);
            if (last) for (int j = i + 1; j < parts.length; j++) sb.append('\n').append(parts[j]);
            line.text = sb.toString();
            out.add(line);
            if (last) break;
        }
        while (!out.isEmpty() && out.get(out.size() - 1).text.isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private static void bindMarker(Script.Node node, int index, JsonObject value) {
        String name = str(value, "variable");
        if (name == null || name.isBlank()) return;
        Value bound = Value.of(Value.VARIABLE);
        bound.name = name;
        String scope = str(value, "scope");
        if (scope != null && !scope.isBlank()) bound.scope = scope;
        node.bindMarker(index, bound);
    }

    private static void readValues(JsonObject op, Script.Node node, Mapping.Act act,
                                   Catalog.Action action, Result result, String where) {
        String id = str(op, "action");
        for (Slot slot : slots(op)) {
            JsonObject entry = slot.entry();
            String name = slot.name();
            JsonObject value = slot.value();
            String type = str(value, "type");

            if ("enum".equals(type)) {
                Mapping.Setting setting = act.settings.get(name);
                String option = setting == null ? null : setting.option(str(value, "enum"));
                if (setting == null || option == null || setting.index >= action.settings.size()) {
                    result.skippedValues++;
                    keepValue(entry, node);
                } else {
                    node.markers.put(setting.index, option);
                    bindMarker(node, setting.index, value);
                    result.markers++;
                }
                continue;
            }

            Integer index = act.args.get(name);
            if (index == null || index >= action.args.size()) {
                result.skippedValues++;
                keepValue(entry, node);
                continue;
            }

            Value v;
            if (Value.ITEM.equals(type)) {
                v = item(value, result, where, id + " → " + name);
                if (v == null) continue;
            } else if (!KNOWN.contains(type)) {
                result.skippedValues++;
                keepValue(entry, node);
                continue;
            } else {
                v = read(value, result);
                if (v == null) { result.skippedValues++; keepValue(entry, node); continue; }
            }
            node.valuesOf(index).add(v);
            result.values++;
        }
    }

    private static Value item(JsonObject value, Result result, String where, String what) {
        String encoded = str(value, "item");
        if (empty(encoded)) return null;
        Value v = Stacks.valueFromServer(encoded);
        if (v == null) {
            result.skippedItems++;
            return null;
        }
        result.items++;
        return v;
    }

    private static final Set<String> KNOWN = Set.of(
            Value.TEXT, Value.NUMBER, Value.LOCATION, Value.VECTOR, Value.SOUND, Value.PARTICLE,
            Value.POTION, Value.GAME_VALUE, Value.VARIABLE, Value.PARAMETER,
            Value.ARRAY, Value.MAP);

    private static List<JsonObject> cells(JsonObject array) {
        List<JsonObject> out = new ArrayList<>();
        if (!array.has("values") || !array.get("values").isJsonArray()) return out;
        for (JsonElement ce : array.getAsJsonArray("values"))
            if (ce.isJsonObject() && !ce.getAsJsonObject().isEmpty()) out.add(ce.getAsJsonObject());
        return out;
    }

    private static Value read(JsonObject value, Result result) {
        try {
            Value v = Value.fromJson(value);
            scrub(v, result);
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void scrub(Value v, Result result) {
        scrub(v.items, result);
        scrub(v.keys, result);
    }

    private static void scrub(List<Value> cells, Result result) {
        for (int i = 0; i < cells.size(); i++) {
            Value cell = cells.get(i);
            if (Value.ITEM.equals(cell.type)) {
                Value real = empty(cell.itemId) ? null : Stacks.valueFromServer(cell.itemId);
                if (real == null) {
                    if (!empty(cell.itemId)) result.skippedItems++;
                    cells.set(i, Value.blank());
                } else {
                    cells.set(i, real);
                    result.items++;
                }
            } else {
                scrub(cell, result);
            }
        }
    }

    private static boolean empty(String encoded) {
        if (encoded == null || encoded.isEmpty()) return true;
        try {
            for (byte b : Base64.getDecoder().decode(encoded)) if (b != 0) return false;
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void unknown(Result result, String id) {
        result.unknownCount++;
        if (result.unknown.size() < 12) result.unknown.add(id);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private Importer() {}
}
