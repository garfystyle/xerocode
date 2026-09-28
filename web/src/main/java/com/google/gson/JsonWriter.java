package com.google.gson;

import java.util.Map;

final class JsonWriter {
    private JsonWriter() {}

    static void write(JsonElement e, StringBuilder out) {
        if (e == null || e instanceof JsonNull) {
            out.append("null");
        } else if (e instanceof JsonPrimitive p) {
            Object v = p.raw();
            if (v instanceof String s) string(s, out);
            else out.append(v.toString());
        } else if (e instanceof JsonArray a) {
            out.append('[');
            boolean first = true;
            for (JsonElement x : a) {
                if (!first) out.append(',');
                first = false;
                write(x, out);
            }
            out.append(']');
        } else if (e instanceof JsonObject o) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> x : o.entrySet()) {
                if (!first) out.append(',');
                first = false;
                string(x.getKey(), out);
                out.append(':');
                write(x.getValue(), out);
            }
            out.append('}');
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    static void string(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\f' -> out.append("\\f");
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append(HEX[c >> 4]).append(HEX[c & 15]);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
