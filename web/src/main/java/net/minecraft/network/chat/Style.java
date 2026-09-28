package net.minecraft.network.chat;

import java.util.Objects;
import net.minecraft.ChatFormatting;

public final class Style {
    public static final Style EMPTY = new Style(null, null, null, null, null, null, null, null);

    private final TextColor color;
    private final Integer shadowColor;
    private final Boolean bold;
    private final Boolean italic;
    private final Boolean underlined;
    private final Boolean strikethrough;
    private final Boolean obfuscated;
    private final String insertion;

    private Style(TextColor color, Integer shadowColor, Boolean bold, Boolean italic, Boolean underlined,
                  Boolean strikethrough, Boolean obfuscated, String insertion) {
        this.color = color;
        this.shadowColor = shadowColor;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.insertion = insertion;
    }

    private static Style make(TextColor color, Integer shadowColor, Boolean bold, Boolean italic, Boolean underlined,
                              Boolean strikethrough, Boolean obfuscated, String insertion) {
        Style s = new Style(color, shadowColor, bold, italic, underlined, strikethrough, obfuscated, insertion);
        return s.equals(EMPTY) ? EMPTY : s;
    }

    public TextColor getColor() { return color; }
    public Integer getShadowColor() { return shadowColor; }
    public boolean isBold() { return bold == Boolean.TRUE; }
    public boolean isItalic() { return italic == Boolean.TRUE; }
    public boolean isStrikethrough() { return strikethrough == Boolean.TRUE; }
    public boolean isUnderlined() { return underlined == Boolean.TRUE; }
    public boolean isObfuscated() { return obfuscated == Boolean.TRUE; }
    public boolean isEmpty() { return this == EMPTY; }
    public String getInsertion() { return insertion; }

    Boolean boldRaw() { return bold; }
    Boolean italicRaw() { return italic; }
    Boolean underlinedRaw() { return underlined; }
    Boolean strikethroughRaw() { return strikethrough; }
    Boolean obfuscatedRaw() { return obfuscated; }

    public Style withColor(TextColor c) { return Objects.equals(color, c) ? this : make(c, shadowColor, bold, italic, underlined, strikethrough, obfuscated, insertion); }
    public Style withColor(ChatFormatting f) { return withColor(f != null ? TextColor.fromLegacyFormat(f) : null); }
    public Style withColor(int rgb) { return withColor(TextColor.fromRgb(rgb)); }
    public Style withShadowColor(int argb) { return make(color, argb, bold, italic, underlined, strikethrough, obfuscated, insertion); }
    public Style withoutShadow() { return make(color, 0, bold, italic, underlined, strikethrough, obfuscated, insertion); }
    public Style withBold(Boolean v) { return Objects.equals(bold, v) ? this : make(color, shadowColor, v, italic, underlined, strikethrough, obfuscated, insertion); }
    public Style withItalic(Boolean v) { return Objects.equals(italic, v) ? this : make(color, shadowColor, bold, v, underlined, strikethrough, obfuscated, insertion); }
    public Style withUnderlined(Boolean v) { return Objects.equals(underlined, v) ? this : make(color, shadowColor, bold, italic, v, strikethrough, obfuscated, insertion); }
    public Style withStrikethrough(Boolean v) { return Objects.equals(strikethrough, v) ? this : make(color, shadowColor, bold, italic, underlined, v, obfuscated, insertion); }
    public Style withObfuscated(Boolean v) { return Objects.equals(obfuscated, v) ? this : make(color, shadowColor, bold, italic, underlined, strikethrough, v, insertion); }
    public Style withInsertion(String v) { return Objects.equals(insertion, v) ? this : make(color, shadowColor, bold, italic, underlined, strikethrough, obfuscated, v); }

    public Style applyFormat(ChatFormatting format) {
        TextColor c = color;
        Boolean b = bold, i = italic, s = strikethrough, u = underlined, o = obfuscated;
        switch (format) {
            case OBFUSCATED -> o = true;
            case BOLD -> b = true;
            case STRIKETHROUGH -> s = true;
            case UNDERLINE -> u = true;
            case ITALIC -> i = true;
            case RESET -> { return EMPTY; }
            default -> c = TextColor.fromLegacyFormat(format);
        }
        return make(c, shadowColor, b, i, u, s, o, insertion);
    }

    public Style applyLegacyFormat(ChatFormatting format) {
        TextColor c = color;
        Boolean b = bold, i = italic, s = strikethrough, u = underlined, o = obfuscated;
        switch (format) {
            case OBFUSCATED -> o = true;
            case BOLD -> b = true;
            case STRIKETHROUGH -> s = true;
            case UNDERLINE -> u = true;
            case ITALIC -> i = true;
            case RESET -> { return EMPTY; }
            default -> {
                o = false;
                b = false;
                s = false;
                u = false;
                i = false;
                c = TextColor.fromLegacyFormat(format);
            }
        }
        return make(c, shadowColor, b, i, u, s, o, insertion);
    }

    public Style applyFormats(ChatFormatting... formats) {
        Style s = this;
        for (ChatFormatting f : formats) {
            if (f == ChatFormatting.RESET) return EMPTY;
            s = s.applyFormat(f);
        }
        return s;
    }

    public Style applyTo(Style other) {
        if (this == EMPTY) return other;
        if (other == EMPTY) return this;
        return make(color != null ? color : other.color,
                shadowColor != null ? shadowColor : other.shadowColor,
                bold != null ? bold : other.bold,
                italic != null ? italic : other.italic,
                underlined != null ? underlined : other.underlined,
                strikethrough != null ? strikethrough : other.strikethrough,
                obfuscated != null ? obfuscated : other.obfuscated,
                insertion != null ? insertion : other.insertion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Style s)) return false;
        return Objects.equals(color, s.color) && Objects.equals(shadowColor, s.shadowColor)
                && Objects.equals(bold, s.bold) && Objects.equals(italic, s.italic)
                && Objects.equals(underlined, s.underlined) && Objects.equals(strikethrough, s.strikethrough)
                && Objects.equals(obfuscated, s.obfuscated) && Objects.equals(insertion, s.insertion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, shadowColor, bold, italic, underlined, strikethrough, obfuscated, insertion);
    }

    @Override
    public String toString() {
        return "Style{color=" + color + ", bold=" + bold + ", italic=" + italic + ", underlined=" + underlined
                + ", strikethrough=" + strikethrough + ", obfuscated=" + obfuscated + "}";
    }
}
