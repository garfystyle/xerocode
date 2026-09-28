package net.minecraft.util;

public final class Mth {
    private Mth() {}

    public static int floor(float v) { return (int) Math.floor(v); }
    public static int floor(double v) { return (int) Math.floor(v); }
    public static int ceil(float v) { return (int) Math.ceil(v); }
    public static int ceil(double v) { return (int) Math.ceil(v); }
    public static int clamp(int v, int min, int max) { return Math.min(Math.max(v, min), max); }
    public static float clamp(float v, float min, float max) { return v < min ? min : Math.min(v, max); }
    public static double clamp(double v, double min, double max) { return v < min ? min : Math.min(v, max); }
    public static float lerp(float t, float a, float b) { return a + t * (b - a); }
    public static double lerp(double t, double a, double b) { return a + t * (b - a); }
}
