package com.xerocode;

import com.xerocode.ui.McText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Finder {
    public static final int PATH_KEEP = 2;
    public static final int ANY = 0, WRITES = 1, READS = 2;
    private static final int DETAIL_MAX = 220, HAY_MAX = 600, VALUE_DEPTH = 3;
    private static final String VARIABLE_ARG = "Переменная", CHECK_ARG = "провер";
    private static final String[][] VAR_CALLS = {
            {"%var_local(", "local"}, {"%var_save(", "save"}, {"%var_line(", "line"}, {"%var(", "game"}};

    public record Ref(String name, String scope, boolean write) {}

    private interface Pick { String detail(Script.Node n); }

    private static final Pick EVERY = n -> "";

    public static final class Hit {
        public final Script.Root root;
        public final Script.Node node;
        public final int line, depth, blocks;
        public final String title, detail, path, id, category, hay;
        private Search.Fields folded;

        Hit(Script.Root root, Script.Node node, int line, int depth, int blocks,
            String title, String detail, String path, String id, String category, String hay) {
            this.root = root; this.node = node;
            this.line = line; this.depth = depth; this.blocks = blocks;
            this.title = title; this.detail = detail; this.path = path;
            this.id = id; this.category = category; this.hay = hay;
        }

        private Search.Fields folded() {
            if (folded == null) folded = new Search.Fields(Search.foldShort(title), Search.foldShort(id),
                    Search.foldShort(category), Search.fold(hay), Search.foldShort(node.action.sign));
            return folded;
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
            out.add(hit(r, head, i + 1, 0, Script.blocks(r.chain), "", ""));
        }
        return out;
    }

    public static List<Hit> all(Script script) { return collect(script, EVERY); }

    private static List<Hit> collect(Script script, Pick pick) {
        List<Hit> out = new ArrayList<>();
        List<Script.Root> roots = ordered(script);
        for (int i = 0; i < roots.size(); i++)
            walk(out, roots.get(i), roots.get(i).chain, i + 1, 0, new ArrayList<>(), pick);
        return out;
    }

    public static List<Hit> search(List<Hit> pool, String query) {
        return Search.rank(pool, query, pool.size(), Hit::folded, true);
    }

    public static String usageName(Script.Node n) {
        if (n.declares()) return Functions.nameOf(n);
        return n.invokes() ? Functions.targetOf(n) : "";
    }

    public static boolean usageProcess(Script.Node n) {
        return n.declares() ? n.isProcess() : n.isStart();
    }

    public static List<Hit> usages(Script script, String name, boolean process) {
        List<Hit> out = new ArrayList<>();
        if (name == null || name.isBlank()) return out;
        List<Hit> all = collect(script, n -> (n.declares() || n.invokes())
                && usageProcess(n) == process && name.equals(usageName(n)) ? "" : null);
        for (Hit h : all) if (h.node.declares()) out.add(relabel(h, "объявление"));
        for (Hit h : all)
            if (!h.node.declares()) out.add(relabel(h, h.path.isEmpty() ? "вызов" : "вызов · " + h.path));
        return out;
    }

    private static Hit relabel(Hit h, String detail) {
        return new Hit(h.root, h.node, h.line, h.depth, h.blocks, h.title, detail, h.path, h.id,
                h.category, h.hay);
    }

    public static List<Hit> exact(Script script, Match match) {
        if (match.error != null) return new ArrayList<>();
        return collect(script, n -> matches(n, match) ? "" : null);
    }

    public static List<Hit> variables(Script script, Match match, String scope, int where, int access) {
        if (match.error != null) return new ArrayList<>();
        return collect(script, n -> {
            List<Ref> picked = null;
            for (Ref r : refs(n)) {
                if (scope != null && !scope.equals(r.scope())) continue;
                if (access == WRITES && !r.write() || access == READS && r.write()) continue;
                if (!match.at(r.name(), where)) continue;
                if (picked == null) picked = new ArrayList<>();
                picked.add(r);
            }
            return picked == null ? null : describe(picked);
        });
    }

    public static List<Ref> refs(Script.Node n) {
        Set<Ref> out = new LinkedHashSet<>();
        List<Catalog.Arg> args = n.args();
        for (Map.Entry<Integer, List<Value>> e : n.values.entrySet()) {
            int i = e.getKey();
            boolean target = i >= 0 && i < args.size() && writes(args.get(i));
            for (Value v : e.getValue()) collect(v, target, out);
        }
        for (Value v : n.markerVars.values()) collect(v, false, out);
        return new ArrayList<>(out);
    }

    public static boolean writes(Catalog.Arg arg) {
        return VARIABLE_ARG.equals(arg.type) && (arg.purpose == null || !arg.purpose.contains(CHECK_ARG));
    }

    private static void collect(Value v, boolean target, Set<Ref> out) {
        if (v == null) return;
        switch (v.type) {
            case Value.VARIABLE -> {
                if (!v.name.isBlank()) out.add(new Ref(v.name, v.scope, target));
                calls(v.name, out);
            }
            case Value.PARAMETER -> { if (!v.name.isBlank()) out.add(new Ref(v.name, "line", true)); }
            case Value.TEXT -> calls(v.text, out);
            case Value.NUMBER -> calls(v.numberText, out);
            case Value.ITEM -> {
                calls(v.itemName, out);
                for (String line : v.lore) calls(line, out);
            }
            default -> { }
        }
        for (Value it : v.items) collect(it, false, out);
        for (Value k : v.keys) collect(k, false, out);
    }

    private static void calls(String s, Set<Ref> out) {
        if (s == null) return;
        for (int at = s.indexOf("%var"); at >= 0; at = s.indexOf("%var", at + 1)) {
            for (String[] call : VAR_CALLS) {
                if (!s.startsWith(call[0], at)) continue;
                int open = at + call[0].length() - 1, close = closing(s, open);
                if (close < 0) break;
                String name = s.substring(open + 1, close);
                if (!name.isBlank()) out.add(new Ref(name, call[1], false));
                break;
            }
        }
    }

    private static int closing(String s, int open) {
        int depth = 0;
        for (int k = open; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return k;
        }
        return -1;
    }

    private static String describe(List<Ref> refs) {
        StringBuilder sb = new StringBuilder();
        String scope = refs.get(0).scope();
        for (Ref r : refs) {
            if (!r.scope().equals(scope)) scope = null;
            append(sb, (r.write() ? "запись «" : "чтение «") + r.name() + "»", " · ");
        }
        if (scope != null) append(sb, Values.scope(scope).name(), " · ");
        return sb.toString();
    }

    private static boolean matches(Script.Node n, Match m) {
        if (m.in(titleOf(n)) || m.in(n.action.name) || m.in(n.action.sign) || m.in(idOf(n))
                || m.in(categoryOf(n)))
            return true;
        List<Catalog.Setting> settings = n.settings();
        for (int i = 0; i < settings.size(); i++) {
            String chosen = n.marker(i);
            if (chosen != null && !chosen.equals(settings.get(i).def) && m.in(chosen)) return true;
            Value bound = n.markerVar(i);
            if (bound != null && m.in(bound.name)) return true;
        }
        for (List<Value> list : n.values.values())
            for (Value v : list)
                if (v != null && !v.isBlank() && found(v, m)) return true;
        return false;
    }

    private static boolean found(Value v, Match m) {
        if (Value.TEXT.equals(v.type)) {
            if (m.in(McText.plain(v.text, v.parsing)) || m.in(v.text)) return true;
        } else if (m.in(v.label()) || m.in(ids(v))) {
            return true;
        }
        for (Value it : v.items) if (it != null && !it.isBlank() && found(it, m)) return true;
        for (Value k : v.keys) if (k != null && !k.isBlank() && found(k, m)) return true;
        return false;
    }

    private static void walk(List<Hit> out, Script.Root root, List<Script.Node> chain,
                             int line, int depth, List<String> parents, Pick pick) {
        for (Script.Node n : chain) {
            String detail = pick.detail(n);
            if (detail != null) out.add(hit(root, n, line, depth, 1 + Script.blocks(n.body), path(parents), detail));
            if (n.cond != null) {
                String inner = pick.detail(n.cond);
                if (inner != null) {
                    parents.add(titleOf(n));
                    out.add(hit(root, n.cond, line, depth + 1, 1, path(parents), inner));
                    parents.remove(parents.size() - 1);
                }
            }
            if (n.body.isEmpty()) continue;
            parents.add(titleOf(n));
            walk(out, root, n.body, line, depth + 1, parents, pick);
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
                           String path, String detail) {
        String summary = summary(node);
        return new Hit(root, node, line, depth, blocks, titleOf(node),
                detail.isEmpty() ? summary : detail, path, idOf(node), categoryOf(node), hay(node, summary));
    }

    private static String categoryOf(Script.Node node) {
        Catalog.Category c = node.action.category;
        return c == null ? "" : c.name + (node.action.subcategory == null ? "" : " " + node.action.subcategory);
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

    private static String hay(Script.Node n, String summary) {
        StringBuilder sb = new StringBuilder(summary);
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
            case Value.CELL -> v.storagePath;
            default -> "";
        };
    }

    private static String selector(Value v) {
        return v.targetless() || "default".equals(v.selection) ? "" : Values.selectorName(v.selection);
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
