package com.xerocode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Match {
    public static final int ANYWHERE = 0, START = 1, END = 2, WHOLE = 3;

    private static final String BAD_REGEX = "ошибка в выражении";
    private static final String WORD = "\\w\u0400-\u04FF";
    private static final String EDGE = "(?:(?<=[" + WORD + "])(?![" + WORD + "])|(?<![" + WORD + "])(?=[" + WORD + "]))";

    private static Boolean unicodeWords;

    private final String needle;
    private final Pattern pattern;
    private final boolean matchCase, wholeWord;
    public final String error;

    private Match(String needle, Pattern pattern, boolean matchCase, boolean wholeWord, String error) {
        this.needle = needle;
        this.pattern = pattern;
        this.matchCase = matchCase;
        this.wholeWord = wholeWord;
        this.error = error;
    }

    public static Match of(String query, boolean matchCase, boolean wholeWord, boolean regex) {
        String q = query == null ? "" : query;
        if (!regex || q.isEmpty())
            return new Match(matchCase ? q : Search.fold(q), null, matchCase, wholeWord, null);
        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern p = unicodeWords() ? compile(q, flags | Pattern.UNICODE_CHARACTER_CLASS) : compile(widen(q), flags);
        if (p == null) p = compile(q, flags);
        return new Match(q, p, matchCase, wholeWord, p == null ? BAD_REGEX : null);
    }

    private static Pattern compile(String q, int flags) {
        try {
            return Pattern.compile(q, flags);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean unicodeWords() {
        if (unicodeWords == null) {
            Pattern p = compile("\\w", Pattern.UNICODE_CHARACTER_CLASS);
            unicodeWords = p != null && p.matcher("ж").matches();
        }
        return unicodeWords;
    }

    static String widen(String regex) {
        StringBuilder sb = new StringBuilder(regex.length() + 16);
        boolean inClass = false;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (c == '\\' && i + 1 < regex.length()) {
                char n = regex.charAt(++i);
                if (n == 'w') sb.append(inClass ? WORD : "[" + WORD + "]");
                else if (n == 'W' && !inClass) sb.append("[^").append(WORD).append(']');
                else if (n == 'b' && !inClass) sb.append(EDGE);
                else sb.append(c).append(n);
                continue;
            }
            if (c == '[' && !inClass) inClass = true;
            else if (c == ']' && inClass && sb.charAt(sb.length() - 1) != '[') inClass = false;
            sb.append(c);
        }
        return sb.toString();
    }

    public boolean empty() { return needle.isEmpty(); }

    public java.util.List<int[]> ranges(String hay) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (error != null || hay == null || needle.isEmpty()) return out;
        if (pattern != null) {
            Matcher m = pattern.matcher(hay);
            int from = 0;
            while (from <= hay.length() && m.find(from)) {
                if (m.end() > m.start() && fits(hay, m.start(), m.end(), ANYWHERE)) out.add(new int[]{m.start(), m.end()});
                from = Math.max(m.end(), m.start() + 1);
            }
            return out;
        }
        String h = matchCase ? hay : Search.fold(hay);
        int n = needle.length();
        for (int at = h.indexOf(needle); at >= 0; at = h.indexOf(needle, at + 1)) {
            if (!fits(h, at, at + n, ANYWHERE)) continue;
            out.add(new int[]{at, at + n});
            at += n - 1;
        }
        return out;
    }

    public boolean in(String hay) { return at(hay, ANYWHERE); }

    public boolean at(String hay, int where) {
        if (error != null || hay == null) return false;
        if (needle.isEmpty()) return true;
        if (pattern != null) return regex(hay, where);
        String h = matchCase ? hay : Search.fold(hay);
        if (where == WHOLE) return h.equals(needle);
        int n = needle.length();
        for (int at = h.indexOf(needle); at >= 0; at = h.indexOf(needle, at + 1))
            if (fits(h, at, at + n, where)) return true;
        return false;
    }

    private boolean regex(String hay, int where) {
        Matcher m = pattern.matcher(hay);
        if (where == WHOLE) return m.matches();
        int from = 0;
        while (from <= hay.length() && m.find(from)) {
            if (fits(hay, m.start(), m.end(), where)) return true;
            if (where == START) return false;
            from = m.start() + 1;
        }
        return false;
    }

    private boolean fits(String h, int start, int end, int where) {
        if (where == START && start != 0) return false;
        if (where == END && end != h.length()) return false;
        if (!wholeWord) return true;
        return (start == 0 || !word(h.charAt(start - 1))) && (end == h.length() || !word(h.charAt(end)));
    }

    private static boolean word(char c) { return c == '_' || Character.isLetterOrDigit(c); }
}
