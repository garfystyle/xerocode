package com.xerocode;

import java.nio.charset.StandardCharsets;

public final class Hex {
    public static String of(byte[] raw) {
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                .append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }

    public static String of(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    private Hex() {}
}
