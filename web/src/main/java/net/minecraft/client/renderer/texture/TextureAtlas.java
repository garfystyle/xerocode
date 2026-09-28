package net.minecraft.client.renderer.texture;

import com.xerocode.web.Sprites;
import net.minecraft.resources.Identifier;

public final class TextureAtlas {
    public TextureAtlasSprite getSprite(Identifier id) {
        return Sprites.particle(id);
    }
}
