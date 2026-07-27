package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.PickItemFromBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.zip.Inflater;

public final class Codespace {
    public static final String DEV_DIMENSION = "creativeplus_editor";

    public static final int LINE_X = 4;
    public static final int FIRST_Y = 5;
    public static final int FLOOR_H = 7;
    public static final int FLOORS = 15;
    public static final int FIRST_Z = 4, LAST_Z = 92, LINE_STEP = 4;

    private static final int SLOT = 0;
    private static final int TP_TICKS = 10;
    private static final int PICK_TICKS = 30;
    private static final int ATTEMPTS = 3;

    private static final int GAP_START = 25, GAP_MIN = 10, GAP_MAX = 45;
    private static final int GAP_UP = 4, GAP_DOWN = 2;

    public static boolean inDev(ClientWorld world) {
        return world != null
                && world.getRegistryKey().getValue().toString().contains(DEV_DIMENSION);
    }

    public static String worldId(ClientWorld world) {
        String path = world.getRegistryKey().getValue().getPath();
        return path.length() >= 14 ? path.substring(6, 14) : "unknown";
    }

    public static boolean chunksReady(ClientWorld world) {
        for (int cz = FIRST_Z >> 4; cz <= LAST_Z >> 4; cz++)
            if (!world.getChunkManager().isChunkLoaded(LINE_X >> 4, cz)) return false;
        return true;
    }

    public static List<BlockPos> lines(ClientWorld world) {
        List<BlockPos> out = new ArrayList<>();
        for (int y = FIRST_Y; y <= FLOORS * FLOOR_H - 2; y += FLOOR_H) {
            if (world.getBlockState(new BlockPos(LINE_X, y - 1, LINE_X)).isAir()) break;
            for (int z = FIRST_Z; z <= LAST_Z; z += LINE_STEP) {
                BlockPos pos = new BlockPos(LINE_X, y, z);
                if (world.getBlockState(pos).isAir()) continue;
                out.add(pos);
            }
        }
        return out;
    }

    public static String template(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        NbtCompound nbt = data.copyNbt();
        NbtCompound bukkit = nbt.getCompound("PublicBukkitValues").orElse(null);
        if (bukkit == null) return null;
        return bukkit.getString("justmc:template").orElse(null);
    }

