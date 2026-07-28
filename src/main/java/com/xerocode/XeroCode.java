package com.xerocode;

import com.xerocode.ui.EditorScreen;
import com.xerocode.ui.CoverScreen;
import com.xerocode.ui.ImportScreen;
import com.xerocode.ui.LocationPick;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;

public final class XeroCode implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("xerocode");

    private static final int SCAN_DELAY = 40;
    private static final int SCAN_RETRIES = 5;
    private static final int DEV_WAIT = 60;

    private static Script script;
    private static XeroCode INSTANCE;
    private boolean openWasDown;
    private boolean playWasDown, buildWasDown;
    private Boolean narratorWas;
    private boolean wasInDev;
    private int pending;
    private int waited;
    private int waitingDev;
    private int holdScreen;
    private EditorScreen holding;
    private boolean restoring;
    private int coverTicks;
    private CoverScreen cover;
    private Runnable coverDone;
    private net.minecraft.world.GameMode coverMode;
    private String worldModeNow = "";

    private static final String[] DENIED = {
            "нет прав",
            "Вы не в мире",
            "Неизвестная или неполная команда"
    };
    private static final String ENTERED = "режим изменения кода";
    private static final String[] MODE_SAID = {
            "режиме строительства", "режиме игры", "нет прав", "Вы не в мире",
            "Неизвестная или неполная команда"
    };

    private static final Set<String> OWN = new LinkedHashSet<>();

    private static Path ownFile() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("xerocode/own-worlds.txt");
    }

    private static String plotId(ClientWorld world) {
        if (world == null) return "";
        String path = world.getRegistryKey().getValue().getPath();
        if (!path.startsWith("world_")) return "";
        int cut = path.indexOf("_" + Codespace.DEV_DIMENSION);
        return cut < 0 ? path : path.substring(0, cut);
    }

    private static void loadOwn() {
        try {
            Path file = ownFile();
            if (Files.exists(file)) OWN.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
            OWN.removeIf(String::isBlank);
        } catch (Exception e) {
            LOG.warn("[xerocode] не удалось прочитать список своих миров", e);
        }
    }

    private static void rememberOwn(String id) {
        if (id.isEmpty() || !OWN.add(id)) return;
        try {
            Files.createDirectories(ownFile().getParent());
            Files.write(ownFile(), OWN, StandardCharsets.UTF_8);
            LOG.info("[xerocode] свой мир запомнен: {}", id);
        } catch (Exception e) {
            LOG.warn("[xerocode] не удалось записать список своих миров", e);
        }
    }

    private static String scriptPlot;

    public static Script script() {
        if (script == null) {
            scriptPlot = plotId(MinecraftClient.getInstance().world);
            script = Script.load(scriptPlot);
        }
        return script;
    }

    private void switchWorld(MinecraftClient client, ClientWorld world) {
        String plot = plotId(world);
        if (script == null) { scriptPlot = plot; return; }
        if (plot.equals(scriptPlot)) return;
        script.save();
        LOG.info("[xerocode] мир сменился: {} → {}",
                scriptPlot == null || scriptPlot.isEmpty() ? "черновик" : scriptPlot,
                plot.isEmpty() ? "черновик" : plot);
        scriptPlot = plot;
        script = Script.load(plot);
        History.clear();
        if (client.currentScreen instanceof EditorScreen) client.setScreen(new EditorScreen(script));
        holding = null;
    }

    private boolean openKeyDown(MinecraftClient client) {
        return keyDown(client, Settings.Hot.OPEN);
    }

    private boolean keyDown(MinecraftClient client, Settings.Hot hot) {
        Settings settings = Settings.get();
        int code = settings.code(hot);
        if (code == Settings.NONE || client.getWindow() == null) return false;
        if (!InputUtil.isKeyPressed(client.getWindow(), code)) return false;
        return modsHeld(client) == settings.mods(hot);
    }

    private static int modsHeld(MinecraftClient client) {
        int mods = 0;
        if (down(client, GLFW.GLFW_KEY_LEFT_CONTROL) || down(client, GLFW.GLFW_KEY_RIGHT_CONTROL))
            mods |= Settings.CTRL;
        if (down(client, GLFW.GLFW_KEY_LEFT_SHIFT) || down(client, GLFW.GLFW_KEY_RIGHT_SHIFT))
            mods |= Settings.SHIFT;
        if (down(client, GLFW.GLFW_KEY_LEFT_ALT) || down(client, GLFW.GLFW_KEY_RIGHT_ALT))
            mods |= Settings.ALT;
        return mods;
    }

    private static boolean down(MinecraftClient client, int code) {
        return InputUtil.isKeyPressed(client.getWindow(), code);
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        preload();
        loadOwn();
        Catalog.load();
        Values.load();
        Pickers.load();
        Mapping.load();

        WorldRenderEvents.START_MAIN.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (holdScreen <= 0 || holding == null || mc.currentScreen != null) return;
            if (picking()) return;
            restore(mc);
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            switchWorld(client, world);
            dropCover(client, "измерение сменилось");
            if (waitingDev > 0 && Codespace.inDev(world) && holding == null) allowed(client);
            if (holdScreen > 0 && holding != null && !picking()) client.setScreen(holding);
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (waitingDev <= 0 || overlay) return true;
            String text = message.getString();
            if (text.contains(ENTERED)) {
                allowed(MinecraftClient.getInstance());
                return false;
            }
            for (String no : DENIED) {
                if (!text.contains(no)) continue;
                waitingDev = 0;
                notMyWorld(MinecraftClient.getInstance());
                return false;
            }
            return true;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String text = message.getString();
            Publish.noteChat(text);
            if (text.contains("режиме строительства")) worldModeNow = "build";
            else if (text.contains("режиме игры")) worldModeNow = "play";
            else if (text.contains(ENTERED)) worldModeNow = "dev";
            if (cover != null) for (String said : MODE_SAID)
                if (text.contains(said)) {
                    dropCover(MinecraftClient.getInstance(), "сервер: " + said);
                    break;
                }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (holdScreen <= 0 || holding == null || restoring || picking()) return;
            if (screen == holding || screen instanceof ImportScreen) return;
            restoring = true;
            client.setScreen(holding);
            restoring = false;
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (script == null) return;
            if (client.currentScreen instanceof EditorScreen editor) editor.rememberView();
            script.save();
            LOG.info("[xerocode] скрипт сохранён при выходе из игры");
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP,
                Identifier.of("xerocode", "location_pick"), (ctx, tick) -> LocationPick.render(ctx));
        WorldRenderEvents.AFTER_ENTITIES.register(LocationPick::renderWorld);

        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                LocationPick.active() && world.isClient() ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                LocationPick.active() && world.isClient() ? ActionResult.FAIL : ActionResult.PASS);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                LocationPick.interceptHands(world));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                LocationPick.interceptHands(world));
        UseItemCallback.EVENT.register((player, world, hand) ->
                LocationPick.interceptHands(world));

        ClientTickEvents.START_CLIENT_TICK.register(LocationPick::startTick);

        ClientTickEvents.START_CLIENT_TICK.register(this::stealHotkeys);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (cover != null) {
                if (client.currentScreen == null) client.setScreen(cover);
                var now = client.interactionManager == null ? null
                        : client.interactionManager.getCurrentGameMode();
                if (coverMode != null && now != null && now != coverMode)
                    dropCover(client, "сменился игровой режим: " + coverMode + " → " + now);
                else if (--coverTicks <= 0) dropCover(client, "сервер не ответил");
            }
            LocationPick.tick(client);
            boolean openRaw = openKeyDown(client);
            if (openRaw && !openWasDown && client.currentScreen == null) pressed(client);
            openWasDown = openRaw;
            boolean playRaw = keyDown(client, Settings.Hot.PLAY);
            if (playRaw && !playWasDown && client.currentScreen == null) worldMode(client, "play");
            playWasDown = playRaw;
            boolean buildRaw = keyDown(client, Settings.Hot.BUILD);
            if (buildRaw && !buildWasDown && client.currentScreen == null) worldMode(client, "build");
            buildWasDown = buildRaw;
            boolean inDev = inDev(client);
            if (inDev) worldModeNow = "dev";
            if (inDev && !wasInDev) entered(client);
            if (!inDev) pending = 0;
            if (pending > 0 && --pending == 0) offerImport(client);
            if (waitingDev > 0 && --waitingDev == 0 && !inDev) {
                notMyWorld(client);
            }
            if (inDev) {
                waitingDev = 0;
                if (holdScreen > 20) holdScreen = 20;
            }
            if (holdScreen > 0) hold(client);
            wasInDev = inDev;
        });
    }

    private void stealHotkeys(MinecraftClient client) {
        steal(client, Settings.Hot.PLAY);
        steal(client, Settings.Hot.BUILD);
        stealNarrator(client);
    }

    private void steal(MinecraftClient client, Settings.Hot hot) {
        Settings settings = Settings.get();
        int code = settings.code(hot);
        if (code == Settings.NONE || settings.mods(hot) == 0
                || modsHeld(client) != settings.mods(hot)) return;
        String key = InputUtil.Type.KEYSYM.createFromCode(code).getTranslationKey();
        for (net.minecraft.client.option.KeyBinding kb : client.options.allKeys) {
            if (!kb.getBoundKeyTranslationKey().equals(key)) continue;
            while (kb.wasPressed()) { }
            kb.setPressed(false);
        }
    }

    private void stealNarrator(MinecraftClient client) {
        Settings settings = Settings.get();
        boolean ours = usesCtrlB(settings, Settings.Hot.BUILD) || usesCtrlB(settings, Settings.Hot.PLAY);
        var option = client.options.getNarratorHotkey();
        if (ours && option.getValue()) {
            narratorWas = true;
            option.setValue(false);
            client.options.write();
            LOG.info("[xerocode] Ctrl+B занят «Строительством» — ванильный диктор по этому сочетанию выключен");
        } else if (!ours && narratorWas != null) {
            option.setValue(narratorWas);
            client.options.write();
            LOG.info("[xerocode] Ctrl+B больше не наш — горячая клавиша диктора возвращена");
            narratorWas = null;
        }
    }

    private static boolean usesCtrlB(Settings settings, Settings.Hot hot) {
        return settings.code(hot) == GLFW.GLFW_KEY_B && settings.mods(hot) == Settings.CTRL;
    }

    private void worldMode(MinecraftClient client, String command) {
        if (client.getNetworkHandler() == null) return;
        if (command.equals(worldModeNow)) {
            client.inGameHud.setOverlayMessage(Text.literal(
                    "play".equals(command) ? "Уже в режиме игры" : "Уже в режиме строительства")
                    .formatted(Formatting.GRAY), false);
            return;
        }
        if (!ownWorld(client)) {
            client.inGameHud.setTitleTicks(3, 40, 8);
            client.inGameHud.setTitle(Text.literal("Вы не в своём мире").formatted(Formatting.RED));
            client.inGameHud.setSubtitle(Text.literal("режимы мира переключаются только в своём"));
            return;
        }
        if (script != null) script.save();
        client.getNetworkHandler().sendChatCommand(command);
        cover("play".equals(command) ? "Запуск мира…" : "Режим строительства…", null);
        LOG.info("[xerocode] режим мира из игры: /{}", command);
    }

    private static boolean ownWorld(MinecraftClient client) {
        if (client.world == null) return false;
        if (client.isInSingleplayer() || client.getNetworkHandler() == null) return true;
        if (Codespace.inDev(client.world)) return true;
        String plot = plotId(client.world);
        return !plot.isEmpty() && OWN.contains(plot);
    }

    public static void cover(String label, Runnable done) {
        if (INSTANCE == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        INSTANCE.holdScreen = 0;
        INSTANCE.holding = null;
        INSTANCE.cover = new CoverScreen(label);
        INSTANCE.coverDone = done;
        INSTANCE.coverTicks = 30;
        INSTANCE.coverMode = client.interactionManager == null ? null
                : client.interactionManager.getCurrentGameMode();
        client.setScreen(INSTANCE.cover);
    }

    public static void coverDismissed() {
        if (INSTANCE == null || INSTANCE.cover == null) return;
        INSTANCE.cover = null;
        INSTANCE.coverDone = null;
        INSTANCE.coverTicks = 0;
        INSTANCE.coverMode = null;
    }

    private void dropCover(MinecraftClient client, String why) {
        if (cover == null) return;
        Runnable done = coverDone;
        cover = null;
        coverDone = null;
        coverTicks = 0;
        if (client.currentScreen instanceof CoverScreen) client.setScreen(null);
        if (done != null) done.run();
    }

    public static void openCanvas(MinecraftClient client) {
        if (INSTANCE == null) { open(client); return; }
        INSTANCE.pressed(client);
    }

    private void pressed(MinecraftClient client) {
        if (LocationPick.active()) { LocationPick.cancel(); return; }
        if (inDev(client)) { open(client); return; }
        if (client.isInSingleplayer() || client.getNetworkHandler() == null) { open(client); return; }
        if (waitingDev > 0) return;

        String plot = plotId(client.world);
        boolean known = !plot.isEmpty() && OWN.contains(plot);
        holding = null;
        holdScreen = 0;
        waitingDev = DEV_WAIT;
        client.getNetworkHandler().sendChatCommand("dev");
        if (known) allowed(client);
    }

    private void allowed(MinecraftClient client) {
        if (holding != null) return;
        waitingDev = 0;
        open(client);
        holding = client.currentScreen instanceof EditorScreen e ? e : null;
        holdScreen = DEV_WAIT;
    }

    public static void canvasClosed() {
        if (INSTANCE == null) return;
        INSTANCE.holdScreen = 0;
        INSTANCE.holding = null;
    }

    private void restore(MinecraftClient client) {
        if (restoring || holding == null) return;
        restoring = true;
        client.execute(() -> {
            restoring = false;
            if (holdScreen > 0 && holding != null && client.currentScreen != holding
                    && !(client.currentScreen instanceof ImportScreen)) {
                client.setScreen(holding);
            }
        });
    }

    private void hold(MinecraftClient client) {
        if (--holdScreen <= 0 || holding == null) return;
        if (picking()) return;
        if (client.currentScreen == holding || client.currentScreen instanceof ImportScreen) return;
        client.setScreen(holding);
    }

    private static boolean picking() {
        return LocationPick.active();
    }

    private void notMyWorld(MinecraftClient client) {
        holdScreen = 0;
        boolean hadCanvas = client.currentScreen instanceof EditorScreen;
        holding = null;
        if (hadCanvas) client.setScreen(null);
        client.inGameHud.setTitleTicks(3, 60, 10);
        client.inGameHud.setTitle(Text.literal("Вы не в своём мире")
                .formatted(Formatting.RED));
        client.inGameHud.setSubtitle(Text.literal("Кодинг открывается только в своём"));
    }

    private void entered(MinecraftClient client) {
        waited = 0;
        rememberOwn(plotId(client.world));
        if (!Settings.canvasMode()) {
            client.inGameHud.setOverlayMessage(Text.literal("2D-редактор — "
                    + Settings.get().label(Settings.Hot.OPEN)).formatted(Formatting.GRAY), false);
            return;
        }
        if (script().roots.isEmpty()) pending = SCAN_DELAY;
        else if (client.currentScreen == null && holding == null) open(client);
    }

    private void offerImport(MinecraftClient client) {
        boolean busy = client.currentScreen != null && !(client.currentScreen instanceof EditorScreen);
        if (client.world == null || busy) return;
        if (!inDev(client) || !script().roots.isEmpty()) return;
        if (!Codespace.chunksReady(client.world) && ++waited < SCAN_RETRIES) {
            pending = SCAN_DELAY;
            return;
        }
        List<BlockPos> lines = Codespace.lines(client.world);
        if (lines.isEmpty()) { open(client); return; }
        ready();
        client.setScreen(new ImportScreen(script(), lines));
    }

    private static void preload() {
        long started = System.nanoTime();
        int loaded = 0;
        try {
            ModContainer mod = FabricLoader.getInstance().getModContainer("xerocode").orElse(null);
            if (mod == null) return;
            ClassLoader loader = XeroCode.class.getClassLoader();
            for (Path root : mod.getRootPaths()) {
                try (Stream<Path> tree = Files.walk(root)) {
                    for (Path path : tree.toList()) {
                        String name = className(root, path);
                        if (name == null) continue;
                        try {
                            Class.forName(name, false, loader);
                            loaded++;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warn("[xerocode] could not preload the mod classes", e);
            return;
        }
        LOG.info("[xerocode] preloaded {} classes in {} ms",
                loaded, (System.nanoTime() - started) / 1_000_000);
    }

    private static String className(Path root, Path path) {
        String rel = root.relativize(path).toString().replace('\\', '/');
        if (!rel.endsWith(".class")) return null;
        String name = rel.substring(0, rel.length() - 6).replace('/', '.');
        return name.startsWith("com.xerocode") ? name : null;
    }

    private static boolean inDev(MinecraftClient client) {
        return Codespace.inDev(client.world);
    }

    private static void ready() {
        if (!Catalog.loaded()) Catalog.load();
        Settings.get().apply();
        if (!Values.loaded()) Values.load();
        if (!Pickers.loaded()) Pickers.load();
        if (!Mapping.loaded()) Mapping.load();
    }

    private static void open(MinecraftClient client) {
        ready();
        Settings settings = Settings.get();
        if (settings.mode != Settings.Mode.CANVAS) {
            settings.mode = Settings.Mode.CANVAS;
            settings.save();
        }
        client.setScreen(new EditorScreen(script()));
    }
}
