package com.xerocode;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Storages {
    private static final String ROOT = "storage", WORLD = "storage world", CELLS = "storage local";
    private static final String WORLD_HEAD = "Хранилища мира", WORLD_NONE = "нет доступа ни к одному хранилищу";
    private static final String CELLS_HEAD = "Ячейки мира", CELLS_NONE = "нет локальных ячеек";
    private static final String[] GIVE_UP = {"Сервис хранилищ недоступен", "работает только в мире разработки"};
    private static final String AGAIN = "Подождите перед повторной отправкой";
    private static final String LINE = "│", DASH = "— ";
    private static final Pattern TOTAL = Pattern.compile(CELLS_HEAD + ":\\s*(\\d+)");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+\\.\\s+");
    private static final int GAP_TICKS = 32, WAIT_TICKS = 100, FRESH_TICKS = 600, SCAN_TICKS = 20, PAGES = 20, TRIES = 3;

    private static final Deque<String> queue = new ArrayDeque<>();
    private static volatile List<String> access = List.of();
    private static volatile List<String> cells = List.of();
    private static final List<String> gathered = new ArrayList<>();
    private static String waiting, world = "";
    private static int waitTimer, gapTimer, freshTimer, scanTimer, tries, version, savedEpoch = -1;
    private static boolean loaded, accessHeard;

    private Storages() {}

    public static List<String> access() { return access; }

    public static boolean asking() { return waiting != null && waiting.equals(WORLD) || queue.contains(WORLD); }

    public static boolean accessKnown() {
        Minecraft mc = Minecraft.getInstance();
        return accessHeard && mc != null && mc.level != null && world.equals(mc.level.dimension().identifier().getPath());
    }

    public static List<String> cells() { return cells; }

    public static int version() { return version * 31 + Values.storageEpoch(); }

    public static void ask() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener net = mc.getConnection();
        if (mc.level == null || net == null || !Codespace.inDev(mc.level)) return;
        if (net.getCommands().getRoot().getChild(ROOT) == null) return;
        String here = mc.level.dimension().identifier().getPath();
        if (here.equals(world) && (freshTimer > 0 || waiting != null || !queue.isEmpty())) return;
        if (!here.equals(world)) {
            access = List.of();
            cells = List.of();
            accessHeard = false;
            version++;
        }
        world = here;
        freshTimer = FRESH_TICKS;
        queue.clear();
        queue.add(WORLD);
        queue.add(CELLS);
    }

    public static void tick(Minecraft mc) {
        if (!loaded) load(mc);
        if (freshTimer > 0) freshTimer--;
        if (++scanTimer >= SCAN_TICKS) {
            scanTimer = 0;
            scan(mc);
            save(mc);
        }
        if (waiting != null) {
            if (--waitTimer <= 0) waiting = null;
            return;
        }
        if (gapTimer > 0) {
            gapTimer--;
            return;
        }
        ClientPacketListener net = mc.getConnection();
        if (queue.isEmpty() || net == null || mc.level == null || !Codespace.inDev(mc.level)) return;
        String command = queue.poll();
        net.sendCommand(command);
        waiting = command;
        waitTimer = WAIT_TICKS;
    }

    public static boolean heard(Component message) {
        String asked = waiting;
        if (asked == null) return false;
        String text = message.getString();
        for (String no : GIVE_UP)
            if (text.contains(no)) {
                queue.clear();
                done();
                return true;
            }
        if (text.contains(AGAIN)) {
            if (++tries <= TRIES) queue.addFirst(asked);
            done();
            return true;
        }
        if (asked.equals(WORLD)) {
            if (text.contains(WORLD_NONE)) {
                access = List.of();
            } else if (text.contains(WORLD_HEAD)) {
                List<String> found = new ArrayList<>();
                for (String body : rows(text)) {
                    int dash = body.indexOf(DASH);
                    String path = (dash < 0 ? body : body.substring(0, dash)).trim();
                    if (!path.isEmpty()) found.add(path);
                }
                access = List.copyOf(found);
            } else {
                return false;
            }
            accessHeard = true;
            version++;
            done();
            return true;
        }
        if (asked.startsWith(CELLS)) {
            if (text.contains(CELLS_NONE)) {
                cells = List.of();
            } else if (text.contains(CELLS_HEAD)) {
                int page = asked.equals(CELLS) ? 1 : Integer.parseInt(asked.substring(CELLS.length()).trim());
                if (page == 1) gathered.clear();
                int before = gathered.size();
                for (String body : rows(text)) {
                    int dash = body.lastIndexOf(DASH);
                    String name = NUMBERED.matcher(dash < 0 ? body : body.substring(0, dash)).replaceFirst("").trim();
                    if (!name.isEmpty() && !gathered.contains(name)) gathered.add(name);
                }
                cells = List.copyOf(gathered);
                Matcher total = TOTAL.matcher(text);
                boolean more = total.find() && gathered.size() < Integer.parseInt(total.group(1))
                        && gathered.size() > before && page < PAGES;
                if (more) queue.addFirst(CELLS + " " + (page + 1));
            } else {
                return false;
            }
            version++;
            done();
            return true;
        }
        return false;
    }

    private static void done() {
        waiting = null;
        gapTimer = GAP_TICKS;
        if (queue.isEmpty()) tries = 0;
    }

    private static List<String> rows(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(LINE)) continue;
            String body = trimmed.substring(LINE.length()).trim();
            if (body.contains(DASH)) out.add(body);
        }
        return out;
    }

    private static void scan(Minecraft mc) {
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) Stacks.valueIn(inv.getItem(i), Value.CELL);
    }

    private static Path file(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("xerocode/storages.json");
    }

    private static void load(Minecraft mc) {
        loaded = true;
        savedEpoch = Values.storageEpoch();
        try {
            Path f = file(mc);
            if (!Files.isRegularFile(f)) return;
            JsonObject known = Json.read(f).getAsJsonObject("storages");
            if (known != null)
                for (String path : known.keySet()) Values.rememberStorage(path, known.get(path).getAsString());
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] хранилища ячеек не прочитались: {}", e.toString());
        }
        savedEpoch = Values.storageEpoch();
    }

    private static void save(Minecraft mc) {
        int epoch = Values.storageEpoch();
        if (epoch == savedEpoch) return;
        savedEpoch = epoch;
        try {
            JsonObject known = new JsonObject();
            for (Map.Entry<String, String> e : Values.storages().entrySet()) known.addProperty(e.getKey(), e.getValue());
            JsonObject root = new JsonObject();
            root.add("storages", known);
            Json.write(file(mc), root);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] хранилища ячеек не записались: {}", e.toString());
        }
    }
}
