package com.xerocode.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Snippet {
    public static final class Stop {
        public final int order;
        public int start, end;

        Stop(int order, int start, int end) {
            this.order = order;
            this.start = start;
            this.end = end;
        }
    }

    public final String text;
    public final List<Stop> stops;

    private Snippet(String text, List<Stop> stops) {
        this.text = text;
        this.stops = stops;
    }

    public static Snippet parse(String body, String indent) {
        StringBuilder out = new StringBuilder();
        List<Stop> stops = new ArrayList<>();
        read(body, 0, body.length(), out, stops, indent);
        stops.sort(Comparator.comparingInt(s -> s.order == 0 ? Integer.MAX_VALUE : s.order));
        return new Snippet(out.toString(), stops);
    }

    private static void read(String s, int from, int to, StringBuilder out, List<Stop> stops, String indent) {
        int i = from;
        while (i < to) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < to) {
                out.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '\n') {
                out.append('\n').append(indent);
                i++;
                continue;
            }
            if (c == '\t') {
                out.append(" ".repeat(Doc.TAB));
                i++;
                continue;
            }
            if (c != '$' || i + 1 >= to) {
                out.append(c);
                i++;
                continue;
            }
            char d = s.charAt(i + 1);
            if (Character.isDigit(d)) {
                int j = i + 1;
                while (j < to && Character.isDigit(s.charAt(j))) j++;
                int order = Integer.parseInt(s.substring(i + 1, j));
                stops.add(new Stop(order, out.length(), out.length()));
                i = j;
                continue;
            }
            if (d != '{') {
                out.append(c);
                i++;
                continue;
            }
            int close = closing(s, i + 1, to);
            if (close < 0) {
                out.append(c);
                i++;
                continue;
            }
            int j = i + 2;
            while (j < close && Character.isDigit(s.charAt(j))) j++;
            if (j == i + 2) {
                out.append(c);
                i++;
                continue;
            }
            int order = Integer.parseInt(s.substring(i + 2, j));
            int start = out.length();
            if (j < close && s.charAt(j) == ':') {
                List<Stop> inner = new ArrayList<>();
                read(s, j + 1, close, out, inner, indent);
                stops.addAll(inner);
            } else if (j < close && s.charAt(j) == '|') {
                int bar = s.indexOf(',', j + 1);
                int end = bar < 0 || bar > close ? s.lastIndexOf('|', close) : bar;
                if (end > j + 1) out.append(s, j + 1, end);
            }
            stops.add(new Stop(order, start, out.length()));
            i = close + 1;
        }
    }

    private static int closing(String s, int from, int to) {
        int depth = 0;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }
}
