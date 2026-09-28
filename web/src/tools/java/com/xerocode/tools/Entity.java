package com.xerocode.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class Entity {
    private record Box(int u, int v, double x, double y, double z, double w, double h, double d, double grow,
                       String faces) {}

    private record Part(double px, double py, double pz, double rx, double ry, double rz, double scale, List<Box> boxes) {}

    private static final Map<String, Integer> DYES = Map.ofEntries(
            Map.entry("white", 0xF9FFFE), Map.entry("orange", 0xF9801D), Map.entry("magenta", 0xC74EBD),
            Map.entry("light_blue", 0x3AB3DA), Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
            Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52), Map.entry("light_gray", 0x9D9D97),
            Map.entry("cyan", 0x169C9C), Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
            Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16), Map.entry("red", 0xB02E26),
            Map.entry("black", 0x1D1D21));

    private Entity() {}

    private static Box box(int u, int v, double x, double y, double z, double w, double h, double d) {
        return new Box(u, v, x, y, z, w, h, d, 0, "DUWNES");
    }

    private static Part part(double px, double py, double pz, Box... boxes) {
        return new Part(px, py, pz, 0, 0, 0, 1, List.of(boxes));
    }

    private static Part part(double px, double py, double pz, double rx, double ry, double rz, Box... boxes) {
        return new Part(px, py, pz, rx, ry, rz, 1, List.of(boxes));
    }

    static List<Cube.Quad> quads(JsonObject model, JsonObject transform, Function<String, BufferedImage> tex) {
        String type = model.get("type").getAsString().replace("minecraft:", "");
        double[][] item = transform(transform);
        List<Cube.Quad> out = new ArrayList<>();
        switch (type) {
            case "chest" -> {
                String t = model.has("texture") ? model.get("texture").getAsString().replace("minecraft:", "") : "normal";
                BufferedImage img = tex.apply("entity/chest/" + t);
                if (img == null) return null;
                emit(out, item, img, 64, 64, 0xFFFFFFFF, part(0, 0, 0, box(0, 19, 1, 0, 1, 14, 10, 14)));
                emit(out, item, img, 64, 64, 0xFFFFFFFF, part(0, 9, 1, box(0, 0, 1, 0, 0, 14, 5, 14)));
                emit(out, item, img, 64, 64, 0xFFFFFFFF, part(0, 9, 1, box(0, 0, 7, -2, 14, 2, 4, 1)));
            }
            case "shulker_box" -> {
                String t = model.has("texture") ? model.get("texture").getAsString().replace("minecraft:", "") : "shulker";
                BufferedImage img = tex.apply("entity/shulker/" + t);
                if (img == null) return null;
                emit(out, item, img, 64, 64, 0xFFFFFFFF, part(0, 24, 0, box(0, 0, -8, -16, -8, 16, 12, 16)));
                emit(out, item, img, 64, 64, 0xFFFFFFFF, part(0, 24, 0, box(0, 28, -8, -8, -8, 16, 8, 16)));
            }
            case "head", "player_head" -> {
                String kind = type.equals("player_head") ? "player" : model.get("kind").getAsString();
                String path = switch (kind) {
                    case "skeleton" -> "entity/skeleton/skeleton";
                    case "wither_skeleton" -> "entity/skeleton/wither_skeleton";
                    case "zombie" -> "entity/zombie/zombie";
                    case "creeper" -> "entity/creeper/creeper";
                    case "piglin" -> "entity/piglin/piglin";
                    case "dragon" -> "entity/enderdragon/dragon";
                    default -> "entity/player/wide/steve";
                };
                BufferedImage img = tex.apply(path);
                if (img == null) return null;
                if (kind.equals("dragon")) {
                    Part upper = new Part(0, -7.986666, 0, 0, 0, 0, 0.75, List.of(
                            box(176, 44, -6, -1, -24, 12, 5, 16), box(112, 30, -8, -8, -10, 16, 16, 16),
                            box(0, 0, -5, -12, -4, 2, 4, 6), box(112, 0, -5, -3, -22, 2, 2, 4),
                            box(0, 0, 3, -12, -4, 2, 4, 6), box(112, 0, 3, -3, -22, 2, 2, 4)));
                    emit(out, item, img, 256, 256, 0xFFFFFFFF, upper);
                    emit(out, item, img, 256, 256, 0xFFFFFFFF,
                            new Part(0, -7.986666 + 4 * 0.75, -8 * 0.75, 0, 0, 0, 0.75, List.of(box(176, 65, -6, 0, -16, 12, 4, 16))));
                } else if (kind.equals("piglin")) {
                    emit(out, item, img, 64, 64, 0xFFFFFFFF, part(0, 0, 0,
                            box(0, 0, -5, -8, -4, 10, 8, 8), box(31, 1, -2, -4, -5, 4, 4, 1), box(2, 4, 2, -2, -5, 1, 2, 1),
                            box(2, 0, -3, -2, -5, 1, 2, 1)));
                    emit(out, item, img, 64, 64, 0xFFFFFFFF, part(4.5, -6, 0, 0, 0, -0.7, box(51, 6, 0, 0, -2, 1, 5, 4)));
                    emit(out, item, img, 64, 64, 0xFFFFFFFF, part(-4.5, -6, 0, 0, 0, 0.7, box(39, 6, -1, 0, -2, 1, 5, 4)));
                } else {
                    boolean tall = img.getHeight() >= img.getWidth();
                    int th = tall ? 64 : 32;
                    emit(out, item, img, 64, th, 0xFFFFFFFF, part(0, 0, 0, box(0, 0, -4, -8, -4, 8, 8, 8)));
                    if (tall) emit(out, item, img, 64, th, 0xFFFFFFFF, part(0, 0, 0,
                            new Box(32, 0, -4, -8, -4, 8, 8, 8, 0.25, "DUWNES")));
                }
            }
            case "banner" -> {
                BufferedImage pole = tex.apply("entity/banner/banner_base");
                BufferedImage flag = tex.apply("entity/banner/base");
                if (pole == null || flag == null) return null;
                String color = model.has("color") ? model.get("color").getAsString() : "white";
                int dye = 0xFF000000 | DYES.getOrDefault(color, 0xFFFFFF);
                emit(out, item, pole, 64, 64, 0xFFFFFFFF, part(0, 0, 0, box(44, 0, -1, -42, -1, 2, 42, 2)));
                emit(out, item, pole, 64, 64, 0xFFFFFFFF, part(0, 0, 0, box(0, 42, -10, -44, -1, 20, 2, 2)));
                emit(out, item, flag, 64, 64, dye, part(0, -44, 0, box(0, 0, -10, 0, -2, 20, 40, 1)));
            }
            case "decorated_pot" -> {
                BufferedImage base = tex.apply("entity/decorated_pot/decorated_pot_base");
                BufferedImage side = tex.apply("entity/decorated_pot/decorated_pot_side");
                if (base == null || side == null) return null;
                emit(out, item, base, 32, 32, 0xFFFFFFFF, part(0, 37, 16, Math.PI, 0, 0,
                        box(0, 0, 4, 17, 4, 8, 3, 8), new Box(0, 5, 5, 20, 5, 6, 1, 6, 0, "DUWNES")));
                emit(out, item, base, 32, 32, 0xFFFFFFFF, part(1, 16, 1, box(-14, 13, 0, 0, 0, 14, 0, 14)));
                emit(out, item, base, 32, 32, 0xFFFFFFFF, part(1, 0, 1, box(-14, 13, 0, 0, 0, 14, 0, 14)));
                Box plane = new Box(1, 0, 0, 0, 0, 14, 16, 0, 0, "N");
                emit(out, item, side, 16, 16, 0xFFFFFFFF, new Part(15, 16, 1, 0, 0, Math.PI, 1, List.of(plane)));
                emit(out, item, side, 16, 16, 0xFFFFFFFF, new Part(1, 16, 1, 0, -Math.PI / 2, Math.PI, 1, List.of(plane)));
                emit(out, item, side, 16, 16, 0xFFFFFFFF, new Part(15, 16, 15, 0, Math.PI / 2, Math.PI, 1, List.of(plane)));
                emit(out, item, side, 16, 16, 0xFFFFFFFF, new Part(1, 16, 15, Math.PI, 0, 0, 1, List.of(plane)));
            }
            case "conduit" -> {
                BufferedImage img = tex.apply("entity/conduit/base");
                if (img == null) return null;
                emit(out, item, img, 32, 16, 0xFFFFFFFF, part(0, 0, 0, box(0, 0, -3, -3, -3, 6, 6, 6)));
            }
            default -> {
                return null;
            }
        }
        return out;
    }

    private static double[][] identity() {
        return new double[][]{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
    }

    private static double[][] translate(double x, double y, double z) {
        double[][] m = identity();
        m[0][3] = x;
        m[1][3] = y;
        m[2][3] = z;
        return m;
    }

    private static double[][] scale(double x, double y, double z) {
        double[][] m = identity();
        m[0][0] = x;
        m[1][1] = y;
        m[2][2] = z;
        return m;
    }

    private static double[][] quat(double x, double y, double z, double w) {
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (n == 0) return identity();
        x /= n;
        y /= n;
        z /= n;
        w /= n;
        return new double[][]{
                {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0},
                {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0},
                {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0},
                {0, 0, 0, 1}};
    }

    private static double[][] rotation(JsonElement e) {
        if (e == null) return identity();
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return quat(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble());
        }
        JsonObject o = e.getAsJsonObject();
        if (o.has("axis") && o.has("angle")) {
            JsonArray ax = o.getAsJsonArray("axis");
            double ang = Math.toRadians(o.get("angle").getAsDouble());
            double s = Math.sin(ang / 2);
            return quat(ax.get(0).getAsDouble() * s, ax.get(1).getAsDouble() * s, ax.get(2).getAsDouble() * s, Math.cos(ang / 2));
        }
        return identity();
    }

    static double[][] matrix(JsonObject t) {
        return transform(t);
    }

    private static double[][] transform(JsonObject t) {
        if (t == null) return identity();
        double[][] m = identity();
        if (t.has("translation")) {
            JsonArray a = t.getAsJsonArray("translation");
            m = Cube.mul(m, translate(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
        }
        m = Cube.mul(m, rotation(t.get("left_rotation")));
        if (t.has("scale")) {
            JsonArray a = t.getAsJsonArray("scale");
            m = Cube.mul(m, scale(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
        }
        return Cube.mul(m, rotation(t.get("right_rotation")));
    }

    private static double[][] partMatrix(Part p) {
        double[][] m = translate(p.px / 16, p.py / 16, p.pz / 16);
        if (p.rz != 0) m = Cube.mul(m, rot4(Cube.rotation(0, 0, p.rz)));
        if (p.ry != 0) m = Cube.mul(m, rot4(Cube.rotation(0, p.ry, 0)));
        if (p.rx != 0) m = Cube.mul(m, rot4(Cube.rotation(p.rx, 0, 0)));
        if (p.scale != 1) m = Cube.mul(m, scale(p.scale, p.scale, p.scale));
        return m;
    }

    private static double[][] rot4(double[][] r) {
        return new double[][]{{r[0][0], r[0][1], r[0][2], 0}, {r[1][0], r[1][1], r[1][2], 0},
                {r[2][0], r[2][1], r[2][2], 0}, {0, 0, 0, 1}};
    }

    private static void emit(List<Cube.Quad> out, double[][] item, BufferedImage img, double tw, double th, int tint, Part p) {
        double[][] m = Cube.mul(item, partMatrix(p));
        for (Box b : p.boxes) {
            double x0 = b.x - b.grow, y0 = b.y - b.grow, z0 = b.z - b.grow;
            double x1 = b.x + b.w + b.grow, y1 = b.y + b.h + b.grow, z1 = b.z + b.d + b.grow;
            double[] t0 = {x0, y0, z0}, t1 = {x1, y0, z0}, t2 = {x1, y1, z0}, t3 = {x0, y1, z0};
            double[] l0 = {x0, y0, z1}, l1 = {x1, y0, z1}, l2 = {x1, y1, z1}, l3 = {x0, y1, z1};
            double u0 = b.u, u1 = b.u + b.d, u2 = b.u + b.d + b.w, u22 = b.u + b.d + b.w + b.w;
            double u3 = b.u + b.d + b.w + b.d, u4 = b.u + b.d + b.w + b.d + b.w;
            double v0 = b.v, v1 = b.v + b.d, v2 = b.v + b.d + b.h;
            if (b.faces.indexOf('D') >= 0) poly(out, m, img, tint, tw, th, new double[][]{l1, l0, t0, t1}, u1, v0, u2, v1);
            if (b.faces.indexOf('U') >= 0) poly(out, m, img, tint, tw, th, new double[][]{t2, t3, l3, l2}, u2, v1, u22, v0);
            if (b.faces.indexOf('W') >= 0) poly(out, m, img, tint, tw, th, new double[][]{t0, l0, l3, t3}, u0, v1, u1, v2);
            if (b.faces.indexOf('N') >= 0) poly(out, m, img, tint, tw, th, new double[][]{t1, t0, t3, t2}, u1, v1, u2, v2);
            if (b.faces.indexOf('E') >= 0) poly(out, m, img, tint, tw, th, new double[][]{l1, t1, t2, l2}, u2, v1, u3, v2);
            if (b.faces.indexOf('S') >= 0) poly(out, m, img, tint, tw, th, new double[][]{l0, l1, l2, l3}, u3, v1, u4, v2);
        }
    }

    private static void poly(List<Cube.Quad> out, double[][] m, BufferedImage img, int tint, double tw, double th,
                             double[][] v, double pu0, double pv0, double pu1, double pv1) {
        double[][] uv = {{pu1 / tw, pv0 / th}, {pu0 / tw, pv0 / th}, {pu0 / tw, pv1 / th}, {pu1 / tw, pv1 / th}};
        double[][] pos = new double[4][];
        for (int i = 0; i < 4; i++) pos[i] = Cube.apply(m, new double[]{v[i][0] / 16, v[i][1] / 16, v[i][2] / 16});
        out.add(new Cube.Quad(pos, uv, img, tint, -1));
    }
}
