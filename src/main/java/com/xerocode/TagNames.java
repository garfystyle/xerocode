package com.xerocode;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class TagNames {

    private static final String RULE = "только a–z, 0–9 и _ - . /";

    private static final int SHOWN = 4;

    public static boolean allowed(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.'
                || c == '/';
    }

    public static String clean(String name) {
        if (name == null) return "";
        if (name.indexOf('%') >= 0) return name;
        String s = name.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(Character.isWhitespace(c) ? '_' : c);
        }
        return out.toString();
    }

    public static String problem(String name) {
        String plain = Placeholders.strip(name == null ? "" : name);
        if (fits(plain)) return null;
        String fixed = clean(name);
        if (fits(Placeholders.strip(fixed))) return "запишется как «" + fixed + "»";
        Set<Character> bad = new LinkedHashSet<>();
        String rest = Placeholders.strip(fixed);
        for (int i = 0; i < rest.length(); i++) if (!allowed(rest.charAt(i))) bad.add(rest.charAt(i));
        StringBuilder said = new StringBuilder("нельзя: ");
        int n = 0;
        for (char c : bad) {
            if (n == SHOWN) {
                said.append(", …");
                break;
            }
            if (n > 0) said.append(", ");
            said.append(c);
            n++;
        }
        return said.append(" — ").append(RULE).toString();
    }

    public static boolean fixable(String name) {
        String p = problem(name);
        return p == null || p.startsWith("запишется");
    }

    private static boolean fits(String s) {
        for (int i = 0; i < s.length(); i++) if (!allowed(s.charAt(i))) return false;
        return true;
    }

    private TagNames() {}
}
