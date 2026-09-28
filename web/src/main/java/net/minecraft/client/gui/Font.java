package net.minecraft.client.gui;

import com.xerocode.web.Glyphs;
import java.util.List;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

public class Font {
    public final int lineHeight = 9;
    private final StringSplitter splitter;

    public Font() {
        splitter = new StringSplitter((cp, style) -> Glyphs.advance(cp, style.isBold()));
    }

    public int width(String str) { return (int) Math.ceil(splitter.stringWidth(str)); }
    public int width(FormattedText text) { return (int) Math.ceil(splitter.stringWidth(text)); }
    public int width(FormattedCharSequence text) { return (int) Math.ceil(splitter.stringWidth(text)); }

    public String plainSubstrByWidth(String str, int width, boolean reverse) {
        return reverse ? splitter.plainTailByWidth(str, width, Style.EMPTY) : splitter.plainHeadByWidth(str, width, Style.EMPTY);
    }

    public String plainSubstrByWidth(String str, int width) {
        return splitter.plainHeadByWidth(str, width, Style.EMPTY);
    }

    public FormattedText substrByWidth(FormattedText text, int width) {
        return splitter.headByWidth(text, width, Style.EMPTY);
    }

    public int wordWrapHeight(FormattedText input, int textWidth) {
        return 9 * splitter.splitLines(input, textWidth, Style.EMPTY).size();
    }

    public int wordWrapHeight(String input, int textWidth) {
        return wordWrapHeight(FormattedText.of(input), textWidth);
    }

    public List<FormattedCharSequence> split(FormattedText input, int maxWidth) {
        return Language.getInstance().getVisualOrder(splitter.splitLines(input, maxWidth, Style.EMPTY));
    }

    public List<FormattedText> splitIgnoringLanguage(FormattedText input, int maxWidth) {
        return splitter.splitLines(input, maxWidth, Style.EMPTY);
    }

    public boolean isBidirectional() { return false; }

    public StringSplitter getSplitter() { return splitter; }

    public PreparedText prepareText(FormattedCharSequence text, float x, float y, int color, boolean shadow,
                                    boolean includeEmpty, int background) {
        Prepared p = new Prepared(x, y, color, shadow);
        text.accept(p);
        return p;
    }

    public PreparedText prepareText(String text, float x, float y, int color, boolean shadow, int background) {
        Prepared p = new Prepared(x, y, color, shadow);
        StringDecomposer.iterateFormatted(text, Style.EMPTY, p);
        return p;
    }

    public interface PreparedText {
        void visit(GlyphVisitor visitor);

        ScreenRectangle bounds();
    }

    public interface GlyphVisitor {
        void glyph(int codepoint, float x, float y, int color, int shadowColor, Style style, boolean shadow);

        void effect(float x0, float y0, float x1, float y1, int color, int shadowColor, boolean shadow);
    }

    private static final class Prepared implements PreparedText, net.minecraft.util.FormattedCharSink {
        private final int color;
        private final boolean shadow;
        private float x;
        private final float y;
        private float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        private final java.util.ArrayList<Object[]> items = new java.util.ArrayList<>();

        Prepared(float x, float y, int color, boolean shadow) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
        }

        private void mark(float l, float t, float r, float b) {
            left = Math.min(left, l);
            top = Math.min(top, t);
            right = Math.max(right, r);
            bottom = Math.max(bottom, b);
        }

        @Override
        public boolean accept(int position, Style style, int cp) {
            boolean bold = style.isBold();
            int textColor = style.getColor() != null ? (color & 0xFF000000) | style.getColor().getValue() : color;
            int shadowColor;
            Integer custom = style.getShadowColor();
            if (custom != null) {
                float ta = ((textColor >>> 24) & 0xFF) / 255f;
                shadowColor = ta != 1f ? ((Math.round(ta * ((custom >>> 24) & 0xFF))) << 24) | (custom & 0xFFFFFF) : custom;
            } else {
                shadowColor = shadow ? scale(textColor) : 0;
            }
            boolean hasShadow = shadowColor != 0;
            int glyph = Glyphs.pick(cp, style.isObfuscated());
            float advance = Glyphs.advance(cp, bold);
            float effectX0 = position == 0 ? x - 1 : x;
            if (Glyphs.visible(glyph)) {
                float extra = bold ? 0.1f : 0;
                float l = x + Glyphs.left(glyph) - extra;
                float t = y + Glyphs.up(glyph) - extra;
                float r = x + Glyphs.right(glyph) + (hasShadow ? 1 : 0) + extra;
                float b = y + Glyphs.down(glyph) + (hasShadow ? 1 : 0) + extra;
                if (style.isItalic()) {
                    float st = 1 - 0.25f * Glyphs.up(glyph), sb = 1 - 0.25f * Glyphs.down(glyph);
                    l += Math.min(st, sb);
                    r += Math.max(st, sb);
                }
                mark(l, t, r, b);
                items.add(new Object[]{glyph, x, y, textColor, shadowColor, style});
            }
            if (style.isStrikethrough()) effect(effectX0, y + 3.5f, x + advance, y + 4.5f, textColor, shadowColor);
            if (style.isUnderlined()) effect(effectX0, y + 8f, x + advance, y + 9f, textColor, shadowColor);
            x += advance;
            return true;
        }

        private void effect(float x0, float y0, float x1, float y1, int c, int sc) {
            mark(x0, y0, x1 + (sc != 0 ? 1 : 0), y1 + (sc != 0 ? 1 : 0));
            items.add(new Object[]{null, x0, y0, c, sc, new float[]{x1, y1}});
        }

        private static int scale(int argb) {
            int r = (int) (((argb >> 16) & 0xFF) * 0.25f), g = (int) (((argb >> 8) & 0xFF) * 0.25f), b = (int) ((argb & 0xFF) * 0.25f);
            return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
        }

        @Override
        public void visit(GlyphVisitor visitor) {
            for (Object[] it : items) {
                int c = (Integer) it[3], sc = (Integer) it[4];
                if (it[0] == null) {
                    float[] end = (float[]) it[5];
                    visitor.effect((Float) it[1], (Float) it[2], end[0], end[1], c, sc, sc != 0);
                } else {
                    visitor.glyph((Integer) it[0], (Float) it[1], (Float) it[2], c, sc, (Style) it[5], sc != 0);
                }
            }
        }

        @Override
        public ScreenRectangle bounds() {
            if (left >= right || top >= bottom) return null;
            int l = (int) Math.floor(left), t = (int) Math.floor(top);
            int r = (int) Math.ceil(right), b = (int) Math.ceil(bottom);
            return new ScreenRectangle(l, t, r - l, b - t);
        }
    }
}
