package net.minecraft.client.gui;

import com.xerocode.web.Toasts;
import net.minecraft.network.chat.Component;

public final class Hud {
    private Component title;

    public void setTitle(Component title) {
        this.title = title;
        Toasts.show(title.getString());
    }

    public void setSubtitle(Component subtitle) {
        Toasts.show((title != null ? title.getString() + " — " : "") + subtitle.getString());
    }

    public void setTimes(int in, int stay, int out) {}

    public void setOverlayMessage(Component message, boolean animate) {
        Toasts.show(message.getString());
    }
}
