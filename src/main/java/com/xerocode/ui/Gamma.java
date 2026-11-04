package com.xerocode.ui;

public final class Gamma {
    public record Preset(String name, double hue, double strength, int accent) {}

    public static final Preset[] PRESETS = {
            new Preset("Синяя", Double.NaN, 1, 0x59A6FF),
            new Preset("Графит", Double.NaN, 0, 0xB4BECC),
            new Preset("Индиго", 285, 2.2, 0x9C8CFF),
            new Preset("Слива", 330, 2.2, 0xE27BD2),
            new Preset("Вино", 15, 2.2, 0xFF7F8F),
            new Preset("Янтарь", 70, 2.2, 0xFFB648),
            new Preset("Лес", 150, 2.2, 0x5CCF8A),
            new Preset("Океан", 210, 2.2, 0x3CC7D8)};
    public static final int CUSTOM = PRESETS.length;
    public static final int COUNT = PRESETS.length + 1;
    public static final String CUSTOM_NAME = "Своя";
    public static final int CONTRAST_NORMAL = 1;
    public static final int TONE_NORMAL = 1;

    private static final double[] DARK_SHIFT = {0.055, 0, -0.075, -0.175};
    private static final double[] LIGHT_SHIFT = {-0.05, 0, 0.02, 0.05};
    private static final double[] INK_PULL = {0, 0, 0.18, 0.30};
    private static final double[] BLOCK_CHROMA = {0.62, 1, 1.28};
    private static final double[] CODE_CHROMA = {0.62, 1, 1.25};
    private static final double TINT_L = 0.62, TINT_C = 0.16, TINT_MAX = 3, BASE_HUE = 262;
    private static final double ACCENT_C = 0.15, ACCENT_SCALE_MAX = 1.25;
    private static final double DARK_ACCENT_L = 0.68, LIGHT_ACCENT_L = 0.58;
    private static final double MIN_FILL_CONTRAST = 2.2;

    private final boolean light, ownAccent;
    private final double hue, strength, shift, pull;
    private final int accent;
    private final double accentHue, accentScale;

    private Gamma(boolean light, double hue, double strength, int accent, boolean ownAccent, int contrast) {
        this.light = light;
        this.hue = hue;
        this.strength = strength;
        this.accent = accent;
        this.ownAccent = ownAccent;
        this.shift = (light ? LIGHT_SHIFT : DARK_SHIFT)[contrast];
        this.pull = INK_PULL[contrast];
        double[] a = lch(accent);
        this.accentHue = a[2];
        this.accentScale = Math.min(ACCENT_SCALE_MAX, a[1] / ACCENT_C);
    }

    public static Gamma of(int gamma, int accent, int tint, int contrast, boolean light) {
        if (gamma == CUSTOM) {
            double[] t = lch(tint);
            boolean grey = t[1] < 0.004;
            return new Gamma(light, grey ? Double.NaN : t[2],
                    grey ? 0 : Math.min(TINT_MAX, t[1] / TINT_C * TINT_MAX), accent, true, contrast);
        }
        Preset p = PRESETS[gamma];
        return new Gamma(light, p.hue(), p.strength(), p.accent(), gamma != 0, contrast);
    }

    public static boolean plain(int gamma, int contrast) {
        return gamma == 0 && contrast == CONTRAST_NORMAL;
    }

    public static int tint(Preset p) {
        double h = Double.isNaN(p.hue()) ? BASE_HUE : p.hue();
        return rgb(TINT_L, p.strength() * TINT_C / TINT_MAX, h);
    }

    public static int block(int rgb, int tone) { return rechroma(rgb, BLOCK_CHROMA[tone]); }

    public static int code(int rgb, int tone) { return rechroma(rgb, CODE_CHROMA[tone]); }

    private static int rechroma(int rgb, double k) {
        if (k == 1) return rgb & 0xFFFFFF;
        double[] c = lch(rgb);
        return rgb(c[0], c[1] * k, c[2]);
    }

    int surface(int argb) { return repaint(argb, shift); }

    int edge(int argb) { return repaint(argb, shift / 2); }

    int plainTint(int argb) { return repaint(argb, 0); }

    int ink(int argb, int top) {
        double[] c = lch(argb);
        double[] t = lch(top);
        return alpha(argb) | toned(c[0] + (t[0] - c[0]) * pull, c);
    }

    int accented(int argb) {
        if (!ownAccent) return argb;
        double[] c = lch(argb);
        return alpha(argb) | rgb(c[0], c[1] * accentScale, accentHue);
    }

    int accent(int base) {
        if (!ownAccent) return base;
        double[] c = lch(accent);
        return rgb(light ? Math.min(c[0], LIGHT_ACCENT_L) : Math.max(c[0], DARK_ACCENT_L), c[1], c[2]);
    }

    static int onFill(int fill) {
        return contrast(0xFFFFFF, fill) >= MIN_FILL_CONTRAST ? 0xFFFFFF : 0x14171D;
    }

    private int repaint(int argb, double by) {
        double[] c = lch(argb);
        return alpha(argb) | toned(c[0] + by, c);
    }

    private int toned(double l, double[] c) {
        double h = Double.isNaN(hue) ? c[2] : hue;
        return rgb(Math.max(0, Math.min(1, l)), c[1] * strength, h);
    }

    private static int alpha(int argb) { return argb & 0xFF000000; }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int rgb) {
        return 0.2126 * linear((rgb >> 16) & 0xFF) + 0.7152 * linear((rgb >> 8) & 0xFF)
                + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double encoded(double v) {
        return v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
    }

    private static double root(double v) { return v < 0 ? -Math.pow(-v, 1 / 3.0) : Math.pow(v, 1 / 3.0); }

    static double[] lch(int rgb) {
        double r = linear((rgb >> 16) & 0xFF), g = linear((rgb >> 8) & 0xFF), b = linear(rgb & 0xFF);
        double l = root(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = root(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = root(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        double lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        double a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        double bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
        double hue = Math.toDegrees(Math.atan2(bb, a));
        return new double[]{lightness, Math.sqrt(a * a + bb * bb), hue < 0 ? hue + 360 : hue};
    }

    private static double[] linearRgb(double lightness, double chroma, double hue) {
        double a = chroma * Math.cos(Math.toRadians(hue)), b = chroma * Math.sin(Math.toRadians(hue));
        double l = lightness + 0.3963377774 * a + 0.2158037573 * b;
        double m = lightness - 0.1055613458 * a - 0.0638541728 * b;
        double s = lightness - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return new double[]{
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s};
    }

    private static boolean inside(double[] c) {
        for (double v : c) if (v < -0.0005 || v > 1.0005) return false;
        return true;
    }

    private static int channel(double v) {
        return (int) Math.round(encoded(Math.max(0, Math.min(1, v))) * 255);
    }

    static int rgb(double lightness, double chroma, double hue) {
        double[] c = linearRgb(lightness, chroma, hue);
        if (!inside(c)) {
            double lo = 0, hi = chroma;
            for (int i = 0; i < 14; i++) {
                double mid = (lo + hi) / 2;
                if (inside(linearRgb(lightness, mid, hue))) lo = mid;
                else hi = mid;
            }
            c = linearRgb(lightness, lo, hue);
        }
        return channel(c[0]) << 16 | channel(c[1]) << 8 | channel(c[2]);
    }
}
