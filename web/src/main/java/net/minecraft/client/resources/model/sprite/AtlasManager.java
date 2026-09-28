package net.minecraft.client.resources.model.sprite;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

public final class AtlasManager {
    private final TextureAtlas particles = new TextureAtlas();

    public TextureAtlas getAtlasOrThrow(Identifier id) { return particles; }
}
