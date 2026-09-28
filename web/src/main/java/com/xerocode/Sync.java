package com.xerocode;

import net.minecraft.client.multiplayer.ClientLevel;

public final class Sync {
    public enum State {
        UNKNOWN, IN_SYNC, CANVAS_AHEAD, WORLD_AHEAD, DIVERGED;

        public boolean risky() { return this == WORLD_AHEAD || this == DIVERGED; }
    }

    private Sync() {}

    public static State state(Script script, ClientLevel world) { return State.UNKNOWN; }

    public static State state(Script script, ClientLevel world, int lines) { return State.UNKNOWN; }

    public static int worldLines(ClientLevel world) { return 0; }

    public static void mark(Script script, ClientLevel world) {}

    public static void published(Script script) {}

    public static void settle(Script script, ClientLevel world) {}

    public static void hush(Script script) {}

    public static boolean hushed(Script script) { return true; }

    public static void forget() {}
}
