package com.xerocode;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

public final class ModuleActions {
    private static final String MENU = "Модули";
    private static final int SEARCH_SLOT = 48, NEXT_SLOT = 8, FIRST = 9, LAST = 44, PAGES = 4;
    private static final int STEP_TICKS = 100, ANSWER_TICKS = 60, EMPTY_TICKS = 40;
    private static final Pattern ID = Pattern.compile("^ID: (\\S+/\\S+)$");
    private static final Pattern STATS = Pattern.compile("(\\d+) ☆\\s+(\\d+) ↓");

    private enum Step { MENU, DIALOG, RESULTS, ANSWER, INSTALL }

    private static final class Job {
        final Modules.Module module;
        final boolean star;
        final Screen home;
        final BiConsumer<Boolean, String> done;
        long startedAt = System.currentTimeMillis();
        Step step = Step.MENU;
        int ticks, pages;
        boolean asked;

        Job(Modules.Module module, boolean star, Screen home, BiConsumer<Boolean, String> done) {
            this.module = module;
            this.star = star;
            this.home = home;
            this.done = done;
        }
    }

    private static Job job;
    private static Map<String, String> ids;
    private static Map<String, Integer> stars;

    private ModuleActions() {}

    public static boolean busy() { return job != null; }

    public static String me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "" : mc.player.getUUID().toString();
    }

    public static String idOf(Modules.Module m) { return known().get(m.id); }

    public static void star(Modules.Module m, Screen home, BiConsumer<Boolean, String> done) {
        start(new Job(m, true, home, done));
    }

    public static void install(Modules.Module m, Screen home, BiConsumer<Boolean, String> done) {
        String id = idOf(m);
        if (id == null) {
            start(new Job(m, false, home, done));
            return;
        }
        if (job != null) {
            done.accept(false, "подожди — меню /module ещё занято");
            return;
        }
        Job next = new Job(m, false, home, done);
        job = next;
        load(id);
        next.startedAt = System.currentTimeMillis();
        next(next, Step.INSTALL);
    }

    public static boolean starred(Modules.Module m) { return starMarks().containsKey(m.id); }

    public static int stars(Modules.Module m) {
        Integer base = starMarks().get(m.id);
        return base == null ? m.stars : Math.max(m.stars, base + 1);
    }

    private static void start(Job next) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener net = mc.getConnection();
        if (net == null) {
            next.done.accept(false, "модули ставятся только на сервере JustMC");
            return;
        }
        if (job != null) {
            next.done.accept(false, "подожди — меню /module ещё занято");
            return;
        }
        job = next;
        if (next.home != null) Veil.show(next.home);
        else mc.gui.setScreen(null);
        net.sendCommand("module");
    }

    static void tick(Minecraft mc) {
        Job j = job;
        if (j == null) return;
        if (++j.ticks > (j.step == Step.ANSWER ? ANSWER_TICKS : STEP_TICKS)) {
            if (j.step == Step.INSTALL) finish(mc, true, "ставлю " + idOf(j.module));
            else finish(mc, false, j.step == Step.ANSWER ? "сервер не ответил про звезду" : "меню /module не ответило");
            return;
        }
        Screen screen = mc.gui.screen();
        switch (j.step) {
            case MENU -> {
                if (!menu(screen)) return;
                click(mc, (ContainerScreen) screen, SEARCH_SLOT, ContainerInput.PICKUP);
                next(j, Step.DIALOG);
            }
            case DIALOG -> {
                if (!(screen instanceof DialogScreen<?> dialog) || !submit(dialog, j.module.name)) return;
                next(j, Step.RESULTS);
            }
            case RESULTS -> {
                if (!menu(screen) || !searched((ContainerScreen) screen, j.module.name)) return;
                ContainerScreen menu = (ContainerScreen) screen;
                if (!loaded(menu)) {
                    if (j.ticks > EMPTY_TICKS) finish(mc, false, "«" + j.module.label + "» не нашёлся в меню /module");
                    return;
                }
                int slot = find(menu, j.module);
                if (slot < 0) {
                    if (j.pages + 1 < PAGES && named(menu, NEXT_SLOT)) {
                        j.pages++;
                        click(mc, menu, NEXT_SLOT, ContainerInput.PICKUP);
                        next(j, Step.RESULTS);
                        return;
                    }
                    finish(mc, false, "«" + j.module.label + "» не нашёлся в меню /module");
                    return;
                }
                String id = idAt(menu, slot);
                remember(j.module.id, id);
                if (!j.star) {
                    mc.player.closeContainer();
                    Veil.lift();
                    if (j.home != null) mc.gui.setScreen(j.home);
                    load(id);
                    j.startedAt = System.currentTimeMillis();
                    next(j, Step.INSTALL);
                    return;
                }
                click(mc, menu, slot, ContainerInput.QUICK_MOVE);
                next(j, Step.ANSWER);
            }
            case ANSWER -> {
                for (String line : Publish.answersSince(j.startedAt)) {
                    String low = line.toLowerCase(Locale.ROOT);
                    if (low.contains("звезд")) {
                        boolean ok = !low.contains("нельзя") && !low.contains("не удалось");
                        if (ok) markStar(j.module);
                        finish(mc, ok, ok ? "звезда стоит" : line);
                        return;
                    }
                }
            }
            case INSTALL -> {
                for (String line : Publish.answersSince(j.startedAt)) {
                    String low = line.toLowerCase(Locale.ROOT);
                    if (!low.contains("модул")) continue;
                    boolean ok = low.contains("подключ") || low.contains("установ") || low.contains("обновл");
                    finish(mc, ok && !low.contains("не ") && !low.contains("нельзя"), line);
                    return;
                }
            }
        }
    }

    private static void next(Job j, Step step) {
        j.step = step;
        j.ticks = 0;
    }

    private static void finish(Minecraft mc, boolean ok, String said) {
        Job j = job;
        job = null;
        Veil.lift();
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        if (j.home != null) mc.gui.setScreen(j.home);
        j.done.accept(ok, said);
    }

    private static Map<String, Integer> starMarks() {
        if (stars != null) return stars;
        stars = new HashMap<>();
        try {
            Path file = starsFile();
            if (file.toFile().isFile()) {
                JsonObject root = Json.read(file);
                JsonObject mine = root.has(me()) && root.get(me()).isJsonObject() ? root.getAsJsonObject(me()) : null;
                if (mine != null) for (String k : mine.keySet()) stars.put(k, mine.get(k).getAsInt());
            }
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] звёзды модулей не читаются: {}", e.toString());
        }
        return stars;
    }

    private static void markStar(Modules.Module m) {
        starMarks().put(m.id, m.stars);
        try {
            Path file = starsFile();
            JsonObject root = file.toFile().isFile() ? Json.read(file) : new JsonObject();
            JsonObject mine = new JsonObject();
            for (Map.Entry<String, Integer> e : starMarks().entrySet()) mine.addProperty(e.getKey(), e.getValue());
            root.add(me(), mine);
            Json.write(file, root);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] звёзды модулей не записались: {}", e.toString());
        }
    }

    private static Path starsFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode").resolve("module-stars.json");
    }

    private static void load(String id) {
        ClientPacketListener net = Minecraft.getInstance().getConnection();
        if (net != null) net.sendCommand("module load " + id);
    }

    private static boolean menu(Screen screen) {
        return screen instanceof ContainerScreen c && MENU.equals(c.getTitle().getString())
                && c.getMenu().slots.size() > SEARCH_SLOT && named(c, SEARCH_SLOT);
    }

    private static boolean named(ContainerScreen c, int slot) {
        return !c.getMenu().slots.get(slot).getItem().isEmpty();
    }

    private static boolean searched(ContainerScreen c, String query) {
        String want = query.toLowerCase(Locale.ROOT);
        for (String line : lore(c.getMenu().slots.get(SEARCH_SLOT).getItem()))
            if (line.toLowerCase(Locale.ROOT).contains(want)) return true;
        return false;
    }

    private static boolean submit(DialogScreen<?> dialog, String query) {
        EditBox box = null;
        Button done = null;
        ArrayDeque<GuiEventListener> queue = new ArrayDeque<>(dialog.children());
        while (!queue.isEmpty()) {
            GuiEventListener child = queue.poll();
            if (child instanceof ContainerEventHandler inner) queue.addAll(inner.children());
            if (child instanceof EditBox e && box == null) box = e;
            else if (child instanceof Button b && !(b instanceof ImageButton) && done == null) done = b;
        }
        if (box == null || done == null) return false;
        box.setValue(query);
        done.setFocused(true);
        done.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        return true;
    }

    private static boolean loaded(ContainerScreen c) {
        for (int slot = FIRST; slot <= LAST; slot++) if (idAt(c, slot) != null) return true;
        return false;
    }

    private static int find(ContainerScreen c, Modules.Module m) {
        int best = -1;
        long bestScore = Long.MAX_VALUE;
        for (int slot = FIRST; slot <= LAST; slot++) {
            String id = idAt(c, slot);
            if (id == null || !id.substring(id.indexOf('/') + 1).equalsIgnoreCase(m.name)) continue;
            long score = 0;
            for (String line : lore(c.getMenu().slots.get(slot).getItem())) {
                Matcher s = STATS.matcher(line);
                if (s.find())
                    score = Math.abs(Long.parseLong(s.group(1)) - m.stars)
                            + Math.abs(Long.parseLong(s.group(2)) - m.installs);
            }
            if (score < bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private static String idAt(ContainerScreen c, int slot) {
        for (String line : lore(c.getMenu().slots.get(slot).getItem())) {
            Matcher m = ID.matcher(line.trim());
            if (m.matches()) return m.group(1);
        }
        return null;
    }

    private static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
    }

    private static void click(Minecraft mc, ContainerScreen c, int slot, ContainerInput input) {
        mc.gameMode.handleContainerInput(c.getMenu().containerId, slot, 0, input, mc.player);
    }

    private static Map<String, String> known() {
        if (ids != null) return ids;
        ids = new HashMap<>();
        try {
            Path file = file();
            if (file.toFile().isFile()) {
                JsonObject root = Json.read(file);
                for (String k : root.keySet()) ids.put(k, root.get(k).getAsString());
            }
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] список ID модулей не читается: {}", e.toString());
        }
        return ids;
    }

    private static void remember(String module, String id) {
        if (id == null || id.equals(known().get(module))) return;
        known().put(module, id);
        JsonObject root = new JsonObject();
        for (Map.Entry<String, String> e : known().entrySet()) root.addProperty(e.getKey(), e.getValue());
        try {
            Json.write(file(), root);
        } catch (Exception e) {
            XeroCode.LOG.warn("[xerocode] список ID модулей не записался: {}", e.toString());
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode").resolve("module-ids.json");
    }
}
