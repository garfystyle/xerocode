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
import java.util.Locale;
import java.util.Map;

public final class Symbols {
    public record Sym(String glyph, String name, String alias, boolean drawable) {}

    private static final List<String> CATEGORIES = new ArrayList<>();
    private static final List<List<Sym>> GROUPS = new ArrayList<>();
    private static final List<Sym> ALL = new ArrayList<>();
    private static final Map<String, Sym> BY_GLYPH = new LinkedHashMap<>();
    private static int drawable;
    private static boolean loaded;

    public static boolean onlyDrawable() { return Settings.get().drawableOnly; }

    public static void flipDrawable() {
        Settings s = Settings.get();
        s.drawableOnly = !s.drawableOnly;
        s.save();
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        CATEGORIES.add("★");
        GROUPS.add(List.of());
        try (InputStream in = Symbols.class.getResourceAsStream("/assets/xerocode/symbols.json")) {
            if (in == null) {
                XeroCode.LOG.error("[xerocode] symbols.json not found in the jar");
                return;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            Map<String, String> names = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("names").entrySet())
                names.put(e.getKey(), e.getValue().getAsString());

            for (JsonElement e : root.getAsJsonArray("cats")) {
                JsonObject o = e.getAsJsonObject();
                List<Sym> group = new ArrayList<>();
                if (o.has("s")) {
                    split(o.get("s").getAsString(), true, names, group);
                    split(o.get("x").getAsString(), false, names, group);
                } else {
                    for (JsonElement m : o.getAsJsonArray("my")) add(group, m.getAsString(), true, names);
                    for (JsonElement m : o.getAsJsonArray("mx")) add(group, m.getAsString(), false, names);
                }
                CATEGORIES.add(o.get("n").getAsString());
                GROUPS.add(group);
            }
        } catch (Exception e) {
            XeroCode.LOG.error("[xerocode] не удалось прочитать symbols.json", e);
        }
    }

    private static void split(String run, boolean draws, Map<String, String> names, List<Sym> out) {
        int i = 0;
        while (i < run.length()) {
            int cp = run.codePointAt(i);
            int n = Character.charCount(cp);
            add(out, run.substring(i, i + n), draws, names);
            i += n;
        }
    }

    private static void add(List<Sym> out, String glyph, boolean draws, Map<String, String> names) {
        Sym known = BY_GLYPH.get(glyph);
        if (known != null) {
            out.add(known);
            return;
        }
        String unicode = unicodeName(glyph);
        String own = names.get(glyph);
        Sym s = new Sym(glyph, own != null ? own : unicode.isEmpty() ? glyph : unicode,
                own != null ? unicode : "", draws);
        BY_GLYPH.put(glyph, s);
        ALL.add(s);
        out.add(s);
        if (draws) drawable++;
    }

    private static String unicodeName(String glyph) {
        if (glyph.codePointCount(0, glyph.length()) > 1) return "";
        try {
            String unicode = Character.getName(glyph.codePointAt(0));
            return unicode == null ? "" : unicode.toLowerCase(Locale.ROOT);
        } catch (Throwable e) {
            return "";
        }
    }

    public static List<String> categories() {
        load();
        return CATEGORIES;
    }

    public static int total() {
        load();
        return ALL.size();
    }

    public static int drawable() {
        load();
        return drawable;
    }

    public static List<String> favourites() { return Settings.get().symbols; }

    public static boolean favourite(String glyph) { return favourites().contains(glyph); }

    public static void toggle(String glyph) {
        List<String> fav = favourites();
        if (!fav.remove(glyph)) fav.add(0, glyph);
        while (fav.size() > 64) fav.remove(fav.size() - 1);
        Settings.get().save();
    }

    public static List<Sym> group(int index) {
        load();
        if (index < 0 || index >= GROUPS.size()) return List.of();
        if (index == 0) {
            List<Sym> out = new ArrayList<>();
            for (String glyph : favourites()) {
                Sym s = BY_GLYPH.get(glyph);
                out.add(s == null ? new Sym(glyph, glyph, "", true) : s);
            }
            return out;
        }
        return filter(GROUPS.get(index));
    }

    private static List<Sym> filter(List<Sym> pool) {
        if (!onlyDrawable()) return pool;
        List<Sym> out = new ArrayList<>(pool.size());
        for (Sym s : pool) if (s.drawable()) out.add(s);
        return out;
    }

    public static List<Sym> search(String query) {
        load();
        String q = query.trim();
        if (q.isEmpty()) return List.of();
        List<Sym> out = new ArrayList<>();
        Sym exact = BY_GLYPH.get(q);
        if (exact != null) out.add(exact);
        for (Sym s : Search.rank(filter(ALL), q, 512,
                s -> new Search.Fields(s.name(), code(s.glyph()), "", s.alias())))
            if (s != exact) out.add(s);
        return out;
    }

    public static String code(String glyph) {
        return String.format(Locale.ROOT, "u+%04x", glyph.codePointAt(0));
    }

    private Symbols() {}
}
