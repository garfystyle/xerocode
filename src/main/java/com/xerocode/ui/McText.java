package com.xerocode.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.xerocode.XeroCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class McText {
    public record Run(String text, Style style) {}

    public record Colour(char code, String name, int rgb, ChatFormatting formatting) {}

    public record Deco(char code, String mini, String label, String title) {}

    public static final List<Colour> COLOURS = new ArrayList<>();

    static {
        for (char c : "0123456789abcdef".toCharArray()) {
            ChatFormatting f = ChatFormatting.getByCode(c);
            if (f == null || rgb(f) == null) continue;
            COLOURS.add(new Colour(c, f.name().toLowerCase(java.util.Locale.ROOT), rgb(f), f));
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

    public static String hexClose(String parsing, int rgb) {
        return MINI.equals(parsing) ? "</#" + String.format("%06x", rgb & 0xFFFFFF) + ">" : "&r";
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

    public static List<Run> runsOf(Component text) {
        List<Run> out = new ArrayList<>();
        if (text == null) return out;
        text.visit((style, str) -> {
            if (!str.isEmpty()) out.add(new Run(str, style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static String from(Component text, String parsing) {
        return write(runsOf(text), parsing);
    }

    public static Component preview(String raw, String parsing) {
        List<Run> runs = runs(raw, parsing);
        if (runs.isEmpty()) return Component.empty();
        MutableComponent out = Component.empty();
        for (Run r : runs) out.append(Component.literal(r.text()).setStyle(r.style()));
        return out;
    }

    private static void push(List<Run> out, StringBuilder buf, Style style) {
        if (buf.length() == 0) return;
        out.add(new Run(buf.toString(), style));
        buf.setLength(0);
    }

    public static Integer rgb(ChatFormatting f) {
        TextColor t = TextColor.fromLegacyFormat(f);
        return t == null ? null : t.getValue();
    }

    private static Style applyLegacy(Style style, ChatFormatting f) {
        if (f == ChatFormatting.RESET) return Style.EMPTY;
        if (rgb(f) != null) return Style.EMPTY.withColor(f);
        return style.applyFormat(f);
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
                if ((next == 'x' || next == 'X') && i + 14 <= s.length()) {
                    StringBuilder hex = new StringBuilder();
                    for (int k = i + 2; k < i + 14; k += 2) if (s.charAt(k) == c) hex.append(s.charAt(k + 1));
                    String code = hex.length() == 6 ? normaliseHex(hex.toString()) : null;
                    if (code != null) {
                        push(out, buf, style);
                        style = Style.EMPTY.withColor(TextColor.fromRgb(hexRgb(code)));
                        i += 14;
                        continue;
                    }
                }
                ChatFormatting f = ChatFormatting.getByCode(next);
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
        Deque<String> names = new ArrayDeque<>();
        Style style = base;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && (s.charAt(i + 1) == '<' || s.charAt(i + 1) == '\\')) {
                buf.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c != '<') { buf.append(c); i++; continue; }
            int close = tagClose(s, i + 1);
            if (close < 0) { buf.append(c); i++; continue; }

            String tag = s.substring(i + 1, close).trim();
            String lower = tag.toLowerCase(Locale.ROOT);
            boolean selfClosing = lower.endsWith("/") && !lower.startsWith("/");
            if (selfClosing) lower = lower.substring(0, lower.length() - 1);
            boolean closing = lower.startsWith("/");
            String body = closing ? lower.substring(1) : lower;
            String name = body.contains(":") ? body.substring(0, body.indexOf(':')) : body;

            if (!closing && "sprite".equals(name) && body.contains(":")) {
                push(out, buf, style);
                out.add(sprite(tag, s.substring(i, close + 1), style));
                i = close + 1;
                continue;
            }

            String put = closing ? null : inserted(name, tag);
            if (put != null) {
                buf.append(put);
                i = close + 1;
                continue;
            }

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
                if (applied == null || !names.contains(name)) { buf.append(s, i, close + 1); i = close + 1; continue; }
                push(out, buf, style);
                while (!names.isEmpty()) {
                    String open = names.pop();
                    style = stack.pop();
                    if (open.equals(name)) break;
                }
                i = close + 1;
                continue;
            }
            if (applied == null) { buf.append(s, i, close + 1); i = close + 1; continue; }
            push(out, buf, style);
            if ("reset".equals(name)) { stack.clear(); names.clear(); style = base; }
            else if (!selfClosing) { stack.push(style); names.push(name); style = applied; }
            else style = applied;
            i = close + 1;
        }
        push(out, buf, style);
        return out;
    }

    private static int tagClose(String s, int from) {
        char quote = 0;
        for (int k = from; k < s.length(); k++) {
            char c = s.charAt(k);
            if (quote != 0) {
                if (c == '\\') k++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '>') return k;
        }
        return s.indexOf('>', from);
    }

    private static String inserted(String name, String tag) {
        return switch (name) {
            case "newline", "br" -> "\n";
            case "lang", "tr", "translate", "lang_or", "tr_or", "translate_or", "key", "selector", "sel", "score", "nbt", "data", "head" -> {
                List<String> args = tagArgs(tag);
                String first = args.size() > 1 ? args.get(1) : "";
                yield switch (name) {
                    case "lang", "tr", "translate" -> Language.getInstance().getOrDefault(first);
                    case "lang_or", "tr_or", "translate_or" -> Language.getInstance().getOrDefault(first, args.size() > 2 ? args.get(2) : first);
                    case "key" -> Component.keybind(first).getString();
                    case "selector", "sel" -> first;
                    case "head" -> "☺";
                    default -> "";
                };
            }
            default -> null;
        };
    }

    private static Run sprite(String tag, String raw, Style style) {
        String body = tag.endsWith("/") ? tag.substring(0, tag.length() - 1) : tag;
        List<String> args = tagArgs(body);
        args.remove(0);
        String atlas, id;
        switch (args.size()) {
            case 1 -> { atlas = ""; id = args.get(0); }
            case 2 -> { atlas = args.get(0); id = args.get(1); }
            case 3 -> { atlas = args.get(0) + ":" + args.get(1); id = args.get(2); }
            default -> { atlas = args.get(0) + ":" + args.get(1); id = args.get(2) + ":" + args.get(3); }
        }
        return SpriteText.run(atlas.trim(), id.trim(), raw, style);
    }

    private static List<String> tagArgs(String tag) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int k = 0; k < tag.length(); k++) {
            char ch = tag.charAt(k);
            if (quote != 0) {
                if (ch == quote) quote = 0;
                else cur.append(ch);
                continue;
            }
            if (ch == '\'' || ch == '"') { quote = ch; continue; }
            if (ch == ':') { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(ch);
        }
        out.add(cur.toString());
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

    private static List<String> tagParts(String body) {
        List<String> args = new ArrayList<>();
        int colon = body.indexOf(':');
        if (colon >= 0) for (String a : body.substring(colon + 1).split(":")) args.add(a.trim());
        return args;
    }

    private static int mix(float t, int a, int b) {
        float k = Math.min(1f, Math.max(0f, t));
        int r = Math.round(((a >> 16) & 0xFF) + k * (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)));
        int g = Math.round(((a >> 8) & 0xFF) + k * (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)));
        int bl = Math.round((a & 0xFF) + k * ((b & 0xFF) - (a & 0xFF)));
        return (r << 16) | (g << 8) | bl;
    }

    private static int hue(float hue) {
        float h = hue * 6;
        int i = (int) Math.floor(h);
        float f = h - i, q = 1 - f;
        float r, g, b;
        switch (i) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static float phaseOf(List<String> args) {
        if (args.isEmpty()) return 0;
        try {
            float p = Float.parseFloat(args.get(args.size() - 1));
            return p < -1 || p > 1 ? 0 : p;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<Integer> coloursOf(List<String> args) {
        List<Integer> out = new ArrayList<>();
        for (String a : args) {
            Integer rgb = colourOf(a);
            if (rgb != null) out.add(rgb);
        }
        return out;
    }

    private static int transition(String body) {
        List<String> args = tagParts(body);
        List<Integer> colours = coloursOf(args);
        if (colours.isEmpty()) return 0xFFFFFF;
        float phase = phaseOf(args);
        boolean negative = phase < 0;
        if (negative) {
            phase += 1;
            java.util.Collections.reverse(colours);
        }
        float steps = 1f / Math.max(1, colours.size() - 1);
        for (int n = 1; n < colours.size(); n++) {
            float at = n * steps;
            if (at < phase) continue;
            float factor = 1 + (phase - at) * (colours.size() - 1);
            return negative ? mix(1 - factor, colours.get(n), colours.get(n - 1)) : mix(factor, colours.get(n - 1), colours.get(n));
        }
        return colours.get(0);
    }

    private static Style shadowed(Style style, String body) {
        List<String> args = tagParts(body);
        if (args.isEmpty()) return style;
        String first = args.get(0);
        if (first.startsWith("#") && first.length() == 9) {
            String hex = normaliseHex(first.substring(0, 7));
            if (hex == null) return style;
            return style.withShadowColor((Integer.parseInt(first.substring(7), 16) << 24) | hexRgb(hex));
        }
        Integer rgb = colourOf(first);
        if (rgb == null) return style;
        float alpha = args.size() > 1 ? phaseOf(args) : 0.25f;
        return style.withShadowColor(((int) (Math.min(1f, Math.max(0f, alpha)) * 255) << 24) | rgb);
    }

    private static List<Run> paint(List<Run> inner, String kind, String body) {
        int total = 0;
        for (Run r : inner) total += r.text().length();
        if (total == 0) return inner;

        List<String> args = tagParts(body);
        boolean rainbow = "rainbow".equals(kind), reversed = false;
        double shift = 0;
        float phase = 0;
        int[] stops = null;
        if (rainbow) {
            String a = args.isEmpty() ? "" : args.get(0);
            if (a.startsWith("!")) {
                reversed = true;
                a = a.substring(1);
            }
            try {
                if (!a.isEmpty()) shift = Integer.parseInt(a) / 10d;
            } catch (NumberFormatException ignored) {
            }
        } else {
            List<Integer> cols = coloursOf(args);
            phase = phaseOf(args);
            if (cols.size() < 2) {
                cols.clear();
                cols.add(0xFFFFFF);
                cols.add(0x000000);
            }
            if (phase < 0) {
                phase += 1;
                java.util.Collections.reverse(cols);
            }
            stops = new int[cols.size()];
            for (int i = 0; i < stops.length; i++) stops[i] = cols.get(i);
        }

        List<Run> out = new ArrayList<>();
        int index = 0;
        for (Run r : inner) {
            for (int k = 0; k < r.text().length(); k++) {
                int rgb;
                if (rainbow) {
                    float at = reversed ? total - 1 - index : index;
                    rgb = hue((float) ((at / total + shift) % 1f));
                } else {
                    float step = total == 1 ? 0 : (float) (stops.length - 1) / (total - 1);
                    float position = index * step + phase * (stops.length - 1);
                    int low = (int) Math.floor(position);
                    rgb = mix(position - low, stops[low % stops.length], stops[(int) Math.ceil(position) % stops.length]);
                }
                out.add(new Run(String.valueOf(r.text().charAt(k)), r.style().withColor(TextColor.fromRgb(rgb))));
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
        String colour = name.replace("grey", "gray");
        for (Colour c : COLOURS) if (c.name().equals(colour)) return c.rgb();
        return null;
    }

    private static Style tagStyle(Style style, String body) {
        if (body.isEmpty()) return null;
        String name = body.contains(":") ? body.substring(0, body.indexOf(':')) : body;
        if ("reset".equals(name)) return Style.EMPTY;

        boolean off = name.startsWith("!");
        if (off) name = name.substring(1);

        if ("click".equals(name) || "hover".equals(name) || "insert".equals(name) || "font".equals(name)) return style;
        if ("shadow".equals(name)) return off ? style.withoutShadow() : shadowed(style, body);
        if ("transition".equals(name)) return style.withColor(TextColor.fromRgb(transition(body)));
        if (name.startsWith("#")) {
            String hex = normaliseHex(name);
            return hex == null ? null : style.withColor(TextColor.fromRgb(hexRgb(hex)));
        }
        if ("color".equals(name) || "colour".equals(name) || "c".equals(name)) {
            if (!body.contains(":")) return style;
            Integer rgb = colourOf(body.substring(body.indexOf(':') + 1));
            return rgb == null ? null : style.withColor(TextColor.fromRgb(rgb));
        }
        String colour = name.replace("grey", "gray");
        for (Colour c : COLOURS)
            if (c.name().equals(colour)) return style.withColor(c.formatting());
        Boolean on = off ? Boolean.FALSE : Boolean.TRUE;
        return switch (name) {
            case "bold", "b" -> style.withBold(on);
            case "italic", "i", "em" -> style.withItalic(on);
            case "underlined", "u" -> style.withUnderlined(on);
            case "strikethrough", "st" -> style.withStrikethrough(on);
            case "obfuscated", "obf" -> style.withObfuscated(on);
            default -> null;
        };
    }

    private static List<Run> jsonRuns(String s) {
        try {
            JsonElement el = JsonParser.parseString(s);
            Component t = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, el).result().orElse(null);
            if (t != null) {
                List<Run> out = new ArrayList<>();
                t.visit((style, str) -> {
                    if (!str.isEmpty()) out.add(new Run(str, style));
                    return java.util.Optional.empty();
                }, Style.EMPTY);
                return out;
            }
        } catch (Throwable ignored) { }
        return List.of(new Run(s, Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
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
        for (Run r : runs) if (SpriteText.tagOf(r.style()) == null) sb.append(r.text());
        return sb.toString();
    }

    public static String plain(String raw, String parsing) {
        return writePlain(runs(raw, parsing));
    }

    public static Component fit(Font tr, List<Run> runs, int maxWidth) {
        MutableComponent out = Component.empty();
        if (maxWidth <= 0) return out;
        int total = 0;
        for (Run r : runs) total += tr.width(r.text());
        if (total <= maxWidth) {
            for (Run r : runs) out.append(Component.literal(r.text()).setStyle(r.style()));
            return out;
        }
        int room = maxWidth - tr.width("…");
        int used = 0;
        for (Run r : runs) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < r.text().length(); i++) {
                int cw = tr.width(String.valueOf(r.text().charAt(i)));
                if (used + cw > room) {
                    if (sb.length() > 0) out.append(Component.literal(sb.toString()).setStyle(r.style()));
                    out.append(Component.literal("…"));
                    return out;
                }
                sb.append(r.text().charAt(i));
                used += cw;
            }
            out.append(Component.literal(sb.toString()).setStyle(r.style()));
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
            if (c.getValue() == k.rgb()) return "&" + k.code();
        return "&#" + String.format("%06x", c.getValue() & 0xFFFFFF);
    }

    private static String miniColour(TextColor c) {
        for (Colour k : COLOURS)
            if (c.getValue() == k.rgb()) return "<" + k.name() + ">";
        return "<#" + String.format("%06x", c.getValue() & 0xFFFFFF) + ">";
    }

    private static String write(List<Run> runs, String reset, boolean colourResets,
                                Function<TextColor, String> colour, Function<Deco, String> deco) {
        StringBuilder sb = new StringBuilder();
        Style prev = Style.EMPTY;
        boolean mini = "<reset>".equals(reset);
        for (Run r : runs) {
            Style now = r.style();
            String sprite = SpriteText.tagOf(now);
            if (sprite != null) {
                if (mini) sb.append(sprite);
                continue;
            }
            if (dropped(prev, now)) { sb.append(reset); prev = Style.EMPTY; }
            TextColor pc = prev.getColor(), nc = now.getColor();
            if (nc != null && (pc == null || pc.getValue() != nc.getValue())) {
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
            MutableComponent t = Component.empty();
            for (Run r : runs) {
                Component sprite = SpriteText.component(r.style());
                if (sprite != null) t.append(sprite);
                else if (SpriteText.tagOf(r.style()) == null)
                    t.append(Component.literal(r.text()).setStyle(r.style()));
            }
            JsonElement el = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, t).result().orElse(null);
            if (el != null) return el.toString();
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] текст не собрался в json, пишу простым", e);
        }
        return writePlain(runs);
    }

    private McText() {}
}
