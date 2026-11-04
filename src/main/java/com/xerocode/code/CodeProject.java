package com.xerocode.code;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.XeroCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import xero.compile.Compiler;
import xero.dialect.Dialect;
import xero.dialect.Dialects;
import xero.json.Json;
import xero.lang.Diagnostic;

public final class CodeProject {
    public static final int FILES_MAX = 300;
    private static final String STATE = ".state.json";
    private static final long SAVE_AFTER_MS = 800;

    public record Problem(CodeFile file, Diagnostic diagnostic, int line, int col) {}

    public static final class Outcome {
        public Map<String, Object> module;
        public final List<Problem> problems = new ArrayList<>();
        public int errors, warnings, lines;
        public boolean ok() { return module != null && errors == 0; }
    }

    private static CodeProject current;
    private static String currentPlot;

    public final String plot;
    public final Path root;
    public final List<CodeFile> files = new ArrayList<>();
    public final List<CodeFile> open = new ArrayList<>();
    public CodeFile active;
    private long dirtyAt;

    private CodeProject(String plot, Path root) {
        this.plot = plot;
        this.root = root;
    }

    public static CodeProject of(String plot) {
        String key = plot == null ? "" : plot;
        if (current != null && key.equals(currentPlot)) return current;
        if (current != null) current.saveAll();
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode").resolve("code")
                .resolve(key.isEmpty() ? "default" : safe(key));
        current = new CodeProject(key, dir);
        currentPlot = key;
        current.scan();
        current.restore();
        return current;
    }

    public static CodeProject current() { return current; }

    private static String safe(String s) { return s.replaceAll("[^0-9A-Za-z_.-]", "_"); }

    private void ensureRoot() throws IOException {
        if (!Files.isDirectory(root)) Files.createDirectories(root);
    }

