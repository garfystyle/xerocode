package com.xerocode;

import com.xerocode.web.Nbt;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Heads {
    private static final String TEXTURES = "textures";
    private static final String SKIN_URL = "http://textures.minecraft.net/texture/";

    public static ItemStack dress(ItemStack stack, String skin) {
        if (skin == null || skin.isEmpty() || !stack.is(Items.PLAYER_HEAD)) return stack;
        Nbt.Compound prop = new Nbt.Compound();
        prop.put("name", new Nbt.Str(TEXTURES));
        prop.put("value", new Nbt.Str(value(skin)));
        Nbt.ListTag props = new Nbt.ListTag();
        props.items.add(prop);
        Nbt.Compound profile = new Nbt.Compound();
        profile.put("properties", props);
        stack.setComponentsPatch(stack.getComponentsPatch().with("minecraft:profile", profile));
        return stack;
    }

    public static String value(String skin) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN_URL + skin + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Heads() {}
}
