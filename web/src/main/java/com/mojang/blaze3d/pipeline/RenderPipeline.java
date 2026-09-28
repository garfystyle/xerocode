package com.mojang.blaze3d.pipeline;

public final class RenderPipeline {
    public enum Blend { ALPHA, PREMULTIPLIED, INVERT, HIGHLIGHT }

    private final String name;
    private final int sortKey;
    private final boolean textured;
    private final Blend blend;

    public RenderPipeline(String name, int sortKey, boolean textured, Blend blend) {
        this.name = name;
        this.sortKey = sortKey;
        this.textured = textured;
        this.blend = blend;
    }

    public int getSortKey() { return sortKey; }
    public boolean textured() { return textured; }
    public Blend blend() { return blend; }

    @Override
    public String toString() { return name; }
}
