package com.google.gson;

import com.google.gson.internal.LazilyParsedNumber;
import java.io.IOException;
import java.io.Reader;

public final class JsonParser {
    private final String s;
    private int at;

    private JsonParser(String s) { this.s = s; }

    public static JsonElement parseString(String json) {
        JsonParser p = new JsonParser(json);
        p.skip();
        if (p.at >= p.s.length()) return JsonNull.INSTANCE;
        JsonElement e = p.value();
        p.skip();
        if (p.at < p.s.length()) throw p.error("Did not consume the entire document.");
        return e;
    }

    public static JsonElement parseReader(Reader reader) {
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            return parseString(sb.toString());
        } catch (IOException e) {
            throw new JsonIOException(e);
        }
    }

    private JsonSyntaxException error(String what) {
        return new JsonSyntaxException(what + " at position " + at);
    }

    private void skip() {
        int n = s.length();
        while (at < n) {
            char c = s.charAt(at);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
                at++;
            } else if (c == '/' && at + 1 < n && s.charAt(at + 1) == '/') {
                while (at < n && s.charAt(at) != '\n') at++;
            } else if (c == '/' && at + 1 < n && s.charAt(at + 1) == '*') {
                int end = s.indexOf("*/", at + 2);
                if (end < 0) throw error("Unterminated comment");
                at = end + 2;
            } else if (c == '#') {
                while (at < n && s.charAt(at) != '\n') at++;
            } else {
                return;
            }
        }
    }

    private JsonElement value() {
        skip();
        if (at >= s.length()) throw error("End of input");
        char c = s.charAt(at);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': case '\'':
                at++;
                return new JsonPrimitive(quoted(c));
            default:
                return literal();
        }
    }

    private JsonObject object() {
        at++;
        JsonObject o = new JsonObject();
        skip();
        if (peek() == '}') { at++; return o; }
        while (true) {
            skip();
            char c = peek();
            String name;
            if (c == '"' || c == '\'') {
                at++;
                name = quoted(c);
            } else {
                name = bare();
                if (name.isEmpty()) throw error("Expected name");
            }
            skip();
            char sep = peek();
            if (sep == ':') at++;
            else if (sep == '=') {
                at++;
                if (peek() == '>') at++;
            } else throw error("Expected ':'");
            o.add(name, value());
            skip();
            char next = peek();
            if (next == ',' || next == ';') {
                at++;
                continue;
            }
            if (next == '}') {
                at++;
                return o;
            }
            throw error("Unterminated object");
        }
    }

    private JsonArray array() {
        at++;
        JsonArray a = new JsonArray();
        skip();
        if (peek() == ']') { at++; return a; }
        while (true) {
            skip();
            char c = peek();
            if (c == ',' || c == ';') {
                at++;
                a.add(JsonNull.INSTANCE);
                continue;
            }
            if (c == ']') {
                at++;
                a.add(JsonNull.INSTANCE);
                return a;
            }
            a.add(value());
            skip();
            char next = peek();
            if (next == ',' || next == ';') {
                at++;
                skip();
                if (peek() == ']') {
                    at++;
                    a.add(JsonNull.INSTANCE);
                    return a;
                }
                continue;
            }
            if (next == ']') {
                at++;
                return a;
            }
            throw error("Unterminated array");
        }
    }

    private char peek() {
        if (at >= s.length()) throw error("End of input");
        return s.charAt(at);
    }

    private String quoted(char quote) {
        StringBuilder sb = null;
        int start = at, n = s.length();
        while (at < n) {
            char c = s.charAt(at++);
            if (c == quote) {
                if (sb == null) return s.substring(start, at - 1);
                sb.append(s, start, at - 1);
                return sb.toString();
            }
            if (c == '\\') {
                if (sb == null) sb = new StringBuilder();
                sb.append(s, start, at - 1);
                if (at >= n) throw error("Unterminated escape sequence");
                char e = s.charAt(at++);
                switch (e) {
                    case 'u' -> {
                        if (at + 4 > n) throw error("Unterminated escape sequence");
                        sb.append((char) Integer.parseInt(s.substring(at, at + 4), 16));
                        at += 4;
                    }
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 'f' -> sb.append('\f');
                    case '\n' -> sb.append('\n');
                    default -> sb.append(e);
                }
                start = at;
            }
        }
        throw error("Unterminated string");
    }

    private static boolean bareChar(char c) {
        switch (c) {
            case '/': case '\\': case ';': case '#': case '=':
            case '{': case '}': case '[': case ']': case ':': case ',':
            case ' ': case '\t': case '\f': case '\r': case '\n':
                return false;
            default:
                return true;
        }
    }

    private String bare() {
        int start = at, n = s.length();
        while (at < n && bareChar(s.charAt(at))) at++;
        return s.substring(start, at);
    }

    private JsonElement literal() {
        String word = bare();
        if (word.isEmpty()) throw error("Expected value");
        switch (word) {
            case "true": return new JsonPrimitive(Boolean.TRUE);
            case "false": return new JsonPrimitive(Boolean.FALSE);
            case "null": return JsonNull.INSTANCE;
            default:
                if (number(word)) return new JsonPrimitive(new LazilyParsedNumber(word));
                return new JsonPrimitive(word);
        }
    }

    private static boolean number(String w) {
        int i = 0, n = w.length();
        if (i < n && w.charAt(i) == '-') i++;
        int digits = i;
        while (i < n && Character.isDigit(w.charAt(i))) i++;
        if (i == digits) return false;
        if (i < n && w.charAt(i) == '.') {
            i++;
            int frac = i;
            while (i < n && Character.isDigit(w.charAt(i))) i++;
            if (i == frac) return false;
        }
        if (i < n && (w.charAt(i) == 'e' || w.charAt(i) == 'E')) {
            i++;
            if (i < n && (w.charAt(i) == '+' || w.charAt(i) == '-')) i++;
            int exp = i;
            while (i < n && Character.isDigit(w.charAt(i))) i++;
            if (i == exp) return false;
        }
        return i == n;
    }
}
