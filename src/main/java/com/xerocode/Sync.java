package com.xerocode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Sync {
    private static final int LINE_END = 92;

    private static final long SETTLE_MS = 2000;

    public enum State {
        UNKNOWN,
        IN_SYNC,
        CANVAS_AHEAD,
        WORLD_AHEAD,
        DIVERGED;

        public boolean risky() { return this == WORLD_AHEAD || this == DIVERGED; }
    }

    private record Stamp(int lines, int heads, int raster, boolean full) {
        boolean differs(Stamp other) {
            if (other == null) return true;
            if (lines != other.lines || heads != other.heads) return true;
            return full && other.full() && raster != other.raster();
        }
    }

    private static final class Base {
        Stamp stamp;
        int canvas;
        boolean awaiting;
    }

    private static Stamp sample;
    private static long sampleAt;
    private static String asked;
    private static Base cached;
    private static String cachedPlot;

    private static Stamp stamp(ClientWorld world) {
        if (world == null || !Codespace.inDev(world) || !Codespace.chunksReady(world)) return null;
        List<BlockPos> heads = Codespace.lines(world);
        int hh = 17;
        for (BlockPos head : heads) hh = (hh * 31 + head.getY()) * 31 + head.getZ();

        boolean full = rowsReady(world);
        int raster = 0;
        if (full) {
            BlockPos.Mutable at = new BlockPos.Mutable();
            for (BlockPos head : heads) {
                raster = (raster * 31 + head.getY()) * 31 + head.getZ();
                for (int x = Codespace.LINE_X; x <= LINE_END; x++) {
                    at.set(x, head.getY(), head.getZ());
                    BlockState state = world.getBlockState(at);
                    raster = raster * 31
                            + (state.isAir() ? 0 : Registries.BLOCK.getId(state.getBlock()).hashCode());
                }
            }
        }
        return new Stamp(heads.size(), hh, raster, full);
    }

    private static boolean rowsReady(ClientWorld world) {
        for (int cx = Codespace.LINE_X >> 4; cx <= LINE_END >> 4; cx++)
            for (int cz = Codespace.FIRST_Z >> 4; cz <= Codespace.LAST_Z >> 4; cz++)
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) return false;
        return true;
    }

    public static State state(Script script, ClientWorld world) {
        if (script == null) return State.UNKNOWN;
        Base base = read(script.plot);
        boolean canvasFilled = !script.roots.isEmpty();
        Stamp now = stamp(world);

        if (base == null) {
            if (!canvasFilled || now == null || now.lines() == 0) return State.UNKNOWN;
            return State.DIVERGED;
        }
        boolean canvasChanged = script.codeHash() != base.canvas;
        if (now == null) return canvasChanged ? State.CANVAS_AHEAD : State.UNKNOWN;
        if (base.awaiting) return canvasChanged ? State.CANVAS_AHEAD : State.IN_SYNC;

        boolean worldChanged = now.differs(base.stamp);
        if (worldChanged && canvasChanged) return State.DIVERGED;
        if (worldChanged) return State.WORLD_AHEAD;
        return canvasChanged ? State.CANVAS_AHEAD : State.IN_SYNC;
    }

    public static int worldLines(ClientWorld world) {
        if (world == null || !Codespace.inDev(world) || !Codespace.chunksReady(world)) return -1;
        return Codespace.lines(world).size();
    }

    public static void mark(Script script, ClientWorld world) {
        if (script == null) return;
        Base base = new Base();
        base.stamp = stamp(world);
        base.canvas = script.codeHash();
        write(script.plot, base);
        sample = null;
    }

    public static void published(Script script) {
        if (script == null) return;
        Base base = new Base();
        base.canvas = script.codeHash();
        base.awaiting = true;
        write(script.plot, base);
        sample = null;
        sampleAt = 0;
    }

    public static void settle(Script script, ClientWorld world) {
        if (script == null) return;
        Base base = read(script.plot);
        if (base == null) return;
        if (!base.awaiting && (base.stamp == null || base.stamp.full())) return;
        if (script.codeHash() != base.canvas) return;
        Stamp now = stamp(world);
        if (now == null || !now.full()) return;
        if (!base.awaiting) {
            if (base.stamp != null && !base.stamp.full() && !now.differs(base.stamp)) {
                base.stamp = now;
                write(script.plot, base);
            }
            return;
        }
        long at = System.currentTimeMillis();
        if (sample == null || now.differs(sample)) {
            sample = now;
            sampleAt = at;
            return;
        }
        if (at - sampleAt < SETTLE_MS) return;
        base.stamp = now;
        base.awaiting = false;
        write(script.plot, base);
        sample = null;
    }

    public static void hush(Script script) {
        asked = script == null ? null : plotKey(script.plot);
    }

    public static boolean hushed(Script script) {
        return script != null && asked != null && asked.equals(plotKey(script.plot));
    }

    public static void forget() {
        asked = null;
        sample = null;
        cached = null;
        cachedPlot = null;
    }

    private static String plotKey(String plot) { return plot == null ? "" : plot; }

    private static Path file(String plot) {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("xerocode");
        return plot == null || plot.isEmpty() ? dir.resolve("sync.json")
                : dir.resolve("worlds").resolve(plot + ".sync.json");
    }

    private static Base read(String plot) {
        String key = plotKey(plot);
        if (cached != null && key.equals(cachedPlot)) return cached;
        try {
            Path path = file(plot);
            if (!Files.exists(path)) return null;
            JsonObject root = JsonParser
                    .parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            Base base = new Base();
            base.canvas = root.get("canvas").getAsInt();
            base.awaiting = root.has("awaiting") && root.get("awaiting").getAsBoolean();
            if (root.has("lines"))
                base.stamp = new Stamp(root.get("lines").getAsInt(), root.get("heads").getAsInt(),
                        root.get("raster").getAsInt(), root.get("full").getAsBoolean());
            cached = base;
            cachedPlot = key;
            return base;
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] слепок мира не прочитался", e);
            return null;
        }
    }

    private static void write(String plot, Base base) {
        cached = base;
        cachedPlot = plotKey(plot);
        try {
            JsonObject root = new JsonObject();
            root.addProperty("canvas", base.canvas);
            root.addProperty("awaiting", base.awaiting);
            if (base.stamp != null) {
                root.addProperty("lines", base.stamp.lines());
                root.addProperty("heads", base.stamp.heads());
                root.addProperty("raster", base.stamp.raster());
                root.addProperty("full", base.stamp.full());
            }
            Path path = file(plot);
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] слепок мира не сохранился", e);
        }
    }

    private Sync() {}
}
