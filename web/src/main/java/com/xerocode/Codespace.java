package com.xerocode;

import com.google.gson.JsonArray;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class Codespace {
    public static final String DEV_DIMENSION = "creativeplus_editor";
    public static final int LINE_X = 4;
    public static final int FIRST_Y = 5;
    public static final int FLOOR_H = 7;
    public static final int FLOORS = 1;
    public static final int FIRST_Z = 4;
    public static final int LAST_Z = 92;
    public static final int LINE_STEP = 4;

    public enum State { RUNNING, DONE, CANCELLED, FAILED }

    private Codespace() {}

    public static boolean inDev(ClientLevel world) { return false; }

    public static String worldId(ClientLevel world) { return ""; }

    public static boolean chunksReady(ClientLevel world) { return false; }

    public static List<BlockPos> lines(ClientLevel world) { return List.of(); }

    public static String template(ItemStack stack) { return null; }

    public static String decompress(String base64) { return null; }

    public static Path savedDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode/saved");
    }

    public static String key(BlockPos pos) { return pos.getX() + "," + pos.getY() + "," + pos.getZ(); }

    public static void serverSaid(String text) {}

    public static void watch() {}

    public static Scan start(ClientLevel world, List<BlockPos> lines, Memo memo) {
        return new Scan();
    }

    public static final class Scan {
        public State state = State.FAILED;
        public String error = "на сайте мира нет";
        public String note = "";
        public Path file;
        public long millis;

        Scan() {}

        public int index() { return 0; }
        public int total() { return 0; }
        public int failedLines() { return 0; }
        public int skippedLines() { return 0; }
        public Set<String> skipList() { return Set.of(); }
        public int blocks() { return 0; }
        public JsonArray handlers() { return new JsonArray(); }
        public float progress() { return 0; }
        public float remaining() { return 0; }
        public void cancel() { state = State.CANCELLED; }
        public void tick() {}
        void saved() {}
        void refused() {}
    }

    public static final class Memo {
        public String world = "";
        public int next;
        public int total;
        public final Set<String> skip = new LinkedHashSet<>();
        public JsonArray handlers = new JsonArray();

        public static Path file() {
            return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode/resume.json");
        }

        public static Memo read(String world) { return null; }

        public static Memo fresh(String world, Memo old) {
            Memo m = new Memo();
            m.world = world;
            return m;
        }

        public boolean fits(int total) { return false; }
        public int done() { return next; }
        public void write() {}
        public static void drop() {}
    }
}
