package com.xerocode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Functions {
    public record Signature(String name, Value display, List<Value> parameters, Value icon,
                            Script.Node declaration) {}

    public static String signatureText(Signature signature) {
        StringBuilder sb = new StringBuilder();
        for (Value p : signature.parameters()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name.isBlank() ? "без имени" : p.name);
            if (Value.PLURAL.equals(p.typeKey)) sb.append("[]");
            if (!Value.ENUM.equals(p.typeKey) && !p.required) sb.append('*');
        }
        return sb.toString();
    }

    public record Known(Map<String, Signature> functions, Map<String, Signature> processes) {
        Map<String, Signature> forNode(Script.Node invoker) {
            return invoker.isStart() ? processes : functions;
        }
    }

    public static Known of(Script script) {
        Known known = new Known(new LinkedHashMap<>(), new LinkedHashMap<>());
        for (Script.Root r : script.roots) collect(r.chain, known);
        return known;
    }

    private static void collect(List<Script.Node> chain, Known known) {
        for (Script.Node n : chain) {
            if (n.declares()) {
                Map<String, Signature> into = n.isProcess() ? known.processes() : known.functions();
                String name = nameOf(n);
                if (!name.isBlank() && !into.containsKey(name))
                    into.put(name, new Signature(name, displayOf(n), parametersOf(n), iconOf(n), n));
            }
            collect(n.body, known);
        }
    }

    public static String nameOf(Script.Node declaration) {
        Value v = declaration.value(Catalog.FN_NAME);
        return v == null ? "" : v.text.trim();
    }

    public static List<Value> parametersOf(Script.Node declaration) {
        List<Value> out = new ArrayList<>();
        List<Value> cells = declaration.values.get(Catalog.FN_PARAMS);
        if (cells == null) return out;
        for (Value v : cells)
            if (Value.PARAMETER.equals(v.type) && !v.name.isBlank()) out.add(v);
        return out;
    }

    public static boolean hidden(Script.Node declaration) {
        return Catalog.HIDDEN.equals(declaration.settingOf(Catalog.SHOW_IN_CALL));
    }

    public static Value displayOf(Script.Node declaration) {
        Value v = declaration.value(Catalog.FN_DISPLAY);
        return v != null && Value.TEXT.equals(v.type) && !v.text.isBlank() ? v : null;
    }

    public static Value iconOf(Script.Node declaration) {
        Value v = declaration.value(Catalog.FN_ICON);
        return v != null && Value.ITEM.equals(v.type) && !v.itemId.isEmpty() ? v : null;
    }

    public static String targetOf(Script.Node invoker) {
        Value v = invoker.value(Catalog.CALL_NAME);
        return v == null ? "" : v.text.trim();
    }

    public static int rebuild(Script script) {
        Known known = of(script);
        for (Script.Root r : script.roots) rebuild(r.chain, known);
        return stamp(known.functions()) * 31 + stamp(known.processes());
    }

    private static int stamp(Map<String, Signature> all) {
        int h = all.size();
        for (Signature s : all.values()) {
            h = h * 31 + s.name().hashCode();
            h = h * 31 + (s.display() == null ? 0 : s.display().hash());
            h = h * 31 + (s.icon() == null ? 0 : s.icon().hash());
            for (Value p : s.parameters()) h = h * 31 + p.hash();
        }
        return h;
    }

    private static void rebuild(List<Script.Node> chain, Known known) {
        for (Script.Node n : chain) {
            if (n.invokes()) apply(n, known.forNode(n).get(targetOf(n)));
            rebuild(n.body, known);
        }
    }

    private static void apply(Script.Node call, Signature signature) {
        if (signature == null) {
            call.dynArgs = null;
            call.dynSettings = null;
            call.dynIcon = null;
            call.dynDisplay = null;
            return;
        }
        call.dynIcon = signature.icon();
        call.dynDisplay = signature.display();
        List<Catalog.Arg> args = new ArrayList<>(call.action.args);
        List<Catalog.Setting> settings = new ArrayList<>(call.action.settings);
        List<String> keys = new ArrayList<>(), markerKeys = new ArrayList<>();
        keys.add("");
        for (int i = 0; i < call.action.settings.size(); i++) markerKeys.add("\0" + i);

        for (Value p : signature.parameters()) {
            if (Value.ENUM.equals(p.typeKey)) {
                List<String> options = new ArrayList<>();
                for (Value.Elem e : p.elements) options.add(e.name);
                if (options.isEmpty()) continue;
                String def = options.contains(p.defaultElement) ? p.defaultElement : options.get(0);
                settings.add(new Catalog.Setting(p.name, options, def, -1));
                markerKeys.add(p.name);
            } else {
                args.add(Catalog.paramArg(p));
                keys.add(p.name);
            }
        }

        Map<String, String> parked = new LinkedHashMap<>();
        for (int i = 1; i < call.dynKeys.size(); i++) {
            String key = call.dynKeys.get(i);
            if (!markerKeys.contains(key)) continue;
            List<Value> v = call.values.get(i);
            if (v != null && !v.isEmpty()) parked.put(key, v.get(0).text);
        }

        remapValues(call, keys);
        remapMarkers(call, markerKeys, settings);
        for (int i = call.action.settings.size(); i < settings.size(); i++) {
            String option = parked.get(settings.get(i).label);
            if (option != null && settings.get(i).options.contains(option))
                call.markers.put(i, option);
        }
        call.dynArgs = args;
        call.dynSettings = settings;
        call.dynKeys = keys;
        call.dynMarkerKeys = markerKeys;
    }

    private static void remapValues(Script.Node call, List<String> keys) {
        List<String> was = call.dynKeys;
        if (was.isEmpty() || was.equals(keys)) {
            if (was.isEmpty()) call.values.keySet().removeIf(i -> i >= keys.size());
            return;
        }
        Map<Integer, List<Value>> moved = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Value>> e : call.values.entrySet()) {
            int old = e.getKey();
            if (old < 0 || old >= was.size()) continue;
            int now = old == 0 ? 0 : keys.indexOf(was.get(old));
            if (now >= 0) moved.put(now, e.getValue());
        }
        call.values.clear();
        call.values.putAll(moved);
    }

    private static void remapMarkers(Script.Node call, List<String> keys,
                                     List<Catalog.Setting> settings) {
        List<String> was = call.dynMarkerKeys;
        Map<Integer, String> moved = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : call.markers.entrySet()) {
            int old = e.getKey();
            int now = was.isEmpty() ? old : (old >= 0 && old < was.size() ? keys.indexOf(was.get(old)) : -1);
            if (now < 0 || now >= settings.size()) continue;
            if (settings.get(now).options.contains(e.getValue())) moved.put(now, e.getValue());
        }
        call.markers.clear();
        call.markers.putAll(moved);
        for (int i = 0; i < settings.size(); i++)
            call.markers.putIfAbsent(i, settings.get(i).def);
    }

    private Functions() {}
}
