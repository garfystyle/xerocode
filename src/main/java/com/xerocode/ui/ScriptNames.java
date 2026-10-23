package com.xerocode.ui;

import com.xerocode.Functions;
import com.xerocode.Script;
import com.xerocode.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScriptNames {
    private ScriptNames() {}

    static List<String> used(Script script, boolean parameters) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Script.Root r : script.roots) collectNames(r.chain, parameters, counts);
        List<String> out = new ArrayList<>(counts.keySet());
        out.sort((a, b) -> counts.get(b) - counts.get(a));
        return out;
    }

    static List<NamePick.Known> known(Script script, Script.Node near) {
        Script.Root here = near == null ? null : script.rootOf(near);
        Map<String, int[]> seen = new LinkedHashMap<>();
        for (Script.Root r : script.roots) {
            boolean mine = r == here;
            collectVars(r.chain, mine, seen);
            if (!r.chain.isEmpty() && r.chain.get(0).declares())
                for (Value p : Functions.parametersOf(r.chain.get(0)))
                    note(seen, p.name, "line", mine);
        }
        List<NamePick.Known> out = new ArrayList<>(seen.size());
        for (Map.Entry<String, int[]> e : seen.entrySet()) {
            String key = e.getKey();
            int cut = key.indexOf('\0');
            out.add(new NamePick.Known(key.substring(cut + 1), key.substring(0, cut),
                    e.getValue()[1] > 0, e.getValue()[0]));
        }
        return out;
    }

    private static void note(Map<String, int[]> seen, String name, String scope, boolean mine) {
        if (name == null || name.isBlank()) return;
        int[] n = seen.computeIfAbsent(scope + '\0' + name, k -> new int[2]);
        n[0]++;
        if (mine) n[1] = 1;
    }

    private static void collectVars(List<Script.Node> chain, boolean mine, Map<String, int[]> seen) {
        for (Script.Node n : chain) {
            for (List<Value> list : n.values.values())
                for (Value v : list) noteValue(v, mine, seen);
            for (Value v : n.markerVars.values()) noteValue(v, mine, seen);
            if (n.cond != null) collectVars(List.of(n.cond), mine, seen);
            collectVars(n.body, mine, seen);
        }
    }

    private static void noteValue(Value v, boolean mine, Map<String, int[]> seen) {
        if (Value.VARIABLE.equals(v.type)) note(seen, v.name, v.scope, mine);
        for (Value it : v.items) noteValue(it, mine, seen);
        for (Value k : v.keys) noteValue(k, mine, seen);
    }

    private static void collectNames(List<Script.Node> chain, boolean parameters,
                                     Map<String, Integer> counts) {
        String want = parameters ? Value.PARAMETER : Value.VARIABLE;
        for (Script.Node n : chain) {
            for (List<Value> list : n.values.values())
                for (Value v : list)
                    if (want.equals(v.type) && !v.name.isBlank())
                        counts.merge(v.name, 1, Integer::sum);
            for (Value v : n.markerVars.values())
                if (want.equals(v.type) && !v.name.isBlank())
                    counts.merge(v.name, 1, Integer::sum);
            if (n.cond != null) collectNames(List.of(n.cond), parameters, counts);
            collectNames(n.body, parameters, counts);
        }
    }
}
