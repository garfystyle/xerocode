package com.xerocode.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Nbt {
    public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    private Nbt() {}

    public abstract static class Tag {
        public abstract byte id();

        public abstract Tag copy();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            print(this, sb);
            return sb.toString();
        }
    }

    public static final class Num extends Tag {
        private final byte type;
        public final Number value;

        public Num(byte type, Number value) {
            this.type = type;
            this.value = value;
        }

        @Override public byte id() { return type; }
        @Override public Tag copy() { return this; }
        public int asInt() { return value.intValue(); }
        public double asDouble() { return value.doubleValue(); }

        @Override
        public boolean equals(Object o) {
            return o instanceof Num n && n.type == type && n.value.equals(value);
        }

        @Override
        public int hashCode() { return value.hashCode() * 31 + type; }
    }

    public static final class Str extends Tag {
        public final String value;

        public Str(String value) {
            this.value = value;
        }

        @Override public byte id() { return STRING; }
        @Override public Tag copy() { return this; }
        @Override public boolean equals(Object o) { return o instanceof Str s && s.value.equals(value); }
        @Override public int hashCode() { return value.hashCode(); }
    }

    public static final class Arr extends Tag {
        private final byte type;
        public final long[] values;

        public Arr(byte type, long[] values) {
            this.type = type;
            this.values = values;
        }

        @Override public byte id() { return type; }
        @Override public Tag copy() { return new Arr(type, values.clone()); }
        @Override public boolean equals(Object o) { return o instanceof Arr a && a.type == type && java.util.Arrays.equals(a.values, values); }
        @Override public int hashCode() { return java.util.Arrays.hashCode(values) * 31 + type; }
    }

    public static final class ListTag extends Tag {
        public final List<Tag> items = new ArrayList<>();

        @Override public byte id() { return LIST; }

        public byte elementType() {
            byte t = END;
            for (Tag x : items) {
                if (t == END) t = x.id();
                else if (t != x.id()) return COMPOUND;
            }
            return t;
        }

        @Override
        public ListTag copy() {
            ListTag l = new ListTag();
            for (Tag t : items) l.items.add(t.copy());
            return l;
        }

        @Override public boolean equals(Object o) { return o instanceof ListTag l && l.items.equals(items); }
        @Override public int hashCode() { return items.hashCode(); }
    }

    public static final class Compound extends Tag {
        public final Map<String, Tag> map = new LinkedHashMap<>();

        @Override public byte id() { return COMPOUND; }

        public Tag get(String key) { return map.get(key); }
        public void put(String key, Tag value) { map.put(key, value); }
        public Tag remove(String key) { return map.remove(key); }
        public boolean isEmpty() { return map.isEmpty(); }
        public int size() { return map.size(); }
        public boolean contains(String key) { return map.containsKey(key); }

        public String getString(String key) {
            return map.get(key) instanceof Str s ? s.value : "";
        }

        public int getInt(String key, int fallback) {
            return map.get(key) instanceof Num n ? n.asInt() : fallback;
        }

        public Compound getCompound(String key) {
            return map.get(key) instanceof Compound c ? c : null;
        }

        @Override
        public Compound copy() {
            Compound c = new Compound();
            for (Map.Entry<String, Tag> e : map.entrySet()) c.map.put(e.getKey(), e.getValue().copy());
            return c;
        }

        @Override public boolean equals(Object o) { return o instanceof Compound c && c.map.equals(map); }
        @Override public int hashCode() { return map.hashCode(); }
    }

    public static Num ofByte(int v) { return new Num(BYTE, (byte) v); }
    public static Num ofInt(int v) { return new Num(INT, v); }

    public static Compound readCompressed(byte[] raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw)))) {
            byte type = in.readByte();
            if (type != COMPOUND) throw new IOException("корень не compound");
            in.readUTF();
            return (Compound) read(in, COMPOUND, 0);
        }
    }

    public static byte[] writeCompressed(Compound root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            write(out, root);
        }
        return bytes.toByteArray();
    }

    private static Tag read(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > 512) throw new IOException("слишком глубоко");
        switch (type) {
            case BYTE: return new Num(BYTE, in.readByte());
            case SHORT: return new Num(SHORT, in.readShort());
            case INT: return new Num(INT, in.readInt());
            case LONG: return new Num(LONG, in.readLong());
            case FLOAT: return new Num(FLOAT, in.readFloat());
            case DOUBLE: return new Num(DOUBLE, in.readDouble());
            case BYTE_ARRAY: {
                long[] v = new long[in.readInt()];
                for (int i = 0; i < v.length; i++) v[i] = in.readByte();
                return new Arr(BYTE_ARRAY, v);
            }
            case STRING: return new Str(in.readUTF());
            case LIST: {
                byte et = in.readByte();
                int n = in.readInt();
                ListTag l = new ListTag();
                for (int i = 0; i < n; i++) l.items.add(read(in, et, depth + 1));
                return l;
            }
            case COMPOUND: {
                Compound c = new Compound();
                while (true) {
                    byte t = in.readByte();
                    if (t == END) break;
                    String name = in.readUTF();
                    c.map.put(name, read(in, t, depth + 1));
                }
                return c;
            }
            case INT_ARRAY: {
                long[] v = new long[in.readInt()];
                for (int i = 0; i < v.length; i++) v[i] = in.readInt();
                return new Arr(INT_ARRAY, v);
            }
            case LONG_ARRAY: {
                long[] v = new long[in.readInt()];
                for (int i = 0; i < v.length; i++) v[i] = in.readLong();
                return new Arr(LONG_ARRAY, v);
            }
            default: throw new IOException("неизвестный тег " + type);
        }
    }

    private static void write(DataOutputStream out, Tag tag) throws IOException {
        switch (tag.id()) {
            case BYTE -> out.writeByte(((Num) tag).value.intValue());
            case SHORT -> out.writeShort(((Num) tag).value.intValue());
            case INT -> out.writeInt(((Num) tag).value.intValue());
            case LONG -> out.writeLong(((Num) tag).value.longValue());
            case FLOAT -> out.writeFloat(((Num) tag).value.floatValue());
            case DOUBLE -> out.writeDouble(((Num) tag).value.doubleValue());
            case BYTE_ARRAY -> {
                long[] v = ((Arr) tag).values;
                out.writeInt(v.length);
                for (long x : v) out.writeByte((int) x);
            }
            case STRING -> out.writeUTF(((Str) tag).value);
            case LIST -> {
                ListTag l = (ListTag) tag;
                byte et = l.elementType();
                if (et == COMPOUND) {
                    boolean mixed = false;
                    for (Tag t : l.items) if (t.id() != COMPOUND) mixed = true;
                    if (mixed) {
                        out.writeByte(COMPOUND);
                        out.writeInt(l.items.size());
                        for (Tag t : l.items) {
                            if (t instanceof Compound c) write(out, c);
                            else {
                                Compound wrap = new Compound();
                                wrap.put("", t);
                                write(out, wrap);
                            }
                        }
                        return;
                    }
                }
                out.writeByte(et);
                out.writeInt(l.items.size());
                for (Tag t : l.items) write(out, t);
            }
            case COMPOUND -> {
                for (Map.Entry<String, Tag> e : ((Compound) tag).map.entrySet()) {
                    out.writeByte(e.getValue().id());
                    out.writeUTF(e.getKey());
                    write(out, e.getValue());
                }
                out.writeByte(END);
            }
            case INT_ARRAY -> {
                long[] v = ((Arr) tag).values;
                out.writeInt(v.length);
                for (long x : v) out.writeInt((int) x);
            }
            case LONG_ARRAY -> {
                long[] v = ((Arr) tag).values;
                out.writeInt(v.length);
                for (long x : v) out.writeLong(x);
            }
            default -> throw new IOException("тег " + tag.id());
        }
    }

    private static boolean bareKey(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '+')) return false;
        }
        return true;
    }

    static void quote(String s, StringBuilder sb) {
        char q = s.indexOf('"') >= 0 && s.indexOf('\'') < 0 ? '\'' : '"';
        sb.append(q);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == q) sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\t') sb.append("\\t");
            else if (c == '\r') sb.append("\\r");
            else sb.append(c);
        }
        sb.append(q);
    }

    private static String num(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) return Long.toString((long) d) + ".0";
        return Double.toString(d);
    }

    private static String num(float f) {
        if (f == Math.rint(f) && !Float.isInfinite(f) && Math.abs(f) < 1e7f) return Long.toString((long) f) + ".0";
        return Float.toString(f);
    }

    public static void print(Tag t, StringBuilder sb) {
        switch (t.id()) {
            case BYTE -> sb.append(((Num) t).value.byteValue()).append('b');
            case SHORT -> sb.append(((Num) t).value.shortValue()).append('s');
            case INT -> sb.append(((Num) t).value.intValue());
            case LONG -> sb.append(((Num) t).value.longValue()).append('L');
            case FLOAT -> sb.append(num(((Num) t).value.floatValue())).append('f');
            case DOUBLE -> sb.append(num(((Num) t).value.doubleValue())).append('d');
            case STRING -> quote(((Str) t).value, sb);
            case BYTE_ARRAY, INT_ARRAY, LONG_ARRAY -> {
                String suffix = t.id() == BYTE_ARRAY ? "B" : t.id() == LONG_ARRAY ? "L" : "";
                sb.append('[').append(t.id() == BYTE_ARRAY ? "B" : t.id() == INT_ARRAY ? "I" : "L").append(';');
                long[] v = ((Arr) t).values;
                for (int i = 0; i < v.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(v[i]).append(suffix);
                }
                sb.append(']');
            }
            case LIST -> {
                sb.append('[');
                List<Tag> items = ((ListTag) t).items;
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append(',');
                    print(items.get(i), sb);
                }
                sb.append(']');
            }
            case COMPOUND -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, Tag> e : ((Compound) t).map.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    if (bareKey(e.getKey())) sb.append(e.getKey());
                    else quote(e.getKey(), sb);
                    sb.append(':');
                    print(e.getValue(), sb);
                }
                sb.append('}');
            }
            default -> sb.append("?");
        }
    }

    public static final class SyntaxError extends Exception {
        public SyntaxError(String message) {
            super(message);
        }
    }

    public static Compound parseCompound(String s) throws SyntaxError {
        Parser p = new Parser(s);
        p.skip();
        Tag t = p.value();
        p.skip();
        if (p.at < s.length()) throw p.error("лишний текст после значения");
        if (!(t instanceof Compound c)) throw p.error("ожидался {…}");
        return c;
    }

    public static Tag parse(String s) throws SyntaxError {
        Parser p = new Parser(s);
        p.skip();
        Tag t = p.value();
        p.skip();
        if (p.at < s.length()) throw p.error("лишний текст после значения");
        return t;
    }

    private static final class Parser {
        private final String s;
        private int at;

        Parser(String s) {
            this.s = s;
        }

        SyntaxError error(String what) {
            return new SyntaxError(what + " (позиция " + at + ")");
        }

        void skip() {
            while (at < s.length() && Character.isWhitespace(s.charAt(at))) at++;
        }

        char peek() throws SyntaxError {
            if (at >= s.length()) throw error("неожиданный конец");
            return s.charAt(at);
        }

        void expect(char c) throws SyntaxError {
            skip();
            if (peek() != c) throw error("ожидалось «" + c + "»");
            at++;
        }

        Tag value() throws SyntaxError {
            skip();
            char c = peek();
            if (c == '{') return compound();
            if (c == '[') return list();
            if (c == '"' || c == '\'') return new Str(quoted());
            return literal();
        }

        Compound compound() throws SyntaxError {
            expect('{');
            Compound out = new Compound();
            skip();
            if (peek() == '}') {
                at++;
                return out;
            }
            while (true) {
                skip();
                String key = peek() == '"' || peek() == '\'' ? quoted() : bare();
                if (key.isEmpty()) throw error("ожидался ключ");
                expect(':');
                out.put(key, value());
                skip();
                char c = peek();
                if (c == ',') {
                    at++;
                    skip();
                    if (peek() == '}') {
                        at++;
                        return out;
                    }
                    continue;
                }
                if (c == '}') {
                    at++;
                    return out;
                }
                throw error("ожидалось «,» или «}»");
            }
        }

        Tag list() throws SyntaxError {
            expect('[');
            skip();
            if (at + 1 < s.length() && (peek() == 'B' || peek() == 'I' || peek() == 'L') && s.charAt(at + 1) == ';') {
                char kind = peek();
                at += 2;
                List<Long> vals = new ArrayList<>();
                skip();
                if (peek() != ']') {
                    while (true) {
                        Tag t = literal();
                        if (!(t instanceof Num n)) throw error("ожидалось число");
                        vals.add(n.value.longValue());
                        skip();
                        if (peek() == ',') {
                            at++;
                            continue;
                        }
                        break;
                    }
                }
                expect(']');
                long[] v = new long[vals.size()];
                for (int i = 0; i < v.length; i++) v[i] = vals.get(i);
                return new Arr(kind == 'B' ? BYTE_ARRAY : kind == 'I' ? INT_ARRAY : LONG_ARRAY, v);
            }
            ListTag out = new ListTag();
            if (peek() == ']') {
                at++;
                return out;
            }
            while (true) {
                out.items.add(value());
                skip();
                char c = peek();
                if (c == ',') {
                    at++;
                    skip();
                    if (peek() == ']') {
                        at++;
                        return out;
                    }
                    continue;
                }
                if (c == ']') {
                    at++;
                    return out;
                }
                throw error("ожидалось «,» или «]»");
            }
        }

        String quoted() throws SyntaxError {
            char q = s.charAt(at++);
            StringBuilder sb = new StringBuilder();
            while (at < s.length()) {
                char c = s.charAt(at++);
                if (c == q) return sb.toString();
                if (c == '\\') {
                    if (at >= s.length()) break;
                    char e = s.charAt(at++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 's' -> sb.append(' ');
                        case 'x' -> {
                            sb.append((char) Integer.parseInt(s.substring(at, at + 2), 16));
                            at += 2;
                        }
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(at, at + 4), 16));
                            at += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("незакрытая строка");
        }

        String bare() {
            int start = at;
            while (at < s.length()) {
                char c = s.charAt(at);
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                        || c == '_' || c == '-' || c == '.' || c == '+') at++;
                else break;
            }
            return s.substring(start, at);
        }

        Tag literal() throws SyntaxError {
            skip();
            String w = bare();
            if (w.isEmpty()) throw error("ожидалось значение");
            String lw = w.toLowerCase(Locale.ROOT);
            if (lw.equals("true")) return ofByte(1);
            if (lw.equals("false")) return ofByte(0);
            try {
                String body = w.replace("_", "");
                char last = Character.toLowerCase(body.charAt(body.length() - 1));
                String digits = body.substring(0, body.length() - 1);
                switch (last) {
                    case 'b':
                        if (isInt(digits)) return new Num(BYTE, (byte) Long.parseLong(digits));
                        break;
                    case 's':
                        if (isInt(digits)) return new Num(SHORT, (short) Long.parseLong(digits));
                        break;
                    case 'l':
                        if (isInt(digits)) return new Num(LONG, Long.parseLong(digits));
                        break;
                    case 'f':
                        if (isFloat(digits)) return new Num(FLOAT, Float.parseFloat(digits));
                        break;
                    case 'd':
                        if (isFloat(digits)) return new Num(DOUBLE, Double.parseDouble(digits));
                        break;
                    default:
                        break;
                }
                if (isInt(body)) {
                    long v = Long.parseLong(body);
                    if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return new Num(INT, (int) v);
                }
                if (isFloat(body)) return new Num(DOUBLE, Double.parseDouble(body));
            } catch (NumberFormatException ignored) {
            }
            return new Str(w);
        }

        private static boolean isInt(String d) {
            if (d.isEmpty()) return false;
            int i = d.charAt(0) == '-' || d.charAt(0) == '+' ? 1 : 0;
            if (i >= d.length()) return false;
            for (; i < d.length(); i++) if (!Character.isDigit(d.charAt(i))) return false;
            return true;
        }

        private static boolean isFloat(String d) {
            if (d.isEmpty()) return false;
            try {
                Double.parseDouble(d);
                return Character.isDigit(d.charAt(d.length() - 1)) || d.endsWith(".");
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
