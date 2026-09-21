package com.xerocode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.Inflater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

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

    private static final int GAP_START = 27, GAP_MIN = 26, GAP_MAX = 70;
    private static final int GAP_UP = 4, GAP_DOWN = 1;
    private static final int REFUSE_UP = 6, REFUSE_MAX = 8;

    private static final double NEAR = 8.0;
    private static final int NEAR_TRIES = 20;

    private static final String SAID_LIMIT = "Подождите перед сохранением";
    private static final String SAID_SAVED = "Строка сохранена";

    public static boolean inDev(ClientLevel world) {
        return world != null
                && world.dimension().identifier().toString().contains(DEV_DIMENSION);
    }

    public static String worldId(ClientLevel world) {
        String path = world.dimension().identifier().getPath();
        return path.length() >= 14 ? path.substring(6, 14) : "unknown";
    }

    public static boolean chunksReady(ClientLevel world) {
        for (int cz = FIRST_Z >> 4; cz <= LAST_Z >> 4; cz++)
            if (!world.getChunkSource().hasChunk(LINE_X >> 4, cz)) return false;
        return true;
    }

    public static List<BlockPos> lines(ClientLevel world) {
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
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag nbt = data.copyTag();
        CompoundTag bukkit = nbt.getCompound("PublicBukkitValues").orElse(null);
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
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode/saved");
    }

    private static Path freeFile(String worldId) {
        String stem = "world_" + worldId + "-"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Path dir = savedDir();
        Path path = dir.resolve(stem + ".json");
        for (int i = 2; Files.exists(path) && i < 100; i++) path = dir.resolve(stem + "-" + i + ".json");
        return path;
    }

    public static String key(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public enum State { RUNNING, DONE, CANCELLED, FAILED }

    public static final class Memo {
        public String world = "";
        public int next;
        public int total;
        public final Set<String> skip = new LinkedHashSet<>();
        public JsonArray handlers = new JsonArray();

        public static Path file() {
            return Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("xerocode/resume.json");
        }

        public static Memo read(String world) {
            try {
                Path path = file();
                if (!Files.exists(path)) return null;
                JsonObject root = JsonParser
                        .parseString(Files.readString(path, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Memo memo = new Memo();
                memo.world = root.get("world").getAsString();
                if (!memo.world.equals(world)) return null;
                memo.next = root.get("next").getAsInt();
                memo.total = root.get("total").getAsInt();
                for (JsonElement el : root.getAsJsonArray("skip")) memo.skip.add(el.getAsString());
                if (root.has("handlers")) memo.handlers = root.getAsJsonArray("handlers");
                return memo;
            } catch (Exception e) {
                XeroCode.LOG.warn("[xerocode] недочитанное чтение не разобралось", e);
                return null;
            }
        }

        public static Memo fresh(String world, Memo old) {
            Memo memo = new Memo();
            memo.world = world;
            if (old != null) memo.skip.addAll(old.skip);
            return memo;
        }

        public boolean fits(int lines) {
            return total == lines && next > 0 && next < lines;
        }

        public int done() {
            return handlers.size();
        }

        public void write() {
            try {
                JsonObject root = new JsonObject();
                root.addProperty("world", world);
                root.addProperty("next", next);
                root.addProperty("total", total);
                JsonArray list = new JsonArray();
                for (String at : skip) list.add(at);
                root.add("skip", list);
                root.add("handlers", handlers);
                Files.createDirectories(file().getParent());
                Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                XeroCode.LOG.warn("[xerocode] недочитанное чтение не сохранилось", e);
            }
        }

        public static void drop() {
            try {
                Files.deleteIfExists(file());
            } catch (IOException ignored) {
            }
        }
    }

    public static final class Scan {
        private final Minecraft client = Minecraft.getInstance();
        private final ClientLevel world;
        private final List<BlockPos> lines;
        private final JsonArray handlers = new JsonArray();
        private final ItemStack slotBefore;
        private final Vec3 origin;
        private final long startedAt = System.currentTimeMillis();
        private final Memo memo;

        private int index;
        private int attempt;
        private int timer;
        private boolean picking;
        private int failed;
        private int ticks;
        private int lastOk = -1000;
        private int pickTick;
        private int gap = GAP_START;
        private int clean;
        private int refusedHere;
        private int nearTries;
        private int skipped;
        private int walked;

        public State state = State.RUNNING;
        public String error = "";
        public Path file;
        public long millis;

        Scan(ClientLevel world, List<BlockPos> lines, Memo memo) {
            this.world = world;
            this.lines = lines;
            this.memo = memo == null ? Memo.fresh(worldId(world), null) : memo;
            LocalPlayer player = client.player;
            this.slotBefore = player == null
                    ? ItemStack.EMPTY : player.getInventory().getItem(SLOT).copy();
            this.origin = player == null ? Vec3.ZERO : player.position();
            if (this.memo.fits(lines.size())) {
                index = this.memo.next;
                for (JsonElement el : this.memo.handlers) handlers.add(el);
            }
            index = ahead(index);
            if (lines.isEmpty() || index >= lines.size()) { state = State.DONE; return; }
            teleport();
        }

        public int index()  { return Math.min(index + 1, lines.size()); }
        public int total()  { return lines.size(); }
        public int failedLines() { return failed; }
        public int skippedLines() { return skipped; }
        public Set<String> skipList() { return memo.skip; }
        public int blocks() { return handlers.size(); }
        public JsonArray handlers() { return handlers; }

        public float progress() {
            return lines.isEmpty() ? 1f : (float) index / lines.size();
        }

        public float remaining() {
            if (walked <= 0) return -1;
            float perLine = (System.currentTimeMillis() - startedAt) / 1000f / walked;
            return perLine * Math.max(0, lines.size() - index);
        }

        public void cancel() {
            if (state != State.RUNNING) return;
            millis = System.currentTimeMillis() - startedAt;
            restore();
            save();
            forget();
            state = State.CANCELLED;
        }

        public void tick() {
            if (state != State.RUNNING) return;
            ticks++;
            if (client.level == null || client.player == null) {
                broke("связь с миром потеряна");
                return;
            }
            if (client.level != world) {
                broke("мир сменился на " + client.level.dimension().identifier().getPath());
                return;
            }
            if (picking) {
                String raw = template(client.player.getInventory().getItem(SLOT));
                if (raw != null) {
                    if (attempt == 0 && ++clean >= 3) {
                        gap = Math.max(GAP_MIN, gap - GAP_DOWN);
                        clean = 0;
                    }
                    collect(raw);
                    next();
                    return;
                }
                if (--timer > 0) return;
                if (++attempt < ATTEMPTS) {
                    clean = 0;
                    gap = Math.min(GAP_MAX, gap + GAP_UP);
                    pick();
                    return;
                }
                failed++;
                next();
            } else {
                if (--timer > 0) return;
                if (waitTeleport()) return;
                pick();
            }
        }

        void saved() {
            if (lastOk < pickTick) lastOk = ticks;
        }

        void refused() {
            refusedHere++;
            clean = 0;
            gap = Math.min(GAP_MAX, gap + REFUSE_UP);
            if (!picking || refusedHere >= REFUSE_MAX) return;
            picking = false;
            timer = Math.max(TP_TICKS, gap - (ticks - lastOk));
        }

        private int ahead(int at) {
            while (at < lines.size() && memo.skip.contains(key(lines.get(at)))) {
                skipped++;
                at++;
            }
            return at;
        }

        private double away() {
            LocalPlayer player = client.player;
            if (player == null) return -1;
            BlockPos pos = lines.get(index);
            return Math.sqrt(player.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }

        private boolean waitTeleport() {
            double away = away();
            if (away < 0 || away <= NEAR || nearTries >= NEAR_TRIES) {
                nearTries = 0;
                return false;
            }
            nearTries++;
            timer = 2;
            return true;
        }

        private void teleport() {
            ClientPacketListener net = client.getConnection();
            BlockPos pos = lines.get(index);
            if (net != null) net.sendCommand(String.format(Locale.ROOT,
                    "editor tp %.2f %d %.1f", 2.85, pos.getY(), pos.getZ() + 0.5));
            picking = false;
            attempt = 0;
            nearTries = 0;
            refusedHere = 0;
            timer = Math.max(TP_TICKS, gap - (ticks - lastOk));
        }

        private void pick() {
            ClientPacketListener net = client.getConnection();
            LocalPlayer player = client.player;
            if (net == null || player == null) { broke("связь с миром потеряна"); return; }
            player.getInventory().setItem(SLOT, ItemStack.EMPTY);
            if (client.gameMode != null)
                client.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, 36 + SLOT);
            player.getInventory().setSelectedSlot(SLOT);
            player.setYRot(-90f);
            player.setXRot(45f);
            net.send(new ServerboundMovePlayerPacket.Rot(-90f, 45f, true, true));
            net.send(new ServerboundPickItemFromBlockPacket(lines.get(index), false));
            picking = true;
            pickTick = ticks;
            timer = PICK_TICKS;
        }

        private void collect(String raw) {
            saved();
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
            walked++;
            index = ahead(index + 1);
            if (index >= lines.size()) { finish(); return; }
            teleport();
        }

        private void broke(String why) {
            millis = System.currentTimeMillis() - startedAt;
            state = State.FAILED;
            error = why;
            if (picking && index < lines.size()) memo.skip.add(key(lines.get(index)));
            memo.next = Math.min(index + 1, lines.size());
            memo.total = lines.size();
            memo.handlers = handlers;
            memo.write();
            save();
        }

        private void finish() {
            millis = System.currentTimeMillis() - startedAt;
            restore();
            save();
            forget();
            state = State.DONE;
        }

        private void forget() {
            memo.next = 0;
            memo.total = 0;
            memo.handlers = new JsonArray();
            if (memo.skip.isEmpty()) Memo.drop();
            else memo.write();
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
            } catch (IOException e) {
                XeroCode.LOG.error("[xerocode] не удалось сохранить код", e);
                file = null;
            }
        }

        private void restore() {
            LocalPlayer player = client.player;
            if (player != null) {
                player.getInventory().setItem(SLOT, slotBefore);
                if (client.gameMode != null)
                    client.gameMode.handleCreativeModeItemAdd(slotBefore, 36 + SLOT);
            }
            if (client.level != world) return;
            ClientPacketListener net = client.getConnection();
            if (net != null) net.sendCommand(String.format(Locale.ROOT,
                    "editor tp %.2f %.2f %.2f", origin.x, origin.y, origin.z));
        }
    }

    private static Scan current;

    public static void serverSaid(String text) {
        Scan scan = current;
        if (scan == null || scan.state != State.RUNNING) return;
        if (text.contains(SAID_LIMIT)) scan.refused();
        else if (text.contains(SAID_SAVED)) scan.saved();
    }

    public static void watch() {
        Scan scan = current;
        if (scan == null || scan.state != State.RUNNING) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == scan.world && client.player != null) return;
        scan.broke("сервер увёл клиента из мира");
    }

    public static Scan start(ClientLevel world, List<BlockPos> lines, Memo memo) {
        if (current != null) current.cancel();
        current = new Scan(world, lines, memo);
        return current;
    }

    private Codespace() {}
}
