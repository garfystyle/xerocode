package com.xerocode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class Rename {
    public static final String GAME = "game", SAVE = "save", LOCAL = "local", LINE = "line";
    private static final List<String> NEAREST = List.of(LINE, LOCAL, GAME, SAVE);
    private static final List<String> FAMILIES = List.of("var", "length", "index", "entry");
    private static final Set<String> PAIRED = Set.of("index", "entry");
    private static final String MATH = "math", DONT_COPY = "Не дублировать";
    private static final String LINE_MODE = "line_variables_mode";
    private static final Set<String> SHARED_MODES = Set.of("copy", "share");
    private static final Set<String> MATH_WORDS = Set.of("abs", "sqrt", "cbrt", "ceil", "floor", "sin",
            "cos", "round", "pow", "min", "max", "sign", "atan2", "root", "clamp", "wrap", "sinr", "cosr",
            "asin", "acos", "pi", "e");

    private record Named(String category, String action, String arg, String scope, String mode,
                         String exact, String caseLabel, String caseOn) {}

    private static final List<Named> NAMED = List.of(
            new Named("Действие с переменной", "Очистить переменные (по именам)", "Имена для сравнения",
                    "Тип переменной", "Режим сравнения", "Полное соответствие",
                    "Игнорирование регистра", "Включено"),
            new Named("Действие с переменной", "Получить имена переменных", "Имена переменных",
                    "Тип переменных", "Вид сравнения", "Точное соответствие",
                    "Игнорировать регистр", "Да"));

    private static final Map<String, String> SCOPE_WORDS = Map.of(
            "Игровая", GAME, "Игровые", GAME, "Сохранённая", SAVE, "Сохранённые", SAVE,
            "Локальная", LOCAL, "Локальные", LOCAL, "Строчная", LINE, "Строчные", LINE);

    public record Target(String name, String scope) {}

    public record Result(String from, String to, String scope, int places, int lines, boolean clash,
                         boolean keptLine) {}

    public static final class Plan {
        public final String name, scope;
        public final int places, lines;
        public final boolean param;
        private final Script script;
        private final Set<Script.Root> roots;
        private final Names names;

        private Plan(Script script, Set<Script.Root> roots, Names names, String name, String scope) {
            this.script = script;
            this.roots = roots;
            this.names = names;
            this.name = name;
            this.scope = scope;
            this.param = LINE.equals(scope) && declaresParam(roots, name);
            Pass dry = new Pass(this, name, scope, false);
            dry.run();
            this.places = dry.count;
            this.lines = dry.lines;
        }

        private boolean shadowed(Script.Root r, String name, String scope, boolean renamed) {
            for (String s : NEAREST) {
                if (s.equals(scope)) return false;
                if (renamed && s.equals(this.scope) && name.equals(this.name)) continue;
                if (names.at(r, s).contains(name)) return true;
            }
            return false;
        }

        private boolean clash(String to, String toScope) {
            if (to.equals(name) && toScope.equals(scope)) return false;
            for (Script.Root r : roots) if (names.at(r, toScope).contains(to)) return true;
            return false;
        }
    }

    public static Target target(List<Value> values) {
        for (Value v : values) {
            Target t = target(v);
            if (t != null) return t;
        }
        return null;
    }

    private static Target target(Value v) {
        if (v == null) return null;
        switch (v.type) {
            case Value.VARIABLE -> { if (!v.name.isBlank()) return new Target(v.name, v.scope); }
            case Value.PARAMETER -> { if (!v.name.isBlank()) return new Target(v.name, LINE); }
            case Value.TEXT -> { Target t = mentioned(v.text); if (t != null) return t; }
            case Value.NUMBER -> { Target t = mentioned(v.numberText); if (t != null) return t; }
            default -> { }
        }
        for (Value it : v.items) {
            Target t = target(it);
            if (t != null) return t;
        }
        for (Value k : v.keys) {
            Target t = target(k);
            if (t != null) return t;
        }
        return null;
    }

    private static Target mentioned(String s) {
        if (s == null) return null;
        for (int at = s.indexOf('%'); at >= 0; at = s.indexOf('%', at + 1)) {
            int open = callOpen(s, at);
            if (open < 0) continue;
            int close = closing(s, open);
            if (close < 0) continue;
            String fn = s.substring(at + 1, open), inner = s.substring(open + 1, close);
            String scope = familyScope(fn);
            if (scope == null) continue;
            int comma = PAIRED.contains(family(fn)) ? topComma(inner) : -1;
            String name = comma < 0 ? inner : inner.substring(0, comma);
            if (!name.isBlank() && name.indexOf('%') < 0) return new Target(name, scope);
        }
        return null;
    }

    public static Plan plan(Script script, Script.Root at, String name, String scope) {
        Links links = new Links(script);
        Set<Script.Root> roots = switch (scope) {
            case LOCAL -> group(at, links.local);
            case LINE -> group(at, links.line);
            default -> new LinkedHashSet<>(script.roots);
        };
        return new Plan(script, roots, new Names(script, links), name, scope);
    }

    public static Result apply(Plan plan, String to, String toScope) {
        String wanted = to == null ? "" : to.trim();
        if (wanted.isEmpty()) return null;
        boolean keptLine = plan.param && !LINE.equals(toScope);
        String scope = keptLine ? LINE : toScope;
        if (wanted.equals(plan.name) && scope.equals(plan.scope))
            return new Result(plan.name, wanted, scope, 0, 0, false, keptLine);
        boolean clash = plan.clash(wanted, scope);
        Pass pass = new Pass(plan, wanted, scope, true);
        pass.run();
        for (Script.Node declaration : pass.declarations)
            renameKeys(plan.script, declaration, plan.name, wanted);
        Functions.rebuild(plan.script);
        return new Result(plan.name, wanted, scope, pass.count, pass.lines, clash, keptLine);
    }

    private static void renameKeys(Script script, Script.Node declaration, String from, String to) {
        String target = Functions.nameOf(declaration);
        if (target.isBlank()) return;
        for (Script.Root r : script.roots)
            each(r.chain, n -> {
                if (!n.invokes() || n.isStart() != declaration.isProcess()
                        || !target.equals(Functions.targetOf(n))) return;
                n.dynKeys.replaceAll(k -> k.equals(from) ? to : k);
                n.dynMarkerKeys.replaceAll(k -> k.equals(from) ? to : k);
            });
    }

    private static boolean declaresParam(Set<Script.Root> roots, String name) {
        for (Script.Root r : roots) {
            if (r.chain.isEmpty() || !r.chain.get(0).declares()) continue;
            for (Value p : Functions.parametersOf(r.chain.get(0))) if (name.equals(p.name)) return true;
        }
        return false;
    }

    private static void each(List<Script.Node> chain, Consumer<Script.Node> visit) {
        for (Script.Node n : chain) {
            visit.accept(n);
            if (n.cond != null) visit.accept(n.cond);
            each(n.body, visit);
        }
    }

    private static Set<Script.Root> group(Script.Root at, Map<Script.Root, Set<Script.Root>> links) {
        Set<Script.Root> out = new LinkedHashSet<>();
        if (at == null) return out;
        Deque<Script.Root> queue = new ArrayDeque<>();
        out.add(at);
        queue.add(at);
        while (!queue.isEmpty())
            for (Script.Root next : links.getOrDefault(queue.poll(), Set.of()))
                if (out.add(next)) queue.add(next);
        return out;
    }

    private static final class Links {
        final Map<Script.Root, Set<Script.Root>> local = new HashMap<>(), line = new HashMap<>();

        Links(Script script) {
            Map<String, Script.Root> functions = new HashMap<>(), processes = new HashMap<>();
            for (Script.Root r : script.roots) {
                if (r.chain.isEmpty() || !r.chain.get(0).declares()) continue;
                Script.Node head = r.chain.get(0);
                String name = Functions.nameOf(head);
                if (!name.isBlank()) (head.isProcess() ? processes : functions).putIfAbsent(name, r);
            }
            for (Script.Root r : script.roots)
                each(r.chain, n -> {
                    if (n.isCall()) {
                        Script.Root f = functions.get(Functions.targetOf(n));
                        if (f == null) return;
                        link(local, r, f);
                        if (sharesLine(n)) link(line, r, f);
                    } else if (n.isStart()) {
                        Script.Root p = processes.get(Functions.targetOf(n));
                        if (p != null && sharesLocal(n)) link(local, r, p);
                    }
                });
        }

        private static void link(Map<Script.Root, Set<Script.Root>> into, Script.Root a,
                                 Script.Root b) {
            if (a == b) return;
            into.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            into.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        }

        private static boolean sharesLocal(Script.Node start) {
            return start.markerVar(0) != null || !DONT_COPY.equals(start.marker(0));
        }

        private static boolean sharesLine(Script.Node call) {
            if (call.raw == null || !call.raw.has("values") || !call.raw.get("values").isJsonArray())
                return false;
            for (JsonElement e : call.raw.getAsJsonArray("values")) {
                if (!e.isJsonObject()) continue;
                JsonObject entry = e.getAsJsonObject();
                if (!LINE_MODE.equals(Json.str(entry, "name"))) continue;
                if (!entry.has("value") || !entry.get("value").isJsonObject()) return false;
                JsonObject value = entry.getAsJsonObject("value");
                if (value.has("variable")) return true;
                return SHARED_MODES.contains(Json.str(value, "enum").toLowerCase(Locale.ROOT));
            }
            return false;
        }
    }

    private static final class Names {
        private final Map<Script.Root, Set<String>> line = new HashMap<>(), local = new HashMap<>();
        private final Map<Script.Root, Set<String>> localGroup = new HashMap<>();
        private final Set<String> game = new HashSet<>(), save = new HashSet<>();
        private final Links links;

        Names(Script script, Links links) {
            this.links = links;
            for (Script.Root r : script.roots) {
                Set<String> ln = new HashSet<>(), lc = new HashSet<>();
                each(r.chain, n -> {
                    for (Finder.Ref ref : Finder.refs(n))
                        switch (ref.scope()) {
                            case LINE -> ln.add(ref.name());
                            case LOCAL -> lc.add(ref.name());
                            case SAVE -> save.add(ref.name());
                            default -> game.add(ref.name());
                        }
                });
                line.put(r, ln);
                local.put(r, lc);
            }
        }

        Set<String> at(Script.Root r, String scope) {
            return switch (scope) {
                case LINE -> line.getOrDefault(r, Set.of());
                case LOCAL -> localGroup.computeIfAbsent(r, this::localOf);
                case SAVE -> save;
                default -> game;
            };
        }

        private Set<String> localOf(Script.Root r) {
            Set<String> out = new HashSet<>();
            for (Script.Root g : group(r, links.local)) out.addAll(local.getOrDefault(g, Set.of()));
            return out;
        }
    }

    private static final class Pass {
        final Plan plan;
        final String to, toScope;
        final boolean write;
        final List<Script.Node> declarations = new ArrayList<>();
        int count, lines;
        boolean bareFrom, bareTo;
        Script.Node head;

        Pass(Plan plan, String to, String toScope, boolean write) {
            this.plan = plan;
            this.to = to;
            this.toScope = toScope;
            this.write = write;
        }

        void run() {
            for (Script.Root r : plan.roots) {
                bareFrom = identifier(plan.name) && !plan.shadowed(r, plan.name, plan.scope, false);
                bareTo = !write || plain(to) && !plan.shadowed(r, to, toScope, true);
                head = r.chain.isEmpty() ? null : r.chain.get(0);
                int before = count;
                each(r.chain, this::node);
                if (count > before) lines++;
            }
        }

        private void node(Script.Node n) {
            named(n);
            for (List<Value> list : n.values.values()) for (Value v : list) value(v, n);
            for (Value v : n.markerVars.values()) value(v, n);
            if (n.raw != null) json(n.raw);
        }

        private void named(Script.Node n) {
            Named rule = rule(n);
            if (rule == null) return;
            int scopeAt = n.settingIndex(rule.scope()), modeAt = n.settingIndex(rule.mode());
            int caseAt = n.settingIndex(rule.caseLabel());
            if (scopeAt < 0 || modeAt < 0) return;
            if (n.markerVar(scopeAt) != null || n.markerVar(modeAt) != null) return;
            if (!plan.scope.equals(SCOPE_WORDS.get(n.marker(scopeAt)))
                    || !rule.exact().equals(n.marker(modeAt))) return;
            boolean anyCase = caseAt >= 0 && n.markerVar(caseAt) == null
                    && rule.caseOn().equals(n.marker(caseAt));
            int arg = -1;
            List<Catalog.Arg> args = n.args();
            for (int i = 0; i < args.size(); i++)
                if (rule.arg().equals(args.get(i).purpose)) arg = i;
            List<Value> names = arg < 0 ? null : n.values.get(arg);
            if (names == null) return;
            List<Value> hits = new ArrayList<>();
            int filled = 0;
            for (Value v : names) {
                if (!Value.TEXT.equals(v.type) || v.text.isEmpty()) continue;
                filled++;
                boolean hit = anyCase ? v.text.equalsIgnoreCase(plan.name) : v.text.equals(plan.name);
                if (hit) hits.add(v);
            }
            boolean moves = !toScope.equals(plan.scope);
            if (hits.isEmpty() || moves && filled != hits.size()) return;
            count += hits.size();
            if (!write) return;
            for (Value v : hits) v.text = to;
            if (!moves) return;
            for (String option : n.settings().get(scopeAt).options)
                if (toScope.equals(SCOPE_WORDS.get(option))) n.markers.put(scopeAt, option);
        }

        private static Named rule(Script.Node n) {
            Catalog.Action a = n.action;
            if (a.category == null) return null;
            for (Named r : NAMED)
                if (r.action().equals(a.name) && r.category().equals(a.category.name)) return r;
            return null;
        }

        private boolean same(String name, String scope) {
            return plan.name.equals(name) && plan.scope.equals(scope);
        }

        private void value(Value v, Script.Node owner) {
            if (v == null) return;
            switch (v.type) {
                case Value.VARIABLE -> {
                    if (same(v.name, v.scope)) {
                        count++;
                        if (write) { v.name = to; v.scope = toScope; }
                    } else {
                        String t = text(v.name);
                        if (write) v.name = t;
                    }
                }
                case Value.PARAMETER -> parameter(v, owner);
                case Value.TEXT -> { String t = text(v.text); if (write) v.text = t; }
                case Value.NUMBER -> { String t = text(v.numberText); if (write) v.numberText = t; }
                case Value.ITEM -> {
                    String t = text(v.itemName);
                    if (write) v.itemName = t;
                    for (int i = 0; i < v.lore.size(); i++) {
                        String l = text(v.lore.get(i));
                        if (write) v.lore.set(i, l);
                    }
                }
                default -> { }
            }
            for (Value it : v.items) value(it, owner);
            for (Value k : v.keys) value(k, owner);
        }

        private void parameter(Value v, Script.Node owner) {
            boolean own = owner == head && owner.declares();
            if (own && LINE.equals(plan.scope) && plan.name.equals(v.name)) {
                count++;
                if (write) {
                    v.name = to;
                    if (!declarations.contains(owner)) declarations.add(owner);
                }
            }
            if (v.paramDefault.isEmpty()) return;
            Value d = Importer.fromServer(v.paramDefault);
            if (d == null) return;
            int before = count;
            value(d, owner);
            if (write && count > before) v.paramDefault = Exporter.toServer(d);
        }

        private void json(JsonElement e) {
            if (e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) json(x);
                return;
            }
            if (!e.isJsonObject()) return;
            JsonObject o = e.getAsJsonObject();
            String type = Json.str(o, "type");
            if (Value.VARIABLE.equals(type) && primitive(o, "variable")) {
                String name = o.get("variable").getAsString();
                if (same(name, Json.str(o, "scope"))) {
                    count++;
                    if (write) { o.addProperty("variable", to); o.addProperty("scope", toScope); }
                } else {
                    String t = text(name);
                    if (write) o.addProperty("variable", t);
                }
            } else if (Value.TEXT.equals(type) && primitive(o, "text")) {
                String t = text(o.get("text").getAsString());
                if (write) o.addProperty("text", t);
            } else if (Value.NUMBER.equals(type) && primitive(o, "number")
                    && o.getAsJsonPrimitive("number").isString()) {
                String t = text(o.get("number").getAsString());
                if (write) o.addProperty("number", t);
            }
            for (Map.Entry<String, JsonElement> m : o.entrySet())
                if (m.getValue().isJsonObject() || m.getValue().isJsonArray()) json(m.getValue());
        }

        private static boolean primitive(JsonObject o, String key) {
            return o.has(key) && o.get(key).isJsonPrimitive();
        }

        String text(String s) {
            if (s == null || s.indexOf('%') < 0) return s;
            StringBuilder out = new StringBuilder(s.length() + 8);
            int i = 0, n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '%') {
                    int open = callOpen(s, i), close = open < 0 ? -1 : closing(s, open);
                    if (close > 0) {
                        out.append(call(s.substring(i + 1, open), s.substring(open + 1, close)));
                        i = close + 1;
                        continue;
                    }
                }
                out.append(c);
                i++;
            }
            return out.toString();
        }

        private String call(String fn, String inner) {
            if (MATH.equals(fn)) return "%" + fn + "(" + math(inner) + ")";
            String scope = familyScope(fn);
            if (scope == null) return "%" + fn + "(" + text(inner) + ")";
            String base = family(fn);
            int comma = PAIRED.contains(base) ? topComma(inner) : -1;
            String name = comma < 0 ? inner : inner.substring(0, comma);
            String rest = comma < 0 ? "" : inner.substring(comma);
            if (same(name, scope)) {
                count++;
                return "%" + base + suffix(toScope) + "(" + to + text(rest) + ")";
            }
            return "%" + fn + "(" + text(name) + text(rest) + ")";
        }

        private String math(String s) {
            StringBuilder out = new StringBuilder(s.length() + 8);
            int i = 0, n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '%') {
                    int open = callOpen(s, i), close = open < 0 ? -1 : closing(s, open);
                    if (close > 0) {
                        out.append(call(s.substring(i + 1, open), s.substring(open + 1, close)));
                        i = close + 1;
                        continue;
                    }
                    int end = simpleEnd(s, i);
                    if (end > 0) {
                        out.append(s, i, end + 1);
                        i = end + 1;
                        continue;
                    }
                    out.append(c);
                    i++;
                    continue;
                }
                if (Character.isDigit(c)) {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.'
                            || s.charAt(j) == '_')) j++;
                    out.append(s, i, j);
                    i = j;
                    continue;
                }
                if (Character.isJavaIdentifierStart(c)) {
                    int j = i + 1;
                    while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                    String word = s.substring(i, j);
                    int k = j;
                    while (k < n && s.charAt(k) == ' ') k++;
                    boolean function = k < n && s.charAt(k) == '(';
                    if (!function && bareFrom && word.equals(plan.name)) {
                        count++;
                        out.append(bareTo ? to : "(" + placeholder(to, toScope) + ")");
                    } else {
                        out.append(word);
                    }
                    i = j;
                    continue;
                }
                out.append(c);
                i++;
            }
            return out.toString();
        }
    }

    public static String placeholder(String name, String scope) {
        return "%var" + suffix(scope) + "(" + name + ")";
    }

    private static String suffix(String scope) {
        return switch (scope) {
            case LOCAL -> "_local";
            case SAVE -> "_save";
            case LINE -> "_line";
            default -> "";
        };
    }

    private static String family(String fn) {
        int cut = fn.indexOf('_');
        return cut < 0 ? fn : fn.substring(0, cut);
    }

    private static String familyScope(String fn) {
        if (!FAMILIES.contains(family(fn))) return null;
        int cut = fn.indexOf('_');
        if (cut < 0) return GAME;
        return switch (fn.substring(cut + 1)) {
            case LOCAL -> LOCAL;
            case SAVE -> SAVE;
            case LINE -> LINE;
            default -> null;
        };
    }

    private static boolean identifier(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++)
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        return !MATH_WORDS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static boolean plain(String name) {
        if (name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean latin = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
            if (!latin && (i == 0 || c < '0' || c > '9')) return false;
        }
        return !MATH_WORDS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static int callOpen(String s, int at) {
        int j = at + 1;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
        return j > at + 1 && j < s.length() && s.charAt(j) == '(' ? j : -1;
    }

    private static int simpleEnd(String s, int at) {
        int j = at + 1;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
        return j > at + 1 && j < s.length() && s.charAt(j) == '%' ? j : -1;
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

    private static int topComma(String s) {
        int depth = 0;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return k;
        }
        return -1;
    }

    private Rename() {}
}
