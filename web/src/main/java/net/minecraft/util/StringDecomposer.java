package net.minecraft.util;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public final class StringDecomposer {
    private static final int REPLACEMENT = 0xFFFD;

    private StringDecomposer() {}

    private static boolean feed(Style style, FormattedCharSink out, int pos, char ch) {
        return Character.isSurrogate(ch) ? out.accept(pos, style, REPLACEMENT) : out.accept(pos, style, ch);
    }

    public static boolean iterate(String s, Style style, FormattedCharSink out) {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= n) return out.accept(i, style, REPLACEMENT);
                char low = s.charAt(i + 1);
                if (Character.isLowSurrogate(low)) {
                    if (!out.accept(i, style, Character.toCodePoint(ch, low))) return false;
                    i++;
                } else if (!out.accept(i, style, REPLACEMENT)) {
                    return false;
                }
            } else if (!feed(style, out, i, ch)) {
                return false;
            }
        }
        return true;
    }

    public static boolean iterateBackwards(String s, Style style, FormattedCharSink out) {
        for (int i = s.length() - 1; i >= 0; i--) {
            char ch = s.charAt(i);
            if (Character.isLowSurrogate(ch)) {
                if (i - 1 < 0) return out.accept(0, style, REPLACEMENT);
                char high = s.charAt(i - 1);
                if (Character.isHighSurrogate(high)) {
                    if (!out.accept(--i, style, Character.toCodePoint(high, ch))) return false;
                } else if (!out.accept(i, style, REPLACEMENT)) {
                    return false;
                }
            } else if (!feed(style, out, i, ch)) {
                return false;
            }
        }
        return true;
    }

    public static boolean iterateFormatted(String s, Style style, FormattedCharSink out) {
        return iterateFormatted(s, 0, style, out);
    }

    public static boolean iterateFormatted(String s, int offset, Style style, FormattedCharSink out) {
        return iterateFormatted(s, offset, style, style, out);
    }

    public static boolean iterateFormatted(String s, int offset, Style current, Style reset, FormattedCharSink out) {
        int n = s.length();
        Style style = current;
        for (int i = offset; i < n; i++) {
            char ch = s.charAt(i);
            if (ch == '§') {
                if (i + 1 >= n) break;
                ChatFormatting f = ChatFormatting.getByCode(s.charAt(i + 1));
                if (f != null) style = f == ChatFormatting.RESET ? reset : style.applyLegacyFormat(f);
                i++;
            } else if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= n) return out.accept(i, style, REPLACEMENT);
                char low = s.charAt(i + 1);
                if (Character.isLowSurrogate(low)) {
                    if (!out.accept(i, style, Character.toCodePoint(ch, low))) return false;
                    i++;
                } else if (!out.accept(i, style, REPLACEMENT)) {
                    return false;
                }
            } else if (!feed(style, out, i, ch)) {
                return false;
            }
        }
        return true;
    }

    public static boolean iterateFormatted(FormattedText text, Style root, FormattedCharSink out) {
        return text.visit((style, contents) -> iterateFormatted(contents, 0, style, out)
                ? Optional.empty() : FormattedText.STOP_ITERATION, root).isEmpty();
    }

    public static String getPlainText(FormattedText text) {
        StringBuilder sb = new StringBuilder();
        iterateFormatted(text, Style.EMPTY, (pos, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }
}
