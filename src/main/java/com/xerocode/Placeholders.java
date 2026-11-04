package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Placeholders {
    public record Item(String insert, String category, String description,
                       boolean numeric) {
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
    private static final List<Item> NUMBERS = new ArrayList<>();
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    public static boolean loaded() { return !ALL.isEmpty(); }

    public static void load() {
        if (loaded()) return;
        try {
            JsonObject root = Json.asset(Placeholders.class, "placeholders.json");
            if (root == null) return;
            for (JsonElement ce : root.getAsJsonArray("cats")) {
                JsonObject c = ce.getAsJsonObject();
                CATEGORIES.put(c.get("id").getAsString(), c.get("n").getAsString());
            }
            for (JsonElement ie : root.getAsJsonArray("items")) {
                JsonObject o = ie.getAsJsonObject();
                Item it = new Item(o.get("p").getAsString(), o.get("c").getAsString(),
                        o.get("d").getAsString(), o.has("num"));
                ALL.add(it);
                if (!it.insert().startsWith("%")) continue;
                PERCENT.add(it);
                if (it.numeric()) NUMBERS.add(it);
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] failed to read placeholders.json", e);
        }
    }

    public static String categoryName(String id) { return CATEGORIES.getOrDefault(id, id); }

    public static List<Item> match(String query, int limit, boolean numbersOnly) {
        List<Item> from = numbersOnly ? NUMBERS : PERCENT;
        if (query.isBlank())
            return new ArrayList<>(from.subList(0, Math.min(limit, from.size())));
        return Search.rank(from, query, limit, it -> new Search.Fields(
                it.name(), "", categoryName(it.category()), it.description()));
    }

    public static String strip(String s) {
        if (s == null || s.indexOf('%') < 0) return s == null ? "" : s;
        int n = s.length();
        StringBuilder out = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            int end = i + 1;
            while (c == '%' && end < n && word(s.charAt(end))) end++;
            if (c != '%' || end == i + 1) { out.append(c); i++; continue; }
            if (end == n) break;
            char next = s.charAt(end);
            if (next == '%') i = end + 1;
            else if (next == '(') i = closed(s, end);
            else { out.append(s, i, end); i = end; }
        }
        return out.toString();
    }

    private static int closed(String s, int open) {
        int depth = 0;
        for (int k = open; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return k + 1;
        }
        return s.length();
    }

    private static boolean word(char c) { return c == '_' || Character.isLetterOrDigit(c); }

    private Placeholders() {}
}
