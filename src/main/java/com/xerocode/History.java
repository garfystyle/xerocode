package com.xerocode;

import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.Deque;

public final class History {
    private static final int MAX = 80;

    private static final Deque<String> undo = new ArrayDeque<>();
    private static final Deque<String> redo = new ArrayDeque<>();

    public static boolean canUndo() { return !undo.isEmpty(); }
    public static boolean canRedo() { return !redo.isEmpty(); }

    public static String snapshot(Script script) { return script.toJson().toString(); }

    public static void push(String state) {
        if (state == null) return;
        undo.push(state);
        if (undo.size() > MAX) undo.removeLast();
        redo.clear();
    }

    public static void clear() {
        undo.clear();
        redo.clear();
    }

    public static void restore(Script script, String state) {
        if (state == null) return;
        script.replaceWith(Script.fromJson(JsonParser.parseString(state).getAsJsonObject()));
    }

    public static boolean undo(Script script) { return step(script, undo, redo); }

    public static boolean redo(Script script) { return step(script, redo, undo); }

    private static boolean step(Script script, Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return false;
        to.push(snapshot(script));
        restore(script, from.pop());
        return true;
    }

    private History() {}
}
