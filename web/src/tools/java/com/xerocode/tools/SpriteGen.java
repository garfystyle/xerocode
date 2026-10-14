package com.xerocode.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

final class SpriteGen {
    static final int CELL = 16, STRIDE = 18, COLS = 64;
    private static final String ROOT = "assets/minecraft/textures/";
    private static final String[] DIRS = {"item/", "block/"};

    private SpriteGen() {}

    static void run(ZipFile jar, Path res, Path site) throws IOException {
        List<String> ids = new ArrayList<>();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(ROOT) || !name.endsWith(".png")) continue;
            String id = name.substring(ROOT.length(), name.length() - ".png".length());
            for (String dir : DIRS) if (id.startsWith(dir)) ids.add(id);
        }
        ids.sort(null);
        List<String> kept = new ArrayList<>();
        List<BufferedImage> cells = new ArrayList<>();
        for (String id : ids) {
            BufferedImage img = cell(jar, jar.getEntry(ROOT + id + ".png"));
            if (img == null) continue;
            kept.add(id);
            cells.add(img);
        }
        int rows = Math.max(1, (cells.size() + COLS - 1) / COLS);
        BufferedImage atlas = new BufferedImage(COLS * STRIDE, rows * STRIDE, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < cells.size(); i++) {
            BufferedImage c = cells.get(i);
            int ox = (i % COLS) * STRIDE + 1, oy = (i / COLS) * STRIDE + 1;
            for (int y = -1; y <= CELL; y++)
                for (int x = -1; x <= CELL; x++)
                    atlas.setRGB(ox + x, oy + y, c.getRGB(Math.clamp(x, 0, CELL - 1), Math.clamp(y, 0, CELL - 1)));
        }
        ImageIO.write(atlas, "png", site.resolve("sprites.png").toFile());
        StringBuilder txt = new StringBuilder("atlas " + atlas.getWidth() + " " + atlas.getHeight() + " "
                + COLS + " " + STRIDE + " " + CELL + "\n");
        for (String id : kept) txt.append(id).append('\n');
        Files.createDirectories(res.resolve("web"));
        Files.writeString(res.resolve("web/sprites.txt"), txt, StandardCharsets.UTF_8);
        System.out.println("sprites: " + kept.size() + " textures");
    }

    private static BufferedImage cell(ZipFile jar, ZipEntry e) throws IOException {
        if (e == null) return null;
        BufferedImage img;
        try (var in = jar.getInputStream(e)) {
            img = ImageIO.read(in);
        }
        if (img == null) return null;
        int w = img.getWidth(), h = Math.min(img.getHeight(), img.getWidth());
        BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        int side = Math.max(w, h);
        int dx = (side - w) / 2, dy = (side - h) / 2;
        for (int y = 0; y < CELL; y++)
            for (int x = 0; x < CELL; x++) {
                int sx = x * side / CELL - dx, sy = y * side / CELL - dy;
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                out.setRGB(x, y, img.getRGB(sx, sy));
            }
        return out;
    }
}
