package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Exporter {
    public static final class Report {
        public int lines, blocks, values, markers, items;
        public int headless;
        public int unmapped;
        public int lostValues;
        public final Set<String> problems = new LinkedHashSet<>();

        void problem(String what) {
            if (problems.size() < 12) problems.add(what);
        }
    }

    public record Result(JsonObject json, Report report) {}

    public static Result export(Script script) {
        Report report = new Report();
        List<Script.Root> roots = new ArrayList<>(script.roots);
        roots.sort(Comparator.<Script.Root>comparingDouble(r -> r.x).thenComparingDouble(r -> r.y));

        JsonArray handlers = new JsonArray();
        for (Script.Root root : roots) {
            if (root.chain.isEmpty()) continue;
            JsonObject handler = handler(root.chain.get(0), report);
            if (handler == null) { report.headless++; continue; }
            handler.addProperty("position", handlers.size());
            handler.add("operations", operations(root.chain.subList(1, root.chain.size()), report));
            handlers.add(handler);
            report.lines++;
        }
        JsonObject json = new JsonObject();
        json.add("handlers", handlers);
        return new Result(json, report);
    }

    private static JsonObject handler(Script.Node head, Report report) {
        JsonObject o = new JsonObject();
        if (head.declares()) {
            o.addProperty("type", head.isProcess() ? "process" : "function");
            o.addProperty("name", Functions.nameOf(head));
            o.add("values", declarationValues(head, report));
            report.blocks++;
            return o;
        }
        if (!head.isHat()) return null;
        String event = Mapping.eventId(head.action);
        if (event == null) {
            report.unmapped++;
            report.problem("событие «" + head.action.name + "»");
            return null;
        }
        o.addProperty("type", "event");
        o.addProperty("event", event);
        report.blocks++;
        return o;
    }

    private static JsonArray declarationValues(Script.Node head, Report report) {
        JsonArray values = new JsonArray();
        JsonArray params = cells(head.values.get(Catalog.FN_PARAMS), report);
        if (!params.isEmpty()) values.add(entry("parameters", array(params)));
        JsonArray desc = cells(head.values.get(Catalog.FN_DESC), report);
        if (!desc.isEmpty()) values.add(entry("description", array(desc)));

        Value icon = head.value(Catalog.FN_ICON);
        String encoded = icon == null ? null : Stacks.toServer(icon);
        if (encoded != null) {
            JsonObject item = new JsonObject();
            item.addProperty("type", Value.ITEM);
            item.addProperty("item", encoded);
            values.add(entry("icon", item));
            report.items++;
            report.values++;
        }
        values.add(entry("is_hidden",
                enumValue("Скрыть".equals(head.marker(0)) ? "TRUE" : "FALSE")));
        report.markers++;
        keptValues(head, values);
        return values;
    }

    private static JsonArray operations(List<Script.Node> chain, Report report) {
        JsonArray out = new JsonArray();
        for (Script.Node node : chain) {
            JsonObject op = operation(node, report);
            if (op == null) {
                for (JsonElement inner : operations(node.body, report)) out.add(inner);
                continue;
            }
            out.add(op);
        }
        return out;
    }

    private static JsonObject operation(Script.Node node, Report report) {
        JsonObject op = new JsonObject();
        if (node.action == Catalog.ELSE) {
            op.addProperty("action", "else");
            op.add("values", new JsonArray());
        } else if (node.invokes()) {
            op.addProperty("action", node.isStart() ? "start_process" : "call_function");
            op.add("values", invokeValues(node, report));
        } else if (node.declares()) {
            report.unmapped++;
            report.problem("«" + node.action.name + "» внутри строки");
            return null;
        } else {
            String id = Mapping.actionId(node.action);
            Mapping.Act act = id == null ? null : Mapping.action(id);
            if (act == null) {
                report.unmapped++;
                report.problem("«" + node.action.name + "» (" + node.action.category.name + ")");
                return null;
            }
            op.addProperty("action", id);
            op.add("values", values(node, act, report));
        }
        report.blocks++;
        blockFields(node, op, report);
        keptFields(node, op);
        if (node.wraps() || !node.body.isEmpty())
            op.add("operations", operations(node.body, report));
        return op;
    }

    private static void blockFields(Script.Node node, JsonObject op, Report report) {
        String target = node.settingOf(Catalog.TARGET);
        if (target != null && !Catalog.TARGET_DEFAULT.equals(target)) {
            String id = Mapping.targetId(node.action, target);
            if (id == null) {
                report.problem("цель «" + target + "» у «" + node.action.name + "»");
            } else {
                JsonObject selection = new JsonObject();
                selection.addProperty("type", id);
                op.add("selection", selection);
                report.markers++;
            }
        }
        if (Catalog.INVERT_ON.equals(node.settingOf(Catalog.INVERT))) {
            op.addProperty("is_inverted", true);
            report.markers++;
        }
    }

    private static JsonArray values(Script.Node node, Mapping.Act act, Report report) {
        JsonArray out = new JsonArray();
        for (Map.Entry<Integer, List<Value>> e : node.values.entrySet()) {
            List<Value> list = e.getValue();
            if (list == null || list.isEmpty()) continue;
            String name = act.argNames.get(e.getKey());
            if (name == null) {
                report.lostValues += list.size();
                report.problem("аргумент " + (e.getKey() + 1) + " у «" + node.action.name + "»");
                continue;
            }
            JsonElement value = slot(list, act.isPlural(name), report);
            if (value != null) out.add(entry(name, value));
        }
        for (Map.Entry<Integer, String> e : node.markers.entrySet()) {
            if (e.getKey() >= node.action.settings.size()) continue;
            String name = act.settingNames.get(e.getKey());
            Map<String, String> ids = act.optionIds.get(e.getKey());
            String id = ids == null ? null : ids.get(e.getValue());
            if (name == null || id == null) continue;
            out.add(entry(name, enumValue(id)));
            report.markers++;
        }
        keptValues(node, out);
        return out;
    }

    private static JsonElement slot(List<Value> list, boolean plural, Report report) {
        if (!plural) return value(list.get(0), report);
        if (list.size() == 1 && Value.ARRAY.equals(list.get(0).type))
            return value(list.get(0), report);
        return array(cells(list, report));
    }

    private static JsonArray cells(List<Value> list, Report report) {
        JsonArray cells = new JsonArray();
        if (list == null) return cells;
        for (Value v : list) cells.add(cell(v, report));
        return cells;
    }

    private static JsonObject cell(Value v, Report report) {
        if (v == null || v.isBlank()) return new JsonObject();
        JsonObject cell = value(v, report);
        return cell == null ? new JsonObject() : cell;
    }

    private static JsonArray invokeValues(Script.Node node, Report report) {
        JsonArray out = new JsonArray();
        String nameKey = node.isStart() ? "process_name" : "function_name";
        Value target = node.value(Catalog.CALL_NAME);
        if (target != null) {
            JsonObject value = value(target, report);
            if (value != null) out.add(entry(nameKey, value));
        }
        if (node.isStart()) {
            marker(out, node, 0, "local_variables_mode", report);
            marker(out, node, 1, "target_mode", report);
        }

        JsonObject args = new JsonObject();
        List<Catalog.Arg> params = node.args();
        for (int i = 1; i < params.size(); i++) {
            List<Value> list = node.values.get(i);
            if (list == null || list.isEmpty()) continue;
            JsonElement passed = params.get(i).list
                    ? array(cells(list, report)) : value(list.get(0), report);
            if (passed == null) continue;
            args.add(paramKey(params.get(i).purpose), passed);
        }
        List<Catalog.Setting> settings = node.settings();
        for (int i = node.action.settings.size(); i < settings.size(); i++) {
            String option = node.marker(i);
            if (option == null || option.isEmpty()) continue;
            args.add(paramKey(settings.get(i).label), text(option, "plain"));
        }
        if (!args.isEmpty()) {
            JsonObject map = new JsonObject();
            map.addProperty("type", Value.MAP);
            map.add("values", args);
            out.add(entry("args", map));
        }
        keptValues(node, out);
        return out;
    }

    private static void marker(JsonArray out, Script.Node node, int index, String name,
                               Report report) {
        if (index >= node.settings().size()) return;
        String id = SERVER_OPTIONS.get(node.marker(index));
        if (id == null) return;
        out.add(entry(name, enumValue(id)));
        report.markers++;
    }

    private static final Map<String, String> SERVER_OPTIONS = Map.of(
            "Не дублировать", "DONT_COPY",
            "Дублировать", "COPY",
            "Общие", "SHARE",
            "Цель события", "CURRENT_TARGET",
            "Текущая цель", "CURRENT_SELECTION",
            "Без цели", "NO_TARGET",
            "Каждая цель в выборке", "FOR_EACH_IN_SELECTION");

    private static String paramKey(String param) {
        return text(param, "plain").toString();
    }

    private static JsonObject text(String s, String parsing) {
        JsonObject o = new JsonObject();
        o.addProperty("type", Value.TEXT);
        o.addProperty("text", s);
        o.addProperty("parsing", parsing);
        return o;
    }

    private static JsonObject value(Value v, Report report) {
        if (v == null) return null;
        switch (v.type) {
            case Value.ITEM -> {
                String encoded = Stacks.toServer(v);
                if (encoded == null) return null;
                JsonObject o = new JsonObject();
                o.addProperty("type", Value.ITEM);
                o.addProperty("item", encoded);
                report.items++;
                report.values++;
                return o;
            }
            case Value.ARRAY -> {
                JsonObject o = new JsonObject();
                o.addProperty("type", Value.ARRAY);
                int last = -1;
                for (int i = 0; i < v.items.size(); i++) if (!v.items.get(i).isBlank()) last = i;
                JsonArray cells = new JsonArray();
                for (int i = 0; i <= last; i++) cells.add(cell(v.items.get(i), report));
                o.add("values", cells);
                report.values++;
                return o;
            }
            case Value.MAP -> {
                JsonObject o = new JsonObject();
                o.addProperty("type", Value.MAP);
                JsonObject entries = new JsonObject();
                for (int i = 0; i < v.keys.size(); i++) {
                    Value key = v.keys.get(i);
                    Value val = i < v.items.size() ? v.items.get(i) : Value.blank();
                    JsonObject keyJson = key.isBlank() ? null : value(key, report);
                    JsonObject valJson = val.isBlank() ? null : value(val, report);
                    entries.add(i + "_" + (keyJson == null ? "{}" : keyJson.toString()),
                            valJson == null ? new JsonObject() : valJson);
                }
                o.add("values", entries);
                report.values++;
                return o;
            }
            default -> {
                report.values++;
                return v.toJson();
            }
        }
    }

    private static JsonObject entry(String name, JsonElement value) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.add("value", value);
        return o;
    }

    private static JsonObject enumValue(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "enum");
        o.addProperty("enum", id.toUpperCase(Locale.ROOT));
        return o;
    }

    private static JsonObject array(JsonArray cells) {
        JsonObject o = new JsonObject();
        o.addProperty("type", Value.ARRAY);
        o.add("values", cells);
        return o;
    }

    private static void keptFields(Script.Node node, JsonObject op) {
        if (node.raw == null) return;
        for (String field : node.raw.keySet()) {
            if ("values".equals(field)) continue;
            op.add(field, node.raw.get(field));
        }
    }

    private static void keptValues(Script.Node node, JsonArray values) {
        if (node.raw == null || !node.raw.has("values")) return;
        Set<String> written = new LinkedHashSet<>();
        for (JsonElement e : values)
            if (e.isJsonObject() && e.getAsJsonObject().has("name"))
                written.add(e.getAsJsonObject().get("name").getAsString());
        for (JsonElement e : node.raw.getAsJsonArray("values")) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("name")) continue;
            if (written.contains(e.getAsJsonObject().get("name").getAsString())) continue;
            values.add(e);
        }
    }

    private Exporter() {}
}
