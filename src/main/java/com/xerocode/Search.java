package com.xerocode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class Search {
    public record Fields(String name, String id, String category, String description, String alias) {
        public Fields(String name, String id, String category, String description) {
            this(name, id, category, description, "");
        }

        public static Fields of(String name, String id) { return new Fields(name, id, "", ""); }
    }

    private static final int NAME_PREFIX = 120, ALIAS_PREFIX = 100, NAME_WORD = 90, ALIAS_WORD = 80,
            NAME_PART = 55, ALIAS_PART = 45, ID_PART = 40, CATEGORY = 20, DESCRIPTION = 10;
    private static final int SHORT_MAX = 20000;
    private static final Map<String, String> SHORT = new HashMap<>();

    public static String fold(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    public static String foldShort(String s) {
        if (s == null) return "";
        String done = SHORT.get(s);
        if (done != null) return done;
        if (SHORT.size() >= SHORT_MAX) SHORT.clear();
        done = fold(s);
        SHORT.put(s, done);
        return done;
    }

    public static <T> List<T> rank(Collection<T> pool, String query, int limit,
                                   Function<T, Fields> fields) {
        return rank(pool, query, limit, item -> {
            Fields f = fields.apply(item);
            return new Fields(fold(f.name()), fold(f.id()), fold(f.category()), fold(f.description()),
                    fold(f.alias()));
        }, true);
    }

    public static <T> List<T> rank(Collection<T> pool, String query, int limit,
                                   Function<T, Fields> folded, boolean ready) {
        String q = fold(query.trim());
        if (q.isEmpty() || limit <= 0) return new ArrayList<>();
        String[] tokens = q.split("\\s+");

        record Hit<T>(T item, int score) {}
        List<Hit<T>> hits = new ArrayList<>();
        for (T item : pool) {
            Fields f = folded.apply(item);
            String name = f.name();
            String flat = name.replace('.', ' ').replace('_', ' ');
            String alias = f.alias();
            String id = f.id();
            String category = f.category();
            String description = f.description();
            int score = 0;
            boolean all = true;
            for (String t : tokens) {
                int s;
                if (name.startsWith(t)) s = NAME_PREFIX;
                else if (!alias.isEmpty() && alias.startsWith(t)) s = ALIAS_PREFIX;
                else if (flat.contains(" " + t)) s = NAME_WORD;
                else if (!alias.isEmpty() && alias.contains(" " + t)) s = ALIAS_WORD;
                else if (name.contains(t)) s = NAME_PART;
                else if (!alias.isEmpty() && alias.contains(t)) s = ALIAS_PART;
                else if (!id.isEmpty() && id.contains(t)) s = ID_PART;
                else if (!category.isEmpty() && category.contains(t)) s = CATEGORY;
                else if (!description.isEmpty() && description.contains(t)) s = DESCRIPTION;
                else { all = false; break; }
                score += s;
            }
            if (all) hits.add(new Hit<>(item, score - Math.min(20, name.length() / 4)));
        }
        hits.sort((a, b) -> b.score() - a.score());

        List<T> out = new ArrayList<>(Math.min(limit, hits.size()));
        for (Hit<T> h : hits) {
            if (out.size() >= limit) break;
            out.add(h.item());
        }
        return out;
    }

    private Search() {}
}
