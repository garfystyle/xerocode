package com.xerocode.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

final class FontGen {
    private record Glyph(int cp, float advance, BufferedImage img, int sx, int sy, int w, int h, float scale, float up) {}

    static final int UP = 4;

    private FontGen() {}

    static BufferedImage upscale(BufferedImage src, int k) {
        BufferedImage out = new BufferedImage(src.getWidth() * k, src.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, src.getRGB(x / k, y / k));
        return out;
    }

    static void run(ZipFile jar, Path resOut, Path siteOut) throws IOException {
        JsonObject def = json(jar, "assets/minecraft/font/include/default.json");
        JsonObject space = json(jar, "assets/minecraft/font/include/space.json");
        Map<Integer, Glyph> glyphs = new LinkedHashMap<>();
        for (JsonElement pe : space.getAsJsonArray("providers")) {
            JsonObject adv = pe.getAsJsonObject().getAsJsonObject("advances");
            for (Map.Entry<String, JsonElement> e : adv.entrySet()) {
                int cp = e.getKey().codePointAt(0);
                glyphs.putIfAbsent(cp, new Glyph(cp, e.getValue().getAsFloat(), null, 0, 0, 0, 0, 1, 0));
            }
        }
        for (JsonElement pe : def.getAsJsonArray("providers")) {
            JsonObject p = pe.getAsJsonObject();
            if (!"bitmap".equals(p.get("type").getAsString())) continue;
            String file = p.get("file").getAsString().replace("minecraft:", "");
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes(jar, "assets/minecraft/textures/" + file)));
            JsonArray rows = p.getAsJsonArray("chars");
            int height = p.has("height") ? p.get("height").getAsInt() : 8;
            int ascent = p.get("ascent").getAsInt();
            int[][] grid = new int[rows.size()][];
            for (int y = 0; y < rows.size(); y++) grid[y] = rows.get(y).getAsString().codePoints().toArray();
            int cw = img.getWidth() / grid[0].length, ch = img.getHeight() / grid.length;
            float scale = (float) height / ch;
            for (int y = 0; y < grid.length; y++) {
                for (int x = 0; x < grid[y].length; x++) {
                    int cp = grid[y][x];
                    if (cp == 0 || glyphs.containsKey(cp)) continue;
                    int actual = actualWidth(img, cw, ch, x, y);
                    float advance = (int) (0.5 + actual * scale) + 1;
                    glyphs.put(cp, new Glyph(cp, advance, img, x * cw, y * ch, cw, ch, scale, 7 - ascent));
                }
            }
        }
        BufferedImage missing = new BufferedImage(5, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 5; x++)
                if (x == 0 || x == 4 || y == 0 || y == 7) missing.setRGB(x, y, 0xFFFFFFFF);
        List<Glyph> all = new ArrayList<>();
        all.add(new Glyph(-1, 6, missing, 0, 0, 5, 8, 1, 0));
        all.addAll(glyphs.values());

        int atlasW = 512, x = 3, y = 1, shelf = 0;
        int[] ax = new int[all.size()], ay = new int[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Glyph g = all.get(i);
            if (g.img == null) continue;
            if (x + g.w + 1 > atlasW) {
                x = 1;
                y += shelf + 1;
                shelf = 0;
            }
            ax[i] = x;
            ay[i] = y;
            x += g.w + 1;
            shelf = Math.max(shelf, g.h);
        }
        int atlasH = Integer.highestOneBit(y + shelf + 1) << 1;
        BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) atlas.setRGB(xx, yy, 0xFFFFFFFF);
        StringBuilder txt = new StringBuilder("atlas " + atlasW + " " + atlasH + "\n");
        for (int i = 0; i < all.size(); i++) {
            Glyph g = all.get(i);
            if (g.img != null) {
                for (int yy = 0; yy < g.h; yy++)
                    for (int xx = 0; xx < g.w; xx++) {
                        int argb = g.img.getRGB(g.sx + xx, g.sy + yy);
                        int a = argb >>> 24;
                        atlas.setRGB(ax[i] + xx, ay[i] + yy, a == 0 ? 0 : (a << 24) | 0xFFFFFF);
                    }
            }
            txt.append(g.cp).append(' ').append(num(g.advance)).append(' ')
                    .append(ax[i]).append(' ').append(ay[i]).append(' ')
                    .append(g.img == null ? 0 : g.w).append(' ').append(g.img == null ? 0 : g.h).append(' ')
                    .append(num(g.scale)).append(' ').append(num(g.up)).append('\n');
        }
        Files.createDirectories(resOut.resolve("web"));
        Files.writeString(resOut.resolve("web/font.txt"), txt, StandardCharsets.UTF_8);
        Files.createDirectories(siteOut);
        ImageIO.write(atlas, "png", siteOut.resolve("font.png").toFile());
        System.out.println("font: " + all.size() + " glyphs, atlas " + atlasW + "x" + atlasH);
    }

    private static String num(float f) {
        return f == (int) f ? Integer.toString((int) f) : String.format(Locale.ROOT, "%.6f", f);
    }

    private static int actualWidth(BufferedImage img, int cw, int ch, int gx, int gy) {
        for (int w = cw - 1; w >= 0; w--) {
            int px = gx * cw + w;
            for (int y = 0; y < ch; y++) {
                int argb = img.getRGB(px, gy * ch + y);
                if ((argb >>> 24) != 0) return w + 1;
            }
        }
        return 0;
    }

    static JsonObject json(ZipFile jar, String path) throws IOException {
        return JsonParser.parseString(new String(bytes(jar, path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static byte[] bytes(ZipFile jar, String path) throws IOException {
        var e = jar.getEntry(path);
        if (e == null) throw new IOException("нет в jar: " + path);
        try (var in = jar.getInputStream(e)) {
            return in.readAllBytes();
        }
    }
}
