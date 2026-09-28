package com.xerocode.web;

import net.minecraft.client.Minecraft;

public final class Input {
    private static final boolean[] KEYS = new boolean[512];
    private static final boolean[] BUTTONS = new boolean[8];
    private static double rawX, rawY;
    private static String clipboard = "";
    private static Object focusOwner;
    private static boolean focusWanted, focusDrawn;
    private static double caretX, caretY;
    private static String selText = "", allText = "";
    private static final StringBuilder rects = new StringBuilder();

    private Input() {}

    public static boolean down(int key) { return key >= 0 && key < KEYS.length && KEYS[key]; }

    public static boolean mouseDown(int button) { return button >= 0 && button < BUTTONS.length && BUTTONS[button]; }

    public static double rawMouseX() { return rawX; }

    public static double rawMouseY() { return rawY; }

    static void key(int key, boolean pressed) {
        if (key >= 0 && key < KEYS.length) KEYS[key] = pressed;
    }

    static void button(int button, boolean pressed) {
        if (button >= 0 && button < BUTTONS.length) BUTTONS[button] = pressed;
    }

    static void releaseAll() {
        java.util.Arrays.fill(KEYS, false);
        java.util.Arrays.fill(BUTTONS, false);
    }

    static void mouse(double x, double y) {
        rawX = x;
        rawY = y;
    }

    public static boolean ctrl() { return down(341) || down(345) || down(343) || down(347); }

    public static boolean shift() { return down(340) || down(344); }

    public static boolean alt() { return down(342) || down(346); }

    public static String clipboard() {
        String sys = Js.clipboard();
        if (sys != null && !sys.isEmpty()) clipboard = sys;
        return clipboard;
    }

    public static void setClipboard(String text) {
        clipboard = text == null ? "" : text;
        Js.setClipboard(clipboard);
    }

    public static void textFocus(Object owner, boolean on) {
        if (on) {
            focusOwner = owner;
            focusWanted = true;
        } else if (focusOwner == owner) {
            focusOwner = null;
            focusWanted = false;
        }
    }

    public static void drawn(Object owner) {
        if (owner == focusOwner) focusDrawn = true;
    }

    public static void caretAt(double x, double y) {
        caretX = x;
        caretY = y;
    }

    public static void selection(String selected, String all) {
        selText = selected == null ? "" : selected;
        allText = all == null ? "" : all;
    }

    public static void textRect(int x, int y, int w, int h) {
        if (rects.length() > 0) rects.append(',');
        rects.append(x).append(',').append(y).append(',').append(w).append(',').append(h);
    }

    public static boolean touch() {
        return Js.touch();
    }

    static void flushFocus() {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        boolean on = focusWanted && focusDrawn;
        focusDrawn = false;
        Js.textFocus(on, caretX * scale, caretY * scale, rects.toString(), scale, selText, allText);
        rects.setLength(0);
        selText = "";
        allText = "";
    }
}
