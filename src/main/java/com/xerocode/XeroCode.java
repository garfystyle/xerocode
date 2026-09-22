package com.xerocode;

import com.xerocode.ui.EditorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.xerocode.ui.Backstage;
import com.xerocode.ui.CoverScreen;
import com.xerocode.ui.ImportScreen;
import com.xerocode.ui.LocationPick;
import com.xerocode.ui.Tape;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
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
    private static final int SETTLE_EVERY = 20;
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
    private int settling;
    private int waitingDev;
    private int holdScreen;
    private EditorScreen holding;
    private boolean restoring;
    private int coverTicks;
    private CoverScreen cover;
    private Runnable coverDone;
    private net.minecraft.world.level.GameType coverMode;
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
        return Minecraft.getInstance().gameDirectory.toPath().resolve("xerocode/own-worlds.txt");
    }

    private static String plotId(ClientLevel world) {
        if (world == null) return "";
        String path = world.dimension().identifier().getPath();
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
        } catch (Exception e) {
            LOG.warn("[xerocode] не удалось записать список своих миров", e);
        }
    }

    private static String scriptPlot;

    public static Script script() {
        if (script == null) {
            scriptPlot = plotId(Minecraft.getInstance().level);
            script = Script.load(scriptPlot);
        }
        return script;
    }

    private void switchWorld(Minecraft client, ClientLevel world) {
        String plot = plotId(world);
        if (script == null) { scriptPlot = plot; return; }
        if (plot.equals(scriptPlot)) return;
        script.save();
        scriptPlot = plot;
        script = Script.load(plot);
        Sync.forget();
        History.clear();
        if (client.gui.screen() instanceof EditorScreen) client.gui.setScreen(new EditorScreen(script));
        holding = null;
    }

    private boolean openKeyDown(Minecraft client) {
        return keyDown(client, Settings.Hot.OPEN);
    }

    private boolean keyDown(Minecraft client, Settings.Hot hot) {
        Settings settings = Settings.get();
        int code = settings.code(hot);
        if (code == Settings.NONE || client.getWindow() == null) return false;
        if (!InputConstants.isKeyDown(client.getWindow(), code)) return false;
        return modsHeld(client) == settings.mods(hot);
    }

    private static int modsHeld(Minecraft client) {
        int mods = 0;
        if (down(client, GLFW.GLFW_KEY_LEFT_CONTROL) || down(client, GLFW.GLFW_KEY_RIGHT_CONTROL))
            mods |= Settings.CTRL;
        if (down(client, GLFW.GLFW_KEY_LEFT_SHIFT) || down(client, GLFW.GLFW_KEY_RIGHT_SHIFT))
            mods |= Settings.SHIFT;
        if (down(client, GLFW.GLFW_KEY_LEFT_ALT) || down(client, GLFW.GLFW_KEY_RIGHT_ALT))
            mods |= Settings.ALT;
        return mods;
    }

    private static boolean down(Minecraft client, int code) {
        return InputConstants.isKeyDown(client.getWindow(), code);
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
        Placeholders.load();

        Tape.register();
        LevelRenderEvents.START_MAIN.register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (holdScreen <= 0 || holding == null || mc.gui.screen() != null) return;
            if (picking()) return;
            restore(mc);
        });

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
            switchWorld(client, world);
            dropCover(client, "измерение сменилось");
            if (waitingDev > 0 && Codespace.inDev(world) && holding == null) allowed(client);
            if (holdScreen > 0 && holding != null && !picking()) client.gui.setScreen(holding);
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && Download.heard(message)) return false;
            if (waitingDev <= 0 || overlay) return true;
            String text = message.getString();
            if (text.contains(ENTERED)) {
                allowed(Minecraft.getInstance());
                return false;
            }
            for (String no : DENIED) {
                if (!text.contains(no)) continue;
                waitingDev = 0;
                notMyWorld(Minecraft.getInstance());
                return false;
            }
            return true;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            Codespace.serverSaid(text);
            if (overlay) return;
            Publish.noteChat(text);
            restartHeard(text);
            if (text.contains("режиме строительства")) worldModeNow = "build";
            else if (text.contains("режиме игры")) worldModeNow = "play";
            else if (text.contains(ENTERED)) worldModeNow = "dev";
            if (cover != null) for (String said : MODE_SAID)
                if (text.contains(said)) {
                    dropCover(Minecraft.getInstance(), "сервер: " + said);
                    break;
                }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (holdScreen <= 0 || holding == null || restoring || picking()) return;
            if (screen == holding || screen instanceof ImportScreen) return;
            restoring = true;
            client.gui.setScreen(holding);
            restoring = false;
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Collab.stop();
            if (script == null) return;
            if (client.gui.screen() instanceof EditorScreen editor) editor.rememberView();
            script.save();
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP,
                Identifier.fromNamespaceAndPath("xerocode", "location_pick"), (ctx, tick) -> LocationPick.render(ctx));
        LevelRenderEvents.COLLECT_SUBMITS.register(LocationPick::renderWorld);

        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                LocationPick.active() && world.isClientSide() ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                LocationPick.active() && world.isClientSide() ? InteractionResult.FAIL : InteractionResult.PASS);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                LocationPick.interceptHands(world));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                LocationPick.interceptHands(world));
        UseItemCallback.EVENT.register((player, world, hand) ->
                LocationPick.interceptHands(world));

        ClientTickEvents.START_CLIENT_TICK.register(LocationPick::startTick);

        ClientTickEvents.START_CLIENT_TICK.register(this::stealHotkeys);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Backstage.guard(client);
            Codespace.watch();
            Collab.tick();
            Market.tick();
            Publish.tick();
            if (cover != null) {
                if (client.gui.screen() == null) client.gui.setScreen(cover);
                var now = client.gameMode == null ? null
                        : client.gameMode.getPlayerMode();
                if (coverMode != null && now != null && now != coverMode)
                    dropCover(client, "сменился игровой режим: " + coverMode + " → " + now);
                else if (--coverTicks <= 0) dropCover(client, "сервер не ответил");
            }
            LocationPick.tick(client);
            boolean openRaw = openKeyDown(client);
            if (openRaw && !openWasDown && client.gui.screen() == null) pressed(client);
            openWasDown = openRaw;
            boolean playRaw = keyDown(client, Settings.Hot.PLAY);
            if (playRaw && !playWasDown && client.gui.screen() == null) worldMode(client, "play");
            playWasDown = playRaw;
            boolean buildRaw = keyDown(client, Settings.Hot.BUILD);
            if (buildRaw && !buildWasDown && client.gui.screen() == null) worldMode(client, "build");
            buildWasDown = buildRaw;
            boolean againRaw = keyDown(client, Settings.Hot.RESTART);
            if (againRaw && !againWasDown && client.gui.screen() == null) restartWorld(client);
            againWasDown = againRaw;
            restartTick(client);
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
                if (++settling >= SETTLE_EVERY) {
                    settling = 0;
                    Sync.settle(script(), client.level);
                }
            }
            if (holdScreen > 0) hold(client);
            wasInDev = inDev;
        });
    }

    private void stealHotkeys(Minecraft client) {
        steal(client, Settings.Hot.PLAY);
        steal(client, Settings.Hot.BUILD);
        steal(client, Settings.Hot.RESTART);
        stealNarrator(client);
    }

    private void steal(Minecraft client, Settings.Hot hot) {
        Settings settings = Settings.get();
        int code = settings.code(hot);
        if (code == Settings.NONE || settings.mods(hot) == 0
                || modsHeld(client) != settings.mods(hot)) return;
        String key = InputConstants.Type.KEYSYM.getOrCreate(code).getName();
        for (net.minecraft.client.KeyMapping kb : client.options.keyMappings) {
            if (!kb.saveString().equals(key)) continue;
            while (kb.consumeClick()) { }
            kb.setDown(false);
        }
    }

    private void stealNarrator(Minecraft client) {
        Settings settings = Settings.get();
        boolean ours = usesCtrlB(settings, Settings.Hot.BUILD) || usesCtrlB(settings, Settings.Hot.PLAY);
        var option = client.options.narratorHotkey();
        if (ours && option.get()) {
            narratorWas = true;
            option.set(false);
            client.options.save();
        } else if (!ours && narratorWas != null) {
            option.set(narratorWas);
            client.options.save();
            narratorWas = null;
        }
    }

    private static boolean usesCtrlB(Settings settings, Settings.Hot hot) {
        return settings.code(hot) == GLFW.GLFW_KEY_B && settings.mods(hot) == Settings.CTRL;
    }

    private int restartStage, restartWait, restartTries;
    private String restartMode = "", restartWorldKey = "";
    private boolean againWasDown;

    public static final String RESTART = "restart";

    public static void restart() {
        if (INSTANCE != null) INSTANCE.restartWorld(Minecraft.getInstance());
    }

    private void restartWorld(Minecraft client) {
        if (client.getConnection() == null) return;
        if (!ownWorld(client)) {
            client.gui.hud.setTimes(3, 40, 8);
            client.gui.hud.setTitle(Component.literal("Вы не в своём мире").withStyle(ChatFormatting.RED));
            client.gui.hud.setSubtitle(Component.literal("мир перезапускается только в своём"));
            return;
        }
        if (script != null) script.save();
        restartWait = 0;
        restartTries = 0;
        restartMode = modeOf(client);
        restartWorldKey = worldOf(client);
        if ("build".equals(worldModeNow)) {
            sendPlay(client);
            return;
        }
        restartStage = 1;
        client.getConnection().sendCommand("build");
        cover("Перезапуск мира…", null);
    }

    private static String modeOf(Minecraft client) {
        return client.gameMode == null ? ""
                : String.valueOf(client.gameMode.getPlayerMode());
    }

    private static String worldOf(Minecraft client) {
        return client.level == null ? "" : client.level.dimension().identifier().toString();
    }

    private boolean stepDone(Minecraft client, String want) {
        if (want.equals(worldModeNow)) return true;
        if (!modeOf(client).equals(restartMode)) return true;
        if (!worldOf(client).equals(restartWorldKey)) return true;
        return restartWait > RESTART_WAIT;
    }

    private static final int RESTART_WAIT = 40, RESTART_PAUSE = 30, RESTART_TRIES = 3;
    private static final String TOO_FAST = "Подождите перед повторной";

    private void restartHeard(String text) {
        if (restartStage == 0 || !text.contains(TOO_FAST)) return;
        restartStage = 3;
        restartWait = 0;
    }

    private void sendPlay(Minecraft client) {
        restartStage = 2;
        restartWait = 0;
        restartTries++;
        restartMode = modeOf(client);
        restartWorldKey = worldOf(client);
        client.getConnection().sendCommand("play");
        cover("Запуск мира…", null);
    }

    private void restartTick(Minecraft client) {
        if (restartStage == 0) return;
        if (client.getConnection() == null) {
            restartStage = 0;
            return;
        }
        restartWait++;
        if (restartStage == 1) {
            if (!stepDone(client, "build")) return;
            restartStage = 3;
            restartWait = 0;
            return;
        }
        if (restartStage == 3) {
            if (restartWait < RESTART_PAUSE) return;
            if (restartTries >= RESTART_TRIES) {
                restartStage = 0;
                return;
            }
            sendPlay(client);
            return;
        }
        if ("play".equals(worldModeNow) || !modeOf(client).equals(restartMode)) {
            restartStage = 0;
            return;
        }
        if (restartWait > RESTART_WAIT * 2) {
            if (restartTries >= RESTART_TRIES) {
                restartStage = 0;
                return;
            }
            restartStage = 3;
            restartWait = 0;
        }
    }

    private void worldMode(Minecraft client, String command) {
        if (client.getConnection() == null) return;
        if (command.equals(worldModeNow)) {
            client.gui.hud.setOverlayMessage(Component.literal(
                    "play".equals(command) ? "Уже в режиме игры" : "Уже в режиме строительства")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (!ownWorld(client)) {
            client.gui.hud.setTimes(3, 40, 8);
            client.gui.hud.setTitle(Component.literal("Вы не в своём мире").withStyle(ChatFormatting.RED));
            client.gui.hud.setSubtitle(Component.literal("режимы мира переключаются только в своём"));
            return;
        }
        if (script != null) script.save();
        client.getConnection().sendCommand(command);
        cover("play".equals(command) ? "Запуск мира…" : "Режим строительства…", null);
    }

    private static boolean ownWorld(Minecraft client) {
        if (client.level == null) return false;
        if (client.isLocalServer() || client.getConnection() == null) return true;
        if (Codespace.inDev(client.level)) return true;
        String plot = plotId(client.level);
        return !plot.isEmpty() && OWN.contains(plot);
    }

    public static void cover(String label, Runnable done) {
        if (INSTANCE == null) return;
        Minecraft client = Minecraft.getInstance();
        INSTANCE.holdScreen = 0;
        INSTANCE.holding = null;
        INSTANCE.cover = new CoverScreen(label);
        INSTANCE.coverDone = done;
        INSTANCE.coverTicks = 30;
        INSTANCE.coverMode = client.gameMode == null ? null
                : client.gameMode.getPlayerMode();
        client.gui.setScreen(INSTANCE.cover);
    }

    public static void coverDismissed() {
        if (INSTANCE == null || INSTANCE.cover == null) return;
        INSTANCE.cover = null;
        INSTANCE.coverDone = null;
        INSTANCE.coverTicks = 0;
        INSTANCE.coverMode = null;
    }

    private void dropCover(Minecraft client, String why) {
        if (cover == null) return;
        Runnable done = coverDone;
        cover = null;
        coverDone = null;
        coverTicks = 0;
        if (client.gui.screen() instanceof CoverScreen) client.gui.setScreen(null);
        if (done != null) done.run();
    }

    public static void openCanvas(Minecraft client) {
        if (INSTANCE == null) { open(client); return; }
        INSTANCE.pressed(client);
    }

    private void pressed(Minecraft client) {
        if (LocationPick.active()) { LocationPick.cancel(); return; }
        if (inDev(client)) { open(client); return; }
        if (client.isLocalServer() || client.getConnection() == null) { open(client); return; }
        if (waitingDev > 0) return;

        String plot = plotId(client.level);
        boolean known = !plot.isEmpty() && OWN.contains(plot);
        holding = null;
        holdScreen = 0;
        waitingDev = DEV_WAIT;
        client.getConnection().sendCommand("dev");
        if (known) allowed(client);
    }

    private void allowed(Minecraft client) {
        if (holding != null) return;
        waitingDev = 0;
        open(client);
        holding = client.gui.screen() instanceof EditorScreen e ? e : null;
        holdScreen = DEV_WAIT;
    }

    public static void canvasClosed() {
        if (INSTANCE == null) return;
        INSTANCE.holdScreen = 0;
        INSTANCE.holding = null;
    }

    private void restore(Minecraft client) {
        if (restoring || holding == null) return;
        restoring = true;
        client.execute(() -> {
            restoring = false;
            if (holdScreen > 0 && holding != null && client.gui.screen() != holding
                    && !(client.gui.screen() instanceof ImportScreen)) {
                client.gui.setScreen(holding);
            }
        });
    }

    private void hold(Minecraft client) {
        if (--holdScreen <= 0 || holding == null) return;
        if (picking()) return;
        if (client.gui.screen() == holding || client.gui.screen() instanceof ImportScreen) return;
        client.gui.setScreen(holding);
    }

    private static boolean picking() {
        return LocationPick.active();
    }

    private void notMyWorld(Minecraft client) {
        holdScreen = 0;
        boolean hadCanvas = client.gui.screen() instanceof EditorScreen;
        holding = null;
        if (hadCanvas) client.gui.setScreen(null);
        client.gui.hud.setTimes(3, 60, 10);
        client.gui.hud.setTitle(Component.literal("Вы не в своём мире")
                .withStyle(ChatFormatting.RED));
        client.gui.hud.setSubtitle(Component.literal("Кодинг открывается только в своём"));
    }

    private void entered(Minecraft client) {
        waited = 0;
        rememberOwn(plotId(client.level));
        if (!Settings.canvasMode()) {
            client.gui.hud.setOverlayMessage(Component.literal("2D-редактор — "
                    + Settings.get().label(Settings.Hot.OPEN)).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (!script().roots.isEmpty() && client.gui.screen() == null && holding == null)
            open(client);
        pending = SCAN_DELAY;
    }

    private void offerImport(Minecraft client) {
        boolean busy = client.gui.screen() != null && !(client.gui.screen() instanceof EditorScreen);
        if (client.level == null || busy) return;
        if (!inDev(client)) return;
        if (!Codespace.chunksReady(client.level) && ++waited < SCAN_RETRIES) {
            pending = SCAN_DELAY;
            return;
        }
        List<BlockPos> lines = Codespace.lines(client.level);
        if (script().roots.isEmpty()) {
            if (lines.isEmpty()) { open(client); return; }
            ready();
            client.gui.setScreen(new ImportScreen(script(), lines));
            return;
        }
        if (lines.isEmpty() || Sync.hushed(script())) return;
        if (!Sync.state(script(), client.level).risky()) return;
        ready();
        client.gui.setScreen(new ImportScreen(script(), lines, ImportScreen.Mode.DIVERGED));
    }

    private static void preload() {
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
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warn("[xerocode] could not preload the mod classes", e);
        }
    }

    private static String className(Path root, Path path) {
        String rel = root.relativize(path).toString().replace('\\', '/');
        if (!rel.endsWith(".class")) return null;
        String name = rel.substring(0, rel.length() - 6).replace('/', '.');
        return name.startsWith("com.xerocode") ? name : null;
    }

    private static boolean inDev(Minecraft client) {
        return Codespace.inDev(client.level);
    }

    private static void ready() {
        if (!Catalog.loaded()) Catalog.load();
        Settings.get().apply();
        if (!Values.loaded()) Values.load();
        if (!Pickers.loaded()) Pickers.load();
        if (!Mapping.loaded()) Mapping.load();
        if (!Placeholders.loaded()) Placeholders.load();
    }

    private static void open(Minecraft client) {
        ready();
        Settings settings = Settings.get();
        if (settings.mode != Settings.Mode.CANVAS) {
            settings.mode = Settings.Mode.CANVAS;
            settings.save();
        }
        client.gui.setScreen(new EditorScreen(script()));
    }
}
