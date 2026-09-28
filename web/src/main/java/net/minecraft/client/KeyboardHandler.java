package net.minecraft.client;

import com.xerocode.web.Input;

public final class KeyboardHandler {
    public String getClipboard() { return Input.clipboard(); }

    public void setClipboard(String text) { Input.setClipboard(text); }
}
