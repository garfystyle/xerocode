package com.xerocode.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.EditBox;

final class Hsv {
    float h, s, v;
    private EditBox field;
    private boolean writing;

    Hsv(float h, float s, float v) {
        this.h = h;
        this.s = s;
        this.v = v;
    }

    Hsv(int rgb) { set(rgb); }

    int rgb() { return McText.hsvRgb(h, s, v); }

    void set(int rgb) {
        float[] hsv = McText.rgbHsv(rgb);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
    }

    void keep(int rgb) {
        float[] hsv = McText.rgbHsv(rgb);
        if (hsv[1] > 0.001f) h = hsv[0];
        if (hsv[2] > 0.001f) s = hsv[1];
        v = hsv[2];
    }

    void square(double across, double down) {
        s = clamp(across);
        v = 1f - clamp(down);
    }

    void hue(double at) { h = clamp(at); }

    void sat(double at) { s = clamp(at); }

    void val(double at) { v = clamp(at); }

    static float clamp(double at) { return (float) Math.max(0, Math.min(1, at)); }

    static String hex(int rgb) { return "#" + String.format("%06x", rgb & 0xFFFFFF); }

    static Integer parse(String text) {
        String normalised = McText.normaliseHex(text);
        return normalised == null ? null : McText.hexRgb(normalised);
    }

    void bind(EditBox field, Consumer<String> typed) {
        this.field = field;
        field.setResponder(text -> { if (!writing) typed.accept(text); });
    }

    void write(String text) {
        if (field == null) return;
        writing = true;
        field.setValue(text);
        writing = false;
    }
}
