package com.xerocode;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Env {
    public static boolean browser() { return false; }

    public static boolean touch() { return false; }

    public static boolean leftHeld() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    public static void clipboard(Consumer<String> to) {
        String text;
        try {
            Minecraft mc = Minecraft.getInstance();
            text = mc == null || mc.keyboardHandler == null ? null : mc.keyboardHandler.getClipboard();
        } catch (Throwable e) {
            text = null;
        }
        to.accept(text);
    }

    public static void pasteButton(int x, int y, int w, int h) {}

    public static boolean keyDown(int key) {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null && GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }

    public static void textFocus(Object owner, boolean on) {}

    public static void textDrawn(Object owner, double caretX, double caretY, String selected, String all) {}

    public static void later(Runnable job) {
        Thread worker = new Thread(job, "xerocode-code");
        worker.setDaemon(true);
        worker.start();
    }

    private Env() {}
}
