package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class Clip {
    private static final String MARK = "xerocode";

    private static final String SLOTS = "slots";

    private static List<Script.Node> held = new ArrayList<>();
    private static JsonArray heldRoots, heldValues, heldSlots;

    public record Placed(int slot, Value value) {}

    private static void forget() {
        held = new ArrayList<>();
        heldRoots = null;
        heldValues = null;
        heldSlots = null;
    }

    private static void publish(String key, JsonArray body) {
        publish(key, body, null);
    }

    private static void publish(String key, JsonArray body, JsonArray slots) {
        JsonObject root = new JsonObject();
        root.addProperty(MARK, 1);
        root.add(key, body);
        if (slots != null) root.add(SLOTS, slots);
        write(root.toString());
    }

    public static void copy(List<Script.Node> chain) {
        forget();
        for (Script.Node n : chain) held.add(n.copy());
        if (held.isEmpty()) return;
        publish("chain", Script.writeChain(held));
    }

    public static void copyRoots(List<Script.Root> roots) {
        forget();
        JsonArray arr = new JsonArray();
        for (Script.Root r : roots) {
            if (r.chain.isEmpty()) continue;
            JsonObject ro = new JsonObject();
            ro.addProperty("x", r.x);
            ro.addProperty("y", r.y);
            ro.add("chain", Script.writeChain(r.chain));
            arr.add(ro);
        }
        if (arr.isEmpty()) return;
        heldRoots = arr;
        publish("roots", arr);
    }

    public static void copyValues(List<Value> values) {
        forget();
        JsonArray arr = new JsonArray();
        for (Value v : values) if (!v.isBlank()) arr.add(v.toJson());
        if (arr.isEmpty()) return;
        heldValues = arr;
        publish("values", arr);
    }

    public static void copySlots(List<Value> cells) {
        forget();
        JsonArray arr = new JsonArray(), at = new JsonArray();
        for (int i = 0; i < cells.size(); i++) {
            Value v = cells.get(i);
            if (v == null || v.isBlank()) continue;
            arr.add(v.toJson());
            at.add(i);
        }
        if (arr.isEmpty()) return;
        heldValues = arr;
        heldSlots = at;
        publish("values", arr, at);
    }

    public static List<Placed> pasteSlots() {
        JsonObject o = readMark();
        if (o == null) return placed(heldValues, heldSlots);
        return placed(Json.arr(o, "values"), Json.arr(o, SLOTS));
    }

    public static List<Placed> boardSlots() {
        JsonObject o = readMark();
        return o == null ? List.of() : placed(Json.arr(o, "values"), Json.arr(o, SLOTS));
    }

    private static List<Placed> placed(JsonArray values, JsonArray slots) {
        if (values == null || slots == null || values.size() != slots.size()) return List.of();
        List<Placed> out = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            try {
                int slot = slots.get(i).getAsInt();
                if (slot >= 0 && values.get(i).isJsonObject())
                    out.add(new Placed(slot, Value.fromJson(values.get(i).getAsJsonObject())));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static JsonArray taken(String key, JsonArray fallback) {
        JsonObject o = readMark();
        if (o == null) return fallback;
        return Json.arr(o, key);
    }

    public static List<Value> pasteValues() {
        return values(taken("values", heldValues));
    }

    public static List<Value> boardValues() {
        JsonObject o = readMark();
        return o == null || !o.has("values") ? List.of() : values(Json.arr(o, "values"));
    }

    private static List<Value> values(JsonArray arr) {
        if (arr == null) return List.of();
        List<Value> out = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            try {
                if (e.isJsonObject()) out.add(Value.fromJson(e.getAsJsonObject()));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    public static boolean ours() {
        return readMark() != null;
    }

    public static boolean has() {
        if (!held.isEmpty() || heldRoots != null) return true;
        JsonObject o = readMark();
        return o != null && (o.has("chain") || o.has("roots"));
    }

    public static List<Script.Node> paste() {
        List<Script.Node> system = readSystem();
        List<Script.Node> source = system.isEmpty() ? held : system;
        List<Script.Node> out = new ArrayList<>(source.size());
        for (Script.Node n : source) out.add(n.copy());
        return out;
    }

    public static List<Script.Root> pasteRoots() {
        JsonArray arr = taken("roots", heldRoots);
        if (arr == null) return List.of();
        List<Script.Root> out = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            try {
                JsonObject ro = e.getAsJsonObject();
                Script.Root r = new Script.Root(ro.get("x").getAsDouble(),
                        ro.get("y").getAsDouble());
                r.chain.addAll(Script.readChain(ro.getAsJsonArray("chain")));
                if (!r.chain.isEmpty()) out.add(r);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static void write(String text) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.keyboardHandler != null) client.keyboardHandler.setClipboard(text);
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] буфер обмена недоступен", e);
        }
    }

    private static String read() {
        try {
            Minecraft client = Minecraft.getInstance();
            return client == null || client.keyboardHandler == null ? "" : client.keyboardHandler.getClipboard();
        } catch (Throwable e) {
            return "";
        }
    }

    private static JsonObject readMark() {
        String text = read();
        if (text == null || !text.contains("\"" + MARK + "\"")) return null;
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            return root.has(MARK) ? root : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static List<Script.Node> readSystem() {
        JsonObject root = readMark();
        if (root == null || !root.has("chain")) return List.of();
        try {
            return Script.readChain(root.getAsJsonArray("chain"));
        } catch (Throwable e) {
            return List.of();
        }
    }

    private Clip() {}
}
