package com.xerocode;

import com.xerocode.ui.McText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Finder {
    public static final int PATH_KEEP = 2;
    private static final int DETAIL_MAX = 220, HAY_MAX = 600, VALUE_DEPTH = 3;

    public static final class Hit {
        public final Script.Root root;
        public final Script.Node node;
        public final int line, depth, blocks;
        public final String title, detail, path, id, category, hay;

        Hit(Script.Root root, Script.Node node, int line, int depth, int blocks,
            String title, String detail, String path, String id, String category, String hay) {
            this.root = root; this.node = node;
            this.line = line; this.depth = depth; this.blocks = blocks;
            this.title = title; this.detail = detail; this.path = path;
            this.id = id; this.category = category; this.hay = hay;
        }

        public int color() {
            Catalog.Category c = node.action.category;
            return c == null ? 0x8A93A6 : c.color;
        }

        public String kind() {
            Catalog.Category c = node.action.category;
            return c == null ? "" : c.name;
        }
    }

    public static List<Script.Root> ordered(Script script) {
        List<Script.Root> roots = new ArrayList<>(script.roots);
        roots.sort(Comparator.<Script.Root>comparingDouble(r -> r.x).thenComparingDouble(r -> r.y));
        return roots;
    }

    public static List<Hit> outline(Script script) {
        List<Hit> out = new ArrayList<>();
        List<Script.Root> roots = ordered(script);
        for (int i = 0; i < roots.size(); i++) {
            Script.Root r = roots.get(i);
            if (r.chain.isEmpty()) continue;
            Script.Node head = r.chain.get(0);
            out.add(hit(r, head, i + 1, 0, Script.blocks(r.chain), ""));
        }
        return out;
    }

    public static List<Hit> all(Script script) {
        List<Hit> out = new ArrayList<>();
        List<Script.Root> roots = ordered(script);
        for (int i = 0; i < roots.size(); i++)
            walk(out, roots.get(i), roots.get(i).chain, i + 1, 0, new ArrayList<>());
        return out;
    }

    public static List<Hit> search(Script script, String query) {
        List<Hit> pool = all(script);
        return Search.rank(pool, query, pool.size(),
                h -> new Search.Fields(h.title, h.id, h.category, h.hay));
    }

    private static void walk(List<Hit> out, Script.Root root, List<Script.Node> chain,
                             int line, int depth, List<String> parents) {
        for (Script.Node n : chain) {
            out.add(hit(root, n, line, depth, 1 + Script.blocks(n.body), path(parents)));
            if (n.cond != null) {
                parents.add(titleOf(n));
                out.add(hit(root, n.cond, line, depth + 1, 1, path(parents)));
                parents.remove(parents.size() - 1);
            }
            if (n.body.isEmpty()) continue;
            parents.add(titleOf(n));
            walk(out, root, n.body, line, depth + 1, parents);
            parents.remove(parents.size() - 1);
        }
    }

    private static String path(List<String> parents) {
        if (parents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, parents.size() - PATH_KEEP); i < parents.size(); i++) {
            if (sb.length() > 0) sb.append(" › ");
            sb.append(parents.get(i));
        }
        return sb.toString();
    }

    private static Hit hit(Script.Root root, Script.Node node, int line, int depth, int blocks,
                           String path) {
        Catalog.Category c = node.action.category;
        String category = c == null ? "" : c.name
                + (node.action.subcategory == null ? "" : " " + node.action.subcategory);
        return new Hit(root, node, line, depth, blocks, titleOf(node), summary(node), path,
                idOf(node), category, hay(node));
    }

    public static String titleOf(Script.Node n) {
        String name = n.action.name;
        if (n.declares()) {
            String own = Functions.nameOf(n);
            if (!own.isBlank()) name = own;
        } else if (n.invokes()) {
            String target = Functions.targetOf(n);
            if (!target.isBlank()) name = target;
        }
        return n.inverted() ? "НЕ " + name : name;
    }

    private static String idOf(Script.Node n) {
        String id = n.isHat() ? Mapping.eventId(n.action) : Mapping.actionId(n.action);
        return id == null ? "" : id;
    }

    public static String summary(Script.Node n) {
        StringBuilder sb = new StringBuilder();
        List<Catalog.Arg> args = n.args();
        for (int i = 0; i < args.size(); i++) {
            List<Value> list = n.values.get(i);
            if (list == null) continue;
            for (Value v : list) {
                if (v == null || v.isBlank()) continue;
                append(sb, plain(v, VALUE_DEPTH), " · ");
                if (sb.length() >= DETAIL_MAX) return sb.substring(0, DETAIL_MAX);
            }
        }
        return sb.toString();
    }

    private static String hay(Script.Node n) {
        StringBuilder sb = new StringBuilder(summary(n));
        List<Catalog.Setting> settings = n.settings();
        for (int i = 0; i < settings.size(); i++) {
            Catalog.Setting s = settings.get(i);
            String chosen = n.marker(i);
            if (chosen == null || chosen.equals(s.def)) continue;
            append(sb, chosen, " ");
        }
        for (int i = 0; i < settings.size(); i++) {
            Value bound = n.markerVar(i);
            if (bound != null) append(sb, bound.name, " ");
        }
        List<Catalog.Arg> args = n.args();
        for (int i = 0; i < args.size() && sb.length() < HAY_MAX; i++) {
            List<Value> list = n.values.get(i);
            if (list == null) continue;
            for (Value v : list) {
                if (v == null || v.isBlank()) continue;
                append(sb, ids(v), " ");
            }
        }
        return sb.length() > HAY_MAX ? sb.substring(0, HAY_MAX) : sb.toString();
    }

    private static void append(StringBuilder sb, String part, String glue) {
        if (part == null || part.isBlank()) return;
        if (sb.length() > 0) sb.append(glue);
        sb.append(part);
    }

    private static String ids(Value v) {
        return switch (v.type) {
            case Value.VARIABLE -> Values.scope(v.scope).name();
            case Value.GAME_VALUE -> v.gameValue + " " + selector(v);
            case Value.SOUND -> v.sound;
            case Value.PARTICLE -> v.particle;
            case Value.POTION -> v.potion;
            case Value.ITEM -> v.itemId + " " + McText.plain(v.itemName, v.itemParsing);
            case Value.BLOCK -> v.block;
            default -> "";
        };
    }

    private static String selector(Value v) {
        return "default".equals(v.selection) ? "" : Values.selectorName(v.selection);
    }

    private static String plain(Value v, int depth) {
        if (Value.TEXT.equals(v.type)) {
            String s = McText.plain(v.text, v.parsing);
            return s.isEmpty() ? v.label() : s;
        }
        if (depth <= 0) return v.label();
        if (Value.ARRAY.equals(v.type)) return "[" + inner(v.items, depth) + "]";
        if (Value.MAP.equals(v.type)) return "{" + pairs(v, depth) + "}";
        return v.label();
    }

    private static String inner(List<Value> items, int depth) {
        StringBuilder sb = new StringBuilder();
        for (Value it : items) {
            if (it == null || it.isBlank()) continue;
            append(sb, plain(it, depth - 1), ", ");
            if (sb.length() >= DETAIL_MAX) break;
        }
        return sb.toString();
    }

    private static String pairs(Value map, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < map.keys.size(); i++) {
            Value k = map.keys.get(i);
            Value val = i < map.items.size() ? map.items.get(i) : null;
            if ((k == null || k.isBlank()) && (val == null || val.isBlank())) continue;
            String left = k == null || k.isBlank() ? "" : plain(k, depth - 1);
            String right = val == null || val.isBlank() ? "" : plain(val, depth - 1);
            append(sb, left + ": " + right, ", ");
            if (sb.length() >= DETAIL_MAX) break;
        }
        return sb.toString();
    }

    private Finder() {}
}
