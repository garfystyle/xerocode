package net.minecraft.client.renderer.texture;

import net.minecraft.resources.Identifier;

public final class TextureAtlasSprite {
    private final Identifier id;
    private final int texture;
    private final float u0, u1, v0, v1;
    private final int width, height;

    public TextureAtlasSprite(Identifier id, int texture, float u0, float u1, float v0, float v1, int width, int height) {
        this.id = id;
        this.texture = texture;
        this.u0 = u0;
        this.u1 = u1;
        this.v0 = v0;
        this.v1 = v1;
        this.width = width;
        this.height = height;
    }

    public Identifier id() { return id; }
    public int texture() { return texture; }
    public float getU0() { return u0; }
    public float getU1() { return u1; }
    public float getV0() { return v0; }
    public float getV1() { return v1; }
    public int width() { return width; }
    public int height() { return height; }
}
