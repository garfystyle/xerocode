package net.minecraft.client.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

public final class LocalPlayer {
    public GameProfile getGameProfile() { return new GameProfile(null, "Игрок"); }

    public ItemStack getMainHandItem() { return ItemStack.EMPTY; }

    public PlayerSkin getSkin() { return null; }
}
