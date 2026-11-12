package com.xerocode;

import java.util.List;

public final class Storages {
    private Storages() {}

    public static List<String> access() { return List.of(); }

    public static boolean asking() { return false; }

    public static boolean accessKnown() { return false; }

    public static List<String> cells() { return List.of(); }

    public static int version() { return Values.storageEpoch(); }

    public static void ask() {}
}
