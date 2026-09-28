package net.minecraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class Gui {
    public final Hud hud = new Hud();
    private final Minecraft minecraft;
    private Screen screen;

    public Gui(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public Screen screen() { return screen; }

    public Object overlay() { return null; }

    public void setScreen(Screen next) {
        if (screen != null && next != screen) screen.removed();
        screen = next;
        if (next != null) {
            next.added();
            next.init(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        } else {
            minecraft.screenClosed();
        }
    }
}
