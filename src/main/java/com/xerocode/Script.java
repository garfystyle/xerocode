package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Script {
    public static final class Node {
        public final Catalog.Action action;
        public final Map<Integer, List<Value>> values = new LinkedHashMap<>();
        public final Map<Integer, String> markers = new LinkedHashMap<>();
        public final Map<Integer, Value> markerVars = new LinkedHashMap<>();
        public final List<Node> body = new ArrayList<>();

        public List<Catalog.Arg> dynArgs;
        public List<Catalog.Setting> dynSettings;
        public List<String> dynKeys = new ArrayList<>(), dynMarkerKeys = new ArrayList<>();
        public transient Value dynIcon;
        public transient Value dynDisplay;

        public JsonObject raw;
        public Node cond;

        public Node(Catalog.Action action) {
            this.action = action;
            for (int i = 0; i < action.settings.size(); i++)
                markers.put(i, action.settings.get(i).def);
        }

        public List<Catalog.Arg> args() { return dynArgs == null ? action.args : dynArgs; }

        public List<Catalog.Setting> settings() {
            List<Catalog.Setting> base = dynSettings == null ? action.settings : dynSettings;
            List<Catalog.Setting> extra = Catalog.extraSettings(action);
            if (extra.isEmpty()) return base;
            if (settingsCache == null || settingsBase != base) {
                List<Catalog.Setting> merged = new ArrayList<>(base.size() + extra.size());
                merged.addAll(base);
                merged.addAll(extra);
                settingsBase = base;
                settingsCache = merged;
            }
            return settingsCache;
        }

        private List<Catalog.Setting> settingsCache, settingsBase;

        public int settingIndex(String label) {
            List<Catalog.Setting> list = settings();
            for (int i = 0; i < list.size(); i++) if (list.get(i).label.equals(label)) return i;
            return -1;
        }

        public String settingOf(String label) {
            int i = settingIndex(label);
            return i < 0 ? null : marker(i);
        }

        public void setSetting(String label, String option) {
            int i = settingIndex(label);
            if (i >= 0) markers.put(i, option);
        }

        public boolean inverted() {
            return Catalog.INVERT_ON.equals(settingOf(Catalog.INVERT));
        }

        public boolean isCall() { return action == Catalog.CALL; }
        public boolean isFunction() { return action == Catalog.FUNCTION; }
        public boolean isProcess() { return action == Catalog.PROCESS; }
        public boolean isStart() { return action == Catalog.START_PROCESS; }
        public boolean declares() { return isFunction() || isProcess(); }
        public boolean invokes() { return isCall() || isStart(); }

        public boolean wraps() { return action.category != null && action.category.wraps(); }
        public boolean isHat() { return action.category != null && action.category.isEvent(); }

        public List<Value> valuesOf(int argIndex) {
            return values.computeIfAbsent(argIndex, k -> new ArrayList<>());
        }
        public Value value(int argIndex) {
            List<Value> v = values.get(argIndex);
            return v == null || v.isEmpty() ? null : v.get(0);
        }
        public String marker(int settingIndex) {
            Catalog.Setting s = settings().get(settingIndex);
            return markers.getOrDefault(settingIndex, s.def);
        }
        public Value markerVar(int settingIndex) { return markerVars.get(settingIndex); }

        public void bindMarker(int settingIndex, Value variable) {
            if (variable == null || variable.name.isBlank()) markerVars.remove(settingIndex);
            else markerVars.put(settingIndex, variable.copy());
        }

        public void cycleMarker(int settingIndex, boolean forward) {
            Catalog.Setting s = settings().get(settingIndex);
            if (s.options.isEmpty()) return;
            int i = Math.max(0, s.options.indexOf(marker(settingIndex)));
            i = (i + (forward ? 1 : s.options.size() - 1)) % s.options.size();
            markers.put(settingIndex, s.options.get(i));
        }

        public Node copy() {
            Node n = new Node(action);
            values.forEach((k, v) -> {
                List<Value> c = new ArrayList<>(v.size());
                for (Value x : v) c.add(x.copy());
                n.values.put(k, c);
            });
            n.markers.clear();
            n.markers.putAll(markers);
            markerVars.forEach((k, v) -> n.markerVars.put(k, v.copy()));
            if (dynArgs != null) n.dynArgs = new ArrayList<>(dynArgs);
            if (dynSettings != null) n.dynSettings = new ArrayList<>(dynSettings);
            n.dynKeys = new ArrayList<>(dynKeys);
            n.dynMarkerKeys = new ArrayList<>(dynMarkerKeys);
            if (raw != null) n.raw = raw.deepCopy();
            if (cond != null) n.cond = cond.copy();
            for (Node c : body) n.body.add(c.copy());
            return n;
        }
    }

    public static final class Root {
        public double x, y;
        public final String id;
        public final List<Node> chain = new ArrayList<>();

        public Root(double x, double y) { this(x, y, newId()); }

        public Root(double x, double y, String id) {
            this.x = x;
            this.y = y;
            this.id = id == null || id.isBlank() ? newId() : id;
        }
    }

    private static final java.security.SecureRandom IDS = new java.security.SecureRandom();

    public static String newId() {
        byte[] raw = new byte[6];
        IDS.nextBytes(raw);
        StringBuilder sb = new StringBuilder(12);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public Root rootById(String id) {
        for (Root r : roots) if (r.id.equals(id)) return r;
        return null;
    }

    public Root rootOf(Node node) {
        for (Root r : roots) if (holds(r.chain, node)) return r;
        return null;
    }

    private static boolean holds(List<Node> chain, Node node) {
        for (Node n : chain) {
            if (n == node) return true;
            if (n.cond != null && holds(List.of(n.cond), node)) return true;
            if (holds(n.body, node)) return true;
        }
        return false;
    }

    public static int rootHash(Root r) {
        int h = Double.hashCode(r.x) * 31 + Double.hashCode(r.y);
        return chainHash(h, r.chain);
    }

    public static int blocks(List<Node> chain) {
        int n = 0;
        for (Node k : chain) n += 1 + blocks(k.body);
        return n;
    }

    public static int blocksIn(List<Root> roots) {
        int n = 0;
        for (Root r : roots) n += blocks(r.chain);
        return n;
    }

    public final List<Root> roots = new ArrayList<>();
    public int paletteW;
    public double viewX = 60, viewY = 50, viewZoom = 1;

    public transient boolean fitOnOpen;

    public transient String plot = "";

    private static Path dir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("xerocode");
    }

    public static Path file() { return dir().resolve("script.json"); }

    public static Path file(String plot) {
        return plot == null || plot.isEmpty() ? file()
                : dir().resolve("worlds").resolve(plot + ".json");
    }

    private static Path adopted() { return dir().resolve("worlds/adopted.txt"); }

    private static void adopt(String plot) {
        try {
            if (plot.isEmpty() || Files.exists(adopted()) || !Files.exists(file())) return;
            Path into = file(plot);
            if (Files.exists(into)) return;
            Script was = read(file());
            if (was.roots.isEmpty()) return;
            Files.createDirectories(into.getParent());
            Files.copy(file(), into);
            Files.writeString(adopted(), plot, StandardCharsets.UTF_8);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] не вышло привязать прежний скрипт к миру", e);
        }
    }

    public JsonObject toJson() {
        JsonArray arr = new JsonArray();
        for (Root r : roots) {
            JsonObject ro = new JsonObject();
            ro.addProperty("id", r.id);
            ro.addProperty("x", r.x);
            ro.addProperty("y", r.y);
            ro.add("chain", writeChain(r.chain));
            arr.add(ro);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        if (paletteW > 0) root.addProperty("paletteW", paletteW);
        root.addProperty("viewX", viewX);
        root.addProperty("viewY", viewY);
        root.addProperty("viewZoom", viewZoom);
        root.add("roots", arr);
        return root;
    }

    public static Script fromJson(JsonObject root) {
        Script s = new Script();
        if (root == null || !root.has("roots")) return s;
        if (root.has("paletteW")) s.paletteW = root.get("paletteW").getAsInt();
        if (root.has("viewX")) s.viewX = root.get("viewX").getAsDouble();
        if (root.has("viewY")) s.viewY = root.get("viewY").getAsDouble();
        if (root.has("viewZoom"))
            s.viewZoom = Math.max(0.2, Math.min(2.0, root.get("viewZoom").getAsDouble()));
        for (JsonElement re : root.getAsJsonArray("roots")) {
            JsonObject ro = re.getAsJsonObject();
            Root rt = new Root(ro.get("x").getAsDouble(), ro.get("y").getAsDouble(),
                    ro.has("id") ? ro.get("id").getAsString() : null);
            rt.chain.addAll(readChain(ro.getAsJsonArray("chain")));
            s.roots.add(rt);
        }
        return s;
    }

    public void replaceWith(Script other) {
        roots.clear();
        roots.addAll(other.roots);
    }

    public int fingerprint() {
        int h = roots.size();
        for (Root r : roots) {
            h = h * 31 + Double.hashCode(r.x);
            h = h * 31 + Double.hashCode(r.y);
            h = chainHash(h, r.chain);
        }
        return h;
    }

    public int codeHash() {
        int h = roots.size();
        for (Root r : roots) h = chainHash(h, r.chain);
        return h;
    }

    private static int chainHash(int h, List<Node> chain) {
        h = h * 31 + chain.size();
        for (Node n : chain) {
            h = h * 31 + System.identityHashCode(n.action);
            for (Map.Entry<Integer, List<Value>> e : n.values.entrySet()) {
                h = h * 31 + e.getKey();
                for (Value v : e.getValue()) h = h * 31 + v.hash();
            }
            for (Map.Entry<Integer, String> e : n.markers.entrySet())
                h = (h * 31 + e.getKey()) * 31 + e.getValue().hashCode();
            for (Map.Entry<Integer, Value> e : n.markerVars.entrySet())
                h = (h * 31 + e.getKey()) * 31 + e.getValue().hash();
            if (n.cond != null) h = chainHash(h, List.of(n.cond));
            h = chainHash(h, n.body);
        }
        return h;
    }

    public void save() {
        JsonObject root = toJson();
        try {
            Path p = file(plot);
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
        } catch (IOException e) {
            XeroCode.LOG.error("[xerocode] save failed", e);
        }
    }

    public static Script load() { return load(""); }

    public static Script load(String plot) {
        adopt(plot);
        Script script = read(file(plot));
        script.plot = plot == null ? "" : plot;
        return script;
    }

    private static Script read(Path p) {
        if (!Files.exists(p)) return new Script();
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return fromJson(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] load failed", e);
            return new Script();
        }
    }

    static JsonArray writeChain(List<Node> chain) {
        JsonArray arr = new JsonArray();
        for (Node n : chain) {
            JsonObject o = new JsonObject();
            o.addProperty("a", Catalog.keyOf(n.action));
            if (!n.values.isEmpty()) {
                JsonObject vals = new JsonObject();
                n.values.forEach((idx, list) -> {
                    if (list.isEmpty()) return;
                    JsonArray la = new JsonArray();
                    for (Value v : list) la.add(v.toJson());
                    vals.add(String.valueOf(idx), la);
                });
                if (!vals.isEmpty()) o.add("v", vals);
            }
            if (!n.markers.isEmpty()) {
                JsonObject mk = new JsonObject();
                n.markers.forEach((idx, opt) -> mk.addProperty(String.valueOf(idx), opt));
                o.add("m", mk);
            }
            if (!n.markerVars.isEmpty()) {
                JsonObject mv = new JsonObject();
                n.markerVars.forEach((idx, v) -> mv.add(String.valueOf(idx), v.toJson()));
                o.add("mv", mv);
            }
            if (n.cond != null) o.add("c", writeChain(List.of(n.cond)).get(0));
            if (!n.body.isEmpty()) o.add("b", writeChain(n.body));
            if (n.raw != null && !n.raw.isEmpty()) o.add("r", n.raw);
            if (!n.dynKeys.isEmpty()) {
                JsonArray keys = new JsonArray();
                for (String k : n.dynKeys) keys.add(k);
                o.add("dk", keys);
            }
            if (!n.dynMarkerKeys.isEmpty()) {
                JsonArray keys = new JsonArray();
                for (String k : n.dynMarkerKeys) keys.add(k);
                o.add("dm", keys);
            }
            arr.add(o);
        }
        return arr;
    }

    static List<Node> readChain(JsonArray arr) {
        List<Node> out = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            Catalog.Action a = Catalog.byKey(o.get("a").getAsString());
            if (a == null) continue;
            Node n = new Node(a);
            if (o.has("dk")) for (JsonElement k : o.getAsJsonArray("dk")) n.dynKeys.add(k.getAsString());
            if (o.has("dm")) for (JsonElement k : o.getAsJsonArray("dm"))
                n.dynMarkerKeys.add(k.getAsString());
            if (o.has("v")) {
                JsonObject vals = o.getAsJsonObject("v");
                for (String k : vals.keySet()) {
                    int idx = Integer.parseInt(k);
                    boolean dyn = !n.dynKeys.isEmpty();
                    if (idx < 0 || (!dyn && idx >= a.args.size())) continue;
                    List<Value> list = n.valuesOf(idx);
                    String argType = dyn ? "Любое значение" : a.args.get(idx).type;
                    for (JsonElement ve : vals.getAsJsonArray(k)) {
                        if (ve.isJsonPrimitive()) list.add(Value.fromLegacy(ve.getAsString(), argType));
                        else if (ve.isJsonObject()) list.add(Value.fromJson(ve.getAsJsonObject()));
                    }
                }
            }
            if (o.has("m")) {
                JsonObject mk = o.getAsJsonObject("m");
                for (String k : mk.keySet()) n.markers.put(Integer.parseInt(k), mk.get(k).getAsString());
            }
            if (o.has("mv")) {
                JsonObject mv = o.getAsJsonObject("mv");
                for (String k : mv.keySet())
                    n.markerVars.put(Integer.parseInt(k),
                            Value.fromJson(mv.getAsJsonObject(k)));
            }
            if (o.has("c") && o.get("c").isJsonObject()) {
                JsonArray one = new JsonArray();
                one.add(o.getAsJsonObject("c"));
                List<Node> read = readChain(one);
                if (!read.isEmpty()) n.cond = read.get(0);
            }
            if (o.has("b")) n.body.addAll(readChain(o.getAsJsonArray("b")));
            if (o.has("r") && o.get("r").isJsonObject()) n.raw = o.getAsJsonObject("r");
            if (n.cond == null && Mapping.loaded() && Mapping.hasConditional(n.action))
                Importer.adoptConditional(n);
            out.add(n);
        }
        return out;
    }
}
