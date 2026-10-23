package com.xerocode.ui;

import com.google.gson.JsonObject;
import com.xerocode.Catalog;
import com.xerocode.Importer;
import com.xerocode.Localized;
import com.xerocode.Script;
import com.xerocode.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class Langs {
    private final Script.Node node;
    private final int argIndex;
    private final boolean list;
    private final Map<String, List<Value>> byLang = new LinkedHashMap<>();
    private String current = "";

    Langs(Script.Node node, int argIndex, boolean list) {
        this.node = node;
        this.argIndex = argIndex;
        this.list = list;
    }

    static String field(Script.Node node, int argIndex) {
        return node == null || !node.declares() ? null : Catalog.localizedField(argIndex);
    }

    String current() { return current; }

    void load(List<Value> base) {
        byLang.put("", copies(base));
        for (Localized.Lang l : Localized.LANGS)
            if (!l.id().isEmpty()) byLang.put(l.id(), read(l.id()));
    }

    List<Value> swap(String id, List<Value> now) {
        byLang.put(current, copies(now));
        current = id;
        return copies(byLang.getOrDefault(id, List.of()));
    }

    void commit(List<Value> now, Consumer<List<Value>> base) {
        byLang.put(current, copies(now));
        for (Localized.Lang l : Localized.LANGS) {
            List<Value> list = byLang.get(l.id());
            if (list == null) continue;
            if (l.id().isEmpty()) base.accept(list);
            else write(l.id(), list);
        }
    }

    private static List<Value> copies(List<Value> list) {
        List<Value> out = new ArrayList<>();
        for (Value v : list) out.add(v.copy());
        return out;
    }

    private static String kept(List<Value> list) {
        List<Value> out = new ArrayList<>();
        for (Value v : list) if (!v.isBlank()) out.add(v);
        return ValueEditor.json(out);
    }

    String json() {
        StringBuilder sb = new StringBuilder();
        for (Localized.Lang l : Localized.LANGS) {
            List<Value> list = byLang.get(l.id());
            sb.append(l.id()).append('=').append(list == null ? "" : kept(list)).append(';');
        }
        return sb.toString();
    }

    String preview(String id) {
        List<Value> list = byLang.get(id);
        if (list == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Value v : list) {
            if (!Value.TEXT.equals(v.type) || v.text.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" / ");
            sb.append(McText.plain(v.text, v.parsing));
            if (sb.length() > 80) break;
        }
        return sb.toString().trim();
    }

    private JsonObject translations(boolean create) {
        if (field(node, argIndex) == null) return null;
        String key = Importer.translationsKey(argIndex);
        if (node.raw != null && node.raw.has(key) && node.raw.get(key).isJsonObject()) {
            JsonObject was = node.raw.getAsJsonObject(key);
            JsonObject now = Localized.normalize(was);
            if (now.equals(was)) return was;
            node.raw.add(key, now);
            return now;
        }
        if (!create) return null;
        if (node.raw == null) node.raw = new JsonObject();
        JsonObject made = new JsonObject();
        node.raw.add(key, made);
        return made;
    }

    private List<Value> read(String id) {
        List<Value> out = new ArrayList<>();
        JsonObject all = translations(false);
        if (all == null || !all.has(id)) return out;
        String text = Localized.entryText(all.get(id));
        String parsing = Localized.entryParsing(all.get(id));
        for (String part : list ? text.split("\n", -1) : new String[]{text}) {
            Value v = Value.of(Value.TEXT);
            v.text = part;
            v.parsing = parsing;
            out.add(v);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).text.isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private void write(String id, List<Value> list) {
        Localized.Joined text = Localized.join(list);
        boolean any = !text.text().isEmpty();
        JsonObject all = translations(any);
        if (all == null) return;
        if (any) all.add(id, Localized.entry(text.text(), text.parsing()));
        else all.remove(id);
        if (all.isEmpty()) node.raw.remove(Importer.translationsKey(argIndex));
        if (node.raw.isEmpty()) node.raw = null;
    }
}
