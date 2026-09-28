package net.minecraft;

import java.util.Locale;

public enum ChatFormatting {
    BLACK("BLACK", '0', 0, 0x000000),
    DARK_BLUE("DARK_BLUE", '1', 1, 0x0000AA),
    DARK_GREEN("DARK_GREEN", '2', 2, 0x00AA00),
    DARK_AQUA("DARK_AQUA", '3', 3, 0x00AAAA),
    DARK_RED("DARK_RED", '4', 4, 0xAA0000),
    DARK_PURPLE("DARK_PURPLE", '5', 5, 0xAA00AA),
    GOLD("GOLD", '6', 6, 0xFFAA00),
    GRAY("GRAY", '7', 7, 0xAAAAAA),
    DARK_GRAY("DARK_GRAY", '8', 8, 0x555555),
    BLUE("BLUE", '9', 9, 0x5555FF),
    GREEN("GREEN", 'a', 10, 0x55FF55),
    AQUA("AQUA", 'b', 11, 0x55FFFF),
    RED("RED", 'c', 12, 0xFF5555),
    LIGHT_PURPLE("LIGHT_PURPLE", 'd', 13, 0xFF55FF),
    YELLOW("YELLOW", 'e', 14, 0xFFFF55),
    WHITE("WHITE", 'f', 15, 0xFFFFFF),
    OBFUSCATED("OBFUSCATED", 'k', true),
    BOLD("BOLD", 'l', true),
    STRIKETHROUGH("STRIKETHROUGH", 'm', true),
    UNDERLINE("UNDERLINE", 'n', true),
    ITALIC("ITALIC", 'o', true),
    RESET("RESET", 'r', -1, null);

    public static final char PREFIX_CODE = '§';

    private final String name;
    private final char code;
    private final boolean isFormat;
    private final String toString;
    private final int id;
    private final Integer color;

    ChatFormatting(String name, char code, int id, Integer color) {
        this(name, code, false, id, color);
    }

    ChatFormatting(String name, char code, boolean isFormat) {
        this(name, code, isFormat, -1, null);
    }

    ChatFormatting(String name, char code, boolean isFormat, int id, Integer color) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.code = code;
        this.isFormat = isFormat;
        this.id = id;
        this.color = color;
        this.toString = "§" + code;
    }

    public char getChar() { return code; }
    public int getId() { return id; }
    public boolean isFormat() { return isFormat; }
    public boolean isColor() { return !isFormat && this != RESET; }
    public Integer getColorValue() { return color; }
    public String getName() { return name; }
    public String getSerializedName() { return name; }

    @Override
    public String toString() { return toString; }

    public static String stripFormatting(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == PREFIX_CODE && i + 1 < input.length()
                    && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(input.charAt(i + 1)) >= 0) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static ChatFormatting getByName(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        for (ChatFormatting f : values())
            if (f.name.replace("_", "").equals(n)) return f;
        return null;
    }

    public static ChatFormatting getById(int id) {
        if (id < 0) return RESET;
        for (ChatFormatting f : values()) if (f.id == id) return f;
        return null;
    }

    public static ChatFormatting getByCode(char code) {
        char c = Character.toLowerCase(code);
        for (ChatFormatting f : values()) if (f.code == c) return f;
        return null;
    }
}
