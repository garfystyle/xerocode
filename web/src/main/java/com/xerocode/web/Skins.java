package com.xerocode.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Skins {
    public record Skin(int handle, int w, int h) {}

    private static final String HOST = "textures.minecraft.net/texture/";
    private static final Map<String, Skin> LOADED = new HashMap<>();
    private static final Set<String> ASKED = new HashSet<>();
    private static final Map<String, String> URLS = new HashMap<>();

    private Skins() {}

    public static Skin of(ItemStack stack) {
        if (stack == null || !stack.is(Items.PLAYER_HEAD)) return null;
        String hash = hash(stack.getRaw("minecraft:profile"));
        if (hash == null) return null;
        Skin got = LOADED.get(hash);
        if (got == null && ASKED.add(hash))
            Js.loadTexture("https://" + HOST + hash, (handle, w, h) -> {
                if (handle > 0 && w >= 64 && h >= 32) LOADED.put(hash, new Skin(handle, w, h));
            });
        return got;
    }

    private static String hash(Nbt.Tag profile) {
        if (!(profile instanceof Nbt.Compound c) || !(c.get("properties") instanceof Nbt.ListTag list))
            return null;
        for (Nbt.Tag t : list.items) {
            if (!(t instanceof Nbt.Compound p) || !"textures".equals(p.getString("name"))) continue;
            String value = p.getString("value");
            String known = URLS.get(value);
            if (known != null) return known.isEmpty() ? null : known;
            String found = "";
            try {
                String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                JsonObject skin = JsonParser.parseString(json).getAsJsonObject()
                        .getAsJsonObject("textures").getAsJsonObject("SKIN");
                String url = skin.get("url").getAsString();
                int at = url.indexOf(HOST);
                if (at >= 0) found = url.substring(at + HOST.length());
            } catch (RuntimeException ignored) {
            }
            URLS.put(value, found);
            return found.isEmpty() ? null : found;
        }
        return null;
    }
}
