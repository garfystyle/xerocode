package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;

public final class Tabs {
    public static final int MAX = 10;
    public static final int NAME_MAX = 40;
    private static final String MAIN = "main";
    private static final String INDEX = "index.json";
    private static final String EMPTY_OP = "empty";
    private static final String SPOT_MARK = "xc:";

    public static final class Tab {
        public final String id;
        public String name;
        Script script;
        String file = "";
        int canvasMark, textMark;

        Tab(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public boolean main() { return MAIN.equals(id); }
    }

    private static final List<Tab> TABS = new ArrayList<>();
    private static Script mainRef;
    private static Path folder;
    private static int active;
    private static boolean touched;

    private record Placed(Script script, Script.Root root) {}

    public record Spot(int x, int y, String name) {
        boolean main() { return name.isEmpty(); }

        String text() { return SPOT_MARK + x + ":" + y + ":" + name; }
    }

    static boolean spotText(JsonElement data) {
        return data != null && data.isJsonPrimitive() && data.getAsString().startsWith(SPOT_MARK);
    }

    public static Spot spot(JsonObject handler) {
        JsonElement data = handler.get(Importer.CUSTOM_DATA);
        if (!spotText(data)) return null;
        String[] parts = data.getAsString().split(":", 4);
        if (parts.length < 4) return null;
        try {
            return new Spot(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void stamp(JsonObject handler, Script script, Script.Root root) {
        int tab = indexOf(script);
        JsonElement data = handler.get(Importer.CUSTOM_DATA);
        if (tab < 0 || data != null && !spotText(data)) return;
        String name = tab == 0 ? "" : TABS.get(tab).name;
        handler.addProperty(Importer.CUSTOM_DATA, new Spot((int) Math.round(root.x), (int) Math.round(root.y), name).text());
    }

    static boolean owns(Script script, Spot spot) {
        int tab = indexOf(script);
        return tab >= 0 && named(tab, spot);
    }

    private static boolean named(int tab, Spot spot) {
        return spot.main() ? tab == 0 : TABS.get(tab).name.equals(spot.name());
    }

    public static JsonArray restore(Script into, JsonArray lines, Font font) {
        if (indexOf(into) < 0) return lines;
        Map<Script, JsonArray> away = new LinkedHashMap<>();
        JsonArray rest = new JsonArray();
        for (JsonElement e : lines) {
            Spot spot = spot(e.getAsJsonObject());
            Script home = spot == null ? null : home(spot, into);
            if (home == null) rest.add(e);
            else away.computeIfAbsent(home, k -> new JsonArray()).add(e);
        }
        away.forEach((home, handlers) -> {
            Importer.importInto(home, handlers, font);
            home.save();
        });
        shown(into);
        return rest;
    }

    private static Script home(Spot spot, Script into) {
        for (int i = 0; i < all().size(); i++) {
            if (!named(i, spot)) continue;
            Script known = script(i);
            return known == into || !known.roots.isEmpty() ? null : known;
        }
        return full() ? null : add(spot.name());
    }

    public static List<Tab> all() {
        fresh();
        return TABS;
    }

    public static int active() {
        fresh();
        return active;
    }

    public static Script activeScript() {
        fresh();
        return script(active);
    }

    public static Script script(int index) {
        fresh();
        Tab tab = TABS.get(Math.max(0, Math.min(TABS.size() - 1, index)));
        if (tab.script == null) tab.script = Script.loadFrom(fileOf(tab), mainRef.plot);
        return tab.script;
    }

    public static int indexOf(Script script) {
        fresh();
        for (int i = 0; i < TABS.size(); i++) if (TABS.get(i).script == script) return i;
        return -1;
    }

    public static void shown(Script script) {
        int i = indexOf(script);
        if (i < 0 || i == active) return;
        active = i;
        saveIndex();
    }

    public static boolean full() { return all().size() >= MAX; }

    public static List<Script> others(Script script) {
        if (mainRef == null) return List.of();
        int own = indexOf(script);
        if (own < 0) return List.of();
        List<Script> out = new ArrayList<>();
        for (int i = 0; i < TABS.size(); i++) if (i != own) out.add(script(i));
        return out;
    }

    public static List<Script> filled(Script from) {
        List<Script> out = new ArrayList<>();
        if (indexOf(from) < 0) {
            if (!Exporter.positions(from).isEmpty()) out.add(from);
            return out;
        }
        for (int i = 0; i < TABS.size(); i++) {
            Script s = script(i);
            if (!Exporter.positions(s).isEmpty()) out.add(s);
        }
        return out;
    }

    public static boolean blank() {
        for (int i = 0; i < all().size(); i++) if (!script(i).roots.isEmpty()) return false;
        return true;
    }

    public static boolean blank(Script from) {
        fresh();
        if (!from.roots.isEmpty()) return false;
        for (Script s : others(from)) if (!s.roots.isEmpty()) return false;
        return true;
    }

    public static int codeHash(Script script, int own) {
        int index = indexOf(script);
        if (index < 0 || TABS.size() < 2) return own;
        int h = index == 0 ? own : script(0).codeHash();
        for (int i = 1; i < TABS.size(); i++) {
            Script s = script(i);
            if (!s.roots.isEmpty()) h = h * 31 + (i == index ? own : s.codeHash());
        }
        return h;
    }

    public static int lineBase(Script script) {
        int index = indexOf(script), base = 0;
        for (int i = 0; i < index; i++) base += Exporter.positions(script(i)).size();
        return base;
    }

    public static void touch() { touched = true; }

    public static boolean touched() { return touched; }

    public static void sent() { touched = false; }

    public static int twins(List<Script> scripts) {
        Set<String> seen = new HashSet<>();
        int twins = 0;
        for (Script s : scripts) {
            Set<String> own = new LinkedHashSet<>();
            for (JsonElement e : Exporter.export(List.of(s)).json().getAsJsonArray("handlers"))
                own.add(twinKey(e.getAsJsonObject()));
            for (String key : own) if (!seen.add(key)) twins++;
        }
        return twins;
    }

    private static String twinKey(JsonObject handler) {
        String type = Json.str(handler, "type");
        if (!"event".equals(type)) return type + ":" + Json.str(handler, "name");
        JsonObject bare = handler.deepCopy();
        bare.remove("position");
        return bare.toString();
    }

    public static JsonArray takeBack(Script into, JsonArray world) {
        Map<String, ArrayDeque<Placed>> placed = new HashMap<>();
        for (Script s : others(into)) {
            JsonArray handlers = Exporter.export(List.of(s)).json().getAsJsonArray("handlers");
            Exporter.positions(s).forEach((root, at) -> placed.computeIfAbsent(
                    shape(handlers.get(at).getAsJsonObject()), k -> new ArrayDeque<>())
                    .add(new Placed(s, root)));
        }
        if (placed.isEmpty()) return world;
        JsonArray rest = new JsonArray();
        Set<Script> changed = new LinkedHashSet<>();
        for (JsonElement e : world) {
            ArrayDeque<Placed> same = placed.get(shape(e.getAsJsonObject()));
            if (same == null || same.isEmpty()) {
                rest.add(e);
                continue;
            }
            Placed line = same.poll();
            if (refresh(line, e.getAsJsonObject())) changed.add(line.script());
        }
        for (Script s : changed) s.save();
        return rest;
    }

    private static boolean refresh(Placed line, JsonObject handler) {
        try {
            List<Script.Node> chain = Importer.chainOf(handler.deepCopy(), new Importer.Result());
            if (chain.isEmpty()) return false;
            Script.Root old = line.root();
            Script.Root fresh = new Script.Root(old.x, old.y);
            fresh.folded = old.folded;
            fresh.chain.addAll(chain);
            line.script().roots.set(line.script().roots.indexOf(old), fresh);
            return true;
        } catch (RuntimeException e) {
            XeroCode.LOG.warn("[xerocode] строка полотна не обновилась из мира", e);
            return false;
        }
    }

    private static String shape(JsonObject handler) {
        StringBuilder out = new StringBuilder(Json.str(handler, "type")).append(':')
                .append(Json.str(handler, "event")).append(Json.str(handler, "name"));
        shape(Json.arr(handler, "operations"), out);
        return out.toString();
    }

    private static void shape(JsonArray operations, StringBuilder out) {
        if (operations == null) return;
        out.append('[');
        for (JsonElement e : operations) {
            if (!e.isJsonObject()) continue;
            JsonObject op = e.getAsJsonObject();
            String action = Json.str(op, "action");
            if (EMPTY_OP.equals(action)) continue;
            out.append(action).append(',');
            shape(Json.arr(op, "operations"), out);
        }
        out.append(']');
    }

    public static Script add(String name) {
        fresh();
        if (TABS.size() >= MAX) return null;
        Tab tab = new Tab(Script.newId(), name == null || name.isBlank() ? freeName() : clean(name));
        Script script = new Script();
        script.plot = mainRef.plot;
        script.home = fileOf(tab);
        tab.script = script;
        TABS.add(tab);
        active = TABS.size() - 1;
        script.save();
        saveIndex();
        return script;
    }

    public static void close(int index) {
        fresh();
        if (index <= 0 || index >= TABS.size()) return;
        Tab tab = TABS.remove(index);
        if (tab.script != null) History.clear(tab.script);
        try {
            Files.deleteIfExists(fileOf(tab));
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] не удалось удалить вкладку {}", tab.name, e);
        }
        if (active >= index) active = Math.max(0, active - 1);
        saveIndex();
    }

    public static void rename(int index, String name) {
        fresh();
        if (index < 0 || index >= TABS.size() || name == null || name.isBlank()) return;
        TABS.get(index).name = clean(name);
        saveIndex();
    }

    public static String name(Script script) {
        int i = indexOf(script);
        return i < 0 ? "" : TABS.get(i).name;
    }

    public static boolean autoName(int index) {
        Tab tab = all().get(index);
        if (tab.main() && mainName().equals("Мир")) return false;
        return tab.name.matches("Полотно \\d+");
    }

    public static void saveAll() { saveAll(null); }

    public static void saveAll(Script done) {
        if (mainRef == null) return;
        for (Tab tab : TABS) if (tab.script != null && tab.script != done) tab.script.save();
        saveIndex();
    }

    public static int fingerprint() {
        fresh();
        int h = active;
        for (Tab tab : TABS) {
            h = h * 31 + tab.id.hashCode() + tab.name.hashCode();
            if (tab.script != null) h = h * 31 + tab.script.fingerprint();
        }
        return h;
    }

    private static void fresh() {
        Script main = XeroCode.script();
        if (main == mainRef) return;
        mainRef = main;
        TABS.clear();
        active = 0;
        touched = false;
        String key = main.plot == null || main.plot.isEmpty() ? "canvas" : main.plot;
        folder = Script.dir().resolve("tabs").resolve(key);
        Tab first = new Tab(MAIN, mainName());
        first.script = main;
        TABS.add(first);
        try {
            Path index = folder.resolve(INDEX);
            if (!Files.exists(index)) return;
            JsonObject root = Json.read(index);
            String chosen = Json.str(root, "active");
            JsonArray list = Json.arr(root, "tabs");
            if (list == null) return;
            for (JsonElement e : list) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String id = Json.str(o, "id"), name = Json.str(o, "name");
                if (id.isEmpty()) continue;
                if (id.equals(MAIN)) {
                    if (!name.isBlank()) first.name = clean(name);
                    linkFrom(first, o);
                    continue;
                }
                if (TABS.size() >= MAX || !id.matches("[0-9a-f]{12}")) continue;
                Tab tab = new Tab(id, name.isBlank() ? freeName() : clean(name));
                linkFrom(tab, o);
                TABS.add(tab);
            }
            for (int i = 0; i < TABS.size(); i++) if (TABS.get(i).id.equals(chosen)) active = i;
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] не удалось прочитать вкладки", e);
        }
    }