    public static String decompress(String encoded) {
        byte[] input = Base64.getDecoder().decode(encoded);
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length * 4);
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            return null;
        } finally {
            inflater.end();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static Path savedDir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("xerocode/saved");
    }

    private static Path freeFile(String worldId) {
        String stem = "world_" + worldId + "-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Path dir = savedDir();
        Path path = dir.resolve(stem + ".json");
        for (int i = 2; Files.exists(path) && i < 100; i++) path = dir.resolve(stem + "-" + i + ".json");
        return path;
    }

    public enum State { RUNNING, DONE, CANCELLED, FAILED }

    public static final class Scan {
        private final MinecraftClient client = MinecraftClient.getInstance();
        private final ClientWorld world;
        private final List<BlockPos> lines;
        private final JsonArray handlers = new JsonArray();
        private final ItemStack slotBefore;
        private final Vec3d origin;
        private final long startedAt = System.currentTimeMillis();

        private int index;
        private int attempt;
        private int timer;
        private boolean picking;
        private int failed;
        private int ticks;
        private int lastPick = -1000;
        private int gap = GAP_START;
        private int clean;
        private int retries;

        public State state = State.RUNNING;
        public String error = "";
        public Path file;
        public long millis;

        Scan(ClientWorld world, List<BlockPos> lines) {
            this.world = world;
            this.lines = lines;
            ClientPlayerEntity player = client.player;
            this.slotBefore = player == null
                    ? ItemStack.EMPTY : player.getInventory().getStack(SLOT).copy();
            this.origin = player == null ? Vec3d.ZERO : player.getEntityPos();
            if (lines.isEmpty()) state = State.DONE;
            else teleport();
        }

        public int index()  { return Math.min(index + 1, lines.size()); }
        public int total()  { return lines.size(); }
        public int failedLines() { return failed; }
        public int blocks() { return handlers.size(); }
        public JsonArray handlers() { return handlers; }

        public float progress() {
            return lines.isEmpty() ? 1f : (float) index / lines.size();
        }

        public float remaining() {
            if (index == 0) return -1;
            float perLine = (System.currentTimeMillis() - startedAt) / 1000f / index;
            return perLine * (lines.size() - index);
        }

        public void cancel() {
            if (state != State.RUNNING) return;
            millis = System.currentTimeMillis() - startedAt;
            restore();
            save();
            state = State.CANCELLED;
        }

        public void tick() {
            if (state != State.RUNNING) return;
            ticks++;
            if (client.player == null || client.world != world) {
                state = State.FAILED;
                error = "Мир сменился, чтение прервано";
                return;
            }
            if (picking) {
                String raw = template(client.player.getInventory().getStack(SLOT));
                if (raw != null) {
                    if (attempt == 0 && ++clean >= 3) { gap = Math.max(GAP_MIN, gap - GAP_DOWN); clean = 0; }
                    collect(raw);
                    next();
                    return;
                }
                if (--timer > 0) return;
                if (++attempt < ATTEMPTS) {
                    retries++;
                    clean = 0;
                    gap = Math.min(GAP_MAX, gap + GAP_UP);
                    pick();
                    return;
                }
                failed++;
                next();
            } else {
                if (--timer > 0) return;
                pick();
            }
        }

        private void teleport() {
            ClientPlayNetworkHandler net = client.getNetworkHandler();
            BlockPos pos = lines.get(index);
            if (net != null) net.sendChatCommand(String.format(Locale.ROOT,
                    "editor tp %.2f %d %.1f", 2.85, pos.getY(), pos.getZ() + 0.5));
            picking = false;
            attempt = 0;
            timer = Math.max(TP_TICKS, gap - (ticks - lastPick));
        }

        private void pick() {
            ClientPlayNetworkHandler net = client.getNetworkHandler();
            ClientPlayerEntity player = client.player;
            if (net == null || player == null) return;
            player.getInventory().setStack(SLOT, ItemStack.EMPTY);
            if (client.interactionManager != null)
                client.interactionManager.clickCreativeStack(ItemStack.EMPTY, 36 + SLOT);
            player.getInventory().setSelectedSlot(SLOT);
            player.setYaw(-90f);
            player.setPitch(45f);
            net.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(-90f, 45f, true, true));
            net.sendPacket(new PickItemFromBlockC2SPacket(lines.get(index), false));
            picking = true;
            lastPick = ticks;
            timer = PICK_TICKS;
        }

        private void collect(String raw) {
            String json = decompress(raw);
            if (json == null) { failed++; return; }
            try {
                JsonObject handler = JsonParser.parseString(json).getAsJsonObject();
                handler.addProperty("position", index);
                handlers.add(handler);
            } catch (RuntimeException e) {
                failed++;
            }
        }

        private void next() {
            index++;
            if (index >= lines.size()) { finish(); return; }
            teleport();
        }

        private void finish() {
            millis = System.currentTimeMillis() - startedAt;
            restore();
            save();
            state = State.DONE;
        }

        private void save() {
            if (handlers.isEmpty()) return;
            try {
                Files.createDirectories(savedDir());
                JsonObject root = new JsonObject();
                root.add("handlers", handlers);
                file = freeFile(worldId(world));
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    w.write(root.toString());
                }
                XeroCode.LOG.info("[xerocode] {} строк кода сохранено в {} (пауза между запросами {} тиков, "
                        + "повторов {})", handlers.size(), file, gap, retries);
            } catch (IOException e) {
                XeroCode.LOG.error("[xerocode] не удалось сохранить код", e);
                file = null;
            }
        }

        private void restore() {
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.getInventory().setStack(SLOT, slotBefore);
                if (client.interactionManager != null)
                    client.interactionManager.clickCreativeStack(slotBefore, 36 + SLOT);
            }
            ClientPlayNetworkHandler net = client.getNetworkHandler();
            if (net != null) net.sendChatCommand(String.format(Locale.ROOT,
                    "editor tp %.2f %.2f %.2f", origin.x, origin.y, origin.z));
        }
    }

    private static Scan current;

    public static Scan start(ClientWorld world, List<BlockPos> lines) {
        if (current != null) current.cancel();
        current = new Scan(world, lines);
        return current;
    }

    private Codespace() {}
}
