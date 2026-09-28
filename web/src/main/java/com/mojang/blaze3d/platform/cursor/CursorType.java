package com.mojang.blaze3d.platform.cursor;

public final class CursorType {
    public static final CursorType DEFAULT = new CursorType("default");

    private final String css;

    public CursorType(String css) {
        this.css = css;
    }

    public String css() { return css; }
}
