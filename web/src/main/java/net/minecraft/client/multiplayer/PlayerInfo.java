package net.minecraft.client.multiplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.PlayerSkin;

public final class PlayerInfo {
    private final GameProfile profile;
    private final PlayerSkin skin;

    public PlayerInfo(GameProfile profile, PlayerSkin skin) {
        this.profile = profile;
        this.skin = skin;
    }

    public GameProfile getProfile() { return profile; }

    public PlayerSkin getSkin() { return skin; }
}
