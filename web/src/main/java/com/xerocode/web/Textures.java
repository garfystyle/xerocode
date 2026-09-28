package com.xerocode.web;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class Textures {
    public interface Size {
        void ready(int w, int h);
    }

    private static final Map<Identifier, Integer> HANDLES = new HashMap<>();

    private Textures() {}

    public static int handle(Identifier id) {
        if (id == null) return 0;
        Integer h = HANDLES.get(id);
        return h == null ? 0 : h;
    }

    public static void loadUrl(Identifier id, String url, Size done) {
        Js.loadTexture(url, (handle, w, h) -> {
            if (handle <= 0) return;
            HANDLES.put(id, handle);
            done.ready(w, h);
        });
    }

    public static void release(Identifier id) {
        Integer h = HANDLES.remove(id);
        if (h != null) Js.releaseTexture(h);
    }
}
