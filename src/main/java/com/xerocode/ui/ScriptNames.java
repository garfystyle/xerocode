package com.xerocode.ui;

import com.xerocode.Functions;
import com.xerocode.Script;
import com.xerocode.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScriptNames {
    private static Script memoScript;
    private static int memoStamp;
    private static Seen memoSeen;
    private static List<String> memoUsed;

    private ScriptNames() {}

    private static void fresh(Script script, int stamp) {
        if (script == memoScript && stamp == memoStamp) return;
        memoScript = script;
        memoStamp = stamp;
        memoSeen = null;
        memoUsed = null;
    }

    static List<String> used(Script script, int stamp) {
        fresh(script, stamp);
        if (memoUsed == null) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Script.Root r : script.roots) collectNames(r.chain, true, counts);
            List<String> out = new ArrayList<>(counts.keySet());
            out.sort((a, b) -> counts.get(b) - counts.get(a));
            memoUsed = out;
        }
        return new ArrayList<>(memoUsed);
    }

    private static final class Name {
        final String name, scope;
        int count;

        Name(String name, String scope) {
            this.name = name;
            this.scope = scope;
        }
    }

    private static final class Seen {
        final Map<String, Map<String, Name>> scopes = new HashMap<>();
        final List<Name> order = new ArrayList<>();

        void note(String name, String scope) {
            if (name == null || name.isBlank()) return;
            Map<String, Name> names = scopes.get(scope);
            if (names == null) {
                names = new HashMap<>();
                scopes.put(scope, names);
            }
            Name n = names.get(name);
            if (n == null) {
                n = new Name(name, scope);
                names.put(name, n);
                order.add(n);
            }
            n.count++;
        }

        boolean has(Name n) {
            Map<String, Name> names = scopes.get(n.scope);
            return names != null && names.containsKey(n.name);
        }
    }

    private static void seenIn(Script.Root r, Seen seen) {
        collectVars(r.chain, seen);
        if (!r.chain.isEmpty() && r.chain.get(0).declares())
            for (Value p : Functions.parametersOf(r.chain.get(0)))
                seen.note(p.name, "line");
    }

    static List<NamePick.Known> known(Script script, Script.Node near, int stamp) {
        fresh(script, stamp);
        if (memoSeen == null) {
            memoSeen = new Seen();
            for (Script.Root r : script.roots) seenIn(r, memoSeen);
        }
        Script.Root here = near == null ? null : script.rootOf(near);
        Seen mine = new Seen();
        if (here != null) seenIn(here, mine);
        List<NamePick.Known> out = new ArrayList<>(memoSeen.order.size());
        for (Name n : memoSeen.order) out.add(new NamePick.Known(n.name, n.scope, mine.has(n), n.count));
        return out;
    }

    private static void collectVars(List<Script.Node> chain, Seen seen) {
        for (Script.Node n : chain) {
            for (List<Value> list : n.values.values())
                for (Value v : list) noteValue(v, seen);
            for (Value v : n.markerVars.values()) noteValue(v, seen);
            if (n.cond != null) collectVars(List.of(n.cond), seen);
            collectVars(n.body, seen);
        }
    }

    private static void noteValue(Value v, Seen seen) {
        if (Value.VARIABLE.equals(v.type)) seen.note(v.name, v.scope);
        for (Value it : v.items) noteValue(it, seen);
        for (Value k : v.keys) noteValue(k, seen);
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
