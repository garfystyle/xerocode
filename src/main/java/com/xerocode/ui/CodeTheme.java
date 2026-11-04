package com.xerocode.ui;

import com.xerocode.Settings;
import xero.dialect.Ink;

final class CodeTheme {
    private static final int[] DARK = new int[19], LIGHT = new int[19];
    private static final int[][] TONED = new int[6][];

    static {
        dark(Ink.PLAIN, 0xD6DCEB);
        dark(Ink.KEYWORD, 0xC59BF5);
        dark(Ink.LITERAL, 0xF2A36B);
        dark(Ink.NUMBER, 0xF2A36B);
        dark(Ink.STRING, 0xA9D88C);
        dark(Ink.ESCAPE, 0x7FD6E8);
        dark(Ink.PLACEHOLDER, 0xF5CE7A);
        dark(Ink.COMMENT, 0x6C7892);
        dark(Ink.OPERATOR, 0x8FC9E8);
        dark(Ink.PUNCT, 0x98A2BC);
        dark(Ink.ACTION, 0x7FB1FA);
        dark(Ink.CONDITION, 0x6FDCC2);
        dark(Ink.FUNCTION, 0xF2D48A);
        dark(Ink.EVENT, 0xF59AB5);
        dark(Ink.VALUE, 0x7FD6C4);
        dark(Ink.VARIABLE, 0xE4C6A0);
        dark(Ink.TARGET, 0xF48C8C);
        dark(Ink.TYPE, 0xF5CE7A);
        dark(Ink.DECORATOR, 0xBFA4F5);
        light(Ink.PLAIN, 0x232936);
        light(Ink.KEYWORD, 0x7A2FC9);
        light(Ink.LITERAL, 0xB4560C);
        light(Ink.NUMBER, 0xB4560C);
        light(Ink.STRING, 0x3B7A19);
        light(Ink.ESCAPE, 0x0A7A92);
        light(Ink.PLACEHOLDER, 0x9A6A00);
        light(Ink.COMMENT, 0x7A8499);
        light(Ink.OPERATOR, 0x1B6E96);
        light(Ink.PUNCT, 0x5A6377);
        light(Ink.ACTION, 0x1E5FCB);
        light(Ink.CONDITION, 0x0B7F68);
        light(Ink.FUNCTION, 0x8A6400);
        light(Ink.EVENT, 0xC22D63);
        light(Ink.VALUE, 0x0B7F72);
        light(Ink.VARIABLE, 0x8A5A22);
        light(Ink.TARGET, 0xC23232);
        light(Ink.TYPE, 0x9A6A00);
        light(Ink.DECORATOR, 0x6A3FC9);
    }

    private CodeTheme() {}

    private static void dark(byte kind, int rgb) { DARK[kind] = 0xFF000000 | rgb; }

    private static void light(byte kind, int rgb) { LIGHT[kind] = 0xFF000000 | rgb; }

    static int[] palette() {
        int tone = Settings.get().codeTone;
        int[] base = Theme.LIGHT ? LIGHT : DARK;
        if (tone == Gamma.TONE_NORMAL) return base;
        int at = (Theme.LIGHT ? 3 : 0) + tone;
        if (TONED[at] == null) {
            TONED[at] = new int[base.length];
            for (int i = 0; i < base.length; i++)
                TONED[at][i] = base[i] == 0 ? 0 : 0xFF000000 | Gamma.code(base[i], tone);
        }
        return TONED[at];
    }

    static int stamp() { return (Theme.LIGHT ? 2 : 1) + Settings.get().codeTone * 4; }

    static int kindColor(String kind) {
        int[] p = palette();
        return switch (kind == null ? "" : kind) {
            case "action" -> p[Ink.ACTION];
            case "condition" -> p[Ink.CONDITION];
            case "event" -> p[Ink.EVENT];
            case "value" -> p[Ink.VALUE];
            case "function" -> p[Ink.FUNCTION];
            case "keyword", "decorator" -> p[Ink.KEYWORD];
            case "variable" -> p[Ink.VARIABLE];
            case "target" -> p[Ink.TARGET];
            case "enum" -> p[Ink.LITERAL];
            case "field" -> p[Ink.PLACEHOLDER];
            default -> p[Ink.PUNCT];
        };
    }

    static String kindMark(String kind) {
        return switch (kind == null ? "" : kind) {
            case "action" -> "д";
            case "condition" -> "у";
            case "event" -> "с";
            case "value" -> "з";
            case "function" -> "ф";
            case "keyword" -> "к";
            case "decorator" -> "@";
            case "variable" -> "п";
            case "target" -> "ц";
            case "enum" -> "м";
            case "field" -> "а";
            default -> "·";
        };
    }
}