    private static void saveIndex() {
        if (mainRef == null) return;
        JsonArray list = new JsonArray();
        for (Tab tab : TABS) {
            JsonObject o = new JsonObject();
            o.addProperty("id", tab.id);
            o.addProperty("name", tab.name);
            if (!tab.file.isEmpty()) {
                o.addProperty(LINK_FILE, tab.file);
                o.addProperty(LINK_CANVAS, tab.canvasMark);
                o.addProperty(LINK_TEXT, tab.textMark);
            }
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("active", TABS.get(active).id);
        root.add("tabs", list);
        try {
            Json.write(folder.resolve(INDEX), root);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] не удалось сохранить вкладки", e);
        }
    }

    private static Path fileOf(Tab tab) { return folder.resolve(tab.id + ".json"); }

    private static final String LINK_FILE = "file", LINK_CANVAS = "canvasMark", LINK_TEXT = "textMark";

    private static void linkFrom(Tab tab, JsonObject o) {
        tab.file = Json.str(o, LINK_FILE);
        tab.canvasMark = Json.num(o, LINK_CANVAS);
        tab.textMark = Json.num(o, LINK_TEXT);
    }

    private static Tab tabOf(Script script) {
        int i = indexOf(script);
        return i < 0 ? null : TABS.get(i);
    }

