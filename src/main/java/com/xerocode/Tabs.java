package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Tabs {
    public static final int MAX = 10;
    public static final int NAME_MAX = 40;
    private static final String MAIN = "main";
    private static final String INDEX = "index.json";

    public static final class Tab {
        public final String id;
        public String name;
        Script script;

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
                    continue;
                }
                if (TABS.size() >= MAX || !id.matches("[0-9a-f]{12}")) continue;
                TABS.add(new Tab(id, name.isBlank() ? freeName() : clean(name)));
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
