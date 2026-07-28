package com.xerocode.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class McText {
    public record Run(String text, Style style) {}

    public record Colour(char code, String name, int rgb, Formatting formatting) {}

    public record Deco(char code, String mini, String label, String title) {}

    public static final List<Colour> COLOURS = new ArrayList<>();

    static {
        for (char c : "0123456789abcdef".toCharArray()) {
            Formatting f = Formatting.byCode(c);
            if (f == null || f.getColorValue() == null) continue;
            COLOURS.add(new Colour(c, f.getName(), f.getColorValue(), f));
        }
    }

    public static final List<Deco> DECOS = List.of(
            new Deco('l', "bold",          "Ж", "жирный"),
            new Deco('o', "italic",        "К", "курсив"),
            new Deco('n', "underlined",    "Ч", "подчёркнутый"),
            new Deco('m', "strikethrough", "З", "зачёркнутый"),
            new Deco('k', "obfuscated",    "?", "обфускация"),
            new Deco('r', "reset",         "⟲", "сбросить формат"));

    public static final String PLAIN = "plain", LEGACY = "legacy",
            MINI = "minimessage", JSON = "json";

    public static boolean formattable(String parsing) {
        return LEGACY.equals(parsing) || MINI.equals(parsing);
    }

    public static boolean supportsGradient(String parsing) {
        return LEGACY.equals(parsing) || MINI.equals(parsing);
    }

    public static String colourTag(String parsing, Colour c) {
        return MINI.equals(parsing) ? "<" + c.name() + ">" : "&" + c.code();
    }

    public static String colourClose(String parsing, Colour c) {
        return MINI.equals(parsing) ? "</" + c.name() + ">" : "&r";
    }

    public static String decoTag(String parsing, Deco d) {
        return MINI.equals(parsing) ? "<" + d.mini() + ">" : "&" + d.code();
    }

    public static String decoClose(String parsing, Deco d) {
        if ('r' == d.code()) return "";
        return MINI.equals(parsing) ? "</" + d.mini() + ">" : "&r";
    }

    public static String hexTag(String parsing, int rgb) {
        String h = String.format("%06x", rgb & 0xFFFFFF);
        return MINI.equals(parsing) ? "<#" + h + ">" : "&#" + h;
    }

    public static String hexClose(String parsing) {
        return MINI.equals(parsing) ? "</color>" : "&r";
    }

    public static String normaliseHex(String s) {
        if (s == null) return null;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return null;
        for (int i = 0; i < 6; i++) if (Character.digit(h.charAt(i), 16) < 0) return null;
        return h.toLowerCase(Locale.ROOT);
    }

    public static int hexRgb(String normalised) { return Integer.parseInt(normalised, 16); }

    public static int gradientAt(int[] stops, double t) {
        if (stops.length == 0) return 0xFFFFFF;
        if (stops.length == 1) return stops[0];
        double p = Math.max(0, Math.min(1, t)) * (stops.length - 1);
        int i = (int) Math.floor(p);
        if (i >= stops.length - 1) return stops[stops.length - 1];
        return lerp(stops[i], stops[i + 1], p - i);
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int rainbowAt(double t, double phase) {
        return hsv((float) ((t + phase) % 1.0), 1f, 1f);
    }

    public static int hsvRgb(float h, float s, float v) { return hsv(h, s, v); }

    public static float[] rgbHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0.0001f) {
            if (max == r) h = ((g - b) / d) / 6f;
            else if (max == g) h = (2 + (b - r) / d) / 6f;
            else h = (4 + (r - g) / d) / 6f;
            if (h < 0) h += 1f;
        }
        return new float[]{h, max <= 0 ? 0 : d / max, max};
    }

    private static int hsv(float h, float s, float v) {
        if (h < 0) h += 1f;
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        int p = Math.round(v * 255 * (1 - s));
        int q = Math.round(v * 255 * (1 - f * s));
        int t = Math.round(v * 255 * (1 - (1 - f) * s));
        int val = Math.round(v * 255);
        return switch (i) {
            case 0 -> (val << 16) | (t << 8) | p;
            case 1 -> (q << 16) | (val << 8) | p;
            case 2 -> (p << 16) | (val << 8) | t;
            case 3 -> (p << 16) | (q << 8) | val;
            case 4 -> (t << 16) | (p << 8) | val;
            default -> (val << 16) | (p << 8) | q;
        };
    }

    public static String gradientMarkup(String parsing, String text, int[] stops) {
        if (text.isEmpty()) return "";
        if (MINI.equals(parsing)) {
            StringBuilder sb = new StringBuilder("<gradient");
            for (int c : stops) sb.append(":#").append(String.format("%06x", c & 0xFFFFFF));
            return sb.append('>').append(text).append("</gradient>").toString();
        }
        return perCharacter(text, i -> gradientAt(stops, text.length() == 1 ? 0
                : i / (double) (text.length() - 1)));
    }

    public static String rainbowMarkup(String parsing, String text) {
        if (text.isEmpty()) return "";
        if (MINI.equals(parsing)) return "<rainbow>" + text + "</rainbow>";
        return perCharacter(text, i -> rainbowAt(text.length() == 1 ? 0
                : i / (double) text.length(), 0));
    }

    private interface CharColour { int at(int index); }

    private static String perCharacter(String text, CharColour f) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++)
            sb.append("&#").append(String.format("%06x", f.at(i) & 0xFFFFFF)).append(text.charAt(i));
        return sb.toString();
    }

    public static List<Run> runs(String raw, String parsing) {
        if (raw == null || raw.isEmpty()) return List.of();
        return switch (parsing) {
            case LEGACY -> legacyRuns(raw);
            case MINI -> miniRuns(raw, Style.EMPTY);
            case JSON -> jsonRuns(raw);
            default -> List.of(new Run(raw, Style.EMPTY));
        };
    }

    public static List<Run> runsOf(Text text) {
        List<Run> out = new ArrayList<>();
        if (text == null) return out;
        text.visit((style, str) -> {
            if (!str.isEmpty()) out.add(new Run(str, style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static String from(Text text, String parsing) {
        return write(runsOf(text), parsing);
    }

    public static Text preview(String raw, String parsing) {
        List<Run> runs = runs(raw, parsing);
        if (runs.isEmpty()) return Text.empty();
        MutableText out = Text.empty();
        for (Run r : runs) out.append(Text.literal(r.text()).setStyle(r.style()));
        return out;
    }

    private static void push(List<Run> out, StringBuilder buf, Style style) {
        if (buf.length() == 0) return;
        out.add(new Run(buf.toString(), style));
        buf.setLength(0);
    }

    private static Style applyLegacy(Style style, Formatting f) {
        if (f == Formatting.RESET) return Style.EMPTY;
        if (f.isColor()) return Style.EMPTY.withColor(f);
        return style.withFormatting(f);
    }

    private static List<Run> legacyRuns(String s) {
        List<Run> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        Style style = Style.EMPTY;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '#' && i + 8 <= s.length()) {
                    String hex = normaliseHex(s.substring(i + 2, i + 8));
                    if (hex != null) {
                        push(out, buf, style);
                        style = Style.EMPTY.withColor(TextColor.fromRgb(hexRgb(hex)));
                        i += 8;
                        continue;
                    }
                }
                Formatting f = Formatting.byCode(next);
                if (f != null) {
                    push(out, buf, style);
                    style = applyLegacy(style, f);
                    i += 2;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        push(out, buf, style);
        return out;
    }

    private static List<Run> miniRuns(String s, Style base) {
        List<Run> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        Deque<Style> stack = new ArrayDeque<>();
        Style style = base;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '<') { buf.append(c); i++; continue; }
            int close = s.indexOf('>', i + 1);
            if (close < 0) { buf.append(c); i++; continue; }

            String tag = s.substring(i + 1, close).trim();
            String lower = tag.toLowerCase(Locale.ROOT);
            boolean selfClosing = lower.endsWith("/") && !lower.startsWith("/");
            if (selfClosing) lower = lower.substring(0, lower.length() - 1);
            boolean closing = lower.startsWith("/");
            String body = closing ? lower.substring(1) : lower;
            String name = body.contains(":") ? body.substring(0, body.indexOf(':')) : body;

            if (!closing && ("gradient".equals(name) || "rainbow".equals(name))) {
                int end = matchingClose(s, close + 1, name);
                String inner = s.substring(close + 1, end < 0 ? s.length() : end);
                push(out, buf, style);
                out.addAll(paint(miniRuns(inner, style), name, body));
                i = end < 0 ? s.length() : s.indexOf('>', end) + 1;
                if (i <= 0) i = s.length();
                continue;
            }

            Style applied = tagStyle(style, body);
            if (closing) {
                if (applied == null) { buf.append(s, i, close + 1); i = close + 1; continue; }
                push(out, buf, style);
                style = stack.isEmpty() ? base : stack.pop();
                i = close + 1;
                continue;
            }
            if (applied == null) { buf.append(s, i, close + 1); i = close + 1; continue; }
            push(out, buf, style);
            if ("reset".equals(name)) { stack.clear(); style = base; }
            else if (!selfClosing) { stack.push(style); style = applied; }
            else style = applied;
            i = close + 1;
        }
        push(out, buf, style);
        return out;
    }

    private static int matchingClose(String s, int from, String name) {
        int depth = 0;
        int i = from;
        while (i < s.length()) {
            int lt = s.indexOf('<', i);
            if (lt < 0) return -1;
            int gt = s.indexOf('>', lt + 1);
            if (gt < 0) return -1;
            String tag = s.substring(lt + 1, gt).trim().toLowerCase(Locale.ROOT);
            String head = tag.startsWith("/") ? tag.substring(1) : tag;
            if (head.contains(":")) head = head.substring(0, head.indexOf(':'));
            if (head.equals(name)) {
                if (tag.startsWith("/")) {
                    if (depth == 0) return lt;
                    depth--;
                } else if (!tag.endsWith("/")) {
                    depth++;
                }
            }
            i = gt + 1;
        }
        return -1;
    }

    private static List<Run> paint(List<Run> inner, String kind, String body) {
        int total = 0;
        for (Run r : inner) total += r.text().length();
        if (total == 0) return inner;

        List<String> args = new ArrayList<>();
        int colon = body.indexOf(':');
        if (colon >= 0) for (String a : body.substring(colon + 1).split(":")) args.add(a.trim());

        int[] stops;
        double phase = 0;
        if ("rainbow".equals(kind)) {
            stops = null;
            for (String a : args) {
                String p = a.startsWith("!") ? a.substring(1) : a;
                try { phase = Double.parseDouble(p); } catch (NumberFormatException ignored) { }
            }
        } else {
            List<Integer> cols = new ArrayList<>();
            for (String a : args) {
                Integer rgb = colourOf(a);
                if (rgb != null) cols.add(rgb);
                else {
                    try { phase = Double.parseDouble(a); } catch (NumberFormatException ignored) { }
                }
            }
            if (cols.size() < 2) { cols.clear(); cols.add(0xFFFFFF); cols.add(0x000000); }
            stops = new int[cols.size()];
            for (int i = 0; i < stops.length; i++) stops[i] = cols.get(i);
        }

        List<Run> out = new ArrayList<>();
        int index = 0;
        for (Run r : inner) {
            for (int k = 0; k < r.text().length(); k++) {
                double t = total == 1 ? 0 : index / (double) (total - 1);
                int rgb = stops == null ? rainbowAt(index / (double) total, phase)
                        : gradientAt(stops, Math.min(1, Math.max(0, t + phase)));
                out.add(new Run(String.valueOf(r.text().charAt(k)),
                        r.style().withColor(TextColor.fromRgb(rgb))));
                index++;
            }
        }
        return out;
    }

    private static Integer colourOf(String name) {
        if (name.startsWith("#")) {
            String hex = normaliseHex(name);
            return hex == null ? null : hexRgb(hex);
        }
        for (Colour c : COLOURS) if (c.name().equals(name)) return c.rgb();
        return null;
    }

    private static Style tagStyle(Style style, String body) {
        if (body.isEmpty()) return null;
        String name = body.contains(":") ? body.substring(0, body.indexOf(':')) : body;
        if ("reset".equals(name)) return Style.EMPTY;

        boolean off = name.startsWith("!");
        if (off) name = name.substring(1);

        if (name.startsWith("#")) {
            String hex = normaliseHex(name);
            return hex == null ? null : style.withColor(TextColor.fromRgb(hexRgb(hex)));
        }
        if ("color".equals(name) || "colour".equals(name) || "c".equals(name)) {
            if (!body.contains(":")) return style;
            Integer rgb = colourOf(body.substring(body.indexOf(':') + 1));
            return rgb == null ? null : style.withColor(TextColor.fromRgb(rgb));
        }
        for (Colour c : COLOURS)
            if (c.name().equals(name)) return style.withColor(c.formatting());
        Boolean on = off ? Boolean.FALSE : Boolean.TRUE;
        return switch (name) {
            case "bold", "b" -> style.withBold(on);
            case "italic", "i", "em" -> style.withItalic(on);
            case "underlined", "u" -> style.withUnderline(on);
            case "strikethrough", "st" -> style.withStrikethrough(on);
            case "obfuscated", "obf" -> style.withObfuscated(on);
            default -> null;
        };
    }

    private static List<Run> jsonRuns(String s) {
        try {
            JsonElement el = JsonParser.parseString(s);
            Text t = TextCodecs.CODEC.parse(JsonOps.INSTANCE, el).result().orElse(null);
            if (t != null) {
                List<Run> out = new ArrayList<>();
                t.visit((style, str) -> {
                    if (!str.isEmpty()) out.add(new Run(str, style));
                    return java.util.Optional.empty();
                }, Style.EMPTY);
                return out;
            }
        } catch (Throwable ignored) { }
        return List.of(new Run(s, Style.EMPTY.withColor(Formatting.DARK_GRAY)));
    }

    public static String convert(String raw, String from, String to) {
        if (from.equals(to)) return raw;
        return write(runs(raw, from), to);
    }

    public static String write(List<Run> runs, String parsing) {
        return switch (parsing) {
            case LEGACY -> writeLegacy(runs);
            case MINI -> writeMini(runs);
            case JSON -> writeJson(runs);
            default -> writePlain(runs);
        };
    }

    public static String writePlain(List<Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (Run r : runs) sb.append(r.text());
        return sb.toString();
    }

    public static String plain(String raw, String parsing) {
        return writePlain(runs(raw, parsing));
    }

    public static Text fit(TextRenderer tr, List<Run> runs, int maxWidth) {
        MutableText out = Text.empty();
        if (maxWidth <= 0) return out;
        int total = 0;
        for (Run r : runs) total += tr.getWidth(r.text());
        if (total <= maxWidth) {
            for (Run r : runs) out.append(Text.literal(r.text()).setStyle(r.style()));
            return out;
        }
        int room = maxWidth - tr.getWidth("…");
        int used = 0;
        for (Run r : runs) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < r.text().length(); i++) {
                int cw = tr.getWidth(String.valueOf(r.text().charAt(i)));
                if (used + cw > room) {
                    if (sb.length() > 0) out.append(Text.literal(sb.toString()).setStyle(r.style()));
                    out.append(Text.literal("…"));
                    return out;
                }
                sb.append(r.text().charAt(i));
                used += cw;
            }
            out.append(Text.literal(sb.toString()).setStyle(r.style()));
        }
        return out;
    }

    private static boolean on(Boolean b) { return b != null && b; }

    private static boolean dropped(Style prev, Style now) {
        return (on(prev.isBold()) && !on(now.isBold()))
                || (on(prev.isItalic()) && !on(now.isItalic()))
                || (on(prev.isUnderlined()) && !on(now.isUnderlined()))
                || (on(prev.isStrikethrough()) && !on(now.isStrikethrough()))
                || (on(prev.isObfuscated()) && !on(now.isObfuscated()))
                || (prev.getColor() != null && now.getColor() == null);
    }

    private static String legacyColour(TextColor c) {
        for (Colour k : COLOURS)
            if (c.getRgb() == k.rgb()) return "&" + k.code();
        return "&#" + String.format("%06x", c.getRgb() & 0xFFFFFF);
    }

    private static String miniColour(TextColor c) {
        for (Colour k : COLOURS)
            if (c.getRgb() == k.rgb()) return "<" + k.name() + ">";
        return "<#" + String.format("%06x", c.getRgb() & 0xFFFFFF) + ">";
    }

    private static String write(List<Run> runs, String reset, boolean colourResets,
                                Function<TextColor, String> colour, Function<Deco, String> deco) {
        StringBuilder sb = new StringBuilder();
        Style prev = Style.EMPTY;
        for (Run r : runs) {
            Style now = r.style();
            if (dropped(prev, now)) { sb.append(reset); prev = Style.EMPTY; }
            TextColor pc = prev.getColor(), nc = now.getColor();
            if (nc != null && (pc == null || pc.getRgb() != nc.getRgb())) {
                sb.append(colour.apply(nc));
                if (colourResets) prev = Style.EMPTY.withColor(nc);
            }
            for (Deco d : DECOS) {
                if (d.code() == 'r') continue;
                if (has(now, d) && !has(prev, d)) sb.append(deco.apply(d));
            }
            sb.append(r.text());
            prev = now;
        }
        return sb.toString();
    }

    private static String writeLegacy(List<Run> runs) {
        return write(runs, "&r", true, McText::legacyColour, d -> "&" + d.code());
    }

    private static String writeMini(List<Run> runs) {
        return write(runs, "<reset>", false, McText::miniColour, d -> "<" + d.mini() + ">");
    }

    private static boolean has(Style s, Deco d) {
        return switch (d.code()) {
            case 'l' -> on(s.isBold());
            case 'o' -> on(s.isItalic());
            case 'n' -> on(s.isUnderlined());
            case 'm' -> on(s.isStrikethrough());
            case 'k' -> on(s.isObfuscated());
            default -> false;
        };
    }

    private static String writeJson(List<Run> runs) {
        try {
            MutableText t = Text.empty();
            for (Run r : runs) t.append(Text.literal(r.text()).setStyle(r.style()));
            JsonElement el = TextCodecs.CODEC.encodeStart(JsonOps.INSTANCE, t).result().orElse(null);
            if (el != null) return el.toString();
        } catch (Throwable ignored) { }
        return writePlain(runs);
    }

    private McText() {}
}
