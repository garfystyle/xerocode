package com.xerocode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MarketImage {
    public static final int AVATAR_MAX = 96;
    public static final int BANNER_W = 640, BANNER_H = 200;
    public static final int UPLOAD_MAX = 96 * 1024;
    public static final double BANNER_RATIO = BANNER_W / (double) BANNER_H;

    private static final int KEEP = 96;

    public static final class Shot {
        public final Identifier id;
        public final int w, h;

        Shot(Identifier id, int w, int h) {
            this.id = id;
            this.w = w;
            this.h = h;
        }
    }

    private static final Map<String, Shot> READY = new HashMap<>();
    private static final Set<String> BUSY = new HashSet<>();
    private static final Set<String> LOST = new HashSet<>();
    private static final Deque<String> ORDER = new ArrayDeque<>();

    public static String hashOf(String ref) {
        return ref != null && ref.startsWith("img:") && ref.length() == 68 ? ref.substring(4) : "";
    }

    public static Shot get(String ref) {
        String hash = hashOf(ref);
        if (hash.isEmpty()) return null;
        Shot have = READY.get(hash);
        if (have != null) {
            ORDER.remove(hash);
            ORDER.addLast(hash);
            return have;
        }
        if (LOST.contains(hash) || !BUSY.add(hash)) return null;
        MarketNet.run(() -> load(hash));
        return null;
    }

    private static Path cacheDir() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("xerocode/market-cache");
    }

    private static void load(String hash) {
        byte[] raw = null;
        Path cached = cacheDir().resolve(hash + ".png");
        try {
            if (Files.exists(cached)) raw = Files.readAllBytes(cached);
        } catch (Exception ignored) {
        }
        if (raw == null || raw.length == 0 || raw.length > UPLOAD_MAX + 4096) {
            try {
                raw = MarketNet.fetch("img/" + hash + ".png");
                Files.createDirectories(cached.getParent());
                Files.write(cached, raw);
            } catch (Exception e) {
                MarketNet.back(() -> {
                    BUSY.remove(hash);
                    LOST.add(hash);
                });
                return;
            }
        }
        byte[] bytes = raw;
        MinecraftClient.getInstance().execute(() -> {
            BUSY.remove(hash);
            try {
                NativeImage image = NativeImage.read(bytes);
                Identifier id = Identifier.of("xerocode", "market/" + hash);
                NativeImageBackedTexture texture =
                        new NativeImageBackedTexture(() -> "xerocode market " + hash, image);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                READY.put(hash, new Shot(id, image.getWidth(), image.getHeight()));
                ORDER.addLast(hash);
                trim();
            } catch (Throwable e) {
                LOST.add(hash);
            }
        });
    }

    private static void trim() {
        while (ORDER.size() > KEEP) {
            String old = ORDER.pollFirst();
            if (old == null) break;
            drop(READY.remove(old));
        }
    }

    public static void forget(String ref) {
        String hash = hashOf(ref);
        if (hash.isEmpty()) return;
        LOST.remove(hash);
        ORDER.remove(hash);
        drop(READY.remove(hash));
    }

    private static void drop(Shot gone) {
        if (gone == null) return;
        try {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(gone.id);
        } catch (Throwable ignored) {
        }
    }

    public static byte[] prepare(Path file, boolean banner) throws Exception {
        if (Files.size(file) > 16 << 20) throw new IllegalArgumentException("файл тяжелее 16 МБ");
        Png.Bitmap shot;
        try (NativeImage image = NativeImage.read(Files.readAllBytes(file))) {
            int w = image.getWidth(), h = image.getHeight();
            if ((long) w * h > 40_000_000L)
                throw new IllegalArgumentException("картинка слишком большая");
            shot = new Png.Bitmap(image.copyPixelsArgb(), w, h);
        }
        int wantW = banner ? BANNER_W : AVATAR_MAX;
        int wantH = banner ? BANNER_H : AVATAR_MAX;
        shot = Png.shrink(Png.crop(shot, banner ? BANNER_RATIO : 1.0), wantW, wantH);
        byte[] png = shot.encode();
        if (png == null) throw new IllegalArgumentException("картинка не собралась");
        if (png.length <= UPLOAD_MAX) return png;
        png = Png.shrink(shot, wantW / 2, wantH / 2).encode();
        if (png == null || png.length > UPLOAD_MAX)
            throw new IllegalArgumentException("картинка не ужалась — возьми попроще");
        return png;
    }

    private MarketImage() {}
}
