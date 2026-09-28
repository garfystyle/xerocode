package net.minecraft.network.chat;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.ChatFormatting;

public final class TextColor {
    private static final String CUSTOM_COLOR_PREFIX = "#";
    private static final TextColor[] LEGACY = new TextColor[16];

    static {
        for (ChatFormatting f : ChatFormatting.values())
            if (f.isColor()) LEGACY[f.getId()] = new TextColor(f.getColorValue(), f.getName());
    }

    private final int value;
    private final String name;

    private TextColor(int value, String name) {
        this.value = value & 0xFFFFFF;
        this.name = name;
    }

    private TextColor(int value) {
        this(value, null);
    }

    public int getValue() { return value; }

    public String serialize() { return name != null ? name : formatValue(); }

    public String formatValue() { return String.format(Locale.ROOT, "#%06X", value); }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TextColor other && value == other.value && Objects.equals(name, other.name));
    }

    @Override
    public int hashCode() { return Objects.hash(value, name); }

    @Override
    public String toString() { return serialize(); }

    public static TextColor fromLegacyFormat(ChatFormatting format) {
        return format != null && format.isColor() ? LEGACY[format.getId()] : null;
    }

    public static TextColor fromRgb(int rgb) {
        return new TextColor(rgb);
    }

    public static TextColor parseColor(String color) {
        if (color == null) return null;
        if (color.startsWith(CUSTOM_COLOR_PREFIX)) {
            try {
                return fromRgb(Integer.parseInt(color.substring(1), 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        ChatFormatting f = ChatFormatting.getByName(color);
        return f != null && f.isColor() ? LEGACY[f.getId()] : null;
    }
}
