package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class Json {
    public static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    public static boolean flag(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public static int num(JsonObject o, String key) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static long big(JsonObject o, String key) {
        try {
            return o.has(key) ? o.get(key).getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static JsonArray arr(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray()
                ? o.getAsJsonArray(key) : null;
    }

    private Json() {}
}
