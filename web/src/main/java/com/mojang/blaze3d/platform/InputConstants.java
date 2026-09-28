package com.mojang.blaze3d.platform;

import com.xerocode.web.Input;

public final class InputConstants {
    public static final int KEY_ESCAPE = 256;

    private InputConstants() {}

    public static boolean isKeyDown(Window window, int key) {
        return Input.down(key);
    }
}
