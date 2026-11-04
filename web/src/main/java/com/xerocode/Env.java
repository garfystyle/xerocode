package com.xerocode;

import com.xerocode.web.Input;
import java.util.function.Consumer;

public final class Env {
    public static boolean browser() { return true; }

    public static boolean touch() { return Input.touch(); }

    public static boolean leftHeld() { return Input.mouseDown(0); }

    public static void clipboard(Consumer<String> to) { Input.readClipboard(to); }

    public static void pasteButton(int x, int y, int w, int h) { Input.pasteRect(x, y, w, h); }

    public static boolean keyDown(int key) { return Input.down(key); }

    public static void textFocus(Object owner, boolean on) { Input.textFocus(owner, on); }

    public static void textDrawn(Object owner, double caretX, double caretY, String selected, String all) {
        Input.drawn(owner);
        Input.caretAt(caretX, caretY);
        Input.selection(selected, all);
    }

    public static void later(Runnable job) { net.minecraft.client.Minecraft.getInstance().execute(job); }

    private Env() {}
}
