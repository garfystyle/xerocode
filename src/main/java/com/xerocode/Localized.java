package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Locale;

public final class Localized {
    public static final String TYPE = "localized_text";
    public static final String EMPTY = "{\"translations\":{}}";
    private static final String DEFAULT_PARSING = "legacy";

    public record Lang(String id, String name) {}

    public static final List<Lang> LANGS = List.of(
            new Lang("", "По умолчанию"),
            new Lang("en-US", "Английский (en_US)"),
            new Lang("ru-RU", "Русский (ru_RU)"),
            new Lang("ua-UA", "Украинский (ua_UA)"));

    public record Joined(String text, String parsing) {}

    public static Joined join(List<Value> lines) {
        StringBuilder sb = new StringBuilder();
        String parsing = DEFAULT_PARSING;
        if (lines != null)
            for (Value v : lines) {
                if (!Value.TEXT.equals(v.type)) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(v.text);
                if (!v.text.isEmpty()) parsing = v.parsing;
            }
        return new Joined(sb.toString(), parsing);
    }

    private static String tag(String id) { return id == null ? "" : id.replace('_', '-'); }

    public static JsonObject normalize(JsonObject o) {
        JsonObject out = new JsonObject();
        if (o == null) return out;
        for (String key : o.keySet()) out.add(tag(key), o.get(key));
        return out;
    }

    public static JsonObject box(String data) {
        if (data == null || data.isBlank()) return new JsonObject();
        try {
            JsonElement e = JsonParser.parseString(data);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    public static JsonObject translations(String data) {
        JsonObject box = box(data);
        return box.has("translations") && box.get("translations").isJsonObject()
                ? normalize(box.getAsJsonObject("translations")) : new JsonObject();
    }

    public static String text(String data) { return entryText(fallback(box(data))); }

    public static String parsing(String data) { return entryParsing(fallback(box(data))); }

    public static JsonObject entry(String text, String parsing) {
        JsonObject o = new JsonObject();
        o.addProperty("rawText", text);
        o.addProperty("parsingType", (parsing == null || parsing.isBlank()
                ? DEFAULT_PARSING : parsing).toUpperCase(Locale.ROOT));
        return o;
    }

    public static String entryText(JsonElement e) {
        if (e == null || !e.isJsonObject()) return "";
        JsonObject o = e.getAsJsonObject();
        return o.has("rawText") ? o.get("rawText").getAsString() : "";
    }

    public static String entryParsing(JsonElement e) {
        if (e == null || !e.isJsonObject()) return DEFAULT_PARSING;
        JsonObject o = e.getAsJsonObject();
        String raw = o.has("parsingType") ? o.get("parsingType").getAsString() : "";
        return raw.isBlank() ? DEFAULT_PARSING : raw.toLowerCase(Locale.ROOT);
    }

    private static JsonObject fallback(JsonObject box) {
        return box.has("fallback") && box.get("fallback").isJsonObject()
                ? box.getAsJsonObject("fallback") : new JsonObject();
    }

    public static String data(String text, String parsing, JsonObject translations) {
        JsonObject box = new JsonObject();
        box.add("translations", translations == null ? new JsonObject() : translations);
        if (!text.isEmpty()) box.add("fallback", entry(text, parsing));
        return box.toString();
    }

    public static boolean blank(String data) {
        JsonObject box = box(data);
        if (!text(data).isEmpty()) return false;
        return !box.has("translations") || !box.get("translations").isJsonObject()
                || box.getAsJsonObject("translations").isEmpty();
    }

    public static JsonObject value(String data) {
        JsonObject o = new JsonObject();
        o.addProperty("type", TYPE);
        o.addProperty("data", data);
        return o;
    }

    private Localized() {}
}
