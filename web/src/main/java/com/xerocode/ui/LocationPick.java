package com.xerocode.ui;

import com.xerocode.Script;

public final class LocationPick {
    private LocationPick() {}

    public static boolean active() { return false; }

    public static void start(Script.Node target, int arg, int valueIndex) {}

    public static void start(com.xerocode.Value value, java.util.function.Consumer<com.xerocode.Value> done) {}
}
