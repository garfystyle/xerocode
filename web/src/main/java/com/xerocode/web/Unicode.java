package com.xerocode.web;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Unicode {
    private static Map<Integer, String> names;

    private Unicode() {}

    public static String name(int cp) {
        if (names == null) {
            names = new HashMap<>();
            try (InputStream in = Unicode.class.getResourceAsStream("/web/unicode.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        int sp = line.indexOf(' ');
                        if (sp > 0) names.put(Integer.parseInt(line.substring(0, sp), 16), line.substring(sp + 1));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return names.get(cp);
    }
}
