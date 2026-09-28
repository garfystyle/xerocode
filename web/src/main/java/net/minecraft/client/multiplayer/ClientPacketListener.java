package net.minecraft.client.multiplayer;

import com.xerocode.web.Input;
import java.util.Collection;
import java.util.List;

public final class ClientPacketListener {
    public Collection<PlayerInfo> getOnlinePlayers() { return List.of(); }

    public PlayerInfo getPlayerInfo(String name) { return null; }

    public void sendCommand(String command) {
        if (command == null || !command.startsWith("module loadUrl")) return;
        Input.setClipboard("/" + command);
    }
}
