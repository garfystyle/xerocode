package com.xerocode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class Search {
    public record Fields(String name, String id, String category, String description) {
        public static Fields of(String name) { return new Fields(name, "", "", ""); }
        public static Fields of(String name, String id) { return new Fields(name, id, "", ""); }
    }

    private static final int NAME_PREFIX = 120, NAME_WORD = 90, NAME_PART = 55,
            ID_PART = 40, CATEGORY = 20, DESCRIPTION = 10;

    public static <T> List<T> rank(Collection<T> pool, String query, int limit,
                                   Function<T, Fields> fields) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || limit <= 0) return new ArrayList<>();
        String[] tokens = q.split("\\s+");

        record Hit<T>(T item, int score) {}
        List<Hit<T>> hits = new ArrayList<>();
        for (T item : pool) {
            Fields f = fields.apply(item);
            String name = f.name().toLowerCase(Locale.ROOT);
            String flat = name.replace('.', ' ').replace('_', ' ');
            String id = f.id().toLowerCase(Locale.ROOT);
            String category = f.category().toLowerCase(Locale.ROOT);
            String description = f.description().toLowerCase(Locale.ROOT);
            int score = 0;
            boolean all = true;
            for (String t : tokens) {
                int s;
                if (name.startsWith(t)) s = NAME_PREFIX;
                else if (flat.contains(" " + t)) s = NAME_WORD;
                else if (name.contains(t)) s = NAME_PART;
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
