package com.xerocode;

import com.mojang.blaze3d.platform.InputConstants;
import com.xerocode.ui.CodeScreen;
import com.xerocode.ui.EditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;

final class KeyGate {
    private static GLFWKeyCallback before;
    private static boolean installed;

    private KeyGate() {}

    static void install(Minecraft client) {
        if (installed || client.getWindow() == null) return;
        installed = true;
        before = GLFW.glfwSetKeyCallback(client.getWindow().handle(), KeyGate::key);
    }

    private static void key(long window, int key, int scancode, int action, int mods) {
        try {
            if (action != GLFW.GLFW_RELEASE && !XeroCode.isRetired() && claimed(key, scancode, mods, action))
                return;
        } catch (Throwable e) {
            XeroCode.LOG.warn("[xerocode] перехват клавиши не удался", e);
        }
        if (before != null) before.invoke(window, key, scancode, action, mods);
    }

    private static boolean claimed(int key, int scancode, int mods, int action) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        if (mc.gui.screen() instanceof CodeScreen code) {
            if (o.keyDebugModifier.isDown()) return false;
            KeyEvent typed = new KeyEvent(key, scancode, mods);
            if (!o.keyScreenshot.matches(InputConstants.getKey(typed))) return false;
            if (action == GLFW.GLFW_PRESS) code.keyPressed(typed);
            return true;
        }
        if (!(mc.gui.screen() instanceof EditorScreen editor)) return false;
        if (o.keyDebugModifier.isDown()) return false;
        KeyEvent event = new KeyEvent(key, scancode, mods);
        InputConstants.Key pressed = InputConstants.getKey(event);
        if (!o.keyScreenshot.matches(pressed) && !o.keyFullscreen.matches(pressed)
                && !o.keyFriends.matches(pressed)) return false;
        if (Settings.get().match(key, mods) == null) return false;
        if (action == GLFW.GLFW_PRESS) editor.keyPressed(event);
        return true;
    }
}
