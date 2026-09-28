package com.xerocode.web;

import java.util.HashMap;
import java.util.Map;

public final class Lang {
    private static final Map<String, String> TEXT = new HashMap<>();

    private Lang() {}

    static void put(String key, String value) { TEXT.put(key, value); }

    public static String get(String key) {
        ItemData.load();
        return TEXT.get(key);
    }
}