    private void scan() {
        files.clear();
        try {
            ensureRoot();
            List<Path> found = new ArrayList<>();
            walk(root, found);
            for (Path p : found) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith(".") || Dialects.forFile(rel) == null) continue;
                files.add(new CodeFile(rel));
            }
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] папка кода не читается", e);
        }
        order();
    }

    private void walk(Path dir, List<Path> out) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.toList()) {
                if (Files.isDirectory(p)) walk(p, out);
                else out.add(p);
            }
        }
    }

    private void order() {
        files.sort(Comparator.comparing((CodeFile f) -> f.folder().toLowerCase(Locale.ROOT))
                .thenComparing(f -> f.name().toLowerCase(Locale.ROOT)));
    }

    private Path pathOf(CodeFile f) { return root.resolve(f.path); }

    public void load(CodeFile f) {
        if (f.loaded) return;
        f.loaded = true;
        String text = "";
        try {
            Path p = pathOf(f);
            if (Files.isRegularFile(p)) text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] файл кода не читается: " + f.path, e);
        }
        f.doc = new Doc(text);
        f.bind();
    }

    public CodeFile find(String path) {
        for (CodeFile f : files) if (f.path.equals(path)) return f;
        return null;
    }

    public static String clean(String name) {
        String s = name.strip().replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        s = s.replaceAll("[:*?\"<>|]", "_");
        return s.replace("../", "");
    }

    public CodeFile create(String path, String text) {
        String clean = clean(path);
        CodeFile had = find(clean);
        if (had != null) return had;
        CodeFile f = new CodeFile(clean);
        f.doc = new Doc(text == null ? "" : text);
        f.loaded = true;
        f.bind();
        f.doc.saved();
        files.add(f);
        order();
        write(f);
        return f;
    }

    public String freeName(String base, String extension) {
        String name = base + "." + extension;
        for (int i = 2; find(name) != null; i++) name = base + i + "." + extension;
        return name;
    }

    public void show(CodeFile f) {
        load(f);
        if (!open.contains(f)) open.add(f);
        active = f;
        stateChanged();
    }

    public void close(CodeFile f) {
        save(f);
        int at = open.indexOf(f);
        open.remove(f);
        if (active == f) active = open.isEmpty() ? null : open.get(Math.min(at, open.size() - 1));
        stateChanged();
    }

    public void delete(CodeFile f) {
        open.remove(f);
        files.remove(f);
        if (active == f) active = open.isEmpty() ? null : open.get(open.size() - 1);
        try {
            Files.deleteIfExists(pathOf(f));
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] файл кода не удалён: " + f.path, e);
        }
        stateChanged();
    }

    public boolean rename(CodeFile f, String wanted) {
        String clean = clean(wanted);
        if (clean.isEmpty() || find(clean) != null || Dialects.forFile(clean) == null) return false;
        Path was = pathOf(f);
        String old = f.path;
        f.path = clean;
        write(f);
        try {
            if (!old.equals(clean)) Files.deleteIfExists(was);
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] старый файл кода остался: " + old, e);
        }
        f.bind();
        order();
        stateChanged();
        return true;
    }

    public void touched() { dirtyAt = System.currentTimeMillis(); }

    public void tick() {
        if (dirtyAt != 0 && System.currentTimeMillis() - dirtyAt > SAVE_AFTER_MS) saveAll();
    }

    public void save(CodeFile f) {
        if (f.doc == null || !f.doc.dirty()) return;
        write(f);
    }

    private void write(CodeFile f) {
        try {
            Path p = pathOf(f);
            Path dir = p.getParent();
            if (dir != null && !Files.isDirectory(dir)) Files.createDirectories(dir);
            Files.write(p, (f.doc == null ? "" : f.doc.text()).getBytes(StandardCharsets.UTF_8));
            if (f.doc != null) f.doc.saved();
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] файл кода не записан: " + f.path, e);
        }
    }

    public void saveAll() {
        dirtyAt = 0;
        for (CodeFile f : files) save(f);
        writeState();
    }

    private void stateChanged() { touched(); }

    private void writeState() {
        JsonObject o = new JsonObject();
        JsonArray list = new JsonArray();
        for (CodeFile f : open) {
            JsonObject item = new JsonObject();
            item.addProperty("path", f.path);
            item.addProperty("x", f.scrollX);
            item.addProperty("y", f.scrollY);
            if (f.doc != null) {
                Doc.Caret c = f.doc.primary();
                item.addProperty("line", c.line);
                item.addProperty("col", c.col);
            }
            list.add(item);
        }
        o.add("open", list);
        if (active != null) o.addProperty("active", active.path);
        try {
            ensureRoot();
            Files.write(root.resolve(STATE), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            XeroCode.LOG.warn("[xerocode] состояние кода не записано", e);
        }
    }

    private void restore() {
        Path p = root.resolve(STATE);
        if (!Files.isRegularFile(p)) return;
        try {
            JsonElement parsed = JsonParser.parseString(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;
            JsonObject o = parsed.getAsJsonObject();
            if (o.has("open")) {
                for (JsonElement e : o.getAsJsonArray("open")) {
                    JsonObject item = e.getAsJsonObject();
                    CodeFile f = find(item.get("path").getAsString());
                    if (f == null) continue;
                    load(f);
                    open.add(f);
                    f.scrollX = item.has("x") ? item.get("x").getAsInt() : 0;
                    f.scrollY = item.has("y") ? item.get("y").getAsInt() : 0;
                    if (item.has("line")) f.doc.single(item.get("line").getAsInt(), item.get("col").getAsInt());
                }
            }
            if (o.has("active")) active = find(o.get("active").getAsString());
            if (active != null && !open.contains(active)) active = null;
            if (active == null && !open.isEmpty()) active = open.get(0);
        } catch (IOException | RuntimeException e) {
            XeroCode.LOG.warn("[xerocode] состояние кода не прочиталось", e);
        }
    }

    public Compiler.Loader loader(Set<String> seen) {
        return path -> {
            if (path.startsWith(Compiler.MODULE)) return null;
            String clean = clean(path);
            if (seen != null) seen.add(clean);
            CodeFile f = find(clean);
            if (f == null) return null;
            load(f);
            return f.doc.text();
        };
    }

    public Outcome build(CodeFile only) {
        Outcome out = new Outcome();
        List<CodeFile> targets = new ArrayList<>();
        if (only != null) targets.add(only);
        else for (CodeFile f : files) if (f.source() && !"json".equals(f.dialect.id())) targets.add(f);
        Set<String> imported = new HashSet<>();
        Map<CodeFile, Dialect.Built> built = new LinkedHashMap<>();
        for (CodeFile f : targets) {
            load(f);
            Set<String> seen = new HashSet<>();
            Dialect.Built b;
            synchronized (f.dialect) {
                b = f.dialect.build(f.path, f.doc.text(), loader(seen));
            }
            seen.remove(f.path);
            imported.addAll(seen);
            built.put(f, b);
            for (Diagnostic d : b.diagnostics()) {
                CodeFile where = d.file() == null ? f : find(clean(d.file()));
                if (where == null) where = f;
                load(where);
                int line = where.doc.lineAt(d.start());
                out.problems.add(new Problem(where, d, line, where.doc.colAt(d.start())));
                if (d.error()) out.errors++;
                else out.warnings++;
            }
        }
        List<Object> handlers = new ArrayList<>();
        int next = 0;
        boolean failed = false;
        for (Map.Entry<CodeFile, Dialect.Built> e : built.entrySet()) {
            if (imported.contains(e.getKey().path) && built.size() > 1) continue;
            Map<String, Object> module = e.getValue().module();
            if (module == null) {
                failed = true;
                continue;
            }
            for (Object h : Json.array(module.get("handlers"))) {
                Map<String, Object> line = new LinkedHashMap<>(Json.object(h));
                int at = line.get("position") instanceof Json.Num n ? n.intValue() : next;
                if (at < next) at = next;
                line.put("position", new Json.Num(String.valueOf(at)));
                next = at + 1;
                handlers.add(line);
            }
        }
        if (!failed) {
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.put("handlers", handlers);
            out.module = merged;
            out.lines = handlers.size();
        }
        return out;
    }
}
