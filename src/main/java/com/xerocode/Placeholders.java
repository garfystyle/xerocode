package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Placeholders {
    public record Item(String insert, String category, String description) {
        public boolean call() { return insert.endsWith("()"); }

        public String name() {
            String s = insert.startsWith("%") ? insert.substring(1) : insert;
            if (s.endsWith("()")) return s.substring(0, s.length() - 2);
            if (s.endsWith("%")) return s.substring(0, s.length() - 1);
            return s;
        }

        public int caret() { return call() ? insert.length() - 1 : insert.length(); }
    }

    public static final List<Item> ALL = new ArrayList<>();
    private static final List<Item> PERCENT = new ArrayList<>();
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    public static boolean loaded() { return !ALL.isEmpty(); }

    public static void load() {
        if (loaded()) return;
        try (InputStream in = Placeholders.class.getResourceAsStream(
                "/assets/xerocode/placeholders.json")) {
            if (in == null) {
                XeroCode.LOG.error("[xerocode] placeholders.json not found in the jar");
                return;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement ce : root.getAsJsonArray("cats")) {
                JsonObject c = ce.getAsJsonObject();
                CATEGORIES.put(c.get("id").getAsString(), c.get("n").getAsString());
            }
            for (JsonElement ie : root.getAsJsonArray("items")) {
                JsonObject o = ie.getAsJsonObject();
                Item it = new Item(o.get("p").getAsString(), o.get("c").getAsString(),
                        o.get("d").getAsString());
                ALL.add(it);
                if (it.insert().startsWith("%")) PERCENT.add(it);
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] failed to read placeholders.json", e);
        }
    }

    public static String categoryName(String id) { return CATEGORIES.getOrDefault(id, id); }

    public static List<Item> match(String query, int limit) {
        if (query.isBlank())
            return new ArrayList<>(PERCENT.subList(0, Math.min(limit, PERCENT.size())));
        return Search.rank(PERCENT, query, limit, it -> new Search.Fields(
                it.name(), "", categoryName(it.category()), it.description()));
    }

    private Placeholders() {}
}
