package com.xerocode;

import com.xerocode.web.Textures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

public final class MarketImage {
    public static final int AVATAR_MAX = 96;
    public static final int BANNER_W = 640, BANNER_H = 200;
    public static final int UPLOAD_MAX = 96 * 1024;
    public static final double BANNER_RATIO = BANNER_W / (double) BANNER_H;

    private static final Map<String, Shot> READY = new HashMap<>();
    private static final Set<String> BUSY = new HashSet<>();

    public static final class Shot {
        public final Identifier id;
        public final int w, h;

        Shot(Identifier id, int w, int h) {
            this.id = id;
            this.w = w;
            this.h = h;
        }
    }

    private MarketImage() {}

    public static String hashOf(String ref) {
        return ref != null && ref.startsWith("img:") && ref.length() == 68 ? ref.substring(4) : "";
    }

    public static Shot get(String ref) {
        String hash = hashOf(ref);
        if (hash.isEmpty()) return null;
        Shot have = READY.get(hash);
        if (have != null) return have;
        if (!BUSY.add(hash)) return null;
        Identifier id = Identifier.fromNamespaceAndPath("xerocode", "market/" + hash);
        Textures.loadUrl(id, MarketNet.BASE + "img/" + hash + ".png",
                (w, h) -> READY.put(hash, new Shot(id, w, h)));
        return null;
    }

    public static void forget(String ref) {
        String hash = hashOf(ref);
        if (hash.isEmpty()) return;
        BUSY.remove(hash);
        Shot gone = READY.remove(hash);
        if (gone != null) Textures.release(gone.id);
    }

    public static byte[] prepare(Path file, boolean banner) throws Exception {
        byte[] raw = Files.readAllBytes(file);
        if (raw.length > UPLOAD_MAX) throw new IllegalArgumentException("файл тяжелее 96 КБ");
        return raw;
    }
}
