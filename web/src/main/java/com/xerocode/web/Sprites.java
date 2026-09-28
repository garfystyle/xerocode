package com.xerocode.web;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class Sprites {
    private static final Map<Identifier, TextureAtlasSprite> PARTICLES = new HashMap<>();
    private static boolean loaded;

    private Sprites() {}

    private static void load() {
        if (loaded) return;
        int texture = Js.texture("particles");
        if (texture <= 0) return;
        loaded = true;
        try (InputStream in = Sprites.class.getResourceAsStream("/web/particles.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String head = r.readLine();
            String[] hf = head.split(" ");
            float aw = Float.parseFloat(hf[1]), ah = Float.parseFloat(hf[2]);
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(" ");
                if (f.length < 5) continue;
                int x = Integer.parseInt(f[1]), y = Integer.parseInt(f[2]);
                int w = Integer.parseInt(f[3]), h = Integer.parseInt(f[4]);
                Identifier id = Identifier.parse(f[0]);
                PARTICLES.put(id, new TextureAtlasSprite(id, texture, x / aw, (x + w) / aw, y / ah, (y + h) / ah, w, h));
            }
        } catch (Exception ignored) {
        }
    }

    public static TextureAtlasSprite particle(Identifier id) {
        load();
        return PARTICLES.get(id);
    }
}
