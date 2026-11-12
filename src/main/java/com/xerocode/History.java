package com.xerocode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class History {
    private static int max = 80;

    public static final class State {
        private final String[] roots;

        private State(String[] roots) { this.roots = roots; }

        @Override public boolean equals(Object o) {
            return o instanceof State s && Arrays.equals(roots, s.roots);
        }

        @Override public int hashCode() { return Arrays.hashCode(roots); }
    }

    private static final class Stacks {
        final Deque<State> undo = new ArrayDeque<>();
        final Deque<State> redo = new ArrayDeque<>();
    }

    private static final Map<Script, Stacks> BY_SCRIPT = new WeakHashMap<>();
    private static Stacks now = new Stacks();

    public static void use(Script script) {
        now = script == null ? new Stacks() : BY_SCRIPT.computeIfAbsent(script, k -> new Stacks());
    }

    public static boolean canUndo() { return !now.undo.isEmpty(); }
    public static boolean canRedo() { return !now.redo.isEmpty(); }

    public static State snapshot(Script script) {
        String[] roots = new String[script.roots.size()];
        for (int i = 0; i < roots.length; i++) roots[i] = Script.frozen(script.roots.get(i));
        return new State(roots);
    }

    public static void limit(int steps) { max = steps; }

    public static void push(State state) {
        if (state == null) return;
        now.undo.push(state);
        while (now.undo.size() > max) now.undo.removeLast();
        now.redo.clear();
    }

    public static void pushFor(Script script, State state) {
        if (script == null || state == null) return;
        Stacks s = BY_SCRIPT.computeIfAbsent(script, k -> new Stacks());
        s.undo.push(state);
        while (s.undo.size() > max) s.undo.removeLast();
        s.redo.clear();
    }

    public static void clear() {
        now.undo.clear();
        now.redo.clear();
    }

    public static void clear(Script script) {
        Stacks s = BY_SCRIPT.get(script);
        if (s == null) return;
        s.undo.clear();
        s.redo.clear();
    }

    public static void restore(Script script, State state) {
        if (state != null) restore(script, state, snapshot(script));
    }

    private static void restore(Script script, State state, State current) {
        Map<String, Deque<Script.Root>> live = new HashMap<>();
        for (int i = 0; i < current.roots.length; i++)
            live.computeIfAbsent(current.roots[i], k -> new ArrayDeque<>()).add(script.roots.get(i));
        List<Script.Root> next = new ArrayList<>(state.roots.length);
        for (String text : state.roots) {
            Deque<Script.Root> same = live.get(text);
            Script.Root kept = same == null ? null : same.poll();
            next.add(kept != null ? kept : Script.thaw(text));
        }
        script.roots.clear();
        script.roots.addAll(next);
    }

    public static boolean undo(Script script) { return step(script, now.undo, now.redo); }

    public static boolean redo(Script script) { return step(script, now.redo, now.undo); }

    private static boolean step(Script script, Deque<State> from, Deque<State> to) {
        if (from.isEmpty()) return false;
        State state = from.pop();
        State current = snapshot(script);
        to.push(current);
        restore(script, state, current);
        return true;
    }

    private History() {}
}
