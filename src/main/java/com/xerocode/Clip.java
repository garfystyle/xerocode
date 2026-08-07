package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public final class Clip {
    private static final String MARK = "xerocode";

    private static List<Script.Node> held = new ArrayList<>();
    private static JsonArray heldRoots, heldValues;

    private static void forget() {
        held = new ArrayList<>();
        heldRoots = null;
        heldValues = null;
    }

    private static void publish(String key, JsonArray body) {
        JsonObject root = new JsonObject();
        root.addProperty(MARK, 1);
        root.add(key, body);
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

    private static JsonArray taken(String key, JsonArray fallback) {
        JsonObject o = readMark();
        if (o == null) return fallback;
        return Json.arr(o, key);
    }

    public static List<Value> pasteValues() {
        JsonArray arr = taken("values", heldValues);
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
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.keyboard != null) client.keyboard.setClipboard(text);
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] буфер обмена недоступен", e);
        }
    }

    private static String read() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client == null || client.keyboard == null ? "" : client.keyboard.getClipboard();
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
