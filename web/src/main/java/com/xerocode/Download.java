package com.xerocode;

import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Download {
    public enum State { WAITING, FETCHING, DONE, FAILED }

    public volatile State state = State.FAILED;
    public volatile String error = "на сайте код загружается ссылкой или файлом";
    public volatile JsonArray handlers;

    private Download() {}

    public static Download start(Minecraft client) { return null; }

    public void tick() {}

    public void cancel() {}

    public static boolean heard(Component message) { return false; }
}
