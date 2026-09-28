package com.xerocode.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class Cube {
    private static final int SS = 4;
    private static final int SIZE = ItemGen.CUBE * SS;
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};

    static final class Quad {
        final double[][] pos;
        final double[][] uv;
        final BufferedImage tex;
        final int tint;
        final double shade;

        Quad(double[][] pos, double[][] uv, BufferedImage tex, int tint, double shade) {
            this.pos = pos;
            this.uv = uv;
            this.tex = tex;
            this.tint = tint;
            this.shade = shade;
        }
    }

    private Cube() {}

    static List<Quad> elements(JsonArray elements, Function<String, BufferedImage> textures, int[] tints, double[][] m) {
        List<Quad> out = new ArrayList<>();
        for (JsonElement ee : elements) {
            JsonObject el = ee.getAsJsonObject();
            double[] from = vec(el.getAsJsonArray("from")), to = vec(el.getAsJsonArray("to"));
            JsonObject er = el.has("rotation") ? el.getAsJsonObject("rotation") : null;
            JsonObject faces = el.getAsJsonObject("faces");
            if (faces == null) continue;
            for (String face : FACES) {
                if (!faces.has(face)) continue;
                JsonObject f = faces.getAsJsonObject(face);
                BufferedImage tex = textures.apply(f.get("texture").getAsString());
                if (tex == null) continue;
                double[] uv = f.has("uv") ? vec(f.getAsJsonArray("uv")) : defaultUv(face, from, to);
                int rotation = f.has("rotation") ? f.get("rotation").getAsInt() : 0;
                int tint = f.has("tintindex") ? tints[Math.max(0, Math.min(15, f.get("tintindex").getAsInt()))] : 0xFFFFFFFF;
                double[][] corners = corners(face, from, to);
                double[][] uvs = {{uv[0] / 16, uv[1] / 16}, {uv[0] / 16, uv[3] / 16}, {uv[2] / 16, uv[3] / 16}, {uv[2] / 16, uv[1] / 16}};
                int shift = ((rotation / 90) % 4 + 4) % 4;
                double[][] ruv = new double[4][];
                for (int i = 0; i < 4; i++) ruv[i] = uvs[(i + shift) % 4];
                double[][] pos = new double[4][];
                for (int i = 0; i < 4; i++) {
                    double[] p = corners[i];
                    if (er != null) p = rotateElement(p, er);
                    pos[i] = apply(m, new double[]{p[0] / 16, p[1] / 16, p[2] / 16});
                }
                out.add(new Quad(pos, ruv, tex, tint, shade(face, er)));
            }
        }
        return out;
    }

    static double[] apply(double[][] m, double[] p) {
        if (m == null) return p;
        return new double[]{
                m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2] + m[0][3],
                m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2] + m[1][3],
                m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2] + m[2][3]};
    }

    static BufferedImage render(List<Quad> quads, JsonObject gui) {
        double[] rot = {30, 225, 0}, tr = {0, 0, 0}, sc = {0.625, 0.625, 0.625};
        if (gui != null) {
            if (gui.has("rotation")) rot = vec(gui.getAsJsonArray("rotation"));
            if (gui.has("translation")) tr = vec(gui.getAsJsonArray("translation"));
            if (gui.has("scale")) sc = vec(gui.getAsJsonArray("scale"));
        }
        double[][] m = rotation(Math.toRadians(rot[0]), Math.toRadians(rot[1]), Math.toRadians(rot[2]));
        int[] color = new int[SIZE * SIZE];
        float[] depth = new float[SIZE * SIZE];
        java.util.Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        for (Quad q : quads) {
            double shade = q.shade >= 0 ? q.shade : normalShade(q.pos);
            double[][] screen = new double[4][];
            for (int i = 0; i < 4; i++) {
                double[] p = q.pos[i];
                double x = (p[0] - 0.5) * sc[0], y = (p[1] - 0.5) * sc[1], z = (p[2] - 0.5) * sc[2];
                double rx = m[0][0] * x + m[0][1] * y + m[0][2] * z + tr[0] / 16;
                double ry = m[1][0] * x + m[1][1] * y + m[1][2] * z + tr[1] / 16;
                double rz = m[2][0] * x + m[2][1] * y + m[2][2] * z + tr[2] / 16;
                screen[i] = new double[]{(rx + 0.5) * SIZE, (0.5 - ry) * SIZE, rz};
            }
            tri(color, depth, screen[0], screen[1], screen[2], q.uv[0], q.uv[1], q.uv[2], q.tex, q.tint, shade);
            tri(color, depth, screen[0], screen[2], screen[3], q.uv[0], q.uv[2], q.uv[3], q.tex, q.tint, shade);
        }
        BufferedImage out = new BufferedImage(ItemGen.CUBE, ItemGen.CUBE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ItemGen.CUBE; y++) {
            for (int x = 0; x < ItemGen.CUBE; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < SS; dy++)
                    for (int dx = 0; dx < SS; dx++) {
                        int c = color[(y * SS + dy) * SIZE + x * SS + dx];
                        int ca = c >>> 24;
                        a += ca;
                        r += ((c >> 16) & 0xFF) * ca;
                        g += ((c >> 8) & 0xFF) * ca;
                        b += (c & 0xFF) * ca;
                    }
                int oa = (int) (a / (SS * SS));
                int or = a == 0 ? 0 : (int) (r / a), og = a == 0 ? 0 : (int) (g / a), ob = a == 0 ? 0 : (int) (b / a);
                out.setRGB(x, y, (oa << 24) | (or << 16) | (og << 8) | ob);
            }
        }
        return out;
    }

    private static double normalShade(double[][] p) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[2][0] - p[1][0], by = p[2][1] - p[1][1], bz = p[2][2] - p[1][2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double anx = Math.abs(nx), any = Math.abs(ny), anz = Math.abs(nz);
        if (any >= anx && any >= anz) return ny > 0 ? 1.0 : 0.5;
        return anz >= anx ? 0.8 : 0.6;
    }

    private static double shade(String face, JsonObject er) {
        if (er != null && er.has("shade") && !er.get("shade").getAsBoolean()) return 1.0;
        return switch (face) {
            case "up" -> 1.0;
            case "down" -> 0.5;
            case "north", "south" -> 0.8;
            default -> 0.6;
        };
    }

    static double[] vec(JsonArray a) {
        double[] v = new double[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsDouble();
        return v;
    }

    private static double[] defaultUv(String face, double[] f, double[] t) {
        return switch (face) {
            case "down" -> new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case "up" -> new double[]{f[0], f[2], t[0], t[2]};
            case "north" -> new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case "south" -> new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case "west" -> new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            default -> new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    private static double[][] corners(String face, double[] f, double[] t) {
        return switch (face) {
            case "north" -> new double[][]{{t[0], t[1], f[2]}, {t[0], f[1], f[2]}, {f[0], f[1], f[2]}, {f[0], t[1], f[2]}};
            case "south" -> new double[][]{{f[0], t[1], t[2]}, {f[0], f[1], t[2]}, {t[0], f[1], t[2]}, {t[0], t[1], t[2]}};
            case "west" -> new double[][]{{f[0], t[1], f[2]}, {f[0], f[1], f[2]}, {f[0], f[1], t[2]}, {f[0], t[1], t[2]}};
            case "east" -> new double[][]{{t[0], t[1], t[2]}, {t[0], f[1], t[2]}, {t[0], f[1], f[2]}, {t[0], t[1], f[2]}};
            case "up" -> new double[][]{{f[0], t[1], f[2]}, {f[0], t[1], t[2]}, {t[0], t[1], t[2]}, {t[0], t[1], f[2]}};
            default -> new double[][]{{f[0], f[1], t[2]}, {f[0], f[1], f[2]}, {t[0], f[1], f[2]}, {t[0], f[1], t[2]}};
        };
    }

    private static double[] rotateElement(double[] p, JsonObject er) {
        double[] o = vec(er.getAsJsonArray("origin"));
        String axis = er.get("axis").getAsString();
        double a = Math.toRadians(er.get("angle").getAsDouble());
        double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
        double c = Math.cos(a), s = Math.sin(a);
        double nx = x, ny = y, nz = z;
        switch (axis) {
            case "x" -> { ny = y * c - z * s; nz = y * s + z * c; }
            case "y" -> { nx = x * c + z * s; nz = -x * s + z * c; }
            default -> { nx = x * c - y * s; ny = x * s + y * c; }
        }
        return new double[]{nx + o[0], ny + o[1], nz + o[2]};
    }

    static double[][] rotation(double ax, double ay, double az) {
        double[][] rx = {{1, 0, 0}, {0, Math.cos(ax), -Math.sin(ax)}, {0, Math.sin(ax), Math.cos(ax)}};
        double[][] ry = {{Math.cos(ay), 0, Math.sin(ay)}, {0, 1, 0}, {-Math.sin(ay), 0, Math.cos(ay)}};
        double[][] rz = {{Math.cos(az), -Math.sin(az), 0}, {Math.sin(az), Math.cos(az), 0}, {0, 0, 1}};
        return mul(mul(rx, ry), rz);
    }

    static double[][] mul(double[][] a, double[][] b) {
        int n = a.length, k = b.length, cols = b[0].length;
        double[][] r = new double[n][cols];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < cols; j++)
                for (int t = 0; t < k; t++) r[i][j] += a[i][t] * b[t][j];
        return r;
    }

    private static void tri(int[] color, float[] depth, double[] a, double[] b, double[] c,
                            double[] ua, double[] ub, double[] uc, BufferedImage tex, int tint, double shade) {
        double minX = Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        double maxX = Math.min(SIZE - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        double minY = Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        double maxY = Math.min(SIZE - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
        if (Math.abs(area) < 1e-9) return;
        int tw = tex.getWidth(), th = tex.getHeight();
        for (int y = (int) minY; y <= maxY; y++) {
            for (int x = (int) minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)) / area;
                double w1 = ((c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int at = y * SIZE + x;
                if (z < depth[at] - 1e-6) continue;
                double u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0];
                double v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1];
                int tx = Math.min(tw - 1, Math.max(0, (int) Math.floor(u * tw)));
                int ty = Math.min(th - 1, Math.max(0, (int) Math.floor(v * th)));
                int texel = ItemGen.mul(tex.getRGB(tx, ty), tint);
                int alpha = texel >>> 24;
                if (alpha < 8) continue;
                int r = (int) (((texel >> 16) & 0xFF) * shade), g = (int) (((texel >> 8) & 0xFF) * shade),
                        bl = (int) ((texel & 0xFF) * shade);
                int shaded = (alpha << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, bl);
                if (alpha >= 250) {
                    color[at] = shaded;
                    depth[at] = (float) z;
                } else {
                    color[at] = ItemGen.over(color[at], shaded);
                }
            }
        }
    }
}
