package net.minecraft.client.gui.render;

public record TextureSetup(int texture, boolean linear) {
    private static final TextureSetup NONE = new TextureSetup(0, false);

    public static TextureSetup noTexture() { return NONE; }

    public static TextureSetup of(int texture, boolean linear) { return new TextureSetup(texture, linear); }

    public int getSortKey() { return texture * 2 + (linear ? 1 : 0); }
}
