package com.xerocode;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public final class Heads {
    private static final String TEXTURES = "textures";
    private static final String SKIN_URL = "http://textures.minecraft.net/texture/";

    public static ItemStack dress(ItemStack stack, String skin) {
        if (skin == null || skin.isEmpty() || !stack.is(Items.PLAYER_HEAD)) return stack;
        try {
            PropertyMap props = new PropertyMap(ImmutableMultimap.of(TEXTURES,
                    new Property(TEXTURES, value(skin))));
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(skin.getBytes(StandardCharsets.UTF_8)), "", props);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] голова {} не собралась", skin, e);
        }
        return stack;
    }

    public static String value(String skin) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN_URL + skin + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Heads() {}
}
