package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xerocode.code.CodeFile;
import com.xerocode.code.CodeProject;
import com.xerocode.code.Doc;
import com.xerocode.ui.Layout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import xero.dialect.Dialect;
import xero.json.Json;

public final class Mirror {
    private static final int APART = 80;
    private static final List<String> NOTES = new ArrayList<>();

    public record Text(CodeFile file, List<String> notes) {}

    private Mirror() {}

    public static synchronized List<String> takeNotes() {
        List<String> out = new ArrayList<>(NOTES);
        NOTES.clear();
        return out;
    }

    private static synchronized void note(String s) { NOTES.add(s); }

    public static Text toText(Script tab, CodeProject project, Dialect fallback) {
        String path = Tabs.linkedFile(tab);
        CodeFile f = path.isEmpty() ? null : project.find(path);
        if (f != null) project.load(f);
        Dialect d = f != null && f.dialect != null ? f.dialect : fallback;
        Dialect.Decompiled got = decompiled(tab, d);
        if (f == null) {
            f = project.create(project.freeName(stem(Tabs.name(tab)), d.extension()), got.text());
        } else {
            replaceAll(f.doc, got.text());
            project.save(f);
        }
        Tabs.link(tab, f.path, canvasMark(tab), f.doc.text().hashCode());
        return new Text(f, got.notes());
    }

    public static Script toCanvas(CodeFile f, CodeProject.Outcome built, Font font) {
        Script tab = built.imports.isEmpty() ? Tabs.linkedTo(f.path) : null;
        boolean fresh = tab == null;
        if (fresh) {
            if (Tabs.full()) return null;
            tab = Tabs.add(stem(f.name()));
            if (tab == null) return null;
        }
        JsonArray handlers = JsonParser.parseString(Json.write(built.module)).getAsJsonObject().getAsJsonArray("handlers");
        replaceCanvas(tab, handlers, font, fresh);
        if (built.imports.isEmpty()) Tabs.link(tab, f.path, canvasMark(tab), f.doc.text().hashCode());
        return tab;
    }

    public static void beforeCanvas(CodeProject project, Font font) {
        project.saveAll();
        for (Script tab : Tabs.linked()) {
            CodeFile f = linkedFile(tab, project);
            if (f == null) continue;
            if (f.doc.text().hashCode() == Tabs.textMark(tab)) continue;
            if (canvasMark(tab) != Tabs.canvasMark(tab)) {
                note(both(f, tab));
                continue;
            }
            CodeProject.Outcome out = project.build(f);
            if (!out.ok()) {
                note("в «" + f.name() + "» ошибки — вкладка «" + Tabs.name(tab) + "» прежняя");
                continue;
            }
            toCanvas(f, out, font);
        }
    }

    public static void beforeCode(CodeProject project) {
        for (Script tab : Tabs.linked()) {
            CodeFile f = linkedFile(tab, project);
            if (f == null || f.dialect == null) continue;
            if (canvasMark(tab) == Tabs.canvasMark(tab)) continue;
            if (f.doc.text().hashCode() != Tabs.textMark(tab)) {
                note(both(f, tab));
                continue;
            }
            try {
                List<String> notes = toText(tab, project, f.dialect).notes();
                for (String n : notes) note(n);
            } catch (RuntimeException e) {
                XeroCode.LOG.error("[xerocode] вкладка не перевелась в файл", e);
                note("вкладка «" + Tabs.name(tab) + "» не перевелась в «" + f.name() + "»");
            }
        }
    }

    public static CodeFile fileOf(Script tab, CodeProject project) {
        String path = Tabs.linkedFile(tab);
        return path.isEmpty() ? null : project.find(path);
    }

    private static CodeFile linkedFile(Script tab, CodeProject project) {
        CodeFile f = fileOf(tab, project);
        if (f == null) {
            Tabs.link(tab, "", 0, 0);
            return null;
        }
        project.load(f);
        return f;
    }

    private static String both(CodeFile f, Script tab) {
        return "«" + f.name() + "» и вкладка «" + Tabs.name(tab) + "» изменились обе — выбери «С полотна» или «На полотно»";
    }

    static int canvasMark(Script tab) {
        List<String> lines = new ArrayList<>();
        for (JsonElement h : Exporter.export(List.of(tab)).json().getAsJsonArray("handlers")) {
            JsonObject line = h.getAsJsonObject().deepCopy();
            line.remove("position");
            lines.add(line.toString());
        }
        Collections.sort(lines);
        return lines.hashCode();
    }

    private static Dialect.Decompiled decompiled(Script tab, Dialect d) {
        Exporter.Result r = Exporter.export(List.of(tab));
        Map<String, Object> module = Json.object(Json.parse(r.json().toString()));
        synchronized (d) {
            return d.decompile(module);
        }
    }

    private static void replaceAll(Doc d, String text) {
        int last = d.lines() - 1;
        d.replace(0, 0, last, d.line(last).length(), text);
        d.single(0, 0);
    }

    private static String stem(String name) {
        String s = name.replaceFirst("\\.[^.]+$", "").replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return s.isEmpty() ? "полотно" : s;
    }

    private static void replaceCanvas(Script tab, JsonArray handlers, Font font, boolean fresh) {
        if (!fresh) {
            History.use(tab);
            History.push(History.snapshot(tab));
        }
        Map<String, Deque<double[]>> spots = new HashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Script.Root r : tab.roots)
            if (!r.chain.isEmpty())
                spots.computeIfAbsent(key(r.chain.get(0), seen), k -> new ArrayDeque<>()).add(new double[]{r.x, r.y});
        Script laid = new Script();
        laid.plot = tab.plot;
        Importer.importInto(laid, handlers, font);
        seen.clear();
        List<Script.Root> moved = new ArrayList<>();
        double right = Double.NEGATIVE_INFINITY;
        for (Script.Root r : laid.roots) {
            Deque<double[]> same = r.chain.isEmpty() ? null : spots.get(key(r.chain.get(0), seen));
            double[] at = same == null ? null : same.poll();
            if (at == null) {
                moved.add(r);
                continue;
            }
            r.x = at[0];
            r.y = at[1];
            right = Math.max(right, r.x + Layout.size(r.chain, font)[0]);
        }
        if (right > Double.NEGATIVE_INFINITY && !moved.isEmpty()) {
            double left = Double.POSITIVE_INFINITY;
            for (Script.Root r : moved) left = Math.min(left, r.x);
            for (Script.Root r : moved) r.x += right + APART - left;
        }
        tab.roots.clear();
        tab.roots.addAll(laid.roots);
        if (fresh) tab.fitOnOpen = true;
        tab.save();
    }

    private static String key(Script.Node head, Map<String, Integer> seen) {
        String base = head.declares() ? (head.isFunction() ? "f:" : "p:") + Functions.nameOf(head)
                : "e:" + (head.action == null ? "" : Catalog.keyOf(head.action));
        return base + "#" + seen.merge(base, 1, Integer::sum);
    }
}
