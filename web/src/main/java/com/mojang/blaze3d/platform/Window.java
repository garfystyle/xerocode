package com.mojang.blaze3d.platform;

public final class Window {
    private int width = 854, height = 480;
    private int guiScale = 2;
    private double pixelRatio = 1;

    public void setSize(int width, int height, int guiScale, double pixelRatio) {
        this.width = width;
        this.height = height;
        this.guiScale = Math.max(1, guiScale);
        this.pixelRatio = pixelRatio;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getScreenWidth() { return width; }
    public int getScreenHeight() { return height; }
    public int getGuiScale() { return guiScale; }
    public double pixelRatio() { return pixelRatio; }

    public int getGuiScaledWidth() {
        int w = width / guiScale;
        return width / guiScale * guiScale != width ? w + 1 : w;
    }

    public int getGuiScaledHeight() {
        int h = height / guiScale;
        return height / guiScale * guiScale != height ? h + 1 : h;
    }

    public long handle() { return 1L; }

    public boolean isFullscreen() { return false; }
}
