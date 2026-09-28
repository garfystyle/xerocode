package net.minecraft.core;

import net.minecraft.resources.Identifier;

public interface ClientAsset {
    record Texture(Identifier id, Identifier texturePath) implements ClientAsset {}
}
