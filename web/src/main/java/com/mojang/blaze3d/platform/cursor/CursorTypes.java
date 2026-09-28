package com.mojang.blaze3d.platform.cursor;

public final class CursorTypes {
    public static final CursorType ARROW = CursorType.DEFAULT;
    public static final CursorType IBEAM = new CursorType("text");
    public static final CursorType CROSSHAIR = new CursorType("crosshair");
    public static final CursorType POINTING_HAND = new CursorType("pointer");
    public static final CursorType RESIZE_NS = new CursorType("ns-resize");
    public static final CursorType RESIZE_EW = new CursorType("ew-resize");
    public static final CursorType RESIZE_ALL = new CursorType("move");
    public static final CursorType NOT_ALLOWED = new CursorType("not-allowed");

    private CursorTypes() {}
}
