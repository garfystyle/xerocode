package net.minecraft.client;

import com.mojang.blaze3d.platform.Window;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public final class Minecraft {
    private static Minecraft instance;

    public final Font font;
    public final com.xerocode.web.GameDir gameDirectory = new com.xerocode.web.GameDir("/game");
    public final Gui gui;
    public final KeyboardHandler keyboardHandler = new KeyboardHandler();
    public ClientLevel level;
    public LocalPlayer player;
    private final Window window = new Window();
    private final net.minecraft.server.packs.resources.ResourceManager resources = new net.minecraft.server.packs.resources.ResourceManager();
    private final net.minecraft.client.resources.model.sprite.AtlasManager atlases = new net.minecraft.client.resources.model.sprite.AtlasManager();
    private final User user = new User("Игрок");
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private Runnable onScreenClosed = () -> {};

    private Minecraft() {
        instance = this;
        font = new Font();
        gui = new Gui(this);
    }

    public static Minecraft getInstance() {
        if (instance == null) new Minecraft();
        return instance;
    }

    public Window getWindow() { return window; }

    private final ClientPacketListener connection = new ClientPacketListener();

    public ClientPacketListener getConnection() { return connection; }

    public User getUser() { return user; }

    public net.minecraft.server.packs.resources.ResourceManager getResourceManager() { return resources; }

    public net.minecraft.client.resources.model.sprite.AtlasManager getAtlasManager() { return atlases; }

    public boolean isLocalServer() { return false; }

    public void execute(Runnable task) { tasks.add(task); }

    public void runTasks() {
        int n = tasks.size();
        for (int i = 0; i < n && !tasks.isEmpty(); i++) tasks.poll().run();
    }

    public void onScreenClosed(Runnable r) { onScreenClosed = r; }

    public void screenClosed() { onScreenClosed.run(); }
}