    public static String linkedFile(Script script) {
        Tab tab = tabOf(script);
        return tab == null ? "" : tab.file;
    }

    public static int canvasMark(Script script) {
        Tab tab = tabOf(script);
        return tab == null ? 0 : tab.canvasMark;
    }

    public static int textMark(Script script) {
        Tab tab = tabOf(script);
        return tab == null ? 0 : tab.textMark;
    }

    public static Script linkedTo(String file) {
        fresh();
        for (int i = 0; i < TABS.size(); i++) if (TABS.get(i).file.equals(file)) return script(i);
        return null;
    }

    public static List<Script> linked() {
        fresh();
        List<Script> out = new ArrayList<>();
        for (int i = 0; i < TABS.size(); i++) if (!TABS.get(i).file.isEmpty()) out.add(script(i));
        return out;
    }

    public static void link(Script script, String file, int canvasMark, int textMark) {
        Tab tab = tabOf(script);
        if (tab == null) return;
        for (Tab other : TABS) if (other != tab && other.file.equals(file)) other.file = "";
        tab.file = file == null ? "" : file;
        tab.canvasMark = canvasMark;
        tab.textMark = textMark;
        saveIndex();
    }

    public static void relink(String from, String to) {
        fresh();
        boolean changed = false;
        for (Tab tab : TABS) {
            if (!tab.file.equals(from)) continue;
            tab.file = to == null ? "" : to;
            changed = true;
        }
        if (changed) saveIndex();
    }

    private static String mainName() {
        return mainRef != null && mainRef.plot != null && !mainRef.plot.isEmpty() ? "Мир" : "Полотно 1";
    }

    private static String freeName() {
        for (int n = 2; ; n++) {
            String name = "Полотно " + n;
            boolean taken = false;
            for (Tab tab : TABS) if (tab.name.equals(name)) taken = true;
            if (!taken) return name;
        }
    }

    private static String clean(String name) {
        String s = name.replaceAll("[\\r\\n\\t]", " ").trim();
        return s.length() > NAME_MAX ? s.substring(0, NAME_MAX) : s;
    }

    private Tabs() {}
}
