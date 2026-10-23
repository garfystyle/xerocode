package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Components {
    public record Info(String name, String about, String example, List<String> fields) {}

    private static final Map<String, Info> ALL = new HashMap<>();
    private static boolean loaded;

    private Components() {}

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            JsonObject root = Json.asset(Components.class, "components.json");
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                List<String> fields = new ArrayList<>();
                if (o.has("f")) for (JsonElement f : o.getAsJsonArray("f")) fields.add(f.getAsString());
                ALL.put(e.getKey(), new Info(Json.str(o, "n"), Json.str(o, "d"), Json.str(o, "e"),
                        List.copyOf(fields)));
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] components.json не прочитан", e);
        }
    }

    public static Info of(String id) {
        load();
        String key = id == null ? "" : id.trim();
        if (key.startsWith("!")) key = key.substring(1);
        if (!key.isEmpty() && key.indexOf(':') < 0) key = "minecraft:" + key;
        return ALL.get(key);
    }

    public static String name(String id) {
        Info info = of(id);
        return info == null ? "" : info.name();
    }
}
